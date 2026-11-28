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

package com.yuriy.openradio.shared.dependencies

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.yuriy.openradio.shared.R
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.FileStoreManager
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.settings.AppRestart
import com.yuriy.openradio.shared.presenter.settings.BufferSettings
import com.yuriy.openradio.shared.presenter.settings.BufferStore
import com.yuriy.openradio.shared.presenter.settings.GeneralSettings
import com.yuriy.openradio.shared.presenter.settings.Notice
import com.yuriy.openradio.shared.presenter.settings.SettingsNotices
import com.yuriy.openradio.shared.presenter.settings.StationFiles
import com.yuriy.openradio.shared.presenter.settings.UserAgentStore
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.SafeToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The general settings stored under the preference keys the dialogs used. */
class GeneralSettingsImpl(
    private val mContext: Context,
    private val mNetwork: NetworkSettingsStorage,
) : GeneralSettings {

    override var startPlayback: StartPlayback
        get() = AppPreferencesManager.startPlayback(mContext)
        set(value) {
            AppPreferencesManager.startPlayback(mContext, value)
        }

    override var masterVolume: Int
        get() = AppPreferencesManager.getMasterVolume(
            mContext, OpenRadioService.MASTER_VOLUME_DEFAULT
        )
        set(value) {
            AppPreferencesManager.setMasterVolume(mContext, value)
        }

    override var btAutoPlay: Boolean
        get() = AppPreferencesManager.isBtAutoPlay(mContext)
        set(value) {
            AppPreferencesManager.setBtAutoPlay(mContext, value)
        }

    override var useMobileData: Boolean
        get() = mNetwork.getUseMobile()
        set(value) {
            mNetwork.setUseMobile(value)
        }
}

/** The four stream buffering values stored under the preference keys the dialog used. */
class BufferStoreImpl(private val mContext: Context) : BufferStore {

    override var values: BufferSettings
        get() = BufferSettings(
            AppPreferencesManager.getMinBuffer(mContext),
            AppPreferencesManager.getMaxBuffer(mContext),
            AppPreferencesManager.getPlayBuffer(mContext),
            AppPreferencesManager.getPlayBufferRebuffer(mContext),
        )
        set(value) {
            AppPreferencesManager.setMinBuffer(mContext, value.min)
            AppPreferencesManager.setMaxBuffer(mContext, value.max)
            AppPreferencesManager.setPlayBuffer(mContext, value.play)
            AppPreferencesManager.setPlayBufferRebuffer(mContext, value.rebuffer)
        }
}

/** The custom user agent stored under the preference keys the dialog used. */
class UserAgentStoreImpl(private val mContext: Context) : UserAgentStore {

    override var isCustom: Boolean
        get() = AppPreferencesManager.isCustomUserAgent(mContext)
        set(value) {
            AppPreferencesManager.isCustomUserAgent(mContext, value)
        }

    @get:OptIn(UnstableApi::class)
    override val current: String
        get() = AppUtils.getUserAgent(mContext)

    override fun storeCustom(value: String) {
        AppPreferencesManager.setCustomUserAgent(mContext, value)
    }
}

/** The stations data file, written and merged through [FileStoreManager] off the main thread. */
class StationFilesImpl(
    private val mContext: Context,
    private val mManager: FileStoreManager,
) : StationFiles {

    override suspend fun exportTo(document: Uri): Boolean = withContext(Dispatchers.IO) {
        mManager.exportTo(mContext, document)
    }

    override suspend fun importFrom(document: Uri): Boolean = withContext(Dispatchers.IO) {
        mManager.importFrom(mContext, document)
    }
}

/** Restarts the application the way the source dialog's restart did. */
class AppRestartImpl(private val mContext: Context) : AppRestart {

    override fun restart() {
        val packageManager = mContext.packageManager
        val intent = packageManager.getLaunchIntentForPackage(mContext.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        try {
            mContext.startActivity(mainIntent)
        } catch (exception: ActivityNotFoundException) {
            AppLogger.e("Can't restart after src changed", exception)
        }
        Runtime.getRuntime().exit(0)
    }
}

/** Shows a [Notice] as a short toast, on any thread. */
class ToastNotices(private val mContext: Context) : SettingsNotices {

    override fun show(notice: Notice) {
        val text = when (notice) {
            Notice.SUCCESS -> R.string.success
            Notice.FAILURE -> R.string.failure
        }
        SafeToast.showAnyThread(mContext, mContext.getString(text))
    }
}
