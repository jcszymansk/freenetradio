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

import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.AppUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers two browse requests for the same node running at once, as when the phone UI and an
 * Android Auto head unit open Favorites at the same moment.
 *
 * The presenter keeps a single instance of each command, so both requests run on the same object.
 * Each case holds the provider until both executions are inside it, so they overlap for certain.
 * While they are held, neither request may be answered: a command that cancels the previous
 * execution has it answered empty by the deadline at that point. Once released, each request has
 * to get the data.
 *
 * The paged commands are not here: they keep the page index on the shared instance, which is a
 * separate defect (fnr-3ktp).
 */
class MediaItemCommandOverlapTest {

    @Test
    fun overlappingFavoritesRequestsBothGetTheFavorites() {
        assertBothAnswered(MediaItemFavoritesList(), "first", "second") { hold ->
            RecordingPresenter(mFavorites = stations("first", "second"), mBeforeAnswer = hold)
        }
    }

    @Test
    fun overlappingLocalStationsRequestsBothGetTheLocalStations() {
        assertBothAnswered(MediaItemLocalsList(), "first", "second") { hold ->
            RecordingPresenter(mDeviceLocals = stations("first", "second"), mBeforeAnswer = hold)
        }
    }

    @Test
    fun overlappingCategoriesRequestsBothGetTheCategories() {
        assertBothAnswered(
            MediaItemAllCategories(),
            MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock",
            parentId = MediaId.MEDIA_ID_ALL_CATEGORIES
        ) { hold ->
            RecordingPresenter(mCategories = setOf(Category("rock", "Rock", 42)), mBeforeAnswer = hold)
        }
    }

    @Test
    fun overlappingCountriesRequestsBothGetTheCountries() {
        assertBothAnswered(
            MediaItemCountriesList(),
            MediaId.MEDIA_ID_COUNTRIES_LIST + "PL",
            parentId = MediaId.MEDIA_ID_COUNTRIES_LIST
        ) { hold ->
            RecordingPresenter(mCountries = setOf(Country("Poland", "PL")), mBeforeAnswer = hold)
        }
    }

    @Test
    fun overlappingNewStationsRequestsBothGetTheStations() {
        assertBothAnswered(MediaItemNewStations(), "first", "second") { hold ->
            RecordingPresenter(mNewStations = stations("first", "second"), mBeforeAnswer = hold)
        }
    }

    @Test
    fun overlappingPopularStationsRequestsBothGetTheStations() {
        assertBothAnswered(MediaItemPopularStations(), "first", "second") { hold ->
            RecordingPresenter(mPopularStations = stations("first", "second"), mBeforeAnswer = hold)
        }
    }

    /**
     * Runs [command] for two requests, each with its own listener and dependencies as the service
     * makes them, against one presenter that [presenterWith] builds around a hold. The hold keeps
     * every provider call waiting until both executions have made one and the test has seen that
     * neither request was answered meanwhile. Then it lets them go, and each request has to be
     * answered once, with [expectedIds].
     */
    private fun assertBothAnswered(
        command: MediaItemCommand,
        vararg expectedIds: String,
        parentId: String = AppUtils.EMPTY_STRING,
        presenterWith: (hold: () -> Unit) -> RecordingPresenter
    ) {
        val bothAsked = CountDownLatch(EXECUTIONS)
        val released = CountDownLatch(1)
        val presenter = presenterWith {
            bothAsked.countDown()
            released.await(RecordingCommandListener.AWAIT_MILLIS, TimeUnit.MILLISECONDS)
        }
        val requests = listOf("first" to RecordingCommandListener(), "second" to RecordingCommandListener())

        for ((_, listener) in requests) {
            dependencies(presenter, listener, parentId = parentId).execute(command, listener.playbackStateListener)
        }

        // Released in finally so that a failed check does not leave both provider threads held
        // into the next test.
        try {
            assertTrue(
                "The two executions were never inside the provider at the same time, so their answers " +
                    "say nothing about overlapping requests",
                bothAsked.await(RecordingCommandListener.AWAIT_MILLIS, TimeUnit.MILLISECONDS)
            )
            // Long enough for the empty answer a cancelled execution gets, which follows the
            // cancellation at once; a correct command cannot answer before the release whatever the wait.
            Thread.sleep(HELD_MILLIS)
            for ((name, listener) in requests) {
                assertEquals(
                    "The $name request was answered while its provider was still working, so the other " +
                        "execution cut it short",
                    0,
                    listener.results
                )
            }
        } finally {
            released.countDown()
        }

        for ((name, listener) in requests) {
            listener.awaitResult()
            assertEquals("The $name request was not answered exactly once", 1, listener.results)
            assertEquals("The $name request did not get the data", expectedIds.toList(), listener.mediaIds)
            listener.assertNoError()
        }
    }

    companion object {

        private const val EXECUTIONS = 2

        /** How long the providers stay held before the test looks for an answer that came too early. */
        private const val HELD_MILLIS = 100L
    }
}
