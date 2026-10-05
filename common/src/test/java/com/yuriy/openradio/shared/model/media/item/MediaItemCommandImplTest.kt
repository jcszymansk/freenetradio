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

import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import com.yuriy.openradio.shared.utils.MediaItemHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the decision every station-listing command hands its loaded set to: a non-empty set
 * becomes playable items, and an empty one is either reported as missing data or answered as an
 * empty page, depending on [MediaItemCommandImpl.doLoadNoDataReceived].
 *
 * The calls are made on the test thread, with no coroutine in between, so what the listener holds
 * when they return is everything the class did.
 */
class MediaItemCommandImplTest {

    @Test
    fun loadedStationsAreDeliveredAsPlayableItemsWithTheirFavoriteFlag() {
        val presenter = RecordingPresenter(mFavoriteIds = setOf("starred"))
        val listener = RecordingCommandListener()
        val loaded = stations("plain", "starred")
        val command = StubCommand(isNoDataReported = true)

        command.handleDataLoaded(listener.playbackStateListener, dependencies(presenter, listener), loaded, PAGE)

        listener.awaitResult().assertMediaIds("plain", "starred")
        assertEquals(listOf("plain", "starred"), presenter.favoriteChecks)
        assertFalse(MediaItemHelper.isFavoriteField(listener.items[0].mediaMetadata))
        assertTrue(MediaItemHelper.isFavoriteField(listener.items[1].mediaMetadata))
        for (item in listener.items) {
            assertTrue("${item.mediaId} is not playable", item.mediaMetadata.isPlayable == true)
        }
        assertEquals(loaded, listener.radioStations)
        assertEquals(PAGE, listener.pageNumber)
        assertEquals(1, listener.results)
        assertEquals("A non-empty set was checked for the no data policy", 0, command.noDataQuestions)
        listener.assertNoError()
    }

    @Test
    fun loadedStationsFollowTheItemsAlreadyCollected() {
        val listener = RecordingCommandListener()
        val dependencies = dependencies(RecordingPresenter(), listener)
        dependencies.addMediaItem(MediaItemBuilder.buildPlayable(station("earlier")))

        StubCommand(isNoDataReported = false).handleDataLoaded(
            listener.playbackStateListener, dependencies, stations("loaded"), PAGE
        )

        listener.awaitResult().assertMediaIds("earlier", "loaded")
        assertEquals(setOf("loaded"), listener.radioStations.map { it.id }.toSet())
    }

    @Test
    fun anEmptySetIsReportedAsMissingDataWhenTheCommandAsksForIt() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()
        val dependencies = dependencies(presenter, listener)
        dependencies.addMediaItem(MediaItemBuilder.buildPlayable(station("earlier")))
        val command = StubCommand(isNoDataReported = true)

        command.handleDataLoaded(listener.playbackStateListener, dependencies, emptySet(), PAGE)

        listener.awaitError()
        assertEquals(1, listener.errors)
        assertEquals(STRING_RESOURCE, listener.error)
        assertEquals(1, listener.results)
        listener.assertMediaIds("earlier")
        assertEquals(emptySet<RadioStation>(), listener.radioStations)
        assertEquals(
            "Missing data is reported for the first page, not the one that came back empty",
            UrlLayer.FIRST_PAGE_INDEX,
            listener.pageNumber
        )
        assertEquals(1, command.noDataQuestions)
        assertEquals(emptyList<String>(), presenter.favoriteChecks)
    }

    @Test
    fun anEmptySetIsAnsweredAsAnEmptyPageOtherwise() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()
        val dependencies = dependencies(presenter, listener)
        dependencies.addMediaItem(MediaItemBuilder.buildPlayable(station("earlier")))
        val command = StubCommand(isNoDataReported = false)

        command.handleDataLoaded(listener.playbackStateListener, dependencies, emptySet(), PAGE)

        listener.awaitResult().assertMediaIds("earlier")
        assertEquals(emptySet<RadioStation>(), listener.radioStations)
        assertEquals(PAGE, listener.pageNumber)
        assertEquals(1, listener.results)
        assertEquals(1, command.noDataQuestions)
        assertEquals(emptyList<String>(), presenter.favoriteChecks)
        listener.assertNoError()
    }

    @Test
    fun anEmptyPageWithoutAPageNumberIsTheFirst() {
        val listener = RecordingCommandListener()

        StubCommand(isNoDataReported = false).handleDataLoaded(
            listener.playbackStateListener, dependencies(RecordingPresenter(), listener), emptySet()
        )

        listener.awaitResult()
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, listener.pageNumber)
        listener.assertNoError()
    }

    @Test
    fun loadedStationsWithoutAPageNumberArriveAsTheFirstPage() {
        val listener = RecordingCommandListener()

        StubCommand(isNoDataReported = false).handleDataLoaded(
            listener.playbackStateListener, dependencies(RecordingPresenter(), listener), stations("only")
        )

        listener.awaitResult().assertMediaIds("only")
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, listener.pageNumber)
    }

    /**
     * The restored instance answer: nothing loaded, so only what was collected before is sent, for
     * the first page, without consulting the presenter.
     */
    @Test
    fun deliveringWithoutASetSendsOnlyTheItemsAlreadyCollected() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()
        val dependencies = dependencies(presenter, listener)
        dependencies.addMediaItem(MediaItemBuilder.buildPlayable(station("earlier")))

        StubCommand(isNoDataReported = true).deliverResult(dependencies)

        listener.awaitResult().assertMediaIds("earlier")
        assertEquals(emptySet<RadioStation>(), listener.radioStations)
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, listener.pageNumber)
        assertEquals(emptyList<String>(), presenter.favoriteChecks)
        listener.assertNoError()
    }

    @Test
    fun executingTheBaseCommandDoesNothing() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()
        val command = StubCommand(isNoDataReported = true)

        command.execute(listener.playbackStateListener, dependencies(presenter, listener))
        listener.settle()

        assertEquals(0, listener.results)
        assertEquals(0, listener.errors)
        assertEquals(0, command.noDataQuestions)
        assertEquals(emptyList<String>(), presenter.favoriteChecks)
    }

    /**
     * The smallest concrete command: its only decision is the no data policy, and it counts how
     * often it was asked.
     */
    private class StubCommand(private val isNoDataReported: Boolean) : MediaItemCommandImpl() {

        var noDataQuestions = 0
            private set

        override fun doLoadNoDataReceived(): Boolean {
            noDataQuestions++
            return isNoDataReported
        }
    }

    private companion object {

        /**
         * Neither the first page nor the default, so a page number that arrives intact was passed
         * through rather than defaulted.
         */
        const val PAGE = 3
    }
}
