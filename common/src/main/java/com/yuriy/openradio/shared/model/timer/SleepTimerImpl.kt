/*
 * Copyright 2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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

/**
 * A one shot timer over a coroutine delay.
 *
 * @param mListener Hears the timer complete.
 * @param mClock Wall clock milliseconds, the same scale as the timestamps handed to [handle]. Only
 * the delay is computed from it; the delay itself elapses in real time.
 */
class SleepTimerImpl(
    private val mListener: SleepTimerListener,
    private val mClock: () -> Long = System::currentTimeMillis
) : SleepTimer {

    private val mTimerTask = CoroutineTimerTask("SleepTimerTask") {
        AppLogger.i("Sleep timer completed")
        mListener.onComplete()
    }

    override fun handle(time: Long, enabled:Boolean) {
        cancel()
        if (!enabled) {
            return
        }
        val delay = time - mClock()
        if (delay <= 0) {
            AppLogger.e("Sleep timer can not be started for a time that is not in the future")
            return
        }
        mTimerTask.mDelay = delay
        mTimerTask.start()
        AppLogger.i("Sleep timer started for ${mTimerTask.mDelay} ms")
    }

    override fun cancel() {
        mTimerTask.cancel()
        AppLogger.i("Sleep timer cancelled")
    }

    override fun accepts(time: Long): Boolean {
        return time > mClock()
    }
}
