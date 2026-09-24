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

import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.utils.MediaItemHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The popular and new station nodes are single-page charts: unlike a category, an empty answer is
 * a failure to report rather than the end of a list.
 */
class MediaItemChartsTest {

    @Test
    fun popularStationsArriveAsPlayableItems() {
        val presenter = RecordingPresenter(mPopularStations = stations("first", "second"))
        val listener = RecordingCommandListener()

        MediaItemPopularStations().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_POPULAR_STATIONS)
        )

        listener.awaitResult().assertMediaIds("first", "second")
        assertEquals(1, presenter.popularStationsRequests)
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, listener.pageNumber)
        for (item in listener.items) {
            assertTrue("${item.mediaId} is not playable", item.mediaMetadata.isPlayable == true)
            assertFalse(MediaItemHelper.isFavoriteField(item.mediaMetadata))
        }
        listener.assertNoError()
    }

    @Test
    fun aPopularStationAlreadyInFavoritesKeepsItsStar() {
        val presenter = RecordingPresenter(
            mPopularStations = stations("plain", "starred"),
            mFavoriteIds = setOf("starred")
        )
        val listener = RecordingCommandListener()

        MediaItemPopularStations().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_POPULAR_STATIONS)
        )

        listener.awaitResult()
        assertEquals(listOf("plain", "starred"), presenter.favoriteChecks)
        assertFalse(MediaItemHelper.isFavoriteField(listener.items[0].mediaMetadata))
        assertTrue(MediaItemHelper.isFavoriteField(listener.items[1].mediaMetadata))
    }

    @Test
    fun anEmptyPopularChartIsReportedAsAnError() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemPopularStations().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_POPULAR_STATIONS)
        )

        listener.awaitResult().awaitError()
        assertTrue(listener.items.isEmpty())
        assertEquals(STRING_RESOURCE, listener.error)
        assertEquals(
            "The command did not ask the provider for the popular chart exactly once, so the " +
                "error above says nothing about an empty chart",
            1,
            presenter.popularStationsRequests
        )
    }

    @Test
    fun newStationsArriveAsPlayableItems() {
        val presenter = RecordingPresenter(mNewStations = stations("first", "second"))
        val listener = RecordingCommandListener()

        MediaItemNewStations().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_NEW_STATIONS)
        )

        listener.awaitResult().assertMediaIds("first", "second")
        assertEquals(1, presenter.newStationsRequests)
        listener.assertNoError()
    }

    @Test
    fun anEmptyNewStationsChartIsReportedAsAnError() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemNewStations().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_NEW_STATIONS)
        )

        listener.awaitResult().awaitError()
        assertTrue(listener.items.isEmpty())
        assertEquals(STRING_RESOURCE, listener.error)
        assertEquals(
            "The command did not ask the provider for the new stations chart exactly once, so " +
                "the error above says nothing about an empty chart",
            1,
            presenter.newStationsRequests
        )
    }

    /**
     * The presenter is given a station it would happily hand over, so the chart arriving empty is
     * a decision the command made rather than an absence of data.
     */
    @Test
    fun aRestoredInstanceLeavesTheChartToTheCacheWithoutAskingTheProvider() {
        val presenter = RecordingPresenter(mPopularStations = stations("first"))
        val listener = RecordingCommandListener()

        MediaItemPopularStations().execute(
            listener.playbackStateListener,
            dependencies(
                presenter,
                listener,
                parentId = MediaId.MEDIA_ID_POPULAR_STATIONS,
                isSavedInstance = true
            )
        )

        listener.assertAnsweredFromCacheBeforeReturning()
        assertEquals(0, presenter.popularStationsRequests)
        listener.assertNoError()
    }

    /**
     * The presenter is given a station it would happily hand over, so the chart arriving empty is
     * a decision the command made rather than an absence of data.
     */
    @Test
    fun aRestoredInstanceLeavesTheNewStationsToTheCacheWithoutAskingTheProvider() {
        val presenter = RecordingPresenter(mNewStations = stations("first"))
        val listener = RecordingCommandListener()

        MediaItemNewStations().execute(
            listener.playbackStateListener,
            dependencies(
                presenter,
                listener,
                parentId = MediaId.MEDIA_ID_NEW_STATIONS,
                isSavedInstance = true
            )
        )

        listener.assertAnsweredFromCacheBeforeReturning()
        assertEquals(0, presenter.newStationsRequests)
        listener.assertNoError()
    }
}
