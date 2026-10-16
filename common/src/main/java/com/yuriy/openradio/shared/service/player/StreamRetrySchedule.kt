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

package com.yuriy.openradio.shared.service.player

/**
 * When the player tries again a stream ExoPlayer gave up for a lost network, and when it stops.
 *
 * A dead zone and a dead station fail in the same way: a connection that is refused or never
 * answered. The phone usually keeps reporting the network it had in a dead zone, so the player
 * cannot tell the two apart by asking for one (TASK-018). The schedule gives a dead zone some
 * minutes to end, waiting longer between attempts as it goes, and then gives the station up,
 * because on a working network nothing else would ever stop retrying a station whose host is gone
 * (fnr-u5nr).
 *
 * The schedule counts the retries of one wait for the network, so [reset] belongs wherever that
 * wait ends or starts over: the stream became ready, playback was stopped, or the stream was
 * prepared again from outside the player. Used on the main thread only.
 *
 * @param mDelaysMs How long to wait before each retry, in order. Each delay is one retry.
 */
class StreamRetrySchedule(private val mDelaysMs: List<Long> = DEFAULT_DELAYS_MS) {

    /**
     * Number of retries handed out since the schedule last started over.
     */
    private var mRetries = 0

    init {
        require(mDelaysMs.isNotEmpty()) { "A retry schedule needs at least one retry" }
        require(mDelaysMs.all { it > 0 }) { "Retry delays must be positive: $mDelaysMs" }
    }

    /**
     * Takes the next retry of the schedule.
     */
    fun next(): StreamRetry {
        if (mRetries == mDelaysMs.size) {
            return StreamRetry.Exhausted
        }
        return StreamRetry.After(mDelaysMs[mRetries++])
    }

    /**
     * Starts the schedule over, for a new wait for the network.
     */
    fun reset() {
        mRetries = 0
    }

    companion object {

        /**
         * The schedule the service uses. ExoPlayer has spent its own retries before the player
         * makes one, from a few seconds for a refused connection to over a minute for one that
         * times out, so these only space the attempts out. Together they keep a station waiting
         * for about five minutes of its own, on top of what ExoPlayer spends on each attempt.
         */
        val DEFAULT_DELAYS_MS = listOf(10_000L, 20_000L, 40_000L, 80_000L, 160_000L)
    }
}

/**
 * What the player does about the next failure of a stream that waits for the network.
 */
sealed class StreamRetry {

    /**
     * Prepare the stream again after [delayMs].
     */
    data class After(val delayMs: Long) : StreamRetry()

    /**
     * The schedule is spent: the station is reported unplayable and not tried again.
     */
    object Exhausted : StreamRetry()
}
