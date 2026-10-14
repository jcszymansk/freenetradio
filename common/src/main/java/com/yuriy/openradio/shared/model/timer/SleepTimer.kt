/*
 * Copyright 2021-2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

interface SleepTimer {

    /**
     * Replaces whatever timer is running with one that completes at [time], or with none when
     * [enabled] is false or [time] is not accepted.
     */
    fun handle(time: Long, enabled:Boolean)

    /**
     * Stops the running timer, if any, so that it never completes.
     */
    fun cancel()

    /**
     * Whether [time] is late enough to be armed: strictly after the timer's current time.
     */
    fun accepts(time: Long): Boolean
}
