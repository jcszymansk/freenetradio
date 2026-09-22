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

package com.yuriy.openradio.shared.utils

import android.content.Context
import android.content.ContextWrapper
import com.yuriy.openradio.shared.model.media.RadioStationToAdd
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * Covers the rules of [RadioStationValidatorImpl] against a [RecordingProbe], so no case reaches
 * the network.
 *
 * Every probe and every answer lands in one [mEvents] list, which is what lets a case assert the
 * order as well as the membership: a stream that is never probed, or a home page probed after
 * the answer went out, fails the same assertion as a wrong answer.
 *
 * Unless a case is about the scopes, both are [Dispatchers.Unconfined], so validation has run to
 * completion by the time [RadioStationValidatorImpl.validate] returns.
 */
class RadioStationValidatorImplTest {

    private val mContext: Context = ContextWrapper(null)

    private val mEvents = mutableListOf<String>()

    @Test
    fun aCandidateWithoutANameFailsBeforeAnythingIsProbed() {
        val probeDispatcher = QueuedDispatcher()

        validate(candidate(name = ""), probeScope = CoroutineScope(probeDispatcher))

        assertEquals(listOf("failure: Radio Station's name is invalid"), mEvents)
        assertEquals("nothing may be queued for probing", 0, probeDispatcher.pending)
    }

    @Test
    fun aCandidateWithoutAStreamUrlFailsBeforeAnythingIsProbed() {
        val probeDispatcher = QueuedDispatcher()

        validate(candidate(url = ""), probeScope = CoroutineScope(probeDispatcher))

        assertEquals(listOf("failure: Radio Station's url is invalid"), mEvents)
        assertEquals("nothing may be queued for probing", 0, probeDispatcher.pending)
    }

    @Test
    fun aStreamUrlTheProbeCouldNotOpenFailsBeforeAnythingIsProbed() {
        val unusable = listOf(
            "127.0.0.1:1/stream",
            "not a url",
            "rtsp://127.0.0.1:1/stream",
            "ftp://127.0.0.1:1/stream",
            "file:///sdcard/stream.mp3",
            "http:///stream",
            "http://"
        )
        for (url in unusable) {
            mEvents.clear()
            val probeDispatcher = QueuedDispatcher()

            validate(candidate(url = url), probeScope = CoroutineScope(probeDispatcher))

            assertEquals(url, listOf("failure: Radio Station's url is invalid"), mEvents)
            assertEquals("nothing may be queued for probing $url", 0, probeDispatcher.pending)
        }
    }

    @Test
    fun httpAndHttpsStreamUrlsInAnyCaseAreProbed() {
        val usable = listOf(
            "https://127.0.0.1:1/stream",
            "HTTPS://127.0.0.1:1/stream",
            "Http://127.0.0.1:1/stream"
        )
        for (url in usable) {
            mEvents.clear()

            validate(candidate(url = url, homePage = ""))

            assertEquals(
                url,
                listOf("probe: $url", "success: Radio Station validated successfully"),
                mEvents
            )
        }
    }

    @Test
    fun aCandidateWithoutANameFailsEvenWithoutAStreamUrl() {
        validate(candidate(name = "", url = ""))

        assertEquals(listOf("failure: Radio Station's name is invalid"), mEvents)
    }

    @Test
    fun anUnreachableStreamFailsWithoutProbingTheHomePage() {
        validate(candidate(), RecordingProbe(unreachable = setOf(STREAM_URL, HOME_PAGE)))

        assertEquals(
            listOf("probe: $STREAM_URL", "failure: Radio Station's stream is invalid"),
            mEvents
        )
    }

    @Test
    fun anUnreachableStreamFailsEvenWithoutAHomePage() {
        validate(candidate(homePage = ""), RecordingProbe(unreachable = setOf(STREAM_URL)))

        assertEquals(
            listOf("probe: $STREAM_URL", "failure: Radio Station's stream is invalid"),
            mEvents
        )
    }

    @Test
    fun anEmptyHomePageIsNotProbedAndProducesNoWarning() {
        validate(candidate(homePage = ""))

        assertEquals(
            listOf("probe: $STREAM_URL", "success: Radio Station validated successfully"),
            mEvents
        )
    }

