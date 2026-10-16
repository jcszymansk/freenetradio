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
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.AppUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers what a browse command does with an answer its provider gives after the deadline has
 * answered the request empty (fnr-3ktp). The late answer does not answer the request, so nothing
 * it did may outlast it: the next request for the page that was late has to get that page, and a
 * node found empty must not report "no data" for a request that was answered long before. That
 * the service then asks for the late page again, rather than for the one after it, is decided by
 * [com.yuriy.openradio.shared.model.media.ClientPages] and covered by its test.
 *
 * The late answer goes to the late listener instead, with the page and the stations the command
 * built, which is what the service stores and pushes to the client (fnr-2r55y).
 *
 * Each case runs the command the way the service does, through
 * [MediaItemCommandDependencies.execute], holds the provider until the deadline has answered, then
 * lets it go and waits for the late worker to finish before looking.
 */
class MediaItemCommandLateAnswerTest {

    @Test
    fun aLateFirstPageOfACategoryIsAskedForAgain() {
        val parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock"
        val presenter = HeldPresenter { hold -> RecordingPresenter(mCategoryStations = stations("first", "second"), mBeforeAnswer = hold) }
        val command = MediaItemChildCategories()

        presenter.runPastTheDeadline(command, parentId)
        val retry = presenter.runInTime(command, parentId)

        retry.assertMediaIds("first")
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, retry.pageNumber)
        assertEquals(
            listOf("rock" to UrlLayer.FIRST_PAGE_INDEX, "rock" to UrlLayer.FIRST_PAGE_INDEX),
            presenter.recording.categoryRequests
        )
    }

    @Test
    fun aLateFirstPageOfACountryIsAskedForAgain() {
        val parentId = MediaId.MEDIA_ID_COUNTRY_STATIONS
        val presenter = HeldPresenter { hold ->
            RecordingPresenter(
                mCountries = setOf(Country("Poland", "PL")),
                mCountryStations = stations("first", "second"),
                mBeforeAnswer = hold
            )
        }
        val command = MediaItemCountryStations()

        presenter.runPastTheDeadline(command, parentId)
        val retry = presenter.runInTime(command, parentId)

        retry.assertMediaIds("first")
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, retry.pageNumber)
        assertEquals(
            listOf(DEFAULT_COUNTRY_CODE to UrlLayer.FIRST_PAGE_INDEX, DEFAULT_COUNTRY_CODE to UrlLayer.FIRST_PAGE_INDEX),
            presenter.recording.countryRequests
        )
    }

    @Test
    fun aRetryOfALateNextPageGetsThatPage() {
        val parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock"
        val presenter = HeldPresenter { hold ->
            RecordingPresenter(mCategoryStations = stations("first", "second", "third"), mBeforeAnswer = hold)
        }
        val command = MediaItemChildCategories()

        presenter.runInTime(command, parentId).assertMediaIds("first")
        presenter.runPastTheDeadline(command, parentId, UrlLayer.FIRST_PAGE_INDEX + 1)
        val retry = presenter.runInTime(command, parentId, UrlLayer.FIRST_PAGE_INDEX + 1)

        retry.assertMediaIds("second")
        assertEquals(UrlLayer.FIRST_PAGE_INDEX + 1, retry.pageNumber)
        assertEquals(
            listOf(
                "rock" to UrlLayer.FIRST_PAGE_INDEX,
                "rock" to UrlLayer.FIRST_PAGE_INDEX + 1,
                "rock" to UrlLayer.FIRST_PAGE_INDEX + 1
            ),
            presenter.recording.categoryRequests
        )
    }

    @Test
    fun aLateFirstPageOfACountryGoesToTheLateListenerWithItsStations() {
        val presenter = HeldPresenter { hold ->
            RecordingPresenter(
                mCountries = setOf(Country("Poland", "PL")),
                mCountryStations = stations("first", "second"),
                mBeforeAnswer = hold
            )
        }
        val late = RecordingCommandListener()

        presenter.runPastTheDeadline(MediaItemCountryStations(), MediaId.MEDIA_ID_COUNTRY_STATIONS, late = late)

        assertEquals(1, late.results)
        late.assertMediaIds("first")
        assertEquals(listOf("first"), late.radioStations.map { it.id })
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, late.pageNumber)
    }

    @Test
    fun aLateNextPageOfACategoryGoesToTheLateListenerWithItsPage() {
        val parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock"
        val presenter = HeldPresenter { hold ->
            RecordingPresenter(mCategoryStations = stations("first", "second"), mBeforeAnswer = hold)
        }
        val late = RecordingCommandListener()

        presenter.runPastTheDeadline(
            MediaItemChildCategories(), parentId, UrlLayer.FIRST_PAGE_INDEX + 1, late = late
        )

        assertEquals(1, late.results)
        late.assertMediaIds("second")
        assertEquals(UrlLayer.FIRST_PAGE_INDEX + 1, late.pageNumber)
    }

    @Test
    fun aLateSearchAnswerGoesToTheLateListener() {
        val presenter = HeldPresenter { hold ->
            RecordingPresenter(mSearchStations = stations("found"), mBeforeAnswer = hold)
        }
        val late = RecordingCommandListener()

        presenter.runPastTheDeadline(
            MediaItemSearchFromService(),
            MediaId.MEDIA_ID_SEARCH_FROM_SERVICE,
            options = AppUtils.makeSearchQueryBundle("jazz"),
            late = late
        )

        assertEquals(listOf("jazz"), presenter.recording.searchRequests)
        assertEquals(1, late.results)
        late.assertMediaIds(MediaId.makeSearchId("found"))
    }

    /** A late empty second page reports no "no data", and has nothing to pass on either. */
    @Test
    fun aLateEmptyNextPageGoesNowhere() {
        val parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock"
        val presenter = HeldPresenter { hold -> RecordingPresenter(mBeforeAnswer = hold) }
        val late = RecordingCommandListener()

        val listener = presenter.runPastTheDeadline(
            MediaItemChildCategories(), parentId, UrlLayer.FIRST_PAGE_INDEX + 1, late = late
        )

        listener.assertNoError()
        assertEquals(0, late.results)
    }

    @Test
    fun aLateEmptyListOfNewStationsReportsNothing() {
        assertLateEmptyNodeReportsNothing(MediaItemNewStations()) { hold -> RecordingPresenter(mBeforeAnswer = hold) }
    }

    @Test
    fun aLateEmptyListOfPopularStationsReportsNothing() {
        assertLateEmptyNodeReportsNothing(MediaItemPopularStations()) { hold -> RecordingPresenter(mBeforeAnswer = hold) }
    }

    @Test
    fun aLateEmptyListOfCategoriesReportsNothing() {
        assertLateEmptyNodeReportsNothing(MediaItemAllCategories()) { hold -> RecordingPresenter(mBeforeAnswer = hold) }
    }

    @Test
    fun aLateEmptyListOfCountriesReportsNothing() {
        assertLateEmptyNodeReportsNothing(MediaItemCountriesList()) { hold -> RecordingPresenter(mBeforeAnswer = hold) }
    }

    /**
     * Without the deadline in the way, the same empty node does report "no data", so the silence
     * the cases above assert is the late answer being dropped rather than a node that never reports.
     */
    @Test
    fun anEmptyListOfNewStationsInTimeReportsNoData() {
        val listener = RecordingCommandListener()

        dependencies(RecordingPresenter(), listener).execute(MediaItemNewStations(), listener.playbackStateListener)

        listener.awaitError()
        assertEquals(1, listener.errors)
        assertEquals(1, listener.results)
    }

    private fun assertLateEmptyNodeReportsNothing(
        command: MediaItemCommand,
        presenterWith: (hold: () -> Unit) -> RecordingPresenter
    ) {
        val listener = HeldPresenter(presenterWith).runPastTheDeadline(command, MediaId.MEDIA_ID_ROOT)

        assertEquals("The command answered more than once", 1, listener.results)
        listener.assertNoError()
    }

    /**
     * A [RecordingPresenter] whose provider calls can be held back until the deadline has answered
     * the request they were made for, which is how a provider stuck on the network looks to a
     * command. Calls made while nothing is held answer at once.
     */
    private class HeldPresenter(presenterWith: (hold: () -> Unit) -> RecordingPresenter) {

        @Volatile
        private var mHeld: Held? = null

        val recording = presenterWith { mHeld?.hold() }

        /**
         * Runs [command] with a deadline that expires while its provider is held, waits until the
         * deadline has answered, releases the provider and waits for the command to finish.
         *
         * @return The listener of the request, holding everything it received, settled.
         */
        fun runPastTheDeadline(
            command: MediaItemCommand,
            parentId: String,
            pageNumber: Int = UrlLayer.FIRST_PAGE_INDEX,
            options: Bundle = Bundle(),
            late: RecordingCommandListener = RecordingCommandListener()
        ): RecordingCommandListener {
            val listener = RecordingCommandListener()
            val held = Held(listener)
            mHeld = held
            try {
                dependencies(
                    recording,
                    listener,
                    parentId = parentId,
                    pageNumber = pageNumber,
                    options = options,
                    timeoutMillis = DEADLINE_MILLIS,
                    lateListener = late
                ).execute(command, listener.playbackStateListener)
                assertTrue(
                    "The command never asked its provider, so its answer says nothing about a late page",
                    held.asked.await(RecordingCommandListener.AWAIT_MILLIS, TimeUnit.MILLISECONDS)
                )
                listener.awaitResult()
            } finally {
                mHeld = null
            }
            assertTrue("The provider was never held past the deadline", held.answeredWhileHeld.get())
            assertEquals("The deadline did not answer the request empty", emptyList<String>(), listener.mediaIds)
            return listener
        }

        /**
         * Runs [command] for [pageNumber] of [parentId], with nothing held.
         *
         * @return The listener of the request, holding its answer, settled.
         */
        fun runInTime(
            command: MediaItemCommand,
            parentId: String,
            pageNumber: Int = UrlLayer.FIRST_PAGE_INDEX
        ): RecordingCommandListener {
            val listener = RecordingCommandListener()
            dependencies(recording, listener, parentId = parentId, pageNumber = pageNumber)
                .execute(command, listener.playbackStateListener)
            listener.awaitResult()
            listener.assertNoError()
            return listener
        }
    }

    /** Holds every provider call until [mListener] has been answered, and records that it was. */
    private class Held(private val mListener: RecordingCommandListener) {

        val asked = CountDownLatch(1)

        val answeredWhileHeld = AtomicBoolean(false)

        fun hold() {
            asked.countDown()
            if (mListener.awaitDelivery()) {
                answeredWhileHeld.set(true)
            }
        }
    }

    companion object {

        /** Far below [RecordingCommandListener.AWAIT_MILLIS], so the deadline expires first. */
        private const val DEADLINE_MILLIS = 50L
    }
}
