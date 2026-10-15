/*
 * Copyright 2017-2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.IntentUtils

/**
 * Created by Chernyshov Yurii
 * At Android Studio
 * On 03/12/18
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * This is a receiver for the connection events of Bluetooth audio devices.
 *
 * It hears the A2DP profile, which carries media audio, rather than the adapter's connection state,
 * which covers every device: a smartwatch connecting or going out of range has nothing to do with
 * the speakers the radio plays through. The headset profile is left out because it carries calls
 * rather than media, and a car stereo connects over both, so hearing it too would report one
 * connection twice.
 *
 * Exported, unlike the other receivers of the service: the Bluetooth application (uid 1002), not
 * the system, sends [BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED]. A receiver that is not
 * exported hears only the system and this application, so it would never hear the broadcast, and
 * Bluetooth auto resume and pause on disconnect would stop.
 */
class BTConnectionReceiver(private val mListener: Listener) : AbstractReceiver(ContextCompat.RECEIVER_EXPORTED) {

    interface Listener {
        fun onSameDeviceConnected()
        fun onDisconnected()
    }

    /** Addresses of the audio devices connected now. A disconnection of any of them pauses. */
    private val mConnectedDevices = mutableSetOf<String>()

    /**
     * Address of the audio device that connected last. Unlike [mConnectedDevices] it outlives the
     * disconnection, because recognising the device when it comes back is what auto resume needs.
     */
    private var mLastConnectedDevice = AppUtils.EMPTY_STRING

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.i("$TAG receive:$intent")
        // Defence in depth: the intent filter already admits only this action, and it is a
        // protected broadcast no other application can send.
        if (intent.action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
            return
        }
        val device = IntentUtils.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE, intent)
        if (device == null) {
            AppLogger.w("$TAG no device provided by $intent")
            return
        }
        when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, STATE_UNKNOWN)) {
            BluetoothProfile.STATE_CONNECTED -> {
                // No address in the log: the diagnostics report attaches the whole logcat.
                AppLogger.i("$TAG device connected")
                if (device.address == mLastConnectedDevice) {
                    AppLogger.i("$TAG connected to the same device.")
                    mListener.onSameDeviceConnected()
                }
                mLastConnectedDevice = device.address
                mConnectedDevices.add(device.address)
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                AppLogger.i("$TAG device disconnected")
                // The listener pauses playback, so a device not seen connecting does not count.
                if (mConnectedDevices.remove(device.address)) {
                    mListener.onDisconnected()
                }
            }
        }
    }

    override fun makeIntentFilter(): IntentFilter {
        return IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
    }

    companion object {
        private val TAG = BTConnectionReceiver::class.java.simpleName

        /** Not one of the [BluetoothProfile] states, so an event without a state is ignored. */
        private const val STATE_UNKNOWN = -1
    }
}
