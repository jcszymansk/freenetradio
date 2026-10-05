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

package wseemann.media.jplaylistparser.parser.xspf

import org.jdom2.JDOMException
import org.jdom2.input.SAXBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import wseemann.media.jplaylistparser.parser.AutoDetectParser
import wseemann.media.jplaylistparser.parser.PlaylistFetcher
import wseemann.media.jplaylistparser.playlist.Playlist
import wseemann.media.jplaylistparser.playlist.PlaylistEntry
import java.io.ByteArrayInputStream
import java.io.StringReader

/**
 * Pins the one XSPF behaviour that is a repaired defect rather than an upstream habit: an XSPF
 * playlist is downloaded from wherever a station points, and the DTD it declares must never be
 * opened, because that would be a connection [PlaylistFetcher] never sees. Dispatch and the
 * ordinary track layout are covered by `AutoDetectParserTest`.
 */
class XSPFPlaylistParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun externalDtdIsNeverRead() {
        val dtd = temporaryFolder.newFile("broken.dtd")
        dtd.writeText("<!ELEMENT broken")
        val xml = "<?xml version=\"1.0\"?>" +
                "<!DOCTYPE playlist SYSTEM \"${dtd.toURI()}\">" +
                "<playlist><trackList><track><location>$STREAM</location>" +
                "<title>Station</title></track></trackList></playlist>"
        // Proves the fixture: a builder that does read the DTD fails on it, so the parse below
        // producing the track means the DTD was not read.
        assertThrows(JDOMException::class.java) { SAXBuilder().build(StringReader(xml)) }
        val fetcher = RecordingFetcher()
        val playlist = Playlist()

        AutoDetectParser(fetcher).parse(
            "$HOST/top.xspf",
            "application/xspf+xml",
            ByteArrayInputStream(xml.toByteArray()),
            playlist
        )

        assertEquals(listOf(STREAM), playlist.playlistEntries.map { it[PlaylistEntry.URI] })
        assertEquals(listOf("Station"), playlist.playlistEntries.map { it[PlaylistEntry.PLAYLIST_METADATA] })
        assertEquals(emptyList<String>(), fetcher.reads)
    }

    private class RecordingFetcher : PlaylistFetcher {

        private val mReads = mutableListOf<String>()

        val reads: List<String>
            get() = mReads.toList()

        override fun fetch(url: String): ByteArray {
            mReads.add(url)
            return ByteArray(0)
        }
    }

    private companion object {

        const val HOST = "http://127.0.0.1"
        const val STREAM = "$HOST/stream"
    }
}
