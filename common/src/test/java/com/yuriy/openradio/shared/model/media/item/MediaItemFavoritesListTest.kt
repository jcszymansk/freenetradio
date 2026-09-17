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

import androidx.media3.common.MediaMetadata
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.MediaItemHelper
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemFavoritesListTest {

    @Test
    fun everyStoredFavoriteBecomesAPlayableItemMarkedAsFavorite() {
        val favorites = stations("first", "second")
        val presenter = RecordingPresenter(mFavorites = favorites)
        val listener = RecordingCommandListener()

        MediaItemFavoritesList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds("first", "second")
        assertEquals(favorites, listener.radioStations)
        assertEquals(0, listener.pageNumber)
        for (item in listener.items) {
            val metadata = item.mediaMetadata
            assertTrue("${item.mediaId} is not playable", metadata.isPlayable == true)
            assertFalse("${item.mediaId} is browsable", metadata.isBrowsable == true)
            assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, metadata.mediaType)
            assertTrue(MediaItemHelper.isFavoriteField(metadata))
        }
        listener.assertNoError()
    }

    @Test
    fun playableMetadataDescribesTheStationAndItsStream() {
        val station = station("station", name = "Radio One", country = "Poland", genre = "Jazz", bitrate = 192)
        station.sortId = 4
        val presenter = RecordingPresenter(mFavorites = setOf(station))
        val listener = RecordingCommandListener()

        MediaItemFavoritesList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        val item = listener.awaitResult().items.single()
        assertEquals("station", item.mediaId)
        assertEquals("Radio One", item.mediaMetadata.title)
        assertEquals("Poland", item.mediaMetadata.subtitle)
        assertEquals("Jazz", item.mediaMetadata.description)
        assertEquals(
            "https://radio.example/station.mp3",
            item.localConfiguration?.uri.toString()
        )
        assertEquals("audio/mpeg", item.localConfiguration?.mimeType)
        assertEquals(192, MediaItemHelper.getBitrateField(item))
        assertEquals(4, MediaItemHelper.getSortIdField(item))
        assertEquals(
            "content://com.github.jcszymansk.freenetradio.images" +
                "?id=station&url=https%253A%252F%252Fradio.example%252Fstation.png",
            item.mediaMetadata.artworkUri.toString()
        )
    }

    @Test
    fun invalidFavoritesAreOmittedFromTheBrowseListButStayInTheStationSet() {
        val favorites = TreeSet<RadioStation>()
        favorites.add(station("valid", sortId = 1))
        favorites.add(RadioStation.makeDefaultInstance(AppUtils.EMPTY_STRING).apply { sortId = 2 })
        val presenter = RecordingPresenter(mFavorites = favorites)
        val listener = RecordingCommandListener()

        MediaItemFavoritesList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds("valid")
        assertEquals(2, listener.radioStations.size)
    }

    @Test
    fun anEmptyFavoritesListIsDeliveredWithoutAnError() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemFavoritesList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult()
        assertTrue(listener.items.isEmpty())
        assertTrue(listener.radioStations.isEmpty())
        listener.assertNoError()
    }

    @Test
    fun aSavedInstanceStillReloadsTheFavorites() {
        val presenter = RecordingPresenter(mFavorites = stations("first"))
        val listener = RecordingCommandListener()

        MediaItemFavoritesList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, isSavedInstance = true)
        )

        listener.awaitResult().assertMediaIds("first")
    }
}
