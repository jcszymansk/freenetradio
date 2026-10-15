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

package com.yuriy.openradio.shared.utils

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test
import wseemann.media.jplaylistparser.playlist.PlaylistEntry

/**
 * Pins the MIME type a stream named by a station's playlist is played with, which decides the
 * media source the player opens it with before its url is looked at.
 */
class ResolvedStreamTest {

    @Test
    fun anEntryWithoutATypeIsTypedFromItsUrl() {
        listOf(
            "https://example.com/live/index.M3U8?token=a.b" to MimeTypes.APPLICATION_M3U8,
            "https://example.com/live/index.m3u8;jsessionid=a.b" to MimeTypes.APPLICATION_M3U8,
            "https://example.com/live/index.m3u8;jsessionid=ab?token=c;d" to MimeTypes.APPLICATION_M3U8,
            "https://example.com/stream.mp3;jsessionid=ab" to MimeTypes.AUDIO_MPEG,
            "https://example.com/stream.mp3" to MimeTypes.AUDIO_MPEG,
            "https://example.com/stream.aac?sid=1" to MimeTypes.AUDIO_AAC,
            "https://example.com/stream" to MimeTypes.AUDIO_UNKNOWN
        ).forEach { (url, mimeType) ->
            assertEquals(url, ResolvedStream(url, mimeType), ResolvedStream.of(entry(url)))
        }
    }

    /**
     * The parser types an entry itself when it found the url to serve HLS by reading it, see
     * `AutoDetectParser`; the url alone would be played as a progressive stream again.
     */
    @Test
    fun theTypeAnEntryCarriesWinsOverItsUrl() {
        listOf(
            "https://example.com/live/listen",
            "https://example.com/station.pls",
            "https://example.com/stream.mp3"
        ).forEach { url ->
            val stream = ResolvedStream.of(entry(url, MimeTypes.APPLICATION_M3U8))

            assertEquals(url, ResolvedStream(url, MimeTypes.APPLICATION_M3U8), stream)
        }
    }

    private companion object {

        fun entry(url: String, mimeType: String? = null): PlaylistEntry {
            val entry = PlaylistEntry()
            entry[PlaylistEntry.URI] = url
            mimeType?.let { entry[PlaylistEntry.MIME_TYPE] = it }
            return entry
        }
    }
}
