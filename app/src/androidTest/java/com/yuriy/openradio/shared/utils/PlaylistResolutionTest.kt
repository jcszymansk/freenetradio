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
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
        assertArrayEquals(arrayOf(""), resolve("$UNREACHABLE_ORIGIN/unreachable.pls"))
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

    /**
     * Reading a playlist is part of playing the station, so on a network playback may not use it
     * does not even open the station url.
     */
    @Test
    fun aPlaylistIsNotOpenedWhileTheNetworkMayNotBeUsed() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        val playlist = mServer.serve("/stream.pls", LoopbackHttpFixture.AUDIO_PLS, pls(stream))
        val downloader = RecordingDownloader(HTTPDownloaderImpl(DirectUrlResolver()))

        assertEquals(PlaylistRead.Blocked, read(playlist, downloader) { false })
        assertEquals(emptyList<String>(), mServer.requestedPaths())
        assertEquals(emptyList<String>(), downloader.reads)
    }

    /**
     * The downloader reads every playlist the station's names, and makes no request of its own
     * while the network may not be used.
     */
    @Test
    fun theDownloaderMakesNoRequestWhileTheNetworkMayNotBeUsed() {
        val referenced = mServer.serve("/referenced", LoopbackHttpFixture.TEXT_PLAIN, "<ASX></ASX>")

        val content = HTTPDownloaderImpl(DirectUrlResolver())
            .downloadDataFromUri(mContext, Uri.parse(referenced), permit = NetworkPermit { false })

        assertEquals(0, content.size)
        assertEquals(emptyList<String>(), mServer.requestedPaths())
    }

    /**
     * The network can become one playback may not use while the station url is still being read,
     * and a station url can answer with something that never ends. The read stops there, rather
     * than at the end of the response, and the server sees the connection close.
     */
    @Test
    fun aStationUrlBeingReadStopsOnceTheNetworkMayNotBeUsed() {
        val endless = mServer.broadcast(
            "/endless.pls", LoopbackHttpFixture.AUDIO_PLS, "[playlist]\n".toByteArray(), BYTES_PER_SECOND
        )

        val answer = readUntilBarred(endless) { mayUseNetwork ->
            read(endless.url) { mayUseNetwork.get() }
        }

        assertEquals(PlaylistRead.Blocked, answer)
        assertTransferEnded(endless)
    }

    /**
     * The same for a playlist the station's names, which the downloader reads: the transfer stops
     * once the network may not be used, and its partial content is not answered as the playlist.
     */
    @Test
    fun aPlaylistItNamesBeingReadStopsOnceTheNetworkMayNotBeUsed() {
        val endless = mServer.broadcast(
            "/endless", LoopbackHttpFixture.TEXT_PLAIN, "<ASX><ENTRY>".toByteArray(), BYTES_PER_SECOND
        )
        val playlist = entryRefTo(endless.url, "/station.asx")

        val answer = readUntilBarred(endless) { mayUseNetwork ->
            read(playlist) { mayUseNetwork.get() }
        }

        assertEquals(PlaylistRead.Blocked, answer)
        assertTransferEnded(endless)
    }

    /**
     * The network refuses the playlist the station's names, and is usable again by the time the
     * read looks back. The refusal still counts: without the playlist it references, the station's
     * names nothing, and answering that as an empty playlist would give up on the station rather
     * than pause it. The predicate refuses only on the downloader's threads, which is the race.
     */
    @Test
    fun aPlaylistItNamesThatTheNetworkRefusedIsBlockedEvenOnceTheNetworkIsUsableAgain() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        val referenced = mServer.serve(
            "/referenced", LoopbackHttpFixture.TEXT_PLAIN, "<ASX><ENTRY><REF href=\"$stream\"/></ENTRY></ASX>"
        )
        val playlist = entryRefTo(referenced, "/station.asx")
        val downloader = RecordingDownloader(HTTPDownloaderImpl(DirectUrlResolver()))
        val reader = Thread.currentThread()

        val answer = read(playlist, downloader) { Thread.currentThread() == reader }

        assertEquals(PlaylistRead.Blocked, answer)
        assertEquals(listOf(referenced), downloader.reads)
        assertEquals(listOf("/station.asx"), mServer.requestedPaths())
    }

    /**
     * Station urls commonly redirect to where the playlist is, with absolute and relative
     * locations alike; the read follows every hop.
     */
    @Test
    fun aRedirectedStationUrlIsFollowedToItsPlaylist() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        mServer.serve("/stream.pls", LoopbackHttpFixture.AUDIO_PLS, pls(stream))
        mServer.redirect("/moved.pls", LoopbackHttpFixture.HTTP_TEMPORARY_REDIRECT, "/stream.pls")
        val station = mServer.redirect(
            "/station.pls", LoopbackHttpFixture.HTTP_FOUND, mServer.url("/moved.pls")
        )

        assertArrayEquals(arrayOf(stream), resolve(station))
        assertEquals(listOf("/station.pls", "/moved.pls", "/stream.pls"), mServer.requestedPaths())
    }

    /**
     * A redirect is a new request, so it is not sent once the network may not be used, even
     * though the url that answered with it was allowed.
     */
    @Test
    fun aRedirectOfTheStationUrlIsNotFollowedOnceTheNetworkMayNotBeUsed() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        mServer.serve("/stream.pls", LoopbackHttpFixture.AUDIO_PLS, pls(stream))
        val station = mServer.redirect("/station.pls", LoopbackHttpFixture.HTTP_FOUND, "/stream.pls")

        val answer = read(station) { mServer.requestedPaths().isEmpty() }

        assertEquals(PlaylistRead.Blocked, answer)
        assertEquals(listOf("/station.pls"), mServer.requestedPaths())
    }

    /**
     * The same for a playlist the station's names, which the downloader opens.
     */
    @Test
    fun aRedirectOfAPlaylistItNamesIsNotFollowedOnceTheNetworkMayNotBeUsed() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        mServer.serve(
            "/referenced", LoopbackHttpFixture.TEXT_PLAIN, "<ASX><ENTRY><REF href=\"$stream\"/></ENTRY></ASX>"
        )
        val moved = mServer.redirect("/moved", LoopbackHttpFixture.HTTP_FOUND, "/referenced")
        val playlist = entryRefTo(moved, "/station.asx")

        val answer = read(playlist) { mServer.requestedPaths().contains("/moved").not() }

        assertEquals(PlaylistRead.Blocked, answer)
        assertEquals(listOf("/station.asx", "/moved"), mServer.requestedPaths())
    }

    /**
     * A server can stop answering in the middle of a playlist, and a read waiting on it receives
     * nothing that a check before the next read could stop. Revoking the permit, which the
     * service does when the policy pauses the station, disconnects it while it waits.
     */
    @Test
    fun revokingThePermitDisconnectsAStationUrlWhoseBodyIsHeldBack() {
        val stall = mServer.stall(
            "/stalled.pls", LoopbackHttpFixture.AUDIO_PLS, "[playlist]\n".toByteArray(), isHeadSent = true
        )

        assertEquals(PlaylistRead.Blocked, revokeWhileWaiting(stall, stall.url))
        assertStallEnded(stall)
    }

    @Test
    fun revokingThePermitDisconnectsAStationUrlWaitingForItsResponse() {
        val stall = mServer.stall("/silent.pls", LoopbackHttpFixture.AUDIO_PLS, ByteArray(0), isHeadSent = false)

        assertEquals(PlaylistRead.Blocked, revokeWhileWaiting(stall, stall.url))
        assertStallEnded(stall)
    }

    /**
     * The same for a playlist the station's names, which the downloader waits on from its own
     * thread.
     */
    @Test
    fun revokingThePermitDisconnectsAPlaylistItNamesThatIsHeldBack() {
        val stall = mServer.stall("/silent", LoopbackHttpFixture.TEXT_PLAIN, ByteArray(0), isHeadSent = false)
        val playlist = entryRefTo(stall.url, "/station.asx")

        assertEquals(PlaylistRead.Blocked, revokeWhileWaiting(stall, playlist))
        assertStallEnded(stall)
    }

    /**
     * The policy pause can revoke the permit after a request was allowed and before its
     * connection connects. A disconnect then does nothing, so the request must not be made at
     * all: the permit is revoked here from inside the check that allows it.
     */
    @Test
    fun aRequestWhosePermitIsRevokedBeforeItConnectsIsNotMade() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        val playlist = mServer.serve("/stream.pls", LoopbackHttpFixture.AUDIO_PLS, pls(stream))
        lateinit var permit: NetworkPermit
        permit = NetworkPermit {
            permit.revoke()
            true
        }

        val answer = NetUtils.extractUrlsFromPlaylist(
            mContext, HTTPDownloaderImpl(DirectUrlResolver()), playlist, permit
        )

        assertEquals(PlaylistRead.Blocked, answer)
        assertEquals(0, mServer.acceptedConnections())
    }

    /**
     * The network can turn between the check that allows a request and the start of its
     * connection, before the service has revoked anything. The start asks again, so no connection
     * is opened.
     */
    @Test
    fun aRequestIsNotMadeWhenTheNetworkTurnsBeforeItsConnectionStarts() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        val playlist = mServer.serve("/stream.pls", LoopbackHttpFixture.AUDIO_PLS, pls(stream))
        val answers = ArrayDeque(listOf(true, false))

        val answer = read(playlist) { answers.removeFirst() }

        assertEquals(PlaylistRead.Blocked, answer)
        assertEquals(0, mServer.acceptedConnections())
    }

    /**
     * Reads [url] on its own thread until [stall] holds a connection from it, revokes the permit
     * the way the service does, and returns what the read answered.
     */
    private fun revokeWhileWaiting(stall: LoopbackHttpFixture.Stall, url: String): PlaylistRead {
        val permit = NetworkPermit { true }
        val reader = Executors.newSingleThreadExecutor()
        try {
            val answer = reader.submit(Callable {
                NetUtils.extractUrlsFromPlaylist(mContext, HTTPDownloaderImpl(DirectUrlResolver()), url, permit)
            })
            await("the read to wait on the stalled server") { stall.openConnections() == 1 }
            Thread.sleep(WAITING_MILLIS)
            assertFalse("The read ended before the permit was revoked", answer.isDone)
            permit.revoke()
            return answer.get(REVOKED_READ_MILLIS, TimeUnit.MILLISECONDS)
        } finally {
            reader.shutdownNow()
        }
    }

    private fun assertStallEnded(stall: LoopbackHttpFixture.Stall) {
        await("the stalled connection to close") { stall.openConnections() == 0 }
        assertEquals(1, stall.connections())
    }

    /**
     * Runs [read] on its own thread while [broadcast] sends, bars the network once the transfer is
     * under way, and returns what the read answered.
     */
    private fun readUntilBarred(
        broadcast: LoopbackHttpFixture.Broadcast,
        read: (AtomicBoolean) -> PlaylistRead
    ): PlaylistRead {
        val mayUseNetwork = AtomicBoolean(true)
        val reader = Executors.newSingleThreadExecutor()
        try {
            val answer = reader.submit(Callable { read(mayUseNetwork) })
            await("the transfer to be under way") { broadcast.bytesSent() > TRANSFER_UNDER_WAY_BYTES }
            assertFalse("The read ended before the network was barred", answer.isDone)
            mayUseNetwork.set(false)
            return answer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            reader.shutdownNow()
        }
    }

    /**
     * Once the server notices the connection is gone it writes nothing more, so the count that
     * matters is the one after the connection closed, held for many of the broadcast's chunks.
     */
    private fun assertTransferEnded(broadcast: LoopbackHttpFixture.Broadcast) {
        await("the connection to the broadcast to close") { broadcast.openConnections() == 0 }
        val sent = broadcast.bytesSent()
        Thread.sleep(QUIET_MILLIS)
        assertEquals("Bytes were sent after the network was barred", sent, broadcast.bytesSent())
        assertEquals(1, broadcast.connections())
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        fail("Timed out waiting for $what")
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

    /**
     * Reads [url] on a network playback may use, where every read runs and answers with urls.
     */
    private fun resolve(
        url: String,
        downloader: DownloaderLayer = HTTPDownloaderImpl(DirectUrlResolver())
    ): Array<String> {
        return when (val answer = read(url, downloader) { true }) {
            is PlaylistRead.Urls -> answer.urls.toTypedArray()
            PlaylistRead.Blocked -> throw AssertionError("A read on a usable network was blocked")
        }
    }

    private fun read(
        url: String,
        downloader: DownloaderLayer = HTTPDownloaderImpl(DirectUrlResolver()),
        mayUseNetwork: () -> Boolean
    ): PlaylistRead {
        return NetUtils.extractUrlsFromPlaylist(mContext, downloader, url, NetworkPermit(mayUseNetwork))
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
            maxBytes: Int,
            permit: NetworkPermit
        ): ByteArray {
            reads.add(uri.toString())
            return mDelegate.downloadDataFromUri(context, uri, parameters, contentTypeFilter, maxBytes, permit)
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

        /**
         * Slow enough that a megabyte would take a minute, so a read that is not stopped is still
         * under way when the test looks.
         */
        const val BYTES_PER_SECOND = 16_000

        /**
         * A few of the broadcast's chunks past its head.
         */
        const val TRANSFER_UNDER_WAY_BYTES = 2_000L

        const val TIMEOUT_SECONDS = 15L

        /**
         * Long enough for the read to be waiting on the socket, and with [REVOKED_READ_MILLIS]
         * still well inside the connections' two-second read timeout, so a read that only ended by
         * timing out fails the test.
         */
        const val WAITING_MILLIS = 500L

        const val REVOKED_READ_MILLIS = 500L

        const val POLL_MILLIS = 50L

        /**
         * Many times the 20 milliseconds between the broadcast's chunks.
         */
        const val QUIET_MILLIS = 2_000L

        const val VIDEO_ASF = "video/x-ms-asf"

        /**
         * Comments of 16 bytes each, either side of the megabyte `NetUtils` allows a playlist.
         */
        const val PADDING_UNDER_LIMIT = 10_000

        const val PADDING_OVER_LIMIT = 100_000
    }
}
