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

package com.yuriy.openradio.shared.model.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Covers what [SleepTimerImpl] does with a timestamp: whether it fires, when it fires, and what a
 * second call does to the first.
 *
 * The timer is a coroutine delay, not an alarm, so the tests wait for a real elapsed delay. Every
 * wait is bounded: [COMPLETION_TIMEOUT_MS] is the deadline for something that is supposed to
 * happen, and [SILENCE_MS] is how long a timer that must stay silent is watched, long enough to
 * outlast the delays the tests ask for.
 */
class SleepTimerImplTest {

    @Test
    fun anEnabledTimerCompletesAfterItsDelay() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener)

        val startedAt = System.currentTimeMillis()
        timer.handle(startedAt + DELAY_MS, true)

        assertTrue("The timer never completed", listener.awaitCompletion())
        assertTrue(
            "The timer completed before its delay had passed",
            System.currentTimeMillis() - startedAt >= DELAY_MS
        )
        assertEquals(1, listener.completions)
    }

    @Test
    fun aDisabledTimerNeverCompletes() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener)

        timer.handle(System.currentTimeMillis() + DELAY_MS, false)

        assertFalse("A disabled timer completed", listener.awaitCompletionDuring(SILENCE_MS))
    }

    /**
     * The user moving the alarm later must not leave the earlier one armed. The replacement is
     * deliberately given a delay long enough to outlast the window this test watches, so a
     * completion inside it can only have come from the first call.
     */
    @Test
    fun handlingAgainReplacesTheTimerItDoesNotAddOne() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener)

        timer.handle(System.currentTimeMillis() + DELAY_MS, true)
        timer.handle(System.currentTimeMillis() + SILENCE_MS + DELAY_MS, true)

        assertFalse(
            "The replaced timer still completed",
            listener.awaitCompletionDuring(SILENCE_MS)
        )
    }

    @Test
    fun disablingCancelsATimerThatIsAlreadyRunning() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener)

        timer.handle(System.currentTimeMillis() + DELAY_MS, true)
        timer.handle(System.currentTimeMillis() + DELAY_MS, false)

        assertFalse("A cancelled timer completed", listener.awaitCompletionDuring(SILENCE_MS))
    }

    /**
     * A timestamp that has already passed is the state a stored alarm is in after the device has
     * been off for a while. It is refused rather than fired immediately.
     */
    @Test
    fun aTimestampInThePastIsRefused() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener)

        timer.handle(System.currentTimeMillis() - DELAY_MS, true)

        assertFalse("A past timestamp fired", listener.awaitCompletionDuring(SILENCE_MS))
    }

    @Test
    fun aCompletedTimerDoesNotFireAgain() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener)

        timer.handle(System.currentTimeMillis() + DELAY_MS, true)

        assertTrue("The timer never completed", listener.awaitCompletion())
        Thread.sleep(SILENCE_MS)
        assertEquals(1, listener.completions)
    }

    /**
     * Counts completions and lets a test wait for the first one, or wait out a window in which
     * none may arrive.
     */
    private class RecordingListener : SleepTimerListener {

        private val mCompletions = AtomicInteger(0)

        private val mCompleted = CountDownLatch(1)

        val completions: Int
            get() = mCompletions.get()

        override fun onComplete() {
            mCompletions.incrementAndGet()
            mCompleted.countDown()
        }

        fun awaitCompletion(): Boolean {
            return awaitCompletionDuring(COMPLETION_TIMEOUT_MS)
        }

        fun awaitCompletionDuring(millis: Long): Boolean {
            return mCompleted.await(millis, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {

        const val DELAY_MS = 200L

        const val COMPLETION_TIMEOUT_MS = 5_000L

        const val SILENCE_MS = 1_000L
    }
}
