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
        val parser = AutoDetectParser(NO_READS)
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
            AutoDetectParser(NO_READS).parse(
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
                "application/xspf+xml",
                "https://example.com/mime-xspf",
                "<PLAYLIST><TRACKLIST><TRACK><LOCATION>" +
                        "https://example.com/mime-xspf</LOCATION>" +
                        "<TITLE>Station</TITLE></TRACK></TRACKLIST></PLAYLIST>"
            )
        )

        fixtures.forEach { fixture ->
            val playlist = Playlist()
            AutoDetectParser(NO_READS).parse(
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

        AutoDetectParser(NO_READS).parse(
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

        AutoDetectParser(NO_READS).parse(
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
            AutoDetectParser(NO_READS).parse(
                "not-a-url",
                "application/octet-stream",
                ByteArrayInputStream(byteArrayOf()),
                Playlist()
            )
        }
    }

    @Test
    fun aStreamWithNeitherTypeNorExtensionIsRecognisedByItsContent() {
        data class Fixture(val name: String, val content: String, val uri: String)

        val fixtures = listOf(
            Fixture("m3u", "#EXTM3U\n#EXTINF:-1,Station\nhttps://example.com/a", "https://example.com/a"),
            Fixture(
                "m3u8 by its HLS tags",
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:10,\nsegment.ts",
                "https://example.com/live/segment.ts"
            ),
            Fixture("pls", "[playlist]\nFile1=https://example.com/b\nLength1=-1", "https://example.com/b"),
            Fixture("pls in upper case", "[PLAYLIST]\nFile1=https://example.com/c\n", "https://example.com/c"),
            Fixture(
                "asx",
                "<asx version=\"3.0\"><entry><ref href=\"https://example.com/d\"/></entry></asx>",
                "https://example.com/d"
            ),
            Fixture(
                "xspf behind a declaration and a comment",
                "<?xml version=\"1.0\"?>\n<!-- generated -->\n<playlist><trackList><track>" +
                        "<location>https://example.com/e</location></track></trackList></playlist>",
                "https://example.com/e"
            ),
            Fixture(
                "asx behind a byte order mark and whitespace",
                "﻿ \r\n\t<ASX><ENTRY><REF HREF=\"https://example.com/f\"/></ENTRY></ASX>",
                "https://example.com/f"
            )
        )

        fixtures.forEach { fixture ->
            val playlist = Playlist()

            AutoDetectParser(NO_READS).parse(
                "https://example.com/live/listen",
                "application/octet-stream",
                ByteArrayInputStream(fixture.content.toByteArray()),
                playlist
            )

            assertEquals(fixture.name, listOf(fixture.uri), uris(playlist))
        }
    }

    @Test
    fun recognisingTheContentDoesNotConsumeIt() {
        val padding = "#EXTINF:-1,Padding\n".repeat(100)
        val content = "#EXTM3U\n$padding" + "https://example.com/after-the-sniffed-head"
        val playlist = Playlist()

        AutoDetectParser(NO_READS).parse(
            "not-a-url", null, ByteArrayInputStream(content.toByteArray()), playlist
        )

        assertTrue(content.length > 1024)
        assertEquals(listOf("https://example.com/after-the-sniffed-head"), uris(playlist))
    }

    @Test
    fun contentInNoKnownFormatIsRejected() {
        assertThrows(JPlaylistParserException::class.java) {
            AutoDetectParser(NO_READS).parse(
                "https://example.com/listen",
                null,
                ByteArrayInputStream("ID3\u0003\u0000 not a playlist".toByteArray()),
                Playlist()
            )
        }
    }

    @Test
    fun anEntryWithAPlaylistExtensionIsReadThroughTheFetcher() {
        val fetcher = RecordingFetcher(
            "https://example.com/inner.pls" to "[playlist]\nFile1=https://example.com/stream\nLength1=-1"
        )
        val playlist = Playlist()

        AutoDetectParser(fetcher).parse(
            "https://example.com/outer.m3u",
            null,
            ByteArrayInputStream("https://example.com/inner.pls\n".toByteArray()),
            playlist
        )

        assertEquals(listOf("https://example.com/inner.pls"), fetcher.reads)
        assertEquals(listOf("https://example.com/stream"), uris(playlist))
    }

    @Test
    fun aNestedPlaylistIsDispatchedOnItsExtensionBeforeItsContent() {
        val fetcher = RecordingFetcher(
            "https://example.com/live/index.m3u8" to "#EXTM3U\n#EXTINF:10,\nsegment.ts"
        )
        val playlist = Playlist()

        AutoDetectParser(fetcher).parse(
            "https://example.com/station.m3u",
            null,
            ByteArrayInputStream("https://example.com/live/index.m3u8\n".toByteArray()),
            playlist
        )

        assertEquals(listOf("https://example.com/live/segment.ts"), uris(playlist))
    }

    @Test
    fun aNestedPlaylistThatCannotBeReadAddsNothing() {
        val fetcher = RecordingFetcher()
        val playlist = Playlist()

        AutoDetectParser(fetcher).parse(
            "https://example.com/outer.m3u",
            null,
            ByteArrayInputStream("https://example.com/gone.pls\nhttps://example.com/kept\n".toByteArray()),
            playlist
        )

        assertEquals(listOf("https://example.com/gone.pls"), fetcher.reads)
        assertEquals(listOf("https://example.com/kept"), uris(playlist))
    }

    @Test
    fun aNestedPlaylistInNoKnownFormatAddsNothing() {
        val fetcher = RecordingFetcher("https://example.com/inner.pls" to "<html>moved</html>")
        val playlist = Playlist()

        AutoDetectParser(fetcher).parse(
            "https://example.com/outer.m3u",
            null,
            ByteArrayInputStream("https://example.com/inner.pls\n".toByteArray()),
            playlist
        )

        assertTrue(playlist.playlistEntries.isEmpty())
    }

    @Test
    fun aNestedPlaylistNamedTwiceIsReadOnce() {
        val fetcher = RecordingFetcher(
            "https://example.com/inner.pls" to "[playlist]\nFile1=https://example.com/stream\nLength1=-1"
        )
        val playlist = Playlist()

        AutoDetectParser(fetcher).parse(
            "https://example.com/outer.m3u",
            null,
            ByteArrayInputStream("https://example.com/inner.pls\nhttps://example.com/inner.pls\n".toByteArray()),
            playlist
        )

        assertEquals(listOf("https://example.com/inner.pls"), fetcher.reads)
        assertEquals(listOf("https://example.com/stream"), uris(playlist))
    }

    @Test
    fun aPlaylistThatNamesItselfIsNotReadAgain() {
        val fetcher = RecordingFetcher()
        val playlist = Playlist()

        AutoDetectParser(fetcher).parse(
            "https://example.com/self.m3u",
            null,
            ByteArrayInputStream("https://example.com/self.m3u\nhttps://example.com/stream\n".toByteArray()),
            playlist
        )

        assertTrue(fetcher.reads.isEmpty())
        assertEquals(listOf("https://example.com/stream"), uris(playlist))
    }

    /**
     * The static guard this replaced compared an entry with the one before it and threw, which
     * lost the whole playlist. A stream named twice is not a cycle.
     */
    @Test
    fun aStreamNamedTwiceInARowIsKeptTwice() {
        val playlist = Playlist()

        AutoDetectParser(NO_READS).parse(
            "https://example.com/twice.m3u",
            null,
            ByteArrayInputStream("https://example.com/stream\nhttps://example.com/stream\n".toByteArray()),
            playlist
        )

        assertEquals(listOf("https://example.com/stream", "https://example.com/stream"), uris(playlist))
    }

    @Test
    fun aChainOfDistinctPlaylistsStopsAtTheDepthLimit() {
        val count = AutoDetectParser.MAX_DEPTH + 2
        fun link(index: Int) = "https://example.com/link$index.m3u"
        fun stream(index: Int) = "https://example.com/stream/$index"
        val fetcher = RecordingFetcher(
            *(1 until count).map { link(it) to "${link(it + 1)}\n${stream(it)}\n" }.toTypedArray()
        )
        val playlist = Playlist()

        AutoDetectParser(fetcher).parse(
            "https://example.com/top.m3u",
            null,
            ByteArrayInputStream("${link(1)}\n".toByteArray()),
            playlist
        )

        val followed = 1..AutoDetectParser.MAX_DEPTH
        assertEquals(followed.map { link(it) }, fetcher.reads)
        assertEquals(
            "each link names the next one before its own stream, so the deepest stream comes first",
            followed.reversed().map { stream(it) },
            uris(playlist)
        )
    }

    /**
     * Serves fixed content per url and records every read in order. A url it does not know is
     * answered the way the downloader answers a failure, with no content.
     */
    private class RecordingFetcher(vararg content: Pair<String, String>) : PlaylistFetcher {

        private val mContent = content.toMap()

        val reads = mutableListOf<String>()

        override fun fetch(url: String): ByteArray {
            reads.add(url)
            return mContent[url]?.toByteArray() ?: ByteArray(0)
        }
    }

    private companion object {

        /**
         * None of these fixtures names another playlist, so any read is a regression: before the
         * parsers read through a fetcher, an entry without a playlist extension was probed with a
         * real request.
         */
        val NO_READS = PlaylistFetcher { url -> throw AssertionError("unexpected read of $url") }

        fun uris(playlist: Playlist): List<String> {
            return playlist.playlistEntries.map { it[PlaylistEntry.URI] }
        }
    }
}
