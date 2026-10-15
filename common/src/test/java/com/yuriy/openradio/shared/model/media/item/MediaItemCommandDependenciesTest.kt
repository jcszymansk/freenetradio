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

import com.yuriy.openradio.shared.utils.MediaItemBuilder
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the answer [MediaItemCommandDependencies] guarantees a browse request: exactly one,
 * whatever the command behind it does (TASK-072).
 *
 * Every command here is a plain [MediaItemCommand] that blocks and answers on its own terms, with
 * nothing of the deadline in it, so whatever the request gets beyond that comes from
 * [MediaItemCommandDependencies.execute], which the service runs every browse command through.
 */
class MediaItemCommandDependenciesTest {

    @Test
    fun onlyTheFirstResultReachesTheListener() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)

        dependencies.resultListener.onResult(listOf(item("first")))
        dependencies.resultListener.onResult(listOf(item("second")))
        dependencies.resultListener.onResult()

        assertEquals(1, listener.results)
        listener.assertMediaIds("first")
    }

    /**
     * What a command keeps for the next request moves on in [MediaItemCommandDependencies.answer]'s
     * callback, so the callback has to run for the answer and only for it, and before the client
     * holds the answer: a client that asks again at once must find it done.
     */
    @Test
    fun onlyTheAnswerRunsItsCallbackAndRunsItBeforeTheListenerHearsOfIt() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)
        val resultsSeenByCallbacks = mutableListOf<Int>()

        val first = dependencies.answer(listOf(item("first"))) { resultsSeenByCallbacks.add(listener.results) }
        val second = dependencies.answer(listOf(item("second"))) { resultsSeenByCallbacks.add(listener.results) }

        assertTrue("The first answer was reported as dropped", first)
        assertFalse("A second answer was reported as delivered", second)
        assertEquals(
            "The callback did not run exactly once, before the listener had the answer",
            listOf(0),
            resultsSeenByCallbacks
        )
        assertEquals(1, listener.results)
        listener.assertMediaIds("first")
    }

    @Test
    fun anAnswerAfterTheDeadlineIsReportedAsDropped() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS)
        var callbackRan = false

        dependencies.execute(MediaItemCommand { _, _ -> listener.awaitDelivery() }, listener.playbackStateListener)
        listener.awaitResult()

        assertFalse(
            "An answer after the deadline was reported as delivered",
            dependencies.answer(listOf(item("late"))) { callbackRan = true }
        )
        assertFalse("A dropped answer ran its callback", callbackRan)
        assertEquals(emptyList<String>(), listener.mediaIds)
    }

    @Test
    fun aCommandThatAnswersInTimeIsTheAnswer() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS)

        dependencies.execute(
            MediaItemCommand { _, _ -> dependencies.resultListener.onResult(listOf(item("answer"))) },
            listener.playbackStateListener
        )

        listener.awaitResult().assertMediaIds("answer")
        assertEquals(1, listener.results)
    }

    /**
     * The command stays blocked until the request has been answered, so the answer can only have
     * come from the deadline, and the command's own answer arrives after it.
     */
    @Test
    fun aStuckCommandIsAnsweredEmptyAtTheDeadlineAndItsLateAnswerIsDropped() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS)

        dependencies.execute(
            MediaItemCommand { _, _ ->
                listener.awaitDelivery()
                dependencies.resultListener.onResult(listOf(item("late")))
            },
            listener.playbackStateListener
        )

        listener.awaitResult()
        assertEquals(1, listener.results)
        assertEquals(emptyList<String>(), listener.mediaIds)
    }

    /**
     * The command answers and then runs on past the deadline, which is the moment a guard that
     * answers on its deadline regardless would send an empty result over the real one. The deadline
     * starts before the command does, so a command that outlives four of them has certainly
     * outlived the first.
     */
    @Test
    fun aCommandThatAnswersAndOutlivesTheDeadlineKeepsItsAnswer() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS)

        dependencies.execute(
            MediaItemCommand { _, _ ->
                dependencies.resultListener.onResult(listOf(item("answer")))
                Thread.sleep(DEADLINE_MILLIS * 4)
            },
            listener.playbackStateListener
        )

        listener.awaitResult().assertMediaIds("answer")
        assertEquals(1, listener.results)
    }

    @Test
    fun aCommandThatReturnsWithoutAnsweringIsAnsweredEmpty() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)

        dependencies.execute(MediaItemCommand { _, _ -> }, listener.playbackStateListener)

        listener.awaitResult()
        assertEquals(1, listener.results)
        assertEquals(emptyList<String>(), listener.mediaIds)
    }

    @Test
    fun aFailedCommandIsAnsweredEmptyAndTheFailureStillSurfaces() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)
        val failure = IllegalStateException("provider failed")

        dependencies.execute(MediaItemCommand { _, _ -> throw failure }, listener.playbackStateListener)

        assertTrue("A failed execution was not answered", listener.awaitDelivery())
        val reported = assertThrows(AssertionError::class.java) { listener.settle() }
        assertEquals(IllegalStateException::class.java, reported.cause?.javaClass)
        assertEquals(failure.message, reported.cause?.message)
        assertEquals(1, listener.results)
        assertEquals(emptyList<String>(), listener.mediaIds)
    }

    /**
     * The scope the execution runs in is cancelled while the command is still blocked, and the
     * request that execution was serving is still waiting for its answer.
     */
    @Test
    fun anExecutionCancelledWithItsScopeIsStillAnswered() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)

        dependencies.execute(MediaItemCommand { _, _ -> listener.awaitDelivery() }, listener.playbackStateListener)
        listener.scope.cancel()

        listener.awaitResult()
        assertEquals(1, listener.results)
        assertEquals(emptyList<String>(), listener.mediaIds)
    }

    @Test
    fun aSecondExecutionOfTheSameDependenciesIsRefused() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)
        dependencies.execute(
            MediaItemCommand { _, _ -> dependencies.resultListener.onResult(listOf(item("first"))) },
            listener.playbackStateListener
        )
        listener.awaitResult()

        assertThrows(IllegalStateException::class.java) {
            dependencies.execute(
                MediaItemCommand { _, _ -> dependencies.resultListener.onResult(listOf(item("second"))) },
                listener.playbackStateListener
            )
        }

        listener.assertMediaIds("first")
        assertEquals(1, listener.results)
    }

    private fun item(id: String) = MediaItemBuilder.buildPlayable(station(id), false)

    companion object {

        /** Far below [RecordingCommandListener.AWAIT_MILLIS], so the deadline expires first. */
        private const val DEADLINE_MILLIS = 50L
    }
}
