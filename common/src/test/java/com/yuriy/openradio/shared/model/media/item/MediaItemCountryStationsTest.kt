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
import com.yuriy.openradio.shared.service.location.Country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemCountryStationsTest {

    @Test
    fun theCountryIsWarmedUpBeforeItsFirstPageIsRequested() {
        val presenter = RecordingPresenter(
            mCountries = setOf(Country("Poland", "PL")),
            mCountryStations = stations("first", "second")
        )
        val listener = RecordingCommandListener()

        MediaItemCountryStations().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_COUNTRY_STATIONS)
        )

        listener.awaitResult().assertMediaIds("first")
        assertEquals(1, presenter.countriesRequests)
        assertEquals(listOf(DEFAULT_COUNTRY_CODE to UrlLayer.FIRST_PAGE_INDEX), presenter.countryRequests)
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, listener.pageNumber)
        listener.assertNoError()
    }

    @Test
    fun scrollingTheSameCountryAsksForTheNextPage() {
        val presenter = RecordingPresenter(
            mCountries = setOf(Country("Poland", "PL")),
            mCountryStations = stations("first", "second")
        )
        val command = MediaItemCountryStations()

        val first = RecordingCommandListener()
        command.execute(
            first.playbackStateListener,
            dependencies(presenter, first, parentId = MediaId.MEDIA_ID_COUNTRY_STATIONS)
        )
        first.awaitResult()

        val second = RecordingCommandListener()
        command.execute(
            second.playbackStateListener,
            dependencies(
                presenter,
                second,
                parentId = MediaId.MEDIA_ID_COUNTRY_STATIONS,
                isSameCatalogue = true
            )
        )

        second.awaitResult().assertMediaIds("second")
        assertEquals(
            listOf(
                DEFAULT_COUNTRY_CODE to UrlLayer.FIRST_PAGE_INDEX,
                DEFAULT_COUNTRY_CODE to UrlLayer.FIRST_PAGE_INDEX + 1
            ),
            presenter.countryRequests
        )
    }

    @Test
    fun aCountryWithoutStationsIsDeliveredWithoutAnError() {
        val presenter = RecordingPresenter(mCountries = setOf(Country("Poland", "PL")))
        val listener = RecordingCommandListener()

        MediaItemCountryStations().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_COUNTRY_STATIONS)
        )

        listener.awaitResult()
        assertTrue(listener.items.isEmpty())
        listener.assertNoError()
    }

    @Test
    fun aRestoredInstanceDeliversTheCachedNodeWithoutAskingTheProvider() {
        val presenter = RecordingPresenter(mCountryStations = stations("first"))
        val listener = RecordingCommandListener()

        MediaItemCountryStations().execute(
            listener.playbackStateListener,
            dependencies(
                presenter,
                listener,
                parentId = MediaId.MEDIA_ID_COUNTRY_STATIONS,
                isSavedInstance = true
            )
        )

        listener.awaitResult()
        assertTrue(listener.items.isEmpty())
        assertTrue(presenter.countryRequests.isEmpty())
        assertEquals(0, presenter.countriesRequests)
    }
}
