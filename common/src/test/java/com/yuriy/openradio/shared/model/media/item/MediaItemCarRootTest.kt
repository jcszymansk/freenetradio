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
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.utils.AppUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The car root is deliberately shallow: the driver sees favorites, what is new, and one entry that
 * leads to everything else.
 */
class MediaItemCarRootTest {

    @Test
    fun carRootKeepsTheTopLevelToThreeEntries() {
        val presenter = RecordingPresenter(mFavorites = stations("favorite"))
        val listener = RecordingCommandListener()

        MediaItemRootCar(Source.RADIO_BROWSER).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_FAVORITES_LIST,
            MediaId.MEDIA_ID_NEW_STATIONS,
            MediaId.MEDIA_ID_BROWSE_CAR
        )
    }

    @Test
    fun carRootWithoutFavoritesOrRadioBrowserShowsOnlyTheBrowseEntry() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemRootCar(Source.WEB_RADIO).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds(MediaId.MEDIA_ID_BROWSE_CAR)
        assertEquals(1, presenter.favoritesRequests)
    }

    @Test
    fun carBrowseCarriesEverythingTheCarRootLeftOut() {
        val presenter = RecordingPresenter(mDeviceLocals = stations("local"))
        val listener = RecordingCommandListener()

        MediaItemBrowseCar(Source.RADIO_BROWSER).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_POPULAR_STATIONS,
            MediaId.MEDIA_ID_ALL_CATEGORIES,
            MediaId.MEDIA_ID_COUNTRIES_LIST,
            MediaId.MEDIA_ID_COUNTRY_STATIONS,
            MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
        )
    }

    @Test
    fun carBrowseDropsPopularAndTheCountryWhenNeitherApplies() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemBrowseCar(Source.WEB_RADIO).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, countryCode = AppUtils.EMPTY_STRING)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_ALL_CATEGORIES,
            MediaId.MEDIA_ID_COUNTRIES_LIST
        )
    }

    @Test
    fun theUseLocationSentinelIsNotAKnownCountryInTheCar() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemBrowseCar(Source.WEB_RADIO).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, countryCode = STRING_RESOURCE)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_ALL_CATEGORIES,
            MediaId.MEDIA_ID_COUNTRIES_LIST
        )
    }

    @Test
    fun carEntriesAreBrowsableFolders() {
        val presenter = RecordingPresenter(mFavorites = stations("favorite"))
        val listener = RecordingCommandListener()

        MediaItemRootCar(Source.RADIO_BROWSER).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult()
        for (item in listener.items) {
            assertTrue("${item.mediaId} is not browsable", item.mediaMetadata.isBrowsable == true)
            assertFalse("${item.mediaId} is playable", item.mediaMetadata.isPlayable == true)
            assertEquals(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS, item.mediaMetadata.mediaType)
        }
    }
}
