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

package com.yuriy.openradio.shared.service.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.service.LocalAudioFixture
import com.yuriy.openradio.shared.service.LoopbackHttpFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Whether the stream the service found for a station it could not read starts playing.
 *
 * The service stops the player when it cannot read a station url, reads the url as a playlist,
 * and puts the stream the playlist names in its place. The service also pauses the player itself,
 * not through a controller, when audio becomes noisy or the Bluetooth device disconnects, and
 * such a pause does not start a new play request: the read goes on and still answers (TASK-105).
 * Neither broadcast can be sent by a test, because only the system may send them, so this drives
 * [OpenRadioPlayer] directly with the calls the service makes, and [OpenRadioPlayer.pause] is the
 * call both broadcasts end in.
 *
 * The stream is a [LoopbackHttpFixture.Broadcast], which counts the connections made to it, so
 * whether the player started it is measured by the server rather than by the player's own word.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerReplacementStreamTest {

    private lateinit var mContext: Context

    private lateinit var mServer: LoopbackHttpFixture

    private lateinit var mBroadcast: LoopbackHttpFixture.Broadcast

    private lateinit var mPlayer: OpenRadioPlayer

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mServer = LoopbackHttpFixture()
        mServer.start()
        mBroadcast = mServer.broadcast(
            "/live.wav",
            LoopbackHttpFixture.AUDIO_WAV,
            LocalAudioFixture(mContext).endlessWavHeader(),
            LocalAudioFixture.BYTES_PER_SECOND
        )
        mPlayer = onMain {
            newOpenRadioPlayer(mContext)
        }
    }

    @After
    fun tearDown() {
        onMain { mPlayer.release() }
        mServer.stop()
    }

    @Test
    fun theReplacementOfAStationStillMeantToPlayIsStarted() {
        failOnAnUnreadableStream()
        onMain { mPlayer.stopForReplacementStream() }

        val start = onMain { mPlayer.replaceAwaitedStream(MediaItem.fromUri(mBroadcast.url)) }

        assertTrue("A station meant to play was held", start)
        assertCurrentItemIsTheBroadcast()
    }

    @Test
    fun aStationPausedWhileItsReplacementIsFoundStaysPaused() {
        failOnAnUnreadableStream()
        onMain { mPlayer.stopForReplacementStream() }
        onMain { mPlayer.pause() }

        val start = onMain { mPlayer.replaceAwaitedStream(MediaItem.fromUri(mBroadcast.url)) }

        assertFalse("A paused station was told to start its replacement", start)
        assertTheBroadcastIsNotStarted()
    }

    /**
     * The playlist is read once per play request, so the stream it named stays the station's:
     * playing the station again plays that stream and has nothing to read.
     */
    @Test
    fun aStationPausedWhileItsReplacementIsFoundKeepsTheReplacement() {
        failOnAnUnreadableStream()
        onMain { mPlayer.stopForReplacementStream() }
        onMain { mPlayer.pause() }
        onMain { mPlayer.replaceAwaitedStream(MediaItem.fromUri(mBroadcast.url)) }

        assertCurrentItemIsTheBroadcast()
        onMain {
            mPlayer.prepare()
            mPlayer.play()
        }
        await("the kept replacement to play") { onMain { mPlayer.isPlaying } }
        assertEquals(1, mBroadcast.connections())
    }

    /**
     * When a stream a playlist named fails, the service stops the player for the next one only
     * then, so a pause that landed earlier, while the failing stream was being opened, comes
     * before that stop.
     */
    @Test
    fun aStationPausedBeforeItsStreamFailedHoldsTheNextOne() {
        failOnAnUnreadableStream()
        onMain { mPlayer.pause() }
        onMain { mPlayer.stopForReplacementStream() }

        val start = onMain { mPlayer.replaceAwaitedStream(MediaItem.fromUri(mBroadcast.url)) }

        assertFalse("A paused station was told to start its next stream", start)
        assertTheBroadcastIsNotStarted()
    }

    /**
     * Plays a url whose body no extractor recognises, which is how the player fails on a station
     * url that is a playlist, and waits for that failure.
     */
    private fun failOnAnUnreadableStream() {
        val noise = mServer.serve("/noise", LoopbackHttpFixture.TEXT_PLAIN, NOT_AUDIO)
        onMain {
            mPlayer.setMediaItem(MediaItem.fromUri(noise))
            mPlayer.prepare()
            mPlayer.play()
        }
        await("the player to fail on the unreadable stream") { onMain { mPlayer.playerError != null } }
        onMain {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertTrue(mPlayer.playWhenReady)
        }
    }

    private fun assertCurrentItemIsTheBroadcast() {
        onMain {
            assertEquals(1, mPlayer.mediaItemCount)
            assertEquals(mBroadcast.url, mPlayer.currentMediaItem?.localConfiguration?.uri.toString())
        }
    }

    /**
     * The service prepares a replacement only when told to start it, so a held one has to be in
     * place, unprepared, with nothing asked of its server.
     */
    private fun assertTheBroadcastIsNotStarted() {
        assertCurrentItemIsTheBroadcast()
        Thread.sleep(QUIET_MILLIS)
        assertEquals(0, mBroadcast.connections())
        onMain {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertFalse(mPlayer.playWhenReady)
        }
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

    private companion object {

        const val TIMEOUT_SECONDS = 15L

        const val NOT_AUDIO = "not really audio, only text that no extractor will recognise"

        const val POLL_MILLIS = 50L

        /**
         * Long enough for a player that was started to have opened its connection.
         */
        const val QUIET_MILLIS = 2_000L
    }
}
