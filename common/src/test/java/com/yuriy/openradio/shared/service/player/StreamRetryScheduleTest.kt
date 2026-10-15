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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How often the player tries again a stream ExoPlayer gave up for a lost network. The schedule
 * does not know whether the phone has a network: a dead zone and a dead station fail in the same
 * way, so the same bound has to let the first come back and stop the second.
 */
class StreamRetryScheduleTest {

    @Test
    fun retriesFollowTheScheduleInOrder() {
        val schedule = StreamRetrySchedule(listOf(1_000L, 2_000L, 4_000L))

        assertEquals(StreamRetry.After(1_000L), schedule.next())
        assertEquals(StreamRetry.After(2_000L), schedule.next())
        assertEquals(StreamRetry.After(4_000L), schedule.next())
    }

    /**
     * A station whose host refuses connections or never answers fails as a lost network even
     * while the phone has a working one (fnr-u5nr). Once the retries are spent the player gives
     * it up, and every further failure finds the schedule still spent.
     */
    @Test
    fun aStationThatKeepsFailingIsGivenUpOnceTheRetriesAreSpent() {
        val schedule = StreamRetrySchedule(listOf(1_000L, 2_000L))
        schedule.next()
        schedule.next()

        assertEquals(StreamRetry.Exhausted, schedule.next())
        assertEquals(StreamRetry.Exhausted, schedule.next())
    }

    @Test
    fun resetStartsTheScheduleOver() {
        val schedule = StreamRetrySchedule(listOf(1_000L, 2_000L))
        schedule.next()
        schedule.next()
        schedule.next()

        schedule.reset()

        assertEquals(StreamRetry.After(1_000L), schedule.next())
        assertEquals(StreamRetry.After(2_000L), schedule.next())
        assertEquals(StreamRetry.Exhausted, schedule.next())
    }

    @Test
    fun theDefaultScheduleIsBoundedAndBacksOff() {
        val delays = drain(StreamRetrySchedule())

        assertEquals(StreamRetrySchedule.DEFAULT_DELAYS_MS, delays)
        assertTrue("The default schedule never retries", delays.isNotEmpty())
        delays.zipWithNext().forEach { (earlier, later) ->
            assertTrue("$later does not back off from $earlier", later > earlier)
        }
    }

    /**
     * The retry the outage test sees first comes after the first delay, so the default keeps the
     * delay the player has always waited before it tries a stream the network stopped (TASK-018).
     */
    @Test
    fun theDefaultScheduleFirstRetriesAfterTenSeconds() {
        assertEquals(StreamRetry.After(10_000L), StreamRetrySchedule().next())
    }

    @Test
    fun aScheduleWithoutRetriesIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StreamRetrySchedule(emptyList())
        }
    }

    @Test
    fun aDelayThatIsNotPositiveIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StreamRetrySchedule(listOf(1_000L, 0L))
        }
    }

    private fun drain(schedule: StreamRetrySchedule): List<Long> {
        val delays = mutableListOf<Long>()
        repeat(MAX_DRAINED) {
            when (val retry = schedule.next()) {
                is StreamRetry.After -> delays.add(retry.delayMs)
                StreamRetry.Exhausted -> return delays
            }
        }
        throw AssertionError("The schedule was not spent after $MAX_DRAINED retries")
    }

    private companion object {

        /**
         * Far more retries than any schedule the player uses, so a schedule that is never spent
         * fails the test instead of hanging it.
         */
        const val MAX_DRAINED = 1_000
    }
}
