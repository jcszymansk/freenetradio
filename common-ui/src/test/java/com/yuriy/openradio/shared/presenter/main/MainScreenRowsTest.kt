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

package com.yuriy.openradio.shared.presenter.main

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.storage.images.ImagesStore
import com.yuriy.openradio.shared.utils.MediaItemHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenRowsTest {

    @Test
    fun aStationRowCarriesNameMetaBitrateAndFlags() {
        val row = MainScreenRows.station(
            station("s1", "Hard Rock Radio FM", "us", "mp3", 128),
            isFavorite = true,
            isCurrent = false,
            canLongPress = true,
            artworkRevision = 0
        )

        assertEquals("s1", row.id)
        assertEquals("Hard Rock Radio FM", row.name)
        assertEquals("HR", row.initials)
        assertEquals("US · MP3", row.meta)
        assertEquals("128k", row.bitrate)
        assertTrue(row.isFavorite)
        assertFalse(row.isCurrent)
        assertTrue(row.canLongPress)
        assertNull("A station without artwork shows its initials", row.artworkUri)
    }

    @Test
    fun theMetaLineLeavesOutWhatTheStationDoesNotHave() {
        assertEquals("NL", MainScreenRows.meta("nl", ""))
        assertEquals("AAC", MainScreenRows.meta("", "aac"))
        assertEquals("", MainScreenRows.meta("", ""))
    }

    @Test
    fun anUnknownBitrateHasNoLabel() {
        assertNull(MainScreenRows.bitrate(0))
        assertEquals("320k", MainScreenRows.bitrate(320))
    }

    @Test
    fun initialsTakeTheFirstTwoWordsOrTheFirstTwoCharacters() {
        assertEquals("RP", MainScreenRows.initials("Radio Paradise"))
        assertEquals("CA", MainScreenRows.initials("Capital"))
        assertEquals("DC", MainScreenRows.initials("DJ & Club Charts"))
        assertEquals("5", MainScreenRows.initials("5"))
        assertEquals("", MainScreenRows.initials("  "))
    }

    @Test
    fun aCountryRowKeepsItsFlag() {
        val row = MainScreenRows.category(category(MediaId.MEDIA_ID_COUNTRIES_LIST + "PL", "Poland", FLAG))

        assertEquals("Poland", row.title)
        assertEquals(FLAG, row.iconResId)
    }

    @Test
    fun theUsersCountryRowKeepsItsFlag() {
        val row = MainScreenRows.category(category(MediaId.MEDIA_ID_COUNTRY_STATIONS, "Poland", FLAG))

        assertEquals(FLAG, row.iconResId)
    }

    @Test
    fun aGenreRowHasNoIcon() {
        val row = MainScreenRows.category(category(MediaId.MEDIA_ID_CHILD_CATEGORIES + "jazz", "Jazz", FLAG))

        assertNull(row.iconResId)
    }

    @Test
    fun aCountryWithoutAFlagDrawableHasNoIcon() {
        val row = MainScreenRows.category(category(MediaId.MEDIA_ID_COUNTRIES_LIST + "XX", "Nowhere", 0))

        assertNull("getIdentifier answers 0 for a missing flag", row.iconResId)
    }

    @Test
    fun aStationWithStoredArtworkCarriesItsImageUri() {
        val item = station("s1", "Capital", "gb", "aac", 48).buildUpon()
            .setMediaMetadata(
                station("s1", "Capital", "gb", "aac", 48).mediaMetadata.buildUpon()
                    .setArtworkUri(ImagesStore.buildImageUri("s1", "https://img.invalid/capital.png"))
                    .build()
            )
            .build()

        val row = MainScreenRows.station(item, false, false, false, 0)

        assertEquals(ImagesStore.buildImageUri("s1", "https://img.invalid/capital.png").toString(), row.artworkUri)
        assertEquals("s1", MainScreenRows.artworkId(item))
    }

    private companion object {

        const val FLAG = 42

        fun station(id: String, name: String, countryCode: String, codec: String, bitrate: Int): MediaItem {
            val extras = Bundle()
            MediaItemHelper.updateCountryCodeField(extras, countryCode)
            MediaItemHelper.updateCodecField(extras, codec)
            MediaItemHelper.updateBitrateField(extras, bitrate)
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        fun category(id: String, title: String, drawableId: Int): MediaItem {
            val extras = Bundle()
            MediaItemHelper.setDrawableId(extras, drawableId)
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }
    }
}
