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

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [MediaItemBuilder.withStreamUrl], which swaps the stream a playlist resolved to into the
 * station's item. The content type is asked of Media3's own [Util.inferContentTypeForUriAndMimeType],
 * the call its default media source factory makes, so these tests show what the player would build.
 */
class MediaItemBuilderTest {

    /**
     * Every form of HLS address the playlist parsers keep as a stream, see
     * `AutoDetectParser.isHlsUrl`, is one the player opens as HLS.
     */
    @Test
    fun anHlsStreamResolvedFromAPlaylistIsPlayedAsHls() {
        listOf(
            "https://example.com/live/index.m3u8",
            "https://example.com/live/index.M3U8?token=a.b",
            "https://example.com/live/index.m3u8#start.0",
            "https://example.com/live/index.m3u8;jsessionid=a.b"
        ).forEach { url ->
            val resolved = MediaItemBuilder.withStreamUrl(stationItem(), url)

            assertEquals(url, MimeTypes.APPLICATION_M3U8, resolved.localConfiguration?.mimeType)
            assertEquals(url, C.CONTENT_TYPE_HLS, contentTypeOf(resolved))
        }
    }

    /**
     * The control: an item that keeps the type of the playlist it came from is opened as a
     * progressive stream, whatever its url says.
     */
    @Test
    fun anHlsStreamKeepingThePlaylistTypeWouldBePlayedAsProgressive() {
        val stale = stationItem().buildUpon().setUri("https://example.com/live/index.m3u8").build()

        assertEquals(C.CONTENT_TYPE_OTHER, contentTypeOf(stale))
    }

    @Test
    fun theTypeIsReadFromTheResolvedStream() {
        data class Fixture(val url: String, val mimeType: String)

        listOf(
            Fixture("https://example.com/live/index.M3U8?token=a.b", MimeTypes.APPLICATION_M3U8),
            Fixture("https://example.com/live/index.m3u8;jsessionid=a.b", MimeTypes.APPLICATION_M3U8),
            Fixture("https://example.com/live/index.m3u8;jsessionid=ab?token=c;d", MimeTypes.APPLICATION_M3U8),
            Fixture("https://example.com/stream.mp3;jsessionid=ab", MimeTypes.AUDIO_MPEG),
            Fixture("https://example.com/stream.mp3", MimeTypes.AUDIO_MPEG),
            Fixture("https://example.com/stream.aac?sid=1", MimeTypes.AUDIO_AAC),
            Fixture("https://example.com/stream", MimeTypes.AUDIO_UNKNOWN)
        ).forEach { fixture ->
            val resolved = MediaItemBuilder.withStreamUrl(stationItem(), fixture.url)

            assertEquals(fixture.url, fixture.url, resolved.localConfiguration?.uri.toString())
            assertEquals(fixture.url, fixture.mimeType, resolved.localConfiguration?.mimeType)
        }
    }

    @Test
    fun everythingButTheStreamIsKept() {
        val station = stationItem()

        val resolved = MediaItemBuilder.withStreamUrl(station, "https://example.com/stream.mp3")

        assertEquals(station.mediaId, resolved.mediaId)
        assertEquals(station.mediaMetadata, resolved.mediaMetadata)
    }

    private companion object {

        /**
         * A station whose address is a playlist, typed the way [MediaItemBuilder.buildPlayable]
         * types it.
         */
        fun stationItem(): MediaItem {
            val uri = Uri.parse("https://example.com/station.pls")
            return MediaItem.Builder()
                .setMediaId("station")
                .setUri(uri)
                .setMimeType(AppUtils.getMimeTypeFromUri(uri))
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Station").build())
                .build()
        }

        fun contentTypeOf(item: MediaItem): Int {
            val configuration = requireNotNull(item.localConfiguration)
            return Util.inferContentTypeForUriAndMimeType(configuration.uri, configuration.mimeType)
        }
    }
}