    @Test
    fun anUnreachableHomePageWarnsAheadOfTheSuccessAndNeverFails() {
        validate(candidate(), RecordingProbe(unreachable = setOf(HOME_PAGE)))

        assertEquals(
            listOf(
                "probe: $STREAM_URL",
                "probe: $HOME_PAGE",
                "warning: Radio Station's home page is invalid",
                "success: Radio Station validated successfully"
            ),
            mEvents
        )
    }

    @Test
    fun aReachableHomePageIsProbedAndOnlySucceeds() {
        validate(candidate())

        assertEquals(
            listOf(
                "probe: $STREAM_URL",
                "probe: $HOME_PAGE",
                "success: Radio Station validated successfully"
            ),
            mEvents
        )
    }

    @Test
    fun theProbeIsHandedTheContextTheCallerValidatedWith() {
        val probe = RecordingProbe()

        validate(candidate(), probe)

        assertEquals(2, probe.contexts.size)
        probe.contexts.forEach { assertSame(mContext, it) }
    }

    @Test
    fun theProbesRunOnTheProbeScopeAndTheAnswersOnTheUiScope() {
        val probeDispatcher = QueuedDispatcher()
        val uiDispatcher = QueuedDispatcher()

        validate(
            candidate(),
            RecordingProbe(unreachable = setOf(HOME_PAGE)),
            uiScope = CoroutineScope(uiDispatcher),
            probeScope = CoroutineScope(probeDispatcher)
        )
        assertEquals("validate must not probe on the caller's thread", emptyList<String>(), mEvents)

        probeDispatcher.runPending()
        assertEquals(
            "the probe scope must probe and leave the answers to the UI scope",
            listOf("probe: $STREAM_URL", "probe: $HOME_PAGE"),
            mEvents
        )

        uiDispatcher.runPending()
        assertEquals(
            listOf(
                "probe: $STREAM_URL",
                "probe: $HOME_PAGE",
                "warning: Radio Station's home page is invalid",
                "success: Radio Station validated successfully"
            ),
            mEvents
        )
    }

    @Test
    fun anUnreachableStreamIsAnsweredOnTheUiScope() {
        val probeDispatcher = QueuedDispatcher()
        val uiDispatcher = QueuedDispatcher()

        validate(
            candidate(),
            RecordingProbe(unreachable = setOf(STREAM_URL)),
            uiScope = CoroutineScope(uiDispatcher),
            probeScope = CoroutineScope(probeDispatcher)
        )
        probeDispatcher.runPending()
        assertEquals(listOf("probe: $STREAM_URL"), mEvents)

        uiDispatcher.runPending()
        assertEquals(
            listOf("probe: $STREAM_URL", "failure: Radio Station's stream is invalid"),
            mEvents
        )
    }

    private fun validate(
        candidate: RadioStationToAdd,
        probe: RecordingProbe = RecordingProbe(),
        uiScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
        probeScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ) {
        RadioStationValidatorImpl(uiScope, probeScope, probe).validate(
            mContext,
            candidate,
            { mEvents.add("success: $it") },
            { mEvents.add("warning: $it") },
            { mEvents.add("failure: $it") }
        )
    }

    private fun candidate(
        name: String = "Test Station",
        url: String = STREAM_URL,
        homePage: String = HOME_PAGE
    ) = RadioStationToAdd(
        name, url, imageLocalUrl = "", homePage, genre = "", country = "", isAddToFav = false
    )

    /**
     * Answers every url as reachable except [unreachable], and records each question in
     * [mEvents] in the order it was asked.
     */
    private inner class RecordingProbe(
        private val unreachable: Set<String> = emptySet()
    ) : ResourceProbe {

        val contexts = mutableListOf<Context>()

        override fun isReachable(context: Context, url: String): Boolean {
            contexts.add(context)
            mEvents.add("probe: $url")
            return url !in unreachable
        }
    }

    /**
     * Holds every dispatched block until [runPending], so a case can observe what has run on
     * one scope before the other gets a turn.
     */
    private class QueuedDispatcher : CoroutineDispatcher() {

        private val mQueue = ArrayDeque<Runnable>()

        val pending: Int
            get() = mQueue.size

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            mQueue.addLast(block)
        }

        fun runPending() {
            while (mQueue.isNotEmpty()) {
                mQueue.removeFirst().run()
            }
        }
    }

    companion object {
        private const val STREAM_URL = "http://127.0.0.1:1/stream"
        private const val HOME_PAGE = "http://127.0.0.1:1/home"
    }
}
