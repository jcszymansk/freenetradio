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

package com.yuriy.openradio.shared.service

import androidx.media3.common.MediaItem
import com.yuriy.openradio.shared.model.media.item.RecordingPresenter
import com.yuriy.openradio.shared.model.media.item.station
import com.yuriy.openradio.shared.model.media.item.stations
import com.yuriy.openradio.shared.utils.MediaItemHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue the service restores at start-up: the favourites, or the provider's new stations when
 * there are none, with the last station at the head when the list does not hold it. Every item
 * carries the favourite flag the presenter answers for its station, whichever list it came from.
 */
class InitialPlaylistTest {

    @Test
    fun theFavouritesCarryTheirHeart() {
        val favorites = stations("a", "b", "c")
        val presenter = RecordingPresenter(
            mFavorites = favorites,
            mFavoriteIds = setOf("a", "b", "c")
        )

        val playlist = InitialPlaylist.build(station("b"), presenter)

        assertEquals(listOf("a", "b", "c"), mediaIds(playlist.items))
        assertTrue(playlist.items.all { MediaItemHelper.isFavoriteField(it.mediaMetadata) })
        assertEquals(1, playlist.startIndex)
        assertEquals(favorites, playlist.stations)
    }

    @Test
    fun aFavouriteLastStationTheListLacksIsPutAtTheHeadWithItsHeart() {
        val presenter = RecordingPresenter(
            mFavorites = stations("a"),
            mFavoriteIds = setOf("a", "x")
        )

        val playlist = InitialPlaylist.build(station("x"), presenter)

        assertEquals(listOf("x", "a"), mediaIds(playlist.items))
        assertTrue(MediaItemHelper.isFavoriteField(playlist.items[0].mediaMetadata))
        assertEquals(0, playlist.startIndex)
        assertEquals(setOf("a", "x"), playlist.stations.map { it.id }.toSet())
    }

    @Test
    fun aLastStationThatIsNoFavouriteIsPutAtTheHeadOutlined() {
        val presenter = RecordingPresenter(
            mFavorites = stations("a"),
            mFavoriteIds = setOf("a")
        )

        val playlist = InitialPlaylist.build(station("y"), presenter)

        assertEquals(listOf("y", "a"), mediaIds(playlist.items))
        assertFalse(MediaItemHelper.isFavoriteField(playlist.items[0].mediaMetadata))
        assertTrue(MediaItemHelper.isFavoriteField(playlist.items[1].mediaMetadata))
        assertEquals(0, playlist.startIndex)
    }

    @Test
    fun withoutFavouritesEachNewStationIsAskedForItsHeart() {
        val presenter = RecordingPresenter(
            mNewStations = stations("n1", "n2"),
            mFavoriteIds = setOf("n2")
        )

        val playlist = InitialPlaylist.build(station("n1"), presenter)

        assertEquals(listOf("n1", "n2"), mediaIds(playlist.items))
        assertFalse(MediaItemHelper.isFavoriteField(playlist.items[0].mediaMetadata))
        assertTrue(MediaItemHelper.isFavoriteField(playlist.items[1].mediaMetadata))
        assertEquals(0, playlist.startIndex)
    }

    private fun mediaIds(items: List<MediaItem>): List<String> {
        return items.map { it.mediaId }
    }
}
