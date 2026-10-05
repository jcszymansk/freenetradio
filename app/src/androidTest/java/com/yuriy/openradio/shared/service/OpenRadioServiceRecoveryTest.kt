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

package com.yuriy.openradio.shared.service

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.R
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers what the service does when a station url does not turn out to be a stream: a playlist it
 * has to resolve first, a server that refuses it, and a host that is not there at all.
 *
 * Two fixtures stand in for the internet. Most of it is local files, as in
 * [OpenRadioServicePlaybackTest]. The rest is [LoopbackHttpFixture], because the two paths here
 * that HTTP defines cannot exist without a server: a playlist is resolved by opening its url as an
 * `HttpURLConnection`, and a refused stream is classified from the status code it was refused
 * with. Both sockets stay on the loopback address, so the device's networking is disabled
 * throughout and nothing leaves it.
 *
 * What the player reports about a failure reaches a controller as the metadata subtitle, which is
 * why the assertions are written against it: it is the same string the phone UI and Android Auto
 * put under the station name.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioServiceRecoveryTest {

    private lateinit var mContext: Context

    private lateinit var mStorages: ServiceStorages

    private lateinit var mBrowser: ServiceBrowser

    private lateinit var mAudio: LocalAudioFixture

    private lateinit var mStations: LocalStationsFixture

    private lateinit var mServer: LoopbackHttpFixture

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mAudio = LocalAudioFixture(mContext)
        mServer = LoopbackHttpFixture()
        mServer.start()
        mStorages = ServiceStorages(mContext)
        mStorages.clear()
        mBrowser = ServiceBrowser()
        mStations = LocalStationsFixture(mStorages, mBrowser)
        mBrowser.connect()
        mBrowser.stop()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.forgetNotifications()
        mBrowser.forgetPlayerEvents()
    }

    @After
    fun tearDown() {
        mStations.parkThePlayer()
        mStorages.clear()
        if (mBrowser.isConnected()) {
            mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        }
        mBrowser.release()
        mAudio.delete()
        mServer.stop()
    }

    /**
     * Station urls in the directories are routinely playlists rather than streams. The player
     * cannot read one, so the service resolves it and plays what it points at, and the user never
     * learns the difference.
     */
    @Test
    fun aPlaylistUrlIsResolvedToTheStreamItPointsAt() {
        val stream = mServer.serve(
            "/fixture.wav", LoopbackHttpFixture.AUDIO_WAV, mAudio.wavBytes(seconds = 10)
        )
        val playlist = mServer.serve(
            "/stream.pls", LoopbackHttpFixture.AUDIO_PLS, plsPointingAt(stream)
        )

        selectAndPlay(mStations.seed(playlist).first())

        mBrowser.awaitPlayback("the playlist url to be replaced by the stream it names") {
            mBrowser.currentMediaItemUri() == stream
        }
        mBrowser.awaitPlaying()
        assertTrue(
            "The playlist was never fetched",
            mServer.requestedPaths().contains("/stream.pls")
        )
    }

    /**
     * A playlist that parses but names nothing leaves the service with no url to try, so it stops
     * rather than replacing the station's url with something it made up, and says so.
     */
    @Test
    fun aPlaylistWithNoEntriesStopsPlayback() {
        val playlist = mServer.serve(
            "/empty.pls", LoopbackHttpFixture.AUDIO_PLS, EMPTY_PLS
        )
        val station = mStations.seed(playlist).first()

        selectAndPlay(station)

        mBrowser.awaitPlayback("the empty playlist to be fetched") {
            mServer.requestedPaths().contains("/empty.pls")
        }
        mBrowser.awaitMetadataSubtitle(string(R.string.media_stream_unplayable))
        awaitSettled()
        assertEquals(playlist, mBrowser.currentMediaItemUri())
        assertFalse(mBrowser.isPlaying())
        assertEquals(READ_ONCE, requestsFor("/empty.pls"))
    }

    /**
     * A playlist whose stream the player cannot read either is not read again: it would only name
     * the same stream. The service gives up after trying what it named, and says so.
     */
    @Test
    fun aPlaylistWhoseStreamIsUnreadableIsResolvedOnce() {
        val noise = mServer.serve("/noise", LoopbackHttpFixture.TEXT_PLAIN, NOT_AUDIO)
        val playlist = mServer.serve(
            "/unreadable.pls", LoopbackHttpFixture.AUDIO_PLS, plsPointingAt(noise)
        )

        selectAndPlay(mStations.seed(playlist).first())

        mBrowser.awaitPlayback("the stream the playlist names to be tried") {
            mServer.requestedPaths().contains("/noise")
        }
        awaitSettled()
        assertEquals(READ_ONCE, requestsFor("/unreadable.pls"))
        mBrowser.awaitMetadataSubtitle(string(R.string.media_stream_unplayable))
        assertFalse(mBrowser.isPlaying())
    }

    /**
     * Playlists commonly list mirrors of one stream, so one that cannot be read is no reason to
     * give up while the playlist names another; and the next one is tried without reading the
     * playlist again.
     */
    @Test
    fun theNextStreamAPlaylistNamesIsTriedWhenTheFirstIsUnreadable() {
        val noise = mServer.serve("/noise", LoopbackHttpFixture.TEXT_PLAIN, NOT_AUDIO)
        val stream = mServer.serve(
            "/fixture.wav", LoopbackHttpFixture.AUDIO_WAV, mAudio.wavBytes(seconds = 10)
        )
        val playlist = mServer.serve(
            "/mirrors.pls", LoopbackHttpFixture.AUDIO_PLS, plsPointingAt(noise, stream)
        )

        selectAndPlay(mStations.seed(playlist).first())

        mBrowser.awaitPlayback("the second stream the playlist names to replace the first") {
            mBrowser.currentMediaItemUri() == stream
        }
        mBrowser.awaitPlaying()
        assertEquals(READ_ONCE, requestsFor("/mirrors.pls"))
    }

    /**
     * Giving up ends one play request, not the station: asking for it again is a new request and
     * reads its playlist again, because the station may have been fixed in the meantime.
     */
    @Test
    fun playingAStationAgainAfterGivingUpReadsItsPlaylistAgain() {
        val noise = mServer.serve("/noise", LoopbackHttpFixture.TEXT_PLAIN, NOT_AUDIO)
        val playlist = mServer.serve(
            "/again.pls", LoopbackHttpFixture.AUDIO_PLS, plsPointingAt(noise)
        )
        val station = mStations.seed(playlist).first()
        selectAndPlay(station)
        mBrowser.awaitMetadataSubtitle(string(R.string.media_stream_unplayable))
        awaitSettled()
        mBrowser.forgetPlayerEvents()

        mBrowser.prepareAndPlay()

        mBrowser.awaitPlayback("the playlist to be read for the second request") {
            requestsFor("/again.pls") == READ_ONCE + 1
        }
        mBrowser.awaitMetadataSubtitle(string(R.string.media_stream_unplayable))
        awaitSettled()
        assertEquals(READ_ONCE + 1, requestsFor("/again.pls"))
    }

    @Test
    fun aForbiddenStreamIsReportedAsForbidden() {
        val refused = mServer.refuse("/forbidden.mp3", LoopbackHttpFixture.HTTP_FORBIDDEN, "Forbidden")

        selectAndPlay(mStations.seed(refused).first())

        mBrowser.awaitMetadataSubtitle(string(R.string.media_stream_http_403))
    }

    @Test
    fun aMissingStreamIsReportedAsNotFound() {
        val refused = mServer.refuse("/missing.mp3", LoopbackHttpFixture.HTTP_NOT_FOUND, "Not Found")

        selectAndPlay(mStations.seed(refused).first())

        mBrowser.awaitMetadataSubtitle(string(R.string.media_stream_http_404))
    }

    /**
     * A stream whose connection cannot be made fails the way one does when the signal goes: with
     * a connection failure rather than a response. The player has to say so rather than report a
     * broken stream, because that is the difference between a station worth retrying and one that
     * is gone.
     */
    @Test
    fun aStreamOnAnUnreachableHostIsReportedAsALostNetwork() {
        selectAndPlay(mStations.seed(UNREACHABLE_STREAM).first())

        mBrowser.awaitMetadataSubtitle(string(R.string.media_stream_network_failed))
        assertFalse(mBrowser.isPlaying())
    }

    /**
     * Losing one station must not cost the user the rest of the list: the queue survives the
     * failure, and the station next to it still plays.
     */
    @Test
    fun aStationRecoversByMovingToOneThatPlays() {
        val stream = mServer.serve(
            "/fixture.wav", LoopbackHttpFixture.AUDIO_WAV, mAudio.wavBytes(seconds = 10)
        )
        val stations = mStations.seed(UNREACHABLE_STREAM, stream)

        selectAndPlay(stations[0])
        mBrowser.awaitMetadataSubtitle(string(R.string.media_stream_network_failed))

        assertEquals(2, mBrowser.mediaItemCount())

        selectAndPlay(stations[1])
        mBrowser.awaitPlaying()
        assertEquals(stations[1].id, mBrowser.currentMediaId())
    }

    /**
     * A network change is also pushed in from the settings UI, and it re-asks the same question
     * playback started under. On a network the user allows, the answer is no and a station that
     * is playing has to keep playing: the command is sent whenever the setting is touched, not
     * only when it turns streaming off.
     */
    @Test
    fun aNetworkChangeLeavesAnAllowedStreamPlaying() {
        val stream = mServer.serve(
            "/fixture.wav", LoopbackHttpFixture.AUDIO_WAV, mAudio.wavBytes(seconds = 10)
        )
        val station = mStations.seed(stream).first()

        selectAndPlay(station)
        mBrowser.awaitPlaying()

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(OpenRadioService.CMD_NET_CHANGED).resultCode
        )

        awaitSettled()
        assertTrue("A permitted network change stopped playback", mBrowser.isPlaying())
        assertEquals(station.id, mBrowser.currentMediaId())
    }

    private fun selectAndPlay(station: RadioStation) {
        mBrowser.setMediaItem(mStations.item(station))
        mBrowser.prepareAndPlay()
    }

    /**
     * Waits out the window in which the service could still act on a failure it is handling on a
     * background scope, for the assertions that are about something *not* happening.
     */
    private fun awaitSettled() {
        Thread.sleep(SETTLE_MILLIS)
    }

    private fun string(id: Int): String {
        return mContext.getString(id)
    }

    private fun plsPointingAt(vararg urls: String): String {
        val entries = urls.withIndex().joinToString("") { (index, url) ->
            val number = index + 1
            "File$number=$url\nTitle$number=Fixture\nLength$number=-1\n"
        }
        return "[playlist]\nNumberOfEntries=${urls.size}\n${entries}Version=2\n"
    }

    private fun requestsFor(path: String): Int {
        return mServer.requestedPaths().count { it == path }
    }

    private companion object {

        const val EMPTY_PLS = "[playlist]\nNumberOfEntries=0\nVersion=2\n"

        /**
         * A body no extractor recognises, which is how a stream the player cannot read fails.
         */
        const val NOT_AUDIO = "not really audio, only text that no extractor will recognise"

        const val UNREACHABLE_STREAM = "$UNREACHABLE_ORIGIN/live.mp3"

        /**
         * Requests for a station playlist that is resolved once: the player opens the station url
         * as a stream first, and the service then reads it as a playlist. A later play request
         * starts from the stream that replaced it, so only the service's read adds to this.
         */
        const val READ_ONCE = 2

        /**
         * Comfortably longer than a playlist resolution over the loopback takes, so an assertion
         * that something did not happen also covers what a late resolution could still do.
         */
        const val SETTLE_MILLIS = 5_000L
    }
}
