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

import android.content.Context
import android.net.Uri
import androidx.core.util.Pair
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.net.DirectUrlResolver
import com.yuriy.openradio.shared.model.net.DownloaderLayer
import com.yuriy.openradio.shared.model.net.HTTPDownloaderImpl
import com.yuriy.openradio.shared.service.LoopbackHttpFixture
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What [NetUtils.extractUrlsFromPlaylist] answers, which is what
 * [com.yuriy.openradio.shared.service.OpenRadioService] acts on when a station url turns out to
 * be a playlist rather than a stream.
 *
 * The parsers themselves are covered on the JVM, in `AutoDetectParserTest`. What this adds is the
 * step above them: opening the url. That step is an `HttpURLConnection`, so it needs a server, and
 * [LoopbackHttpFixture] is one bound to the loopback address. The end-to-end effect of each answer
 * is in `OpenRadioServiceRecoveryTest`; this is where the answers themselves are pinned, because
 * the service acts on them from a background scope and a test that only watches the player cannot
 * tell which of them it acted on.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaylistResolutionTest {

    private lateinit var mContext: Context

    private lateinit var mServer: LoopbackHttpFixture

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mServer = LoopbackHttpFixture()
        mServer.start()
    }

    @After
    fun tearDown() {
        mServer.stop()
    }

    @Test
    fun aPlaylistResolvesToTheStreamItNames() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        val playlist = mServer.serve("/stream.pls", LoopbackHttpFixture.AUDIO_PLS, pls(stream))

        assertArrayEquals(arrayOf(stream), resolve(playlist))
    }

    @Test
    fun aPlaylistResolvesToEveryStreamItNames() {
        val first = mServer.serve("/first.mp3", AUDIO_MPEG, "not really audio")
        val second = mServer.serve("/second.mp3", AUDIO_MPEG, "not really audio")
        val playlist = mServer.serve(
            "/two.pls", LoopbackHttpFixture.AUDIO_PLS, pls(first, second)
        )

        assertArrayEquals(arrayOf(first, second), resolve(playlist))
    }

    @Test
    fun aPlaylistThatNamesNothingResolvesToNoUrls() {
        val playlist = mServer.serve(
            "/empty.pls", LoopbackHttpFixture.AUDIO_PLS, "[playlist]\nNumberOfEntries=0\nVersion=2\n"
        )

        assertEquals(0, resolve(playlist).size)
    }

    @Test
    fun aMalformedPlaylistResolvesToNoUrls() {
        val playlist = mServer.serve(
            "/broken.pls", LoopbackHttpFixture.AUDIO_PLS, "<html><body>not a playlist</body></html>"
        )

        assertEquals(0, resolve(playlist).size)
    }

    /**
     * A url that cannot be opened is answered with one empty string rather than with no urls, and
     * the two are indistinguishable to the caller. `handlePlayListUrlsExtracted` only asks whether
     * the answer is empty, so it puts this empty string on the station as its stream url, which is
     * TASK-043. This pins the answer as it stands so the fix has something to change.
     */
    @Test
    fun aPlaylistThatCannotBeOpenedResolvesToOneEmptyUrl() {
        assertArrayEquals(arrayOf(""), resolve("http://127.0.0.1:1/unreachable.pls"))
    }

    /**
     * A local file is the other way to reach that same answer:
     * [NetUtils.extractUrlsFromPlaylist] opens every url as an `HttpURLConnection`, so a url on
     * any other scheme fails before it is ever read.
     */
    @Test
    fun aPlaylistOnANonHttpSchemeResolvesToOneEmptyUrl() {
        assertArrayEquals(arrayOf(""), resolve("file:///does/not/matter.pls"))
    }

    /**
     * An ASX `ENTRYREF` is the one reference a playlist makes that is a playlist whatever its url
     * says. The referenced url here has no extension, so it is recognised by its content, and it is
     * read through the downloader handed in rather than by the parser.
     */
    @Test
    fun anAsxEntryRefIsFollowedThroughTheDownloader() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        val referenced = mServer.serve(
            "/referenced", LoopbackHttpFixture.TEXT_PLAIN, "<ASX><ENTRY><REF href=\"$stream\"/></ENTRY></ASX>"
        )
        val playlist = mServer.serve(
            "/station.asx", VIDEO_ASF, "<ASX version=\"3.0\"><ENTRYREF href=\"$referenced\"/></ASX>"
        )
        val downloader = RecordingDownloader(HTTPDownloaderImpl(DirectUrlResolver()))

        assertArrayEquals(arrayOf(stream), resolve(playlist, downloader))
        assertEquals(listOf(referenced), downloader.reads)
        assertEquals(listOf("/station.asx", "/referenced"), mServer.requestedPaths())
    }

    /**
     * A url that names a playlist can answer with a live stream, which never ends. The read is
     * bounded so that the process does not grow until it dies; a response over the limit is
     * refused whole, because half a playlist is not a shorter playlist.
     */
    @Test
    fun aReferencedPlaylistLongerThanTheLimitIsRefused() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        val underTheLimit = mServer.serve(
            "/short", LoopbackHttpFixture.TEXT_PLAIN, paddedAsx(stream, PADDING_UNDER_LIMIT)
        )
        val overTheLimit = mServer.serve(
            "/endless", LoopbackHttpFixture.TEXT_PLAIN, paddedAsx(stream, PADDING_OVER_LIMIT)
        )

        assertArrayEquals(arrayOf(stream), resolve(entryRefTo(underTheLimit, "/short.asx")))
        assertEquals(0, resolve(entryRefTo(overTheLimit, "/long.asx")).size)
    }

    private fun entryRefTo(reference: String, path: String): String {
        return mServer.serve(path, VIDEO_ASF, "<ASX version=\"3.0\"><ENTRYREF href=\"$reference\"/></ASX>")
    }

    /**
     * A playlist that names [stream] and is [padding] comments long. The padding sits inside the
     * root element, so the only thing separating the two fixtures is their size.
     */
    private fun paddedAsx(stream: String, padding: Int): String {
        return "<ASX version=\"3.0\"><ENTRY><REF href=\"$stream\"/></ENTRY>" +
                "<!-- padding -->".repeat(padding) + "</ASX>"
    }

    private fun resolve(
        url: String,
        downloader: DownloaderLayer = HTTPDownloaderImpl(DirectUrlResolver())
    ): Array<String> {
        return NetUtils.extractUrlsFromPlaylist(mContext, downloader, url)
    }

    /**
     * Passes every read on to [mDelegate] and records the url it was asked for.
     */
    private class RecordingDownloader(private val mDelegate: DownloaderLayer) : DownloaderLayer {

        val reads = CopyOnWriteArrayList<String>()

        override fun downloadDataFromUri(
            context: Context,
            uri: Uri,
            parameters: List<Pair<String, String>>,
            contentTypeFilter: String?,
            maxBytes: Int
        ): ByteArray {
            reads.add(uri.toString())
            return mDelegate.downloadDataFromUri(context, uri, parameters, contentTypeFilter, maxBytes)
        }
    }

    private fun pls(vararg urls: String): String {
        return buildString {
            append("[playlist]\n")
            append("NumberOfEntries=${urls.size}\n")
            for ((index, url) in urls.withIndex()) {
                append("File${index + 1}=$url\n")
                append("Title${index + 1}=Fixture ${index + 1}\n")
                append("Length${index + 1}=-1\n")
            }
            append("Version=2\n")
        }
    }

    private companion object {

        const val AUDIO_MPEG = "audio/mpeg"

        const val VIDEO_ASF = "video/x-ms-asf"

        /**
         * Comments of 16 bytes each, either side of the megabyte `NetUtils` allows a playlist.
         */
        const val PADDING_UNDER_LIMIT = 10_000

        const val PADDING_OVER_LIMIT = 100_000
    }
}
