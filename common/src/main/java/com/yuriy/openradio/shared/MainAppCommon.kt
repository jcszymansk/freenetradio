/*
 * Copyright 2017-2022 The "Open Radio" Project. Author: Chernyshov Yuriy [chernyshov.yuriy@gmail.com]
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

package com.yuriy.openradio.shared

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Created with Android Studio.
 * User: Yuriy Chernyshov
 * Date: 12/21/13
 * Time: 6:29 PM
 */
open class MainAppCommon : MultiDexApplication() {

    private val mAppJob = SupervisorJob()
    protected val mAppScope = CoroutineScope(Dispatchers.Main + mAppJob)

    override fun onCreate() {
        super.onCreate()
        MultiDex.install(applicationContext)
        DependencyRegistryCommon.init(applicationContext)

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        mAppScope.launch(Dispatchers.IO) {
            correctBufferSettings(applicationContext)
        }
    }

    companion object {

        /**
         * Correct mal formatted values entered by user.
         *
         * @param context Context of a callee.
         */
        @UnstableApi
        private fun correctBufferSettings(context: Context) {
            val maxBufferMs = AppPreferencesManager.getMaxBuffer(context)
            val minBufferMs = AppPreferencesManager.getMinBuffer(context)
            val playBufferMs = AppPreferencesManager.getPlayBuffer(context)
            val playBufferRebufferMs = AppPreferencesManager.getPlayBufferRebuffer(context)
            if (maxBufferMs < minBufferMs) {
                AppPreferencesManager.setMaxBuffer(context, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS)
                AppPreferencesManager.setMinBuffer(context, DefaultLoadControl.DEFAULT_MIN_BUFFER_MS)
            }
            if (minBufferMs < playBufferMs) {
                AppPreferencesManager.setPlayBuffer(
                    context, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
                )
                AppPreferencesManager.setMinBuffer(context, DefaultLoadControl.DEFAULT_MIN_BUFFER_MS)
            }
            if (minBufferMs < playBufferRebufferMs) {
                AppPreferencesManager.setPlayBufferRebuffer(
                    context, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                AppPreferencesManager.setMinBuffer(context, DefaultLoadControl.DEFAULT_MIN_BUFFER_MS)
            }
        }
    }
}
