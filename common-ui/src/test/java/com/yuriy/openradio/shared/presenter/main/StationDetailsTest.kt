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

import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.setVariant
import com.yuriy.openradio.shared.model.media.setVariantFixed
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins what Station details shows (REQ-012): COUNTRY, GENRE, STREAM, URL and WEB in that order,
 * each only when the station has a value for it, and the facts read the same from the item the
 * player holds as from the station restored after a cold start.
 */
class StationDetailsTest {

    @Test
    fun aStationWithEveryFieldShowsEveryRowInOrder() {
        assertEquals(
            listOf(
                StationDetail(DetailKind.COUNTRY, "Netherlands"),
                StationDetail(DetailKind.GENRE, "Classic rock"),
                StationDetail(DetailKind.STREAM, "MP3 · 192 kbps"),
                StationDetail(DetailKind.URL, "http://stream.arrow.nl/arrow.pls"),
                StationDetail(DetailKind.WEB, "arrow.nl", "https://arrow.nl"),
            ),
            StationDetails.rows(EVERY_FIELD)
        )
    }

    @Test
    fun eachMissingFieldDropsOnlyItsOwnRow() {
        val cases = mapOf(
            DetailKind.COUNTRY to EVERY_FIELD.copy(country = ""),
            DetailKind.GENRE to EVERY_FIELD.copy(genre = ""),
            DetailKind.STREAM to EVERY_FIELD.copy(codec = "", bitrate = 0),
            DetailKind.URL to EVERY_FIELD.copy(streamUrl = ""),
            DetailKind.WEB to EVERY_FIELD.copy(homePage = ""),
        )
        for ((missing, facts) in cases) {
            assertEquals(missing.name, DetailKind.entries - missing, StationDetails.rows(facts).map { it.kind })
        }
    }

    @Test
    fun blankValuesCountAsMissingAndValuesAreTrimmed() {
        val rows = StationDetails.rows(StationFacts.NONE.copy(country = "  ", genre = " Jazz "))

        assertEquals(listOf(StationDetail(DetailKind.GENRE, "Jazz")), rows)
    }

    @Test
    fun aStationWithNothingHasNoRows() {
        assertEquals(emptyList<StationDetail>(), StationDetails.rows(StationFacts.NONE))
    }

    @Test
    fun theStreamRowShowsTheCodecOrTheBitrateAloneWhenOnlyOneIsKnown() {
        assertEquals("MP3 · 192 kbps", StationDetails.stream("mp3", 192))
        assertEquals("AAC", StationDetails.stream("AAC", 0))
        assertEquals("128 kbps", StationDetails.stream("", 128))
        assertEquals("", StationDetails.stream(" ", 0))
    }

    @Test
    fun theWebRowShowsTheHostOfTheHomePage() {
        assertEquals("arrow.nl", StationDetails.host("https://arrow.nl"))
        assertEquals("www.arrow.nl", StationDetails.host("https://www.arrow.nl/listen?x=1#top"))
        assertEquals("127.0.0.1", StationDetails.host("http://127.0.0.1:9/home"))
        assertEquals("arrow.nl", StationDetails.host("https://user@arrow.nl/"))
        assertEquals("arrow.nl", StationDetails.host("arrow.nl/listen"))
    }

    @Test
    fun aHomePageWithoutASchemeOpensOverHttp() {
        assertEquals("https://arrow.nl", StationDetails.link("https://arrow.nl"))
        assertEquals("http://arrow.nl", StationDetails.link("arrow.nl"))
    }

    @Test
    fun theFactsOfAPlayableItemAreTheStationsOwn() {
        assertEquals(EVERY_FIELD, StationDetails.of(MediaItemBuilder.buildPlayable(arrow())))
    }

    @Test
    fun theFactsOfARestoredStationAreTheStationsOwn() {
        assertEquals(EVERY_FIELD, StationDetails.of(arrow()))
    }

    private companion object {

        val EVERY_FIELD = StationFacts(
            country = "Netherlands",
            genre = "Classic rock",
            codec = "MP3",
            bitrate = 192,
            streamUrl = "http://stream.arrow.nl/arrow.pls",
            homePage = "https://arrow.nl",
        )

        fun arrow(): RadioStation = RadioStation.makeDefaultInstance("ar").apply {
            name = "Arrow Classic Rock"
            country = "Netherlands"
            genre = "Classic rock"
            codec = "MP3"
            homePage = "https://arrow.nl"
            setVariant(192, "http://stream.arrow.nl/arrow.pls")
            setVariantFixed(192, "http://stream.arrow.nl/arrow.mp3")
        }
    }
}
