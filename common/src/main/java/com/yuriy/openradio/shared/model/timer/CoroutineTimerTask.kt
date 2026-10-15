/*
 * Copyright 2021-2022 The "Open Radio" Project. Author: Chernyshov Yuriy [chernyshov.yuriy@gmail.com]
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

package com.yuriy.openradio.shared.model.timer

import com.yuriy.openradio.shared.utils.AppLogger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs an action once, after a delay, on a coroutine of its own scope.
 *
 * At most one run is pending at a time: [start] replaces the pending run, so calls that race each
 * other cannot leave two armed. The delay is an argument of [start] rather than state, so nothing
 * can change it between one call and the next.
 */
class CoroutineTimerTask internal constructor(private val mName: String, private val mAction: suspend () -> Unit) {

    private val mCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The pending run. Guarded by this task's monitor.
     */
    private var mJob: Job? = null

    /**
     * Cancels the pending run, if any, and schedules the action to run once after [delay]
     * milliseconds.
     */
    @Synchronized
    fun start(delay: Long) {
        mJob?.cancel("replaced by start()")
        mJob = mCoroutineScope.launch(CoroutineName(mName)) {
            delay(delay)
            try {
                mAction()
            } catch (e: Throwable) {
                AppLogger.e("Can't do action on sleep timer", e)
            }
        }
    }

    /**
     * Immediately stops the timer task, even if the job is currently running,
     * by cancelling the underlying Job.
     */
    @Synchronized
    fun cancel() {
        mJob?.cancel("cancel() called")
        mJob = null
    }
}
