/*
 * Copyright 2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.model.translation

import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamBitrate
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.media.setVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RadioStationJsonSerializerTest {

    @Test
    fun serializationRoundTrip() {
        val expected = RadioStation.makeDefaultInstance("station-id").apply {
            name = "Station"
            setVariant(192, "https://example.com/stream")
            country = "Poland"
            countryCode = "PL"
            genre = "Jazz"
            homePage = "https://example.com"
            codec = "MP3"
            isLocal = true
            sortId = 7
            imageUrl = "https://example.com/image.png"
        }

        val serialized = RadioStationJsonSerializer().serialize(expected)
        val actual = RadioStationJsonDeserializer().deserialize(serialized)

        assertEquals(expected.id, actual.id)
        assertEquals(expected.name, actual.name)
        assertEquals(expected.getStreamBitrate(), actual.getStreamBitrate())
        assertEquals(expected.getStreamUrlFixed(), actual.getStreamUrlFixed())
        assertEquals(expected.country, actual.country)
        assertEquals(expected.countryCode, actual.countryCode)
        assertEquals(expected.genre, actual.genre)
        assertEquals(expected.homePage, actual.homePage)
        assertEquals(expected.codec, actual.codec)
        assertEquals(expected.isLocal, actual.isLocal)
        assertEquals(expected.sortId, actual.sortId)
        assertEquals(expected.imageUrl, actual.imageUrl)
    }

    @Test
    fun emptyStationSerializesToEmptyObject() {
        val serialized = RadioStationJsonSerializer().serialize(
            RadioStation.makeDefaultInstance("empty-station")
        )

        assertEquals("{}", serialized)
    }

    @Test
    fun malformedAndMarkerValuesDeserializeAsInvalid() {
        val deserializer = RadioStationJsonDeserializer()

        listOf("", "true", "false", "{").forEach { value ->
            assertSame(RadioStation.INVALID_INSTANCE, deserializer.deserialize(value))
        }
    }
}