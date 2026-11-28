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

import android.net.Uri
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether a stations file is being written or read, shown as a progress line on Settings. */
data class TransferState(val running: Boolean)

/**
 * The stations export and import of Settings.
 *
 * No document (a cancelled UI, a result that is not OK, a result without a URI) is a failure, as
 * it was with the file storage dialog. What the screen shows afterwards is the existing success
 * or failure message.
 *
 * Main thread only.
 */
@MainThread
class StationTransferModel(
    private val mFiles: StationFiles,
    private val mNotices: SettingsNotices,
    private val mScope: CoroutineScope,
) {

    private var mRunning = 0

    private val mState = MutableStateFlow(TransferState(running = false))

    /** The stations transfer as the screen shows it. */
    val state: StateFlow<TransferState> = mState.asStateFlow()

    /** Writes the stations file to [document], or reports a failure when there is none. */
    @MainThread
    fun exportTo(document: Uri?) {
        transfer(document) { mFiles.exportTo(it) }
    }

    /** Merges the stations file from [document], or reports a failure when there is none. */
    @MainThread
    fun importFrom(document: Uri?) {
        transfer(document) { mFiles.importFrom(it) }
    }

    /** Reports a document UI that could not be started. */
    @MainThread
    fun failedToStart() {
        mNotices.show(Notice.FAILURE)
    }

    private fun transfer(document: Uri?, action: suspend (Uri) -> Boolean) {
        if (document == null) {
            mNotices.show(Notice.FAILURE)
            return
        }
        mScope.launch {
            mRunning++
            mState.value = TransferState(running = true)
            val succeeded = action(document)
            mRunning--
            mState.value = TransferState(running = mRunning > 0)
            mNotices.show(if (succeeded) Notice.SUCCESS else Notice.FAILURE)
        }
    }
}
