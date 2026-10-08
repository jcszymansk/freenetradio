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

package com.yuriy.openradio.shared.model.storage.cache.api

import java.util.concurrent.TimeUnit

/**
 * Decides whether a cached API response is still young enough to be served instead of asking
 * the network again. Both instants are wall-clock milliseconds, as [System.currentTimeMillis]
 * reports them.
 */
object ApiCacheFreshness {

    /**
     * How long a response stays servable after it was written.
     */
    val WINDOW_MILLIS: Long = TimeUnit.DAYS.toMillis(1)

    /**
     * @param writtenAtMillis when the response was stored.
     * @param nowMillis the moment of the read.
     * @return true when the response was written no later than [nowMillis] and at most
     * [WINDOW_MILLIS] before it. A record dated after the read only exists when the wall clock
     * was moved back since the write; its age is unknown, so it is not trusted.
     */
    fun isFresh(writtenAtMillis: Long, nowMillis: Long): Boolean {
        val ageMillis = nowMillis - writtenAtMillis
        return ageMillis in 0..WINDOW_MILLIS
    }
}
