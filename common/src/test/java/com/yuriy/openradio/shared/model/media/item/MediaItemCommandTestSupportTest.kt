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

package com.yuriy.openradio.shared.model.media.item

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [RecordingCommandListener] itself, because the browse command tests read their verdict
 * off it: a listener that accepts what a command that never ran delivers would pass them all.
 */
class MediaItemCommandTestSupportTest {

    /**
     * Every browse command ends its background work with
     * `withTimeoutOrNull(CMD_TIMEOUT_MS) { ... } ?: resultListener.onResult()`, and that fallback
     * delivers no items, the first page and no error - the same state a command that ran and found
     * nothing delivers. The await has to run out first, or a command that got nowhere satisfies
     * every test whose expectation is an empty node.
     */
    @Test
    fun theAwaitRunsOutBeforeACommandCanFallThroughItsOwnTimeout() {
        assertTrue(
            "An await of ${RecordingCommandListener.AWAIT_MILLIS} ms outlives the " +
                "${MediaItemCommand.CMD_TIMEOUT_MS} ms command timeout, so a timed out command " +
                "still answers inside it",
            RecordingCommandListener.AWAIT_MILLIS < MediaItemCommand.CMD_TIMEOUT_MS
        )
    }

    /**
     * The latch stands in for the command timeout expiring: the result the command is about to
     * deliver is the empty one the fallback sends, and it is held back until the await is over.
     */
    @Test
    fun aCommandThatAnswersOnlyAfterTheAwaitFailsTheTestByName() {
        val listener = RecordingCommandListener()
        val timeoutExpired = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val command = MediaItemCommand { _, dependencies ->
            Thread {
                timeoutExpired.await()
                dependencies.resultListener.onResult()
                delivered.countDown()
            }.apply { isDaemon = true }.start()
        }

        try {
            command.execute(listener.playbackStateListener, dependencies(RecordingPresenter(), listener))

            val failure = assertThrows(AssertionError::class.java) { listener.awaitResult() }
            assertEquals(RecordingCommandListener.RESULT_MISSING, failure.message)
            assertEquals(0, listener.results)
        } finally {
            timeoutExpired.countDown()
        }

        assertTrue("The stand in for the timeout fallback never delivered", delivered.await(1, TimeUnit.SECONDS))
        assertEquals(0, listener.items.size)
    }

    /**
     * The restored instance assertion is the one place where an empty result is the expected
     * answer, so it has to reject a listener that has not been given one.
     */
    @Test
    fun theRestoredInstanceAssertionRejectsAListenerThatWasNeverAnswered() {
        val listener = RecordingCommandListener()

        val failure = assertThrows(AssertionError::class.java) {
            listener.assertAnsweredFromCacheBeforeReturning()
        }

        assertTrue(NOT_INLINE, failure.message?.contains(ANSWERED_ELSEWHERE) == true)
    }

    /**
     * The delivery a restored instance has to be told apart from is an identical empty result sent
     * later by the command's coroutine. Another thread stands in for that coroutine, and it is
     * joined before the assertion runs, so the rejection is about where the result came from and
     * not about which of the two got there first.
     */
    @Test
    fun theRestoredInstanceAssertionRejectsAnIdenticalResultDeliveredOffTheCallingThread() {
        val listener = RecordingCommandListener()

        val deliverer = Thread { listener.onResult() }
        deliverer.start()
        deliverer.join()

        assertEquals(1, listener.results)
        assertTrue(listener.items.isEmpty())
        val failure = assertThrows(AssertionError::class.java) {
            listener.assertAnsweredFromCacheBeforeReturning()
        }

        assertTrue(NOT_INLINE, failure.message?.contains(ANSWERED_ELSEWHERE) == true)
    }

    private companion object {

        const val ANSWERED_ELSEWHERE = "A restored instance did not answer on the thread that called execute"

        const val NOT_INLINE = "The failure does not say the result did not arrive inline"
    }
}
