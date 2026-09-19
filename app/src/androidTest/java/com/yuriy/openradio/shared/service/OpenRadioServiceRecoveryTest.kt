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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.R
import com.yuriy.openradio.shared.model.media.RadioStation
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
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
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
     * rather than replacing the station's url with something it made up.
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
        awaitSettled()
        assertEquals(playlist, mBrowser.currentMediaItemUri())
        assertFalse(mBrowser.isPlaying())
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
     * With the device's networking disabled, a host outside the loopback address cannot be
     * resolved, which is the same failure a stream hits when the signal goes. The player has to
     * say so rather than report a broken stream, because that is the difference between a station
     * worth retrying and one that is gone.
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

    private fun plsPointingAt(url: String): String {
        return "[playlist]\nNumberOfEntries=1\nFile1=$url\nTitle1=Fixture\nLength1=-1\nVersion=2\n"
    }

    private companion object {

        const val EMPTY_PLS = "[playlist]\nNumberOfEntries=0\nVersion=2\n"

        /**
         * A host in a reserved top-level domain, so it cannot resolve even if the device is put
         * back on a network by mistake.
         */
        const val UNREACHABLE_STREAM = "http://stream.invalid/live.mp3"

        /**
         * Comfortably longer than `OpenRadioService.API_CALL_TIMEOUT_MS`, which bounds the
         * playlist resolution a failure kicks off.
         */
        const val SETTLE_MILLIS = 5_000L
    }
}
