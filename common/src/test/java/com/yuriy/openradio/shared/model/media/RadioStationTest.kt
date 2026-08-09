package com.yuriy.openradio.shared.model.media

import com.yuriy.openradio.shared.service.location.LocationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationTest {

    @Test
    fun fixedStreamIsPreferredWithPrimaryAsFallback() {
        val station = RadioStation.makeDefaultInstance("station").apply {
            setVariant(128, "https://example.com/primary")
        }

        assertEquals("https://example.com/primary", station.getStreamUrlFixed())
        assertEquals(128, station.getStreamBitrate())

        station.setVariantFixed(192, "https://example.com/fixed")

        assertEquals("https://example.com/fixed", station.getStreamUrlFixed())
    }

    @Test
    fun countryNamesAreNormalizedCaseInsensitively() {
        val station = RadioStation.makeDefaultInstance("station")

        station.country = LocationService.GB_WRONG.lowercase()
        assertEquals(LocationService.GB_CORRECT, station.country)

        station.country = LocationService.TW_WRONG_A.uppercase()
        assertEquals(LocationService.TW_CORRECT, station.country)

        station.country = LocationService.TW_WRONG_B
        assertEquals(LocationService.TW_CORRECT, station.country)

        station.country = "Poland"
        assertEquals("Poland", station.country)
    }

    @Test
    fun copyPreservesStateAndOwnsItsStreams() {
        val original = RadioStation.makeDefaultInstance("station").apply {
            name = "Station"
            country = "Poland"
            countryCode = "PL"
            genre = "Jazz"
            description = "Description"
            homePage = "https://example.com"
            urlResolved = "https://example.com/resolved"
            lastCheckOk = 1
            lastCheckOkTime = "2026-08-09"
            imageUrl = "https://example.com/image.png"
            codec = "MP3"
            isLocal = true
            sortId = 7
            setVariant(128, "https://example.com/primary")
            setVariantFixed(192, "https://example.com/fixed")
        }

        val copy = RadioStation.makeCopyInstance(original)

        assertEquals(original.id, copy.id)
        assertEquals(original.name, copy.name)
        assertEquals(original.country, copy.country)
        assertEquals(original.countryCode, copy.countryCode)
        assertEquals(original.genre, copy.genre)
        assertEquals(original.description, copy.description)
        assertEquals(original.homePage, copy.homePage)
        assertEquals(original.urlResolved, copy.urlResolved)
        assertEquals(original.lastCheckOk, copy.lastCheckOk)
        assertEquals(original.lastCheckOkTime, copy.lastCheckOkTime)
        assertEquals(original.imageUrl, copy.imageUrl)
        assertEquals(original.codec, copy.codec)
        assertEquals(original.isLocal, copy.isLocal)
        assertEquals(original.sortId, copy.sortId)
        assertEquals(original.mediaStream, copy.mediaStream)
        assertEquals(original.mediaStreamFixed, copy.mediaStreamFixed)

        original.setVariant(64, "https://example.com/changed")
        original.setVariantFixed(64, "https://example.com/fixed-changed")

        assertEquals("https://example.com/primary", copy.mediaStream.getVariant(0).url)
        assertEquals("https://example.com/fixed", copy.mediaStreamFixed.getVariant(0).url)
    }

    @Test
    fun streamAssignmentCopiesVariants() {
        val source = MediaStream.makeDefaultInstance().apply {
            setVariant(128, "https://example.com/stream")
        }
        val station = RadioStation.makeDefaultInstance("station")

        station.mediaStream = source
        station.mediaStreamFixed = source
        source.clear()

        assertEquals("https://example.com/stream", station.mediaStream.getVariant(0).url)
        assertEquals("https://example.com/stream", station.mediaStreamFixed.getVariant(0).url)
    }

    @Test
    fun identityAndOrderingUseStreamsSortIdAndName() {
        val first = RadioStation.makeDefaultInstance("same").apply {
            name = "Bravo"
            sortId = 1
            setVariant(128, "https://example.com/stream")
        }
        val equal = RadioStation.makeCopyInstance(first)
        val differentStream = RadioStation.makeDefaultInstance("same").apply {
            setVariant(64, "https://example.com/other")
        }
        val laterSortId = RadioStation.makeDefaultInstance("later").apply {
            name = "Alpha"
            sortId = 2
        }
        val laterName = RadioStation.makeDefaultInstance("name").apply {
            name = "Charlie"
            sortId = 1
        }

        assertEquals(first, first)
        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, differentStream)
        assertNotEquals(first, RadioStation.makeDefaultInstance("different"))
        assertNotEquals(first, null)
        assertNotEquals(first, "same")
        assertTrue(first < laterSortId)
        assertTrue(first < laterName)
        assertFalse(first.isInvalid())
        assertTrue(RadioStation.INVALID_INSTANCE.isInvalid())
        assertTrue(RadioStation.makeDefaultInstance("").isInvalid())
    }
}
