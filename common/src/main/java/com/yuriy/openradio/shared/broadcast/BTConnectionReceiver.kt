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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
 * This is a receiver for Bluetooth device connection events.
 *
 * Exported, unlike the other receivers of the service: the Bluetooth application (uid 1002), not
 * the system, sends [BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED]. A receiver that is not
 * exported hears only the system and this application, so it would never hear the broadcast, and
 * Bluetooth auto resume and pause on disconnect would stop.
 */
class BTConnectionReceiver(private val mListener: Listener) : AbstractReceiver(ContextCompat.RECEIVER_EXPORTED) {

    interface Listener {
        fun onSameDeviceConnected()
        fun onDisconnected()
    }

    private var mConnectedDevice = AppUtils.EMPTY_STRING

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.i("$TAG receive:$intent")
        // Defence in depth: the intent filter already admits only this action, and it is a
        // protected broadcast no other application can send.
        if (intent.action != BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED) {
            return
        }
        val device = IntentUtils.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE, intent)
        if (device == null) {
            AppLogger.w("$TAG no device provided by $intent")
            return
        }
        when (intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR)) {
            BluetoothAdapter.STATE_CONNECTED -> {
                // No address in the log: the diagnostics report attaches the whole logcat.
                AppLogger.i("$TAG device connected")
                if (device.address == mConnectedDevice) {
                    AppLogger.i("$TAG connected to the same device.")
                    mListener.onSameDeviceConnected()
                }
                mConnectedDevice = device.address
            }
            BluetoothAdapter.STATE_DISCONNECTED -> {
                AppLogger.i("$TAG device disconnected")
                // The listener pauses playback, so only the device last seen connecting counts.
                if (device.address == mConnectedDevice) {
                    mListener.onDisconnected()
                }
            }
        }
    }

    override fun makeIntentFilter(): IntentFilter {
        return IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
    }

    companion object {
        private val TAG = BTConnectionReceiver::class.java.simpleName
    }
}
