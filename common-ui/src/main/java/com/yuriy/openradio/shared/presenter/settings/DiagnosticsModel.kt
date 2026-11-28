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

package com.yuriy.openradio.shared.presenter.settings

import androidx.annotation.MainThread
import com.yuriy.openradio.shared.model.logging.LoggingLayer
import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.shared.presenter.screen.ScreenStack
import com.yuriy.openradio.shared.utils.AppLogger
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Either logs are being collected, or a collected report waits to be shared. */
data class DiagnosticsState(val collecting: Boolean, val report: File?) {

    /**
     * Whether "Create report" is enabled: only while nothing is being collected or shared. It is
     * a getter rather than a constructor value so it cannot disagree with the other two fields.
     */
    val canCreate: Boolean
        get() = !collecting && report == null
}

/**
 * The diagnostic report sub-screen of Settings.
 *
 * The report still leaves the app only through the per-report share chooser that the platform
 * opens for [DiagnosticsState.report]. The collection's answers arrive on a background thread, so
 * [mScope] is where they are handled and the state is updated.
 *
 * Main thread only.
 */
@MainThread
class DiagnosticsModel(
    private val mLogs: LoggingLayer,
    private val mNotices: SettingsNotices,
    private val mScreens: ScreenStack,
    private val mScope: CoroutineScope,
) {

    private val mState = MutableStateFlow(DiagnosticsState(collecting = false, report = null))

    /** The diagnostic report sub-screen as the screen shows it. */
    val state: StateFlow<DiagnosticsState> = mState.asStateFlow()

    /** Shows the warning sub-screen. */
    @MainThread
    fun open() {
        mScreens.push(Destination.Diagnostics)
    }

    /** Starts collecting the logs unless a collection or a report is already in the way. */
    @MainThread
    fun create() {
        if (!mState.value.canCreate) {
            return
        }
        mState.value = DiagnosticsState(collecting = true, report = null)
        mLogs.collectAdbLogs(
            onSuccess = { file ->
                mScope.launch {
                    mState.value = DiagnosticsState(collecting = false, report = file)
                }
            },
            onError = { message ->
                mScope.launch {
                    AppLogger.e("$TAG the report could not be collected: $message")
                    mState.value = DiagnosticsState(collecting = false, report = null)
                    mNotices.show(Notice.FAILURE)
                }
            },
        )
    }

    /**
     * Handles the report once the platform has opened its share chooser, or failed to open it
     * ([shared] is false).
     */
    @MainThread
    fun reportHandled(shared: Boolean) {
        mState.value = DiagnosticsState(collecting = false, report = null)
        if (!shared) {
            mNotices.show(Notice.FAILURE)
        }
    }

    private companion object {
        const val TAG = "DiagnosticsModel"
    }
}
