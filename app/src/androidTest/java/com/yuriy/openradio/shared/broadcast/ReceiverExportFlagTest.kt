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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the export flag each receiver of the service is registered with, as the platform receives
 * it.
 *
 * A receiver that is not exported hears only broadcasts sent by the system or by this
 * application. That suits [BecomingNoisyReceiver] and [ConnectivityReceiver], whose broadcasts
 * come from system_server, and it would silence [BTConnectionReceiver], whose broadcast comes from
 * the Bluetooth application: Bluetooth auto resume and pause on disconnect would stop without any
 * error.
 *
 * The receivers register with a context that records the call instead of passing it on, so nothing
 * is registered with the system. The flag reaches the platform only from Android 13 on; below that
 * [ContextCompat] turns a not exported registration into a permission instead, so the cases need a
 * device on Android 13 or later.
 */
@RunWith(AndroidJUnit4::class)
class ReceiverExportFlagTest {

    private lateinit var mContext: RecordingContext

    @Before
    fun setUp() {
        assumeTrue(
            "The registration flags reach the platform from Android 13 on",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        )
        mContext = RecordingContext(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun theBecomingNoisyReceiverIsNotExported() {
        val receiver = BecomingNoisyReceiver(object : BecomingNoisyReceiver.Listener {
            override fun onAudioBecomingNoisy() {}
        })

        receiver.register(mContext)

        assertRegisteredWith(receiver, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    @Test
    fun theConnectivityReceiverIsNotExported() {
        val receiver = ConnectivityReceiver(object : ConnectivityReceiver.Listener {
            override fun onConnectivityChange() {}
        })

        receiver.register(mContext)

        assertRegisteredWith(receiver, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    @Test
    fun theBluetoothConnectionReceiverIsExported() {
        val receiver = BTConnectionReceiver(object : BTConnectionReceiver.Listener {
            override fun onSameDeviceConnected() {}
            override fun onDisconnected() {}
        })

        receiver.register(mContext)

        assertRegisteredWith(receiver, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun assertRegisteredWith(receiver: BroadcastReceiver, flags: Int) {
        assertEquals("Registrations", 1, mContext.registrations.size)
        val registration = mContext.registrations.single()
        assertSame(receiver, registration.receiver)
        assertEquals(flags, registration.flags)
    }

    private class Registration(val receiver: BroadcastReceiver?, val flags: Int)

    /**
     * Records every registration, whichever overload it arrives through, and registers nothing.
     * The overloads without flags record none, so a registration that lost its flag on the way
     * fails the comparison.
     */
    private class RecordingContext(base: Context) : ContextWrapper(base) {

        val registrations = mutableListOf<Registration>()

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
            return record(receiver, NO_FLAGS)
        }

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? {
            return record(receiver, flags)
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
            broadcastPermission: String?,
            scheduler: Handler?
        ): Intent? {
            return record(receiver, NO_FLAGS)
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
            broadcastPermission: String?,
            scheduler: Handler?,
            flags: Int
        ): Intent? {
            return record(receiver, flags)
        }

        private fun record(receiver: BroadcastReceiver?, flags: Int): Intent? {
            registrations.add(Registration(receiver, flags))
            return null
        }
    }

    private companion object {

        const val NO_FLAGS = 0
    }
}
