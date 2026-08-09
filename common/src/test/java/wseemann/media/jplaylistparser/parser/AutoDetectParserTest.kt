/*
 * Copyright 2017-2020 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package wseemann.media.jplaylistparser.parser

import org.hamcrest.MatcherAssert
import org.hamcrest.core.Is
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import wseemann.media.jplaylistparser.parser.m3u.M3UPlaylistParser
import wseemann.media.jplaylistparser.parser.m3u8.M3U8PlaylistParser
import wseemann.media.jplaylistparser.parser.pls.PLSPlaylistParser
import wseemann.media.jplaylistparser.playlist.Playlist
import wseemann.media.jplaylistparser.exception.JPlaylistParserException
import wseemann.media.jplaylistparser.playlist.PlaylistEntry
import java.io.ByteArrayInputStream
/**
 * Created by Chernyshov Yurii
 * At Android Studio
 * On 25/11/17
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class AutoDetectParserTest {

    @Test
    fun testFileExtension() {
        val parser = AutoDetectParser(0)
        var url = "http://s06.hktoolbar.com/radio-HTTP/cr2-hd.3gp/chunklist.m3u8?nimblesessionid=41472102"
        var ext = parser.getFileExtension(url)
        MatcherAssert.assertThat(ext, Is.`is`(M3U8PlaylistParser.EXTENSION))

        url = "http://s06.hktoolbar.com/radio-HTTP/cr2-hd.3gp/chunklist.m3u?nimblesessionid=41472102"
        ext = parser.getFileExtension(url)
        MatcherAssert.assertThat(ext, Is.`is`(M3UPlaylistParser.EXTENSION))

        url = "http://s06.hktoolbar.com/radio-HTTP/cr2-hd.3gp/chunklist.pls?nimblesessionid=41472102"
        ext = parser.getFileExtension(url)
        MatcherAssert.assertThat(ext, Is.`is`(PLSPlaylistParser.EXTENSION))
    }

    @Test
    fun testExtractFileNameFromHeader() {
        val param1 = "attachment; filename=playlist_9068.pls"
        MatcherAssert.assertThat(
                AutoDetectParser.getFileExtFromHeaderParam(param1),
                Is.`is`("playlist_9068.pls")
        )

        val param2 = "filename=playlist_9068.pls"
        MatcherAssert.assertThat(
                AutoDetectParser.getFileExtFromHeaderParam(param2),
                Is.`is`("playlist_9068.pls")
        )
    }

    @Test
    fun dispatchesSupportedFormatsFromStreams() {
        data class Fixture(val extension: String, val uri: String, val content: String)

        val fixtures = listOf(
            Fixture(
                ".m3u",
                "https://example.com/stream?format=m3u",
                "#EXTM3U\n#EXTINF:-1,Station\nhttps://example.com/stream?format=m3u"
            ),
            Fixture(
                ".m3u8",
                "https://example.com/stream?format=m3u8",
                "#EXTM3U\n#EXTINF:-1,Station\nhttps://example.com/stream?format=m3u8"
            ),
            Fixture(
                ".pls",
                "https://example.com/stream?format=pls",
                "[playlist]\nFile1=https://example.com/stream?format=pls\nTitle1=Station\nLength1=-1"
            ),
            Fixture(
                ".asx",
                "ftp://example.com/stream-asx",
                "<ASX><ENTRY><TITLE>Station</TITLE>" +
                        "<REF href=\"ftp://example.com/stream-asx\"/></ENTRY></ASX>"
            ),
            Fixture(
                ".xspf",
                "https://example.com/stream?format=xspf",
                "<PLAYLIST><TRACKLIST><TRACK><LOCATION>" +
                        "https://example.com/stream?format=xspf</LOCATION>" +
                        "<TITLE>Station</TITLE></TRACK></TRACKLIST></PLAYLIST>"
            )
        )

        fixtures.forEach { fixture ->
            val playlist = Playlist()
            AutoDetectParser(1000).parse(
                "https://example.com/playlist${fixture.extension}",
                if (fixture.extension == ".asx") "video/x-ms-asf" else null,
                ByteArrayInputStream(fixture.content.toByteArray()),
                playlist
            )

            assertEquals(fixture.extension, 1, playlist.playlistEntries.size)
            val entry = playlist.playlistEntries.single()
            assertEquals(fixture.uri, entry[PlaylistEntry.URI])
            assertEquals("Station", entry[PlaylistEntry.PLAYLIST_METADATA])
        }
    }

    @Test
    fun dispatchesFromMimeTypesWithParameters() {
        data class Fixture(val mimeType: String, val uri: String, val content: String)

        val fixtures = listOf(
            Fixture(
                "audio/x-mpegurl",
                "https://example.com/mime-m3u",
                "#EXTM3U\n#EXTINF:-1,Station\nhttps://example.com/mime-m3u"
            ),
            Fixture(
                "audio/x-scpls",
                "https://example.com/mime-pls",
                "[playlist]\nFile1=https://example.com/mime-pls\nTitle1=Station\nLength1=-1"
            ),
            Fixture(
                "video/x-ms-asf",
                "ftp://example.com/mime-asx",
                "<ASX><ENTRY><TITLE>Station</TITLE>" +
                        "<REF href=\"ftp://example.com/mime-asx\"/></ENTRY></ASX>"
            ),
            Fixture(
                "video/application/xspf+xml",
                "https://example.com/mime-xspf",
                "<PLAYLIST><TRACKLIST><TRACK><LOCATION>" +
                        "https://example.com/mime-xspf</LOCATION>" +
                        "<TITLE>Station</TITLE></TRACK></TRACKLIST></PLAYLIST>"
            )
        )

        fixtures.forEach { fixture ->
            val playlist = Playlist()
            AutoDetectParser(0).parse(
                "not-a-url",
                "${fixture.mimeType}; charset=UTF-8",
                ByteArrayInputStream(fixture.content.toByteArray()),
                playlist
            )

            assertEquals(fixture.mimeType, fixture.uri, playlist.playlistEntries.single()[PlaylistEntry.URI])
        }
    }

    @Test
    fun extensionsAreCaseInsensitive() {
        val playlist = Playlist()

        AutoDetectParser(0).parse(
            "https://example.com/playlist.PLS",
            null,
            ByteArrayInputStream(
                "[playlist]\nFile1=https://example.com/stream\nTitle1=Station\nLength1=-1".toByteArray()
            ),
            playlist
        )

        assertEquals("https://example.com/stream", playlist.playlistEntries.single()[PlaylistEntry.URI])
    }

    @Test
    fun malformedPlaylistProducesNoEntries() {
        val playlist = Playlist()

        AutoDetectParser(0).parse(
            "https://example.com/playlist.pls",
            null,
            ByteArrayInputStream("not a playlist".toByteArray()),
            playlist
        )

        assertTrue(playlist.playlistEntries.isEmpty())
    }

    @Test
    fun unsupportedFormatIsRejectedWithoutNetworking() {
        assertThrows(JPlaylistParserException::class.java) {
            AutoDetectParser(0).parse(
                "not-a-url",
                "application/octet-stream",
                ByteArrayInputStream(byteArrayOf()),
                Playlist()
            )
        }
    }
}
