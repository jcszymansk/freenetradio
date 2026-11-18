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

package com.yuriy.openradio.mobile.view.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.yuriy.openradio.mobile.view.main.ArtworkCache
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.images.ImagesStore
import com.yuriy.openradio.shared.model.timer.SleepTimerListener
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.permission.PermissionChecker
import com.yuriy.openradio.shared.presenter.main.CountrySetting
import com.yuriy.openradio.shared.presenter.main.MainScreenModel
import com.yuriy.openradio.shared.service.location.LocationService
import com.yuriy.openradio.shared.utils.AppUtils

/**
 * What the main screen needs from Android that the model must not touch: the location lookup and
 * its permission, the Bluetooth permission, artwork change notifications and the sleep timer.
 * An artwork change drops that logo from every [ArtworkCache], so only what shows it redraws.
 */
internal class MainScreenPlatform(
    private val mActivity: AppCompatActivity,
    private val mModel: MainScreenModel,
    private val mCountrySetting: CountrySetting,
    private val mSleepTimerModel: SleepTimerModel,
    private val mArtworkCaches: List<ArtworkCache>,
) {
    private val mLocationMessenger = Messenger(LocationHandler())
    private val mArtworkObserver = ArtworkObserver()
    private val mTimerListener = object : SleepTimerListener {
        override fun onComplete() {
            // The timer completes on a background dispatcher, and the model is main thread only.
            mActivity.runOnUiThread { mModel.close() }
        }
    }

    fun attach() {
        mActivity.applicationContext.contentResolver.registerContentObserver(ImagesStore.AUTHORITY_URI, true, mArtworkObserver)
        mSleepTimerModel.addSleepTimerListener(mTimerListener)
    }

    fun detach() {
        mActivity.applicationContext.contentResolver.unregisterContentObserver(mArtworkObserver)
        mSleepTimerModel.removeSleepTimerListener(mTimerListener)
        LocationService.doCancelWork(mActivity.applicationContext)
    }

    fun onResume() {
        val content = mActivity.findViewById<View>(android.R.id.content)
        LocationService.checkCountry(mActivity, content, mLocationMessenger, mCountrySetting.countryCode())
        val context = mActivity.applicationContext
        if (AppUtils.hasVersionS() && AppPreferencesManager.isBtAutoPlay(context) &&
            !PermissionChecker.isBluetoothConnectGranted(context)
        ) {
            PermissionChecker.requestBluetoothPermission(mActivity, content)
        }
    }

    fun onPermissionsResult(permissions: Array<String>, grantResults: IntArray) {
        val context = mActivity.applicationContext
        for (i in permissions.indices) {
            val isGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
            when (permissions[i]) {
                Manifest.permission.ACCESS_COARSE_LOCATION -> if (isGranted) {
                    LocationService.doEnqueueWork(context, mLocationMessenger)
                }
                Manifest.permission.BLUETOOTH_CONNECT -> AppPreferencesManager.setBtAutoPlay(context, isGranted)
            }
        }
    }

    @SuppressLint("HandlerLeak")
    private inner class LocationHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == LocationService.MSG_ID_ON_LOCATION) {
                mCountrySetting.setCountryCode(LocationService.getCountryCode(msg))
            }
        }
    }

    private inner class ArtworkObserver : ContentObserver(Handler(Looper.getMainLooper())) {
        // The two-argument overload is the one API 26 to 29 dispatch to; API 30 and later reach
        // it through the default of the flags overload.
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri != null) {
                val imageId = ImagesStore.getId(uri)
                mArtworkCaches.forEach { it.invalidate(imageId) }
            }
        }
    }
}
