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
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the answer [MediaItemCommandDependencies] guarantees a browse request: exactly one,
 * whatever the work behind it does (TASK-072).
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

    @Test
    fun workThatAnswersInTimeIsTheAnswer() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS)

        dependencies.launchWithDeadline { dependencies.resultListener.onResult(listOf(item("answer"))) }

        listener.awaitResult().assertMediaIds("answer")
        assertEquals(1, listener.results)
    }

    /**
     * The work stays blocked until the request has been answered, so the answer can only have come
     * from the deadline, and the work's own answer arrives after it.
     */
    @Test
    fun stuckWorkIsAnsweredEmptyAtTheDeadlineAndItsLateAnswerIsDropped() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS)

        dependencies.launchWithDeadline {
            listener.awaitDelivery()
            dependencies.resultListener.onResult(listOf(item("late")))
        }

        listener.awaitResult()
        assertEquals(1, listener.results)
        assertEquals(emptyList<String>(), listener.mediaIds)
    }

    /**
     * The work answers and then runs on until the deadline cancels it, which is the moment a guard
     * that answers on its deadline regardless would send an empty result over the real one.
     */
    @Test
    fun workThatAnswersAndOutlivesTheDeadlineKeepsItsAnswer() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS)

        dependencies.launchWithDeadline {
            dependencies.resultListener.onResult(listOf(item("answer")))
            awaitCancellation()
        }

        listener.awaitResult().assertMediaIds("answer")
        assertEquals(1, listener.results)
    }

    @Test
    fun workThatReturnsWithoutAnsweringIsAnsweredEmpty() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)

        dependencies.launchWithDeadline { }

        listener.awaitResult()
        assertEquals(1, listener.results)
        assertEquals(emptyList<String>(), listener.mediaIds)
    }

    @Test
    fun failedWorkIsAnsweredEmptyAndTheFailureStillSurfaces() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)
        val failure = IllegalStateException("provider failed")

        dependencies.launchWithDeadline { throw failure }

        assertTrue("A failed execution was not answered", listener.awaitDelivery())
        val reported = assertThrows(AssertionError::class.java) { listener.settle() }
        assertEquals(IllegalStateException::class.java, reported.cause?.javaClass)
        assertEquals(failure.message, reported.cause?.message)
        assertEquals(1, listener.results)
        assertEquals(emptyList<String>(), listener.mediaIds)
    }

    /**
     * A command cancels the execution it is replacing, and the request that execution was serving
     * is still waiting for its answer.
     */
    @Test
    fun anAbandonedExecutionIsStillAnswered() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)

        val execution = dependencies.launchWithDeadline { awaitCancellation() }
        execution.cancel()

        listener.awaitResult()
        assertEquals(1, listener.results)
        assertEquals(emptyList<String>(), listener.mediaIds)
    }

    private fun item(id: String) = MediaItemBuilder.buildPlayable(station(id), false)

    companion object {

        /** Far below [RecordingCommandListener.AWAIT_MILLIS], so the deadline expires first. */
        private const val DEADLINE_MILLIS = 50L
    }
}
