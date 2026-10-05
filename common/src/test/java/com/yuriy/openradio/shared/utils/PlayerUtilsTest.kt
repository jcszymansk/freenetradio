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

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.yuriy.openradio.shared.model.media.MediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [PlayerUtils.isEndOfList], the check the browse clients use to tell that a page brought
 * nothing more, and [PlayerUtils.playerStateToString].
 *
 * The end of list marker is a real Media3 [MediaItem] built in the object's initializer. Media3's
 * `MediaItem` is plain Java, so the marker's id is the one [MediaItemBuilder.buildMediaItemListEnded]
 * set and not a framework default; the ordinary item case below would fail if the two were equal.
 */
class PlayerUtilsTest {

    @Test
    fun aNullListIsTheEndOfTheList() {
        assertTrue(PlayerUtils.isEndOfList(null))
    }

    @Test
    fun anEmptyListIsNotTheEndOfTheList() {
        assertFalse(PlayerUtils.isEndOfList(emptyList()))
    }

    @Test
    fun aListHoldingOnlyANullEntryIsTheEndOfTheList() {
        assertTrue(PlayerUtils.isEndOfList(listOf(null)))
    }

    @Test
    fun aListHoldingOnlyTheEndOfListMarkerIsTheEndOfTheList() {
        assertTrue(PlayerUtils.isEndOfList(listOf(MediaItemBuilder.buildMediaItemListEnded())))
    }

    @Test
    fun aListHoldingOnlyAnOrdinaryItemIsNotTheEndOfTheList() {
        assertFalse(PlayerUtils.isEndOfList(listOf(item("station"))))
    }

    @Test
    fun aMarkerFollowedByAnotherItemIsNotTheEndOfTheList() {
        assertFalse(PlayerUtils.isEndOfList(listOf(MediaItemBuilder.buildMediaItemListEnded(), item("station"))))
    }

    @Test
    fun anItemBeforeTheMarkerIsNotTheEndOfTheList() {
        assertFalse(PlayerUtils.isEndOfList(listOf(item("station"), MediaItemBuilder.buildMediaItemListEnded())))
    }

    /**
     * Current behaviour, and it looks unintended: the marker is recognised by reference identity
     * of its id string, so a marker whose id equals [MediaId.MEDIA_ID_LIST_ENDED] but is another
     * String instance, as it would be after crossing a Binder in a Bundle, is not the end of the
     * list.
     */
    @Test
    fun aMarkerWhoseIdIsAnEqualButDistinctStringIsNotRecognised() {
        val copiedId = String(MediaId.MEDIA_ID_LIST_ENDED.toCharArray())
        assertEquals(MediaId.MEDIA_ID_LIST_ENDED, copiedId)
        assertNotSame(MediaId.MEDIA_ID_LIST_ENDED, copiedId)

        assertFalse(PlayerUtils.isEndOfList(listOf(item(copiedId))))
    }

    @Test
    fun eachMedia3PlayerStateHasItsName() {
        assertEquals("STATE_IDLE", PlayerUtils.playerStateToString(Player.STATE_IDLE))
        assertEquals("STATE_BUFFERING", PlayerUtils.playerStateToString(Player.STATE_BUFFERING))
        assertEquals("STATE_READY", PlayerUtils.playerStateToString(Player.STATE_READY))
        assertEquals("STATE_ENDED", PlayerUtils.playerStateToString(Player.STATE_ENDED))
    }

    @Test
    fun anUnknownStateIsNamedUndefinedWithItsValue() {
        assertEquals("UNDEFINED{99}", PlayerUtils.playerStateToString(99))
        assertEquals("UNDEFINED{-1}", PlayerUtils.playerStateToString(-1))
    }

    private fun item(mediaId: String): MediaItem {
        return MediaItem.Builder().setMediaId(mediaId).build()
    }
}
