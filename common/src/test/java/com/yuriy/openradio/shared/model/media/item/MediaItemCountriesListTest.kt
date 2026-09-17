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
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.MediaItemHelper
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemCountriesListTest {

    @Test
    fun everyKnownCountryBecomesABrowsableEntryCarryingItsFlag() {
        val countries = TreeSet<Country>()
        countries.add(Country("Germany", "DE"))
        countries.add(Country("Poland", "PL"))
        val presenter = RecordingPresenter(mCountries = countries)
        val listener = RecordingCommandListener()

        MediaItemCountriesList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_COUNTRIES_LIST)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_COUNTRIES_LIST + "DE",
            MediaId.MEDIA_ID_COUNTRIES_LIST + "PL"
        )
        assertEquals(1, presenter.countriesRequests)
        assertEquals("Germany", listener.items[0].mediaMetadata.title)
        assertEquals("DE", listener.items[0].mediaMetadata.subtitle)
        for (item in listener.items) {
            assertTrue("${item.mediaId} is not browsable", item.mediaMetadata.isBrowsable == true)
            assertFalse("${item.mediaId} is playable", item.mediaMetadata.isPlayable == true)
            assertEquals(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS, item.mediaMetadata.mediaType)
            assertEquals(FLAG_DRAWABLE_ID, MediaItemHelper.getDrawableId(item.mediaMetadata.extras))
        }
        listener.assertNoError()
    }

    @Test
    fun aCountryTheAppHasNoNameForIsSkipped() {
        val countries = TreeSet<Country>()
        countries.add(Country("Poland", "PL"))
        countries.add(Country("Atlantis", "XX"))
        val presenter = RecordingPresenter(mCountries = countries)
        val listener = RecordingCommandListener()

        MediaItemCountriesList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_COUNTRIES_LIST)
        )

        listener.awaitResult().assertMediaIds(MediaId.MEDIA_ID_COUNTRIES_LIST + "PL")
    }

    @Test
    fun anEmptyCountryListIsReportedAsAnErrorAndNothingIsDelivered() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemCountriesList().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_COUNTRIES_LIST)
        )

        listener.awaitError()
        assertEquals(1, listener.errors)
        assertEquals(STRING_RESOURCE, listener.error)
        listener.assertNoResult()
    }
}
