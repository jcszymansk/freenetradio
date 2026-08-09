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
