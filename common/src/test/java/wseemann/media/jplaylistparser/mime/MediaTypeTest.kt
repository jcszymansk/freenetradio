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

package wseemann.media.jplaylistparser.mime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTypeTest {

    @Test
    fun parsesAndNormalizesMimeTypes() {
        assertEquals("audio/mpeg", MediaType.parse(" Audio / MPEG ").toString())
        assertEquals(
            "audio/mpeg; charset=UTF-8; profile=high",
            MediaType.parse("Audio/MPEG; profile=high; Charset=UTF-8").toString()
        )
        assertEquals(
            "audio/mpeg; charset=UTF-8",
            MediaType.parse("charset=UTF-8; Audio/MPEG").toString()
        )
    }

    @Test
    fun quotesAndUnquotesParameterValues() {
        assertEquals(
            "audio/mpeg; profile=\"high quality\"",
            MediaType.parse("audio/mpeg; profile='high quality'").toString()
        )
        assertEquals(
            "audio/mpeg; profile=\"high quality\"",
            MediaType.parse("audio/mpeg; profile=\"high quality\"").toString()
        )
    }

    @Test
    fun rejectsMalformedMimeTypes() {
        listOf(null, "", "audio", "/mpeg", "audio/").forEach { value ->
            assertNull(value, MediaType.parse(value))
        }
    }

    @Test
    fun factoriesAndValueSemanticsUseCanonicalNames() {
        val audio = MediaType.audio("mpeg")
        val sameAudio = MediaType.parse("audio/mpeg")
        val video = MediaType.video("mpeg")

        assertEquals(audio, sameAudio)
        assertEquals(audio.hashCode(), sameAudio.hashCode())
        assertNotEquals(audio, video)
        assertNotEquals(audio, "audio/mpeg")
        assertTrue(audio!!.compareTo(video!!) < 0)
    }
}
