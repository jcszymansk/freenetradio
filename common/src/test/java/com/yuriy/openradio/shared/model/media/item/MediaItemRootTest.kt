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
import com.yuriy.openradio.shared.utils.MediaItemHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemRootTest {

    @Test
    fun radioBrowserRootOffersChartsCategoriesAndTheKnownCountry() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemRoot(Source.RADIO_BROWSER).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_NEW_STATIONS,
            MediaId.MEDIA_ID_POPULAR_STATIONS,
            MediaId.MEDIA_ID_ALL_CATEGORIES,
            MediaId.MEDIA_ID_COUNTRIES_LIST,
            MediaId.MEDIA_ID_COUNTRY_STATIONS
        )
        assertTrue(listener.radioStations.isEmpty())
        listener.assertNoError()
    }

    @Test
    fun webRadioRootDropsTheChartsItsProviderCannotServe() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemRoot(Source.WEB_RADIO).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_ALL_CATEGORIES,
            MediaId.MEDIA_ID_COUNTRIES_LIST,
            MediaId.MEDIA_ID_COUNTRY_STATIONS
        )
    }

    @Test
    fun favoritesAndLocalsBracketTheRootOnlyWhenPopulated() {
        val presenter = RecordingPresenter(
            mFavorites = stations("favorite"),
            mDeviceLocals = stations("local")
        )
        val listener = RecordingCommandListener()

        MediaItemRoot(Source.RADIO_BROWSER).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_FAVORITES_LIST,
            MediaId.MEDIA_ID_NEW_STATIONS,
            MediaId.MEDIA_ID_POPULAR_STATIONS,
            MediaId.MEDIA_ID_ALL_CATEGORIES,
            MediaId.MEDIA_ID_COUNTRIES_LIST,
            MediaId.MEDIA_ID_COUNTRY_STATIONS,
            MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
        )
        assertEquals(1, presenter.favoritesRequests)
        assertEquals(1, presenter.deviceLocalsRequests)
    }

    @Test
    fun onlyFavoritesAppearWhenNoStationIsStoredOnTheDevice() {
        val presenter = RecordingPresenter(mFavorites = stations("favorite"))
        val listener = RecordingCommandListener()

        MediaItemRoot(Source.WEB_RADIO).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_FAVORITES_LIST,
            MediaId.MEDIA_ID_ALL_CATEGORIES,
            MediaId.MEDIA_ID_COUNTRIES_LIST,
            MediaId.MEDIA_ID_COUNTRY_STATIONS
        )
    }

    @Test
    fun anUnknownCountryLeavesTheCountryEntryOut() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemRoot(Source.WEB_RADIO).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, countryCode = AppUtils.EMPTY_STRING)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_ALL_CATEGORIES,
            MediaId.MEDIA_ID_COUNTRIES_LIST
        )
    }

    @Test
    fun everyRootEntryIsBrowsableAndCarriesAnIcon() {
        val presenter = RecordingPresenter(
            mFavorites = stations("favorite"),
            mDeviceLocals = stations("local")
        )
        val listener = RecordingCommandListener()

        MediaItemRoot(Source.RADIO_BROWSER).execute(
            listener.playbackStateListener,
            dependencies(presenter, listener)
        )

        listener.awaitResult()
        for (item in listener.items) {
            val metadata = item.mediaMetadata
            assertTrue("${item.mediaId} is not browsable", metadata.isBrowsable == true)
            assertFalse("${item.mediaId} is playable", metadata.isPlayable == true)
            assertEquals(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS, metadata.mediaType)
            assertTrue(
                "${item.mediaId} has no icon",
                MediaItemHelper.isDrawableIdValid(MediaItemHelper.getDrawableId(metadata.extras))
            )
            assertEquals(null, item.localConfiguration)
        }
        assertEquals(
            FLAG_DRAWABLE_ID,
            MediaItemHelper.getDrawableId(
                listener.items.single { it.mediaId == MediaId.MEDIA_ID_COUNTRY_STATIONS }.mediaMetadata.extras
            )
        )
    }
}
