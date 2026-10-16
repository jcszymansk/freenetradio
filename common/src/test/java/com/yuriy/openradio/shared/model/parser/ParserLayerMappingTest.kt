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

package com.yuriy.openradio.shared.model.parser

import android.net.Uri
import com.yuriy.openradio.shared.model.filter.FilterImpl
import com.yuriy.openradio.shared.model.media.MediaStream
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamBitrate
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.net.UrlLayerWebRadioImpl
import com.yuriy.openradio.shared.model.translation.MediaIdBuilderDefault
import com.yuriy.openradio.shared.service.location.Country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserLayerMappingTest {

    @Test
    fun radioBrowserMapsValidStation() {
        val data = """
            [{
              "stationuuid": "radio-browser-id",
              "name": "Radio Browser",
              "homepage": "https://radio.example/home",
              "country": "Poland",
              "countrycode": "PL",
              "favicon": "https://radio.example/icon.png",
              "lastcheckoktime": "2024-01-02T03:04:05Z",
              "url_resolved": "https://radio.example/resolved",
              "codec": "MP3",
              "lastcheckok": 1,
              "bitrate": 128,
              "url": "https://radio.example/stream"
            }]
        """.trimIndent()

        val station = ParserLayerRadioBrowserImpl(FilterImpl())
            .getRadioStation(data, MediaIdBuilderDefault(), Uri.parse(""))

        assertEquals("radio-browser-id", station.id)
        assertEquals("Radio Browser", station.name)
        assertEquals("https://radio.example/home", station.homePage)
        assertEquals("Poland", station.country)
        assertEquals("PL", station.countryCode)
        assertEquals("https://radio.example/icon.png", station.imageUrl)
        assertEquals("2024-01-02T03:04:05Z", station.lastCheckOkTime)
        assertEquals("https://radio.example/resolved", station.urlResolved)
        assertEquals("MP3", station.codec)
        assertEquals(1, station.lastCheckOk)
        assertEquals(128, station.getStreamBitrate())
        assertEquals("https://radio.example/stream", station.getStreamUrlFixed())
    }

    @Test
    fun webRadioMapsValidStation() {
        val data = """
            {
              "web-radio-id": {
                "Genre": ["Rock"],
                "Name": "Web Radio",
                "StreamUri": "https://webradio.example/stream",
                "Codec": "AAC",
                "Bitrate": 64,
                "Homepage": "https://webradio.example/home",
                "Country": "Poland",
                "Description": "A web radio station",
                "Image": "web-radio.png"
              }
            }
        """.trimIndent()

        val stations = ParserLayerWebRadioImpl(emptySet()).getRadioStations(
            data,
            MediaIdBuilderDefault(),
            Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        )
        val station = stations.single()

        assertFalse(station.isMediaStreamEmpty())
        assertEquals("web-radio-id", station.id)
        assertEquals("Web Radio", station.name)
        assertEquals("https://webradio.example/home", station.homePage)
        assertEquals("Poland", station.country)
        assertEquals("https://jcorporation.github.io/webradiodb/db/pics/web-radio.png", station.imageUrl)
        assertEquals("AAC", station.codec)
        assertEquals("A web radio station", station.description)
        assertEquals(64, station.getStreamBitrate())
        assertEquals("https://webradio.example/stream", station.getStreamUrlFixed())
    }

    @Test
    fun missingStationIdIsRejectedByBothParsers() {
        val radioBrowserStations = ParserLayerRadioBrowserImpl(FilterImpl()).getRadioStations(
            """[{"name":"No ID","url":"https://radio.example/stream"}]""",
            MediaIdBuilderDefault(),
            Uri.parse("")
        )
        assertTrue(radioBrowserStations.isEmpty())

        val webRadioStations = ParserLayerWebRadioImpl(emptySet()).getRadioStations(
            """{"":{"Genre":["Rock"],"Name":"No ID","StreamUri":"https://webradio.example/stream"}}""",
            MediaIdBuilderDefault(),
            Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        )
        assertTrue(webRadioStations.isEmpty())
    }

    @Test
    fun missingStreamUrlIsRejectedByBothParsers() {
        val radioBrowserStations = ParserLayerRadioBrowserImpl(FilterImpl()).getRadioStations(
            """[{"stationuuid":"radio-browser-id","name":"No stream"},{"stationuuid":"empty-stream-id","name":"Empty stream","url":""}]""",
            MediaIdBuilderDefault(),
            Uri.parse("")
        )
        assertTrue(radioBrowserStations.isEmpty())

        val webRadioStations = ParserLayerWebRadioImpl(emptySet()).getRadioStations(
            """{"web-radio-id":{"Genre":["Rock"],"Name":"No stream"},"empty-stream-id":{"Genre":["Rock"],"Name":"Empty stream","StreamUri":""}}""",
            MediaIdBuilderDefault(),
            Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        )
        assertTrue(webRadioStations.isEmpty())
    }

    @Test
    fun malformedAndEmptyJsonReturnNoStations() {
        val radioBrowser = ParserLayerRadioBrowserImpl(FilterImpl())
        assertTrue(radioBrowser.getRadioStations("", MediaIdBuilderDefault(), Uri.parse("")).isEmpty())
        assertTrue(radioBrowser.getRadioStations("{", MediaIdBuilderDefault(), Uri.parse("")).isEmpty())

        val webRadio = ParserLayerWebRadioImpl(emptySet())
        val categoryUri = Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        assertTrue(webRadio.getRadioStations("", MediaIdBuilderDefault(), categoryUri).isEmpty())
        assertTrue(webRadio.getRadioStations("[", MediaIdBuilderDefault(), categoryUri).isEmpty())
    }

    @Test
    fun unknownFieldsDoNotAffectMapping() {
        val radioBrowserStation = ParserLayerRadioBrowserImpl(FilterImpl()).getRadioStations(
            """[{"stationuuid":"radio-browser-id","name":"Known Radio","url":"https://radio.example/stream","unknown":"ignored","nested":{"ignored":true}}]""",
            MediaIdBuilderDefault(),
            Uri.parse("")
        ).single()
        assertEquals("Known Radio", radioBrowserStation.name)
        assertEquals("https://radio.example/stream", radioBrowserStation.getStreamUrlFixed())

        val webRadioStation = ParserLayerWebRadioImpl(emptySet()).getRadioStations(
            """{"web-radio-id":{"Genre":["Rock"],"Name":"Known Web Radio","StreamUri":"https://webradio.example/stream","Unknown":"ignored","Nested":{"ignored":true}}}""",
            MediaIdBuilderDefault(),
            Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        ).single()
        assertEquals("Known Web Radio", webRadioStation.name)
        assertEquals("https://webradio.example/stream", webRadioStation.getStreamUrlFixed())
    }

    @Test
    fun filteredStationsAreExcluded() {
        val stations = ParserLayerRadioBrowserImpl(FilterImpl()).getRadioStations(
            """
                [
                  {
                    "stationuuid": "blocked-stream",
                    "name": "Blocked Stream",
                    "url": "https://stream.radiojar.com/z4qyckhr9druv"
                  },
                  {
                    "stationuuid": "blocked-homepage",
                    "name": "Blocked Homepage",
                    "homepage": "https://radiosindia.com",
                    "url": "https://radio.example/blocked"
                  },
                  {
                    "stationuuid": "allowed",
                    "name": "Allowed",
                    "url": "https://radio.example/allowed"
                  }
                ]
            """.trimIndent(),
            MediaIdBuilderDefault(),
            Uri.parse("")
        )

        assertEquals("allowed", stations.single().id)
    }

    @Test
    fun categoriesIncludeCountsAndNormalizedTitles() {
        val radioBrowserCategories = ParserLayerRadioBrowserImpl(FilterImpl()).getAllCategories(
            """[{"name":"rock","stationcount":4},{"name":"jazz","stationcount":2}]"""
        ).toList()
        assertEquals(2, radioBrowserCategories.size)
        assertEquals("rock", radioBrowserCategories[0].id)
        assertEquals("Rock", radioBrowserCategories[0].title)
        assertEquals(4, radioBrowserCategories[0].stationsCount)
        assertEquals("jazz", radioBrowserCategories[1].id)
        assertEquals("Jazz", radioBrowserCategories[1].title)
        assertEquals(2, radioBrowserCategories[1].stationsCount)

        val webRadioCategories = ParserLayerWebRadioImpl(emptySet()).getAllCategories(
            """{"radio-one":{"Genre":["Rock","Jazz"]},"radio-two":{"Genre":["Rock"]}}"""
        ).toList()
        assertEquals(2, webRadioCategories.size)
        assertEquals("Rock", webRadioCategories[0].id)
        assertEquals("Rock", webRadioCategories[0].title)
        assertEquals(2, webRadioCategories[0].stationsCount)
        assertEquals("Jazz", webRadioCategories[1].id)
        assertEquals("Jazz", webRadioCategories[1].title)
        assertEquals(1, webRadioCategories[1].stationsCount)
    }

    @Test
    fun categoriesWithTheSameStationCountAreAllKeptByBothParsers() {
        val radioBrowserCategories = ParserLayerRadioBrowserImpl(FilterImpl()).getAllCategories(
            """
            [{"name":"rock","stationcount":2},{"name":"jazz","stationcount":2},
             {"name":"pop","stationcount":5},{"name":"blues","stationcount":2},
             {"name":"Rock","stationcount":2}]
            """.trimIndent()
        )
        assertEquals(
            listOf("pop", "blues", "jazz", "Rock", "rock"),
            radioBrowserCategories.map { it.id }
        )

        val webRadioCategories = ParserLayerWebRadioImpl(emptySet()).getAllCategories(
            """
            {"radio-one":{"Genre":["Rock","Jazz","Pop"]},"radio-two":{"Genre":["Pop","Blues"]},
             "radio-three":{"Genre":["Ambient"]}}
            """.trimIndent()
        )
        assertEquals(
            listOf("Pop", "Ambient", "Blues", "Jazz", "Rock"),
            webRadioCategories.map { it.id }
        )
    }

    /**
     * The id becomes the child's media id, so a repeated name would put two browse children under
     * one id. The first entry wins, whatever the counts say.
     */
    @Test
    fun aRepeatedRadioBrowserCategoryIsListedOnceWithItsFirstCount() {
        val categories = ParserLayerRadioBrowserImpl(FilterImpl()).getAllCategories(
            """
            [{"name":"rock","stationcount":2},{"name":"jazz","stationcount":3},
             {"name":"rock","stationcount":9},{"name":"rock","stationcount":2}]
            """.trimIndent()
        )

        assertEquals(listOf("jazz", "rock"), categories.map { it.id })
        assertEquals(listOf(3, 2), categories.map { it.stationsCount })
    }

    /**
     * The count is the browse child's subtitle and its sort key, so reading an unreadable count as
     * zero would label a real category "0 stations" and sink it. The row is skipped instead, like
     * any other row that cannot be read, and a skipped row does not claim its name.
     */
    @Test
    fun aRadioBrowserCategoryWithAMalformedCountIsSkipped() {
        val categories = ParserLayerRadioBrowserImpl(FilterImpl()).getAllCategories(
            """
            [{"name":"rock","stationcount":"many"},{"name":"jazz","stationcount":3},
             {"name":"pop","stationcount":null},{"name":"blues","stationcount":{}},
             {"name":"rock","stationcount":2},{"name":"folk"}]
            """.trimIndent()
        )

        assertEquals(listOf("jazz", "rock", "folk"), categories.map { it.id })
        assertEquals(listOf(3, 2, 0), categories.map { it.stationsCount })
    }

    /**
     * The bitrate is only shown next to the station, and the stream url alone makes it playable, so
     * an unreadable bitrate is read as the one a station without the field gets: dropping the
     * station would lose more than a wrong bitrate does.
     */
    @Test
    fun aRadioBrowserStationWithAMalformedBitrateKeepsTheDefault() {
        val stations = ParserLayerRadioBrowserImpl(FilterImpl()).getRadioStations(
            """
            [{"stationuuid":"word","name":"word","url":"https://radio.example/word","bitrate":"fast"},
             {"stationuuid":"null","name":"null","url":"https://radio.example/null","bitrate":null},
             {"stationuuid":"object","name":"object","url":"https://radio.example/object","bitrate":{}},
             {"stationuuid":"text-number","name":"text-number","url":"https://radio.example/text-number","bitrate":"96"},
             {"stationuuid":"number","name":"number","url":"https://radio.example/number","bitrate":128}]
            """.trimIndent(),
            MediaIdBuilderDefault(),
            Uri.parse("")
        )

        assertEquals(
            mapOf("word" to 0, "null" to 0, "object" to 0, "text-number" to 96, "number" to 128),
            stations.associate { it.id to it.getStreamBitrate() }
        )
    }

    @Test
    fun aWebRadioStationWithAMalformedBitrateKeepsTheDefault() {
        val stations = ParserLayerWebRadioImpl(emptySet()).getRadioStations(
            """
            {"word":{"Genre":["Rock"],"Name":"word","StreamUri":"https://radio.example/word","Bitrate":"fast"},
             "null":{"Genre":["Rock"],"Name":"null","StreamUri":"https://radio.example/null","Bitrate":null},
             "object":{"Genre":["Rock"],"Name":"object","StreamUri":"https://radio.example/object","Bitrate":{}},
             "text-number":{"Genre":["Rock"],"Name":"text-number","StreamUri":"https://radio.example/text-number","Bitrate":"96"},
             "number":{"Genre":["Rock"],"Name":"number","StreamUri":"https://radio.example/number","Bitrate":64}}
            """.trimIndent(),
            MediaIdBuilderDefault(),
            Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        )

        assertEquals(
            mapOf(
                "word" to MediaStream.BIT_RATE_DEFAULT,
                "null" to MediaStream.BIT_RATE_DEFAULT,
                "object" to MediaStream.BIT_RATE_DEFAULT,
                "text-number" to 96,
                "number" to 64
            ),
            stations.associate { it.id to it.getStreamBitrate() }
        )
    }

    @Test
    fun countryCodesAreMappedByBothParsers() {
        val radioBrowserCountries = ParserLayerRadioBrowserImpl(FilterImpl()).getAllCountries(
            """[{"iso_3166_1":"PL"},{"iso_3166_1":"??"}]"""
        )
        assertEquals(setOf(Country("Poland", "PL")), radioBrowserCountries)

        val webRadioCountries = ParserLayerWebRadioImpl(emptySet()).getAllCountries(
            """["Poland","Unknown"]"""
        )
        assertEquals(setOf(Country("Poland", "PL")), webRadioCountries)
    }

    @Test
    fun webRadioFiltersByCategoryCountryAndCaseInsensitiveSearch() {
        val data = """
            {
              "rock-station": {
                "Genre": ["Rock"],
                "Name": "Morning Rock",
                "StreamUri": "https://radio.example/rock",
                "Country": "Poland"
              },
              "jazz-station": {
                "Genre": ["Jazz"],
                "Name": "Evening Jazz",
                "StreamUri": "https://radio.example/jazz",
                "Country": "Germany"
              },
              "mixed-station": {
                "Genre": ["Rock", "Jazz"],
                "Name": "Mixed Selection",
                "StreamUri": "https://radio.example/mixed",
                "Country": "Poland"
              }
            }
        """.trimIndent()
        val categoryUri = Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        val countryUri = Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_COUNTRY_ID}PL")
        val searchUri = Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_SEARCH_ID}ROCK")

        assertEquals(
            setOf("rock-station", "mixed-station"),
            ParserLayerWebRadioImpl(emptySet())
                .getRadioStations(data, MediaIdBuilderDefault(), categoryUri)
                .map { it.id }
                .toSet()
        )
        assertEquals(
            setOf("rock-station", "mixed-station"),
            ParserLayerWebRadioImpl(setOf(Country("Poland", "PL")))
                .getRadioStations(data, MediaIdBuilderDefault(), countryUri)
                .map { it.id }
                .toSet()
        )
        assertEquals(
            setOf("rock-station"),
            ParserLayerWebRadioImpl(emptySet())
                .getRadioStations(data, MediaIdBuilderDefault(), searchUri)
                .map { it.id }
                .toSet()
        )
    }

    @Test
    fun webRadioSkipsMalformedEntries() {
        val data = """
            {
              "not-an-object": "invalid",
              "bad-genres": {
                "Genre": "Rock",
                "Name": "Bad Genres",
                "StreamUri": "https://radio.example/bad-genres"
              },
              "missing-genre": {
                "Name": "Missing Genre",
                "StreamUri": "https://radio.example/missing-genre"
              }
            }
        """.trimIndent()
        val parser = ParserLayerWebRadioImpl(emptySet())

        assertTrue(parser.getAllCategories(data).isEmpty())
        assertTrue(
            parser.getRadioStations(
                data,
                MediaIdBuilderDefault(),
                Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
            ).isEmpty()
        )
    }

    /**
     * The station matches every kind of criteria, and each is asked first, so the empty answer to
     * the request without any is down to the missing criteria and not to the station.
     */
    @Test
    fun webRadioListsNothingWithoutCriteria() {
        val data = """
            {
              "rock-station": {
                "Genre": ["Rock"],
                "Name": "Morning Rock",
                "StreamUri": "https://radio.example/rock",
                "Country": "Poland"
              }
            }
        """.trimIndent()
        val parser = ParserLayerWebRadioImpl(setOf(Country("Poland", "PL")))

        for (key in listOf(
            "${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock",
            "${UrlLayerWebRadioImpl.KEY_COUNTRY_ID}PL",
            "${UrlLayerWebRadioImpl.KEY_SEARCH_ID}Rock"
        )) {
            val uri = Uri.parse("https://example.com?$key")
            assertEquals(
                key,
                setOf("rock-station"),
                parser.getRadioStations(data, MediaIdBuilderDefault(), uri)
                    .map { it.id }
                    .toSet()
            )
        }
        assertTrue(
            parser.getRadioStations(
                data,
                MediaIdBuilderDefault(),
                Uri.parse("https://example.com")
            ).isEmpty()
        )
    }

    @Test
    fun webRadioSearchUsesNameWithoutRequiringCountry() {
        val data = """
            {
              "matching-station": {
                "Genre": ["Rock"],
                "Name": "Morning Rock",
                "StreamUri": "https://radio.example/rock"
              },
              "missing-name": {
                "Genre": ["Rock"],
                "StreamUri": "https://radio.example/missing-name",
                "Country": "Poland"
              }
            }
        """.trimIndent()

        assertEquals(
            setOf("matching-station"),
            ParserLayerWebRadioImpl(emptySet())
                .getRadioStations(
                    data,
                    MediaIdBuilderDefault(),
                    Uri.parse("https://example.com?${UrlLayerWebRadioImpl.KEY_SEARCH_ID}rock")
                )
                .map { it.id }
                .toSet()
        )
    }

    @Test
    fun radioBrowserHandlesMalformedProviderShapesIndependently() {
        val parser = ParserLayerRadioBrowserImpl(FilterImpl())
        val stations = parser.getRadioStations(
            """[42,{"stationuuid":"valid","name":"Valid","url":"https://radio.example/stream"}]""",
            MediaIdBuilderDefault(),
            Uri.parse("")
        )
        val categories = parser.getAllCategories(
            """[false,{"name":"rock","stationcount":1}]"""
        )
        val countries = parser.getAllCountries(
            """["invalid",{"iso_3166_1":"PL"}]"""
        )

        assertEquals(setOf("valid"), stations.map { it.id }.toSet())
        assertEquals(setOf("rock"), categories.map { it.id }.toSet())
        assertEquals(setOf(Country("Poland", "PL")), countries)
        assertTrue(parser.getAllCategories("{").isEmpty())
        assertTrue(parser.getAllCountries("{").isEmpty())
        assertSame(
            RadioStation.INVALID_INSTANCE,
            parser.getRadioStation("[]", MediaIdBuilderDefault(), Uri.parse(""))
        )
    }
}
