/*
 * Copyright 2026 The "FreeNetRadio" Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yuriy.openradio.shared.broadcast

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the decision [BTConnectionReceiver] makes for every Bluetooth connection event it sees.
 *
 * The behaviour worth pinning down is that the first connection of a device is deliberately
 * silent. The callback exists to resume playback for a car or a headset the user has already
 * listened through in this application lifetime, so the receiver has to see the same address twice
 * before it reports anything: the first event only records the address. Starting playback on the
 * very first connection would mean the app takes over the speakers of a device it has never played
 * through.
 *
 * The other decision is which disconnection pauses: only that of a device carrying audio, whatever
 * order the devices connected in. A car stereo connects twice over, once to the adapter and once
 * over A2DP, while a smartwatch only ever connects to the adapter. The scenarios that mix them send
 * both broadcasts, as the platform does, so a receiver that listened to the adapter would fail them.
 *
 * Both broadcasts are protected, so the events are handed to [BTConnectionReceiver.onReceive]
 * directly rather than sent.
 */
@RunWith(AndroidJUnit4::class)
class BTConnectionReceiverTest {

    private lateinit var mContext: Context

    private lateinit var mListener: RecordingListener

    private lateinit var mReceiver: BTConnectionReceiver

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mListener = RecordingListener()
        mReceiver = BTConnectionReceiver(mListener)
    }

    @Test
    fun anEventWithoutADeviceIsIgnored() {
        val intent = Intent(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            .putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED)

        mReceiver.onReceive(mContext, intent)

        assertEquals(0, mListener.sameDeviceConnections)
        assertEquals(0, mListener.disconnections)
    }

    @Test
    fun theFirstConnectionOfADeviceIsSilent() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        assertEquals(0, mListener.sameDeviceConnections)
        assertEquals(0, mListener.disconnections)
    }

    @Test
    fun reconnectingTheSameDeviceIsReported() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        assertEquals(1, mListener.sameDeviceConnections)
        assertEquals(0, mListener.disconnections)
    }

    @Test
    fun connectingAnotherDeviceIsSilentAndReplacesTheRememberedAddress() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        mReceiver.onReceive(mContext, connected(SECOND_ADDRESS))

        assertEquals(0, mListener.sameDeviceConnections)

        mReceiver.onReceive(mContext, connected(SECOND_ADDRESS))

        assertEquals(1, mListener.sameDeviceConnections)
    }

    /**
     * The remembered address is the one seen last, so the device connected before it is no longer
     * the same device as far as the receiver is concerned.
     */
    @Test
    fun theDeviceConnectedBeforeTheLastOneIsNoLongerTheSameDevice() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))
        mReceiver.onReceive(mContext, connected(SECOND_ADDRESS))

        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        assertEquals(0, mListener.sameDeviceConnections)
    }

    @Test
    fun aDisconnectionBeforeAnyConnectionIsIgnored() {
        mReceiver.onReceive(mContext, disconnected(FIRST_ADDRESS))

        assertEquals(0, mListener.disconnections)
    }

    @Test
    fun aDisconnectionAfterAConnectionIsReported() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        mReceiver.onReceive(mContext, disconnected(FIRST_ADDRESS))

        assertEquals(1, mListener.disconnections)
        assertEquals(0, mListener.sameDeviceConnections)
    }

    /**
     * The service pauses on every reported disconnection, so a device that was never seen
     * connecting must not be reported.
     */
    @Test
    fun aDisconnectionOfAnotherDeviceIsIgnored() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        mReceiver.onReceive(mContext, disconnected(SECOND_ADDRESS))

        assertEquals(0, mListener.disconnections)
        assertEquals(0, mListener.sameDeviceConnections)
    }

    /**
     * Connecting a second audio device does not make the first one stop counting: whichever of them
     * disconnects, the audio it carried stops.
     */
    @Test
    fun everyConnectedAudioDeviceIsReportedWhenItDisconnects() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))
        mReceiver.onReceive(mContext, connected(SECOND_ADDRESS))

        mReceiver.onReceive(mContext, disconnected(FIRST_ADDRESS))

        assertEquals(1, mListener.disconnections)

        mReceiver.onReceive(mContext, disconnected(SECOND_ADDRESS))

        assertEquals(2, mListener.disconnections)
    }

    /**
     * The car connects, then a smartwatch does. The watch going out of range must not pause the
     * radio playing through the car.
     */
    @Test
    fun aNonAudioDeviceConnectedAfterTheAudioDeviceDoesNotPauseWhenItDisconnects() {
        audioDeviceConnects(CAR_ADDRESS)
        otherDeviceConnects(WATCH_ADDRESS)

        otherDeviceDisconnects(WATCH_ADDRESS)

        assertEquals(0, mListener.disconnections)

        audioDeviceDisconnects(CAR_ADDRESS)

        assertEquals(1, mListener.disconnections)
    }

    /**
     * Same devices, the other way round: the car's disconnection pauses even though the watch was
     * the last device to connect.
     */
    @Test
    fun theAudioDevicePausesWhenItDisconnectsAfterANonAudioDeviceConnected() {
        audioDeviceConnects(CAR_ADDRESS)
        otherDeviceConnects(WATCH_ADDRESS)

        audioDeviceDisconnects(CAR_ADDRESS)

        assertEquals(1, mListener.disconnections)

        otherDeviceDisconnects(WATCH_ADDRESS)

        assertEquals(1, mListener.disconnections)
    }

    /**
     * The watch connected first and the car after it: neither order lets the watch pause playback.
     */
    @Test
    fun aNonAudioDeviceConnectedBeforeTheAudioDeviceDoesNotPauseWhenItDisconnects() {
        otherDeviceConnects(WATCH_ADDRESS)
        audioDeviceConnects(CAR_ADDRESS)

        otherDeviceDisconnects(WATCH_ADDRESS)

        assertEquals(0, mListener.disconnections)

        audioDeviceDisconnects(CAR_ADDRESS)

        assertEquals(1, mListener.disconnections)
    }

    /**
     * A device that is not carrying audio is not the device the auto resume follows, so the car
     * coming back after the watch connected still resumes playback.
     */
    @Test
    fun aNonAudioDeviceDoesNotDisplaceTheRememberedAudioDevice() {
        audioDeviceConnects(CAR_ADDRESS)
        otherDeviceConnects(WATCH_ADDRESS)
        audioDeviceDisconnects(CAR_ADDRESS)

        audioDeviceConnects(CAR_ADDRESS)

        assertEquals(1, mListener.sameDeviceConnections)
    }

    /**
     * Ignoring another device's disconnection must not make the receiver forget the device it is
     * following, or the next reconnection of that device would no longer resume playback.
     */
    @Test
    fun aDisconnectionOfAnotherDeviceKeepsTheRememberedAddress() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))
        mReceiver.onReceive(mContext, disconnected(SECOND_ADDRESS))

        mReceiver.onReceive(mContext, disconnected(FIRST_ADDRESS))

        assertEquals(1, mListener.disconnections)

        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        assertEquals(1, mListener.sameDeviceConnections)
    }

    /**
     * A disconnected device no longer counts as connected, so a repeated disconnection does not
     * pause a second time.
     */
    @Test
    fun aDeviceNoLongerCountsAsConnectedOnceItDisconnects() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        mReceiver.onReceive(mContext, disconnected(FIRST_ADDRESS))
        mReceiver.onReceive(mContext, disconnected(FIRST_ADDRESS))

        assertEquals(1, mListener.disconnections)
    }

    @Test
    fun aDeviceThatConnectsAgainIsReportedAgainWhenItDisconnects() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))
        mReceiver.onReceive(mContext, disconnected(FIRST_ADDRESS))
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        mReceiver.onReceive(mContext, disconnected(FIRST_ADDRESS))

        assertEquals(2, mListener.disconnections)
    }

    /**
     * The whole point of the remembered address: it outlives the disconnection, so the device the
     * user has already listened through is recognised when it comes back, which is the reconnection
     * the auto resume exists for.
     */
    @Test
    fun aDeviceIsStillRememberedAfterItDisconnects() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))
        mReceiver.onReceive(mContext, disconnected(FIRST_ADDRESS))

        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        assertEquals(1, mListener.sameDeviceConnections)
    }

    @Test
    fun anIntermediateConnectionStateIsIgnored() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        mReceiver.onReceive(mContext, withState(THIRD_ADDRESS, BluetoothProfile.STATE_CONNECTING))
        mReceiver.onReceive(mContext, withState(FIRST_ADDRESS, BluetoothProfile.STATE_DISCONNECTING))

        assertEquals(0, mListener.sameDeviceConnections)
        assertEquals(0, mListener.disconnections)

        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        assertEquals(1, mListener.sameDeviceConnections)
    }

    @Test
    fun anEventWithoutAConnectionStateIsIgnored() {
        val intent = Intent(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            .putExtra(BluetoothDevice.EXTRA_DEVICE, remoteDevice(FIRST_ADDRESS))

        mReceiver.onReceive(mContext, intent)

        assertEquals(0, mListener.sameDeviceConnections)
        assertEquals(0, mListener.disconnections)
    }

    /**
     * The intent filter already keeps other actions out, and the receiver checks the action again
     * as defence in depth. An intent that carries the same extras under another action must
     * neither be reported nor be remembered as the device last seen connecting or as connected.
     */
    @Test
    fun anEventWithAnotherActionIsIgnored() {
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS).setAction(FOREIGN_ACTION))
        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS).setAction(FOREIGN_ACTION))

        assertEquals(0, mListener.sameDeviceConnections)

        mReceiver.onReceive(mContext, connected(FIRST_ADDRESS))

        assertEquals(0, mListener.sameDeviceConnections)

        mReceiver.onReceive(mContext, disconnected(SECOND_ADDRESS).setAction(FOREIGN_ACTION))

        assertEquals(0, mListener.disconnections)

        mReceiver.onReceive(mContext, connected(SECOND_ADDRESS).setAction(FOREIGN_ACTION))
        mReceiver.onReceive(mContext, disconnected(SECOND_ADDRESS))

        assertEquals(0, mListener.disconnections)
    }

    /**
     * The diagnostics report attaches the whole logcat, so a hardware address the receiver logs at
     * any level ends up in a file the user shares.
     */
    @Test
    fun noHardwareAddressIsLogged() {
        mReceiver.onReceive(mContext, connected(LOGGED_ADDRESS))
        mReceiver.onReceive(mContext, connected(LOGGED_ADDRESS))
        mReceiver.onReceive(mContext, disconnected(LOGGED_ADDRESS))

        val log = ownLog()

        assertTrue(
            "The receiver's own lines are not in the log, so it was not read",
            log.contains("$TAG device connected")
        )
        assertFalse("The log holds a hardware address", log.contains(LOGGED_ADDRESS))
    }

    /**
     * The adapter's own connection state covers every device, whether it carries audio or not, so
     * the receiver hears the audio profile instead.
     */
    @Test
    fun theIntentFilterSubscribesToTheA2dpConnectionStateActionAndNothingElse() {
        val filter = mReceiver.makeIntentFilter()

        assertEquals(1, filter.countActions())
        assertTrue(filter.hasAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED))
    }

    /** What the platform sends when a car stereo or a headset connects: both broadcasts. */
    private fun audioDeviceConnects(address: String) {
        mReceiver.onReceive(mContext, adapterEvent(address, BluetoothAdapter.STATE_CONNECTED))
        mReceiver.onReceive(mContext, connected(address))
    }

    private fun audioDeviceDisconnects(address: String) {
        mReceiver.onReceive(mContext, disconnected(address))
        mReceiver.onReceive(mContext, adapterEvent(address, BluetoothAdapter.STATE_DISCONNECTED))
    }

    /** What the platform sends when a device without an audio profile, such as a watch, connects. */
    private fun otherDeviceConnects(address: String) {
        mReceiver.onReceive(mContext, adapterEvent(address, BluetoothAdapter.STATE_CONNECTED))
    }

    private fun otherDeviceDisconnects(address: String) {
        mReceiver.onReceive(mContext, adapterEvent(address, BluetoothAdapter.STATE_DISCONNECTED))
    }

    private fun connected(address: String): Intent {
        return withState(address, BluetoothProfile.STATE_CONNECTED)
    }

    private fun disconnected(address: String): Intent {
        return withState(address, BluetoothProfile.STATE_DISCONNECTED)
    }

    private fun withState(address: String, state: Int): Intent {
        return Intent(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            .putExtra(BluetoothProfile.EXTRA_STATE, state)
            .putExtra(BluetoothDevice.EXTRA_DEVICE, remoteDevice(address))
    }

    private fun adapterEvent(address: String, state: Int): Intent {
        return Intent(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            .putExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, state)
            .putExtra(BluetoothDevice.EXTRA_DEVICE, remoteDevice(address))
    }

    /**
     * A device is asked of the platform rather than built here, because the receiver reads it back
     * through the same parcelling the system broadcast uses. An image without a Bluetooth adapter
     * cannot answer, and these tests say so rather than substituting something of their own: a
     * device assembled by hand would only be testing the shape this test chose for it.
     */
    private fun remoteDevice(address: String): BluetoothDevice {
        val adapter = mContext.getSystemService(BluetoothManager::class.java)?.adapter
        assertNotNull("This device has no Bluetooth adapter to take a remote device from", adapter)
        return adapter!!.getRemoteDevice(address)
    }

    /**
     * Reads what this process has logged so far. The shell reads it, because an application sees
     * its own log only on some releases.
     */
    private fun ownLog(): String {
        val command = "logcat -d --pid=${Process.myPid()}"
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }

    private class RecordingListener : BTConnectionReceiver.Listener {

        private var mSameDeviceConnections = 0

        private var mDisconnections = 0

        val sameDeviceConnections: Int
            get() = mSameDeviceConnections

        val disconnections: Int
            get() = mDisconnections

        override fun onSameDeviceConnected() {
            mSameDeviceConnections++
        }

        override fun onDisconnected() {
            mDisconnections++
        }
    }

    private companion object {

        const val FIRST_ADDRESS = "00:11:22:33:44:55"

        const val SECOND_ADDRESS = "AA:BB:CC:DD:EE:FF"

        const val THIRD_ADDRESS = "12:34:56:78:9A:BC"

        const val CAR_ADDRESS = "C0:C0:C0:C0:C0:C0"

        const val WATCH_ADDRESS = "D0:D0:D0:D0:D0:D0"

        /** Used by no other test, so another test's events cannot put it in the log. */
        const val LOGGED_ADDRESS = "5A:5A:5A:5A:5A:5A"

        const val FOREIGN_ACTION = "com.example.FAKE_CONNECTION_STATE_CHANGED"

        val TAG: String = BTConnectionReceiver::class.java.simpleName
    }
}
