package com.yuriy.openradio.shared.model.parser

import android.net.TestUri
import com.yuriy.openradio.shared.model.filter.FilterImpl
import com.yuriy.openradio.shared.model.media.getStreamBitrate
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.net.UrlLayerWebRadioImpl
import com.yuriy.openradio.shared.model.translation.MediaIdBuilderDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            .getRadioStation(data, MediaIdBuilderDefault(), TestUri(""))

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
            TestUri("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
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
            TestUri("")
        )
        assertTrue(radioBrowserStations.isEmpty())

        val webRadioStations = ParserLayerWebRadioImpl(emptySet()).getRadioStations(
            """{"":{"Genre":["Rock"],"Name":"No ID","StreamUri":"https://webradio.example/stream"}}""",
            MediaIdBuilderDefault(),
            TestUri("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        )
        assertTrue(webRadioStations.isEmpty())
    }

    @Test
    fun missingStreamUrlIsRejectedByBothParsers() {
        val radioBrowserStations = ParserLayerRadioBrowserImpl(FilterImpl()).getRadioStations(
            """[{"stationuuid":"radio-browser-id","name":"No stream"},{"stationuuid":"empty-stream-id","name":"Empty stream","url":""}]""",
            MediaIdBuilderDefault(),
            TestUri("")
        )
        assertTrue(radioBrowserStations.isEmpty())

        val webRadioStations = ParserLayerWebRadioImpl(emptySet()).getRadioStations(
            """{"web-radio-id":{"Genre":["Rock"],"Name":"No stream"},"empty-stream-id":{"Genre":["Rock"],"Name":"Empty stream","StreamUri":""}}""",
            MediaIdBuilderDefault(),
            TestUri("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        )
        assertTrue(webRadioStations.isEmpty())
    }

    @Test
    fun malformedAndEmptyJsonReturnNoStations() {
        val radioBrowser = ParserLayerRadioBrowserImpl(FilterImpl())
        assertTrue(radioBrowser.getRadioStations("", MediaIdBuilderDefault(), TestUri("")).isEmpty())
        assertTrue(radioBrowser.getRadioStations("{", MediaIdBuilderDefault(), TestUri("")).isEmpty())

        val webRadio = ParserLayerWebRadioImpl(emptySet())
        val categoryUri = TestUri("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        assertTrue(webRadio.getRadioStations("", MediaIdBuilderDefault(), categoryUri).isEmpty())
        assertTrue(webRadio.getRadioStations("[", MediaIdBuilderDefault(), categoryUri).isEmpty())
    }

    @Test
    fun unknownFieldsDoNotAffectMapping() {
        val radioBrowserStation = ParserLayerRadioBrowserImpl(FilterImpl()).getRadioStations(
            """[{"stationuuid":"radio-browser-id","name":"Known Radio","url":"https://radio.example/stream","unknown":"ignored","nested":{"ignored":true}}]""",
            MediaIdBuilderDefault(),
            TestUri("")
        ).single()
        assertEquals("Known Radio", radioBrowserStation.name)
        assertEquals("https://radio.example/stream", radioBrowserStation.getStreamUrlFixed())

        val webRadioStation = ParserLayerWebRadioImpl(emptySet()).getRadioStations(
            """{"web-radio-id":{"Genre":["Rock"],"Name":"Known Web Radio","StreamUri":"https://webradio.example/stream","Unknown":"ignored","Nested":{"ignored":true}}}""",
            MediaIdBuilderDefault(),
            TestUri("https://example.com?${UrlLayerWebRadioImpl.KEY_CATEGORY_ID}Rock")
        ).single()
        assertEquals("Known Web Radio", webRadioStation.name)
        assertEquals("https://webradio.example/stream", webRadioStation.getStreamUrlFixed())
    }

}
