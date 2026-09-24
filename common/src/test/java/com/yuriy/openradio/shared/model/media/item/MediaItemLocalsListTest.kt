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

class MediaItemLocalsListTest {

    @Test
    fun stationsAddedOnTheDeviceBecomePlayableItems() {
        val locals = stations("first", "second")
        val presenter = RecordingPresenter(mDeviceLocals = locals)
        val listener = RecordingCommandListener()

        MediaItemLocalsList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds("first", "second")
        assertEquals(locals, listener.radioStations)
        for (item in listener.items) {
            assertTrue("${item.mediaId} is not playable", item.mediaMetadata.isPlayable == true)
            assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, item.mediaMetadata.mediaType)
        }
        listener.assertNoError()
    }

    @Test
    fun theFavoriteFlagOfALocalStationComesFromTheFavoritesStore() {
        val presenter = RecordingPresenter(
            mDeviceLocals = stations("plain", "starred"),
            mFavoriteIds = setOf("starred")
        )
        val listener = RecordingCommandListener()

        MediaItemLocalsList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult()
        assertEquals(listOf("plain", "starred"), presenter.favoriteChecks)
        assertFalse(MediaItemHelper.isFavoriteField(listener.items[0].mediaMetadata))
        assertTrue(MediaItemHelper.isFavoriteField(listener.items[1].mediaMetadata))
    }

    @Test
    fun invalidLocalStationsAreOmittedFromTheBrowseList() {
        val locals = TreeSet<RadioStation>()
        locals.add(station("valid", sortId = 1))
        locals.add(RadioStation.makeDefaultInstance(AppUtils.EMPTY_STRING).apply { sortId = 2 })
        val presenter = RecordingPresenter(mDeviceLocals = locals)
        val listener = RecordingCommandListener()

        MediaItemLocalsList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds("valid")
        assertEquals(2, listener.radioStations.size)
        assertEquals(listOf("valid"), presenter.favoriteChecks)
    }

    @Test
    fun anEmptyLocalsListIsDeliveredWithoutAnError() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemLocalsList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult()
        assertTrue(listener.items.isEmpty())
        listener.assertNoError()
        assertEquals(
            "The command did not read the device locals store exactly once, so the empty list " +
                "above says nothing about an empty store",
            1,
            presenter.deviceLocalsRequests
        )
    }

    /**
     * Unlike the provider nodes, the locals list is never left to the browse tree cache: a station
     * added or edited on the device has to show up the next time the list is opened, restored or
     * not, the same way the favorites list does.
     */
    @Test
    fun aSavedInstanceStillReloadsTheLocals() {
        val presenter = RecordingPresenter(mDeviceLocals = stations("first"))
        val listener = RecordingCommandListener()

        MediaItemLocalsList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, isSavedInstance = true)
        )

        listener.awaitResult().assertMediaIds("first")
        assertEquals(1, presenter.deviceLocalsRequests)
        listener.assertNoError()
    }
}
