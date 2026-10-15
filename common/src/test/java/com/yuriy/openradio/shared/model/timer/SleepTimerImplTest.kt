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
import kotlin.concurrent.thread

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
     * The user moving the alarm later must leave exactly one timer armed: not the earlier one, and
     * not neither.
     *
     * Both halves have to be asserted. Watching only the earlier deadline pass in silence would
     * also be satisfied by a `handle` that cancels and then never starts anything, which is the
     * failure that would lose the user their alarm outright. The replacement is given a delay long
     * enough to outlast the silent window, so a completion inside that window can only have come
     * from the first call and a completion after it can only have come from the second.
     */
    @Test
    fun handlingAgainReplacesTheTimerItDoesNotAddOne() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener)

        val startedAt = System.currentTimeMillis()
        timer.handle(startedAt + DELAY_MS, true)
        timer.handle(startedAt + SILENCE_MS + DELAY_MS, true)

        assertFalse(
            "The replaced timer still completed",
            listener.awaitCompletionDuring(SILENCE_MS)
        )
        assertTrue("The replacement timer never completed", listener.awaitCompletion())
        assertTrue(
            "The replacement completed before its own delay had passed",
            System.currentTimeMillis() - startedAt >= SILENCE_MS + DELAY_MS
        )
        assertEquals("The timer fired for both calls", 1, listener.completions)
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

    /**
     * The model's validity check refuses this instant, so the timer must refuse it too: a
     * timestamp equal to the clock is not a delay of zero, it is a moment that has arrived.
     */
    @Test
    fun aTimestampEqualToNowIsRefused() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener) { NOW }

        timer.handle(NOW, true)

        assertFalse("A timestamp equal to now fired", listener.awaitCompletionDuring(SILENCE_MS))
    }

    @Test
    fun onlyATimestampAfterNowIsAccepted() {
        val timer = SleepTimerImpl(RecordingListener()) { NOW }

        assertFalse(timer.accepts(NOW - 1))
        assertFalse(timer.accepts(NOW))
        assertTrue(timer.accepts(NOW + 1))
    }

    /**
     * The delay is measured from the injected clock, not from the system one, so a timestamp one
     * delay past the injected instant fires after that delay.
     */
    @Test
    fun theDelayIsMeasuredFromTheClock() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener) { NOW }

        val startedAt = System.currentTimeMillis()
        timer.handle(NOW + DELAY_MS, true)

        assertTrue("The timer never completed", listener.awaitCompletion())
        assertTrue(
            "The timer completed before its delay had passed",
            System.currentTimeMillis() - startedAt >= DELAY_MS
        )
    }

    @Test
    fun cancelStopsATimerThatIsAlreadyRunning() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener)

        timer.handle(System.currentTimeMillis() + DELAY_MS, true)
        timer.cancel()

        assertFalse("A cancelled timer completed", listener.awaitCompletionDuring(SILENCE_MS))
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
     * Calls that race each other must still leave exactly one timer armed. Each thread asks for a
     * different delay, so a call whose start picked up another call's delay, or whose timer
     * survived being replaced, shows up as a second completion.
     */
    @Test
    fun concurrentCallsLeaveOneTimerArmed() {
        val listener = RecordingListener()
        val timer = SleepTimerImpl(listener)
        val ready = CountDownLatch(RACING_THREADS)
        val go = CountDownLatch(1)
        val startedAt = System.currentTimeMillis()
        val threads = (0 until RACING_THREADS).map { index ->
            thread {
                ready.countDown()
                go.await()
                timer.handle(startedAt + DELAY_MS + index, true)
            }
        }
        ready.await()
        go.countDown()
        threads.forEach { it.join() }

        assertTrue("No timer completed", listener.awaitCompletion())
        Thread.sleep(SILENCE_MS)
        assertEquals("More than one racing call left a timer armed", 1, listener.completions)
    }

    /**
     * A listener may arm the next timer while it hears the current one complete. The timer that
     * just completed must not take the new one down with it as it finishes.
     */
    @Test
    fun aTimerArmedFromACompletionStillCompletes() {
        val listener = RearmingListener()
        val timer = SleepTimerImpl(listener)
        listener.mTimer = timer

        timer.handle(System.currentTimeMillis() + DELAY_MS, true)

        assertTrue("The timer armed on completion never completed", listener.awaitSecondCompletion())
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

    /**
     * Arms the timer again, one delay ahead, when it hears the first completion, and lets a test
     * wait for the second.
     */
    private class RearmingListener : SleepTimerListener {

        lateinit var mTimer: SleepTimer

        private val mCompletions = AtomicInteger(0)

        private val mSecondCompletion = CountDownLatch(1)

        override fun onComplete() {
            if (mCompletions.incrementAndGet() == 1) {
                mTimer.handle(System.currentTimeMillis() + DELAY_MS, true)
            } else {
                mSecondCompletion.countDown()
            }
        }

        fun awaitSecondCompletion(): Boolean {
            return mSecondCompletion.await(COMPLETION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {

        /**
         * A fixed instant for the tests that inject a clock, far from the real one so that a
         * timer computing its delay from the system clock instead would be off by decades.
         */
        const val NOW = 1_700_000_000_000L

        const val DELAY_MS = 200L

        const val COMPLETION_TIMEOUT_MS = 5_000L

        const val SILENCE_MS = 1_000L

        const val RACING_THREADS = 16
    }
}
