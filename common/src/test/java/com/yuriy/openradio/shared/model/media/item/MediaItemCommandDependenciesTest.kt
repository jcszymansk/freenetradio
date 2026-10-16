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

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

    /**
     * A result after one that answered the request is not late: the client has its answer, so
     * neither listener hears of it.
     */
    @Test
    fun onlyTheFirstResultReachesTheListener() {
        val listener = RecordingCommandListener()
        val late = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener, lateListener = late)

        dependencies.resultListener.onResult(listOf(item("first")))
        dependencies.resultListener.onResult(listOf(item("second")))
        dependencies.resultListener.onResult()

        assertEquals(1, listener.results)
        listener.assertMediaIds("first")
        assertEquals("A result after the answer was passed on as late", 0, late.results)
    }

    /**
     * The service tells a first page from a later one by the page an answer carries, which is the
     * page the request asked for.
     */
    @Test
    fun onlyTheFirstAnswerIsDeliveredForThePageTheRequestAskedFor() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener, pageNumber = UrlLayer.FIRST_PAGE_INDEX + 2)

        val first = dependencies.answer(listOf(item("first")))
        val second = dependencies.answer(listOf(item("second")))

        assertTrue("The first answer was reported as dropped", first)
        assertFalse("A second answer was reported as delivered", second)
        assertEquals(1, listener.results)
        listener.assertMediaIds("first")
        assertEquals(UrlLayer.FIRST_PAGE_INDEX + 2, listener.pageNumber)
    }

    /**
     * An answer after the deadline does not answer the request, so it reports itself undelivered,
     * which keeps a node found empty from reporting "no data" long after its answer. It still goes
     * to the late listener, which is how the service learns of the page (fnr-2r55y).
     */
    @Test
    fun anAnswerAfterTheDeadlineIsReportedAsNotTheAnswerAndGoesToTheLateListener() {
        val listener = RecordingCommandListener()
        val late = RecordingCommandListener()
        val dependencies = dependencies(
            RecordingPresenter(),
            listener,
            pageNumber = UrlLayer.FIRST_PAGE_INDEX + 1,
            timeoutMillis = DEADLINE_MILLIS,
            lateListener = late
        )

        dependencies.execute(MediaItemCommand { _, _ -> listener.awaitDelivery() }, listener.playbackStateListener)
        listener.awaitResult()

        assertFalse(
            "An answer after the deadline was reported as delivered",
            dependencies.answer(listOf(item("late")), stations("late"))
        )
        assertEquals(emptyList<String>(), listener.mediaIds)
        assertEquals(1, listener.results)
        assertEquals(1, late.results)
        late.assertMediaIds("late")
        assertEquals(listOf("late"), late.radioStations.map { it.id })
        assertEquals(UrlLayer.FIRST_PAGE_INDEX + 1, late.pageNumber)
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
    fun aStuckCommandIsAnsweredEmptyAtTheDeadlineAndItsLateAnswerGoesToTheLateListener() {
        val listener = RecordingCommandListener()
        val late = RecordingCommandListener()
        val dependencies = dependencies(
            RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS, lateListener = late
        )

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
        assertEquals(1, late.results)
        late.assertMediaIds("late")
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, late.pageNumber)
    }

    @Test
    fun onlyTheFirstLateAnswerReachesTheLateListener() {
        val listener = RecordingCommandListener()
        val late = RecordingCommandListener()
        val dependencies = dependencies(
            RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS, lateListener = late
        )

        dependencies.execute(
            MediaItemCommand { _, _ ->
                listener.awaitDelivery()
                dependencies.answer(listOf(item("first")))
                dependencies.answer(listOf(item("second")))
            },
            listener.playbackStateListener
        )

        listener.awaitResult()
        assertEquals(1, listener.results)
        assertEquals(1, late.results)
        late.assertMediaIds("first")
    }

    /**
     * The deadline answered the request with nothing, so a late answer of nothing has nothing to
     * add, and a non-empty one after it is still the page the client lacks.
     */
    @Test
    fun aLateEmptyAnswerIsNotPassedOnAndLeavesTheLateListenerForTheNextOne() {
        val listener = RecordingCommandListener()
        val late = RecordingCommandListener()
        val dependencies = dependencies(
            RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS, lateListener = late
        )

        dependencies.execute(
            MediaItemCommand { _, _ ->
                listener.awaitDelivery()
                dependencies.answer(emptyList())
                dependencies.answer(listOf(item("page")))
            },
            listener.playbackStateListener
        )

        listener.awaitResult()
        assertEquals(1, listener.results)
        assertEquals(1, late.results)
        late.assertMediaIds("page")
    }

    @Test
    fun aCommandThatAnswersInTimeHasNoLateAnswer() {
        val listener = RecordingCommandListener()
        val late = RecordingCommandListener()
        val dependencies = dependencies(
            RecordingPresenter(), listener, timeoutMillis = DEADLINE_MILLIS, lateListener = late
        )

        dependencies.execute(
            MediaItemCommand { _, _ ->
                dependencies.answer(listOf(item("answer")))
                dependencies.answer(listOf(item("again")))
            },
            listener.playbackStateListener
        )

        listener.awaitResult().assertMediaIds("answer")
        assertEquals(0, late.results)
    }

    /**
     * The service handles the late page by telling the client to ask again. Were it to do so
     * before the deadline's empty answer reached the client, the empty answer would land on the
     * fresh one. So the late answer waits for the empty one to be delivered: here the empty answer
     * is slow to deliver, and the command answers while it is being delivered.
     */
    @Test
    fun aLateAnswerIsPassedOnOnlyOnceTheDeadlineAnswerIsDelivered() {
        val events = ConcurrentLinkedQueue<String>()
        val delivering = CountDownLatch(1)
        val listener = object : OpenRadioService.ResultListener {
            override fun onResult(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int) {
                delivering.countDown()
                Thread.sleep(DEADLINE_MILLIS * 4)
                events.add("answer ${items.map { it.mediaId }}")
            }
        }
        val late = object : OpenRadioService.ResultListener {
            override fun onResult(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int) {
                events.add("late ${items.map { it.mediaId }}")
            }
        }
        val recording = RecordingCommandListener()
        val dependencies = MediaItemCommandDependencies(
            testContext(), RecordingPresenter(), DEFAULT_COUNTRY_CODE, "parent", UrlLayer.FIRST_PAGE_INDEX,
            false, Bundle(), recording.scope, listener, late, DEADLINE_MILLIS
        )

        dependencies.execute(
            MediaItemCommand { _, _ ->
                delivering.await(RecordingCommandListener.AWAIT_MILLIS, TimeUnit.MILLISECONDS)
                dependencies.answer(listOf(item("page")))
            },
            recording.playbackStateListener
        )

        recording.settle()
        assertEquals(listOf("answer []", "late [page]"), events.toList())
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
     * The scope is the one the service runs every browse command in, so the command after a failed
     * one has to run as if nothing had happened, rather than start in a cancelled scope and be
     * answered empty without asking anything.
     */
    @Test
    fun aFailedCommandLeavesItsScopeRunningTheCommandsAfterIt() {
        val failures = ConcurrentLinkedQueue<Throwable>()
        val scope = MediaItemCommandDependencies.newCommandScope { _, failure -> failures.add(failure) }
        try {
            val failed = RecordingCommandListener()
            dependencies(RecordingPresenter(), failed, scope = scope)
                .execute(MediaItemCommand { _, _ -> throw IllegalStateException("provider failed") }, failed.playbackStateListener)
            assertTrue("A failed execution was not answered", failed.awaitDelivery())
            joinChildren(scope)
            val next = RecordingCommandListener()
            val dependencies = dependencies(RecordingPresenter(), next, scope = scope)

            dependencies.execute(
                MediaItemCommand { _, _ -> dependencies.resultListener.onResult(listOf(item("answer"))) },
                next.playbackStateListener
            )

            assertTrue("The command after a failed one was not answered", next.awaitDelivery())
            joinChildren(scope)
            next.assertMediaIds("answer")
            assertEquals(1, failed.results)
            assertEquals(listOf("provider failed"), failures.map { it.message })
        } finally {
            scope.cancel()
        }
    }

    /**
     * Run in the scope exactly as the service builds it, failure log and all. A failure that
     * reaches the thread's uncaught exception handler ends the process on Android, so the request
     * being answered is not enough: the failure must end at the scope's own handler.
     */
    @Test
    fun aFailedCommandInTheServiceScopeDoesNotReachTheUncaughtExceptionHandler() {
        val uncaught = ConcurrentLinkedQueue<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, failure -> uncaught.add(failure) }
        val scope = MediaItemCommandDependencies.newCommandScope()
        try {
            val listener = RecordingCommandListener()
            dependencies(RecordingPresenter(), listener, scope = scope)
                .execute(MediaItemCommand { _, _ -> throw IllegalStateException("provider failed") }, listener.playbackStateListener)

            assertTrue("A failed execution was not answered", listener.awaitDelivery())
            joinChildren(scope)

            assertEquals(emptyList<Throwable>(), uncaught.toList())
            assertTrue("The failure cancelled the service scope", scope.isActive)
            assertEquals(1, listener.results)
        } finally {
            scope.cancel()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    /**
     * The failure is logged with the node it failed for, so a browse list that came up empty can be
     * told apart from one that had nothing to show.
     */
    @Test
    fun aFailedCommandIsLoggedWithItsNode() {
        val logged = ConcurrentLinkedQueue<Pair<String, Throwable>>()
        val scope = MediaItemCommandDependencies.newCommandScope { message, failure -> logged.add(message to failure) }
        try {
            val failure = IllegalStateException("provider failed")
            val listener = RecordingCommandListener()
            dependencies(RecordingPresenter(), listener, parentId = "failing node", scope = scope)
                .execute(MediaItemCommand { _, _ -> throw failure }, listener.playbackStateListener)

            assertTrue("A failed execution was not answered", listener.awaitDelivery())
            joinChildren(scope)

            val (message, reported) = logged.single()
            assertSame(failure, reported)
            assertTrue("The log does not name the node: $message", message.contains("failing node"))
        } finally {
            scope.cancel()
        }
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

    /**
     * Waits for every execution launched in [scope]. A failed one has passed its failure to the
     * scope's handler before it counts as finished, so what the handler did is final afterwards.
     */
    private fun joinChildren(scope: CoroutineScope) {
        runBlocking { withTimeout(RecordingCommandListener.AWAIT_MILLIS) { scope.coroutineContext.job.children.toList().joinAll() } }
    }

    companion object {

        /** Far below [RecordingCommandListener.AWAIT_MILLIS], so the deadline expires first. */
        private const val DEADLINE_MILLIS = 50L
    }
}
