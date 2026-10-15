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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.service.LocalAudioFixture
import com.yuriy.openradio.shared.service.LoopbackHttpFixture
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * What the player does when the network stops carrying a station for longer than ExoPlayer keeps
 * trying on its own (TASK-018).
 *
 * A brief outage never reaches the player: ExoPlayer retries the stream itself and the station
 * plays on. A long one outlasts those retries, ExoPlayer gives the stream up, and from then on
 * only the player can bring it back. The service also resumes it on a network-connected event,
 * but a phone that loses its signal in a dead zone usually keeps reporting the network it had,
 * so that event may never come. The player has to try the stream again by itself.
 *
 * The station is a [LoopbackHttpFixture.Broadcast] of MP3 frames, which, like a radio station's
 * stream, declares neither a length nor a duration, so ExoPlayer treats it as live and retries it
 * as it would a real one. The device's networking stays disabled throughout: the outage is the
 * broadcast going off the air, which is how a dead zone looks from the phone, and no
 * network-connected event is ever sent.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerOutageTest {

    private lateinit var mContext: Context

    private lateinit var mServer: LoopbackHttpFixture

    private lateinit var mBroadcast: LoopbackHttpFixture.Broadcast

    private lateinit var mPlayer: OpenRadioPlayer

    private val mEvents = CopyOnWriteArrayList<Event>()

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mServer = LoopbackHttpFixture()
        mServer.start()
        mBroadcast = mServer.broadcast(
            "/live.mp3",
            LoopbackHttpFixture.AUDIO_MPEG,
            ByteArray(0),
            LocalAudioFixture.MP3_BYTES_PER_SECOND,
            LocalAudioFixture(mContext).silentMp3Frame()
        )
        mPlayer = onMain {
            newOpenRadioPlayer(mContext).also { it.addListener(EventRecorder()) }
        }
    }

    @After
    fun tearDown() {
        onMain { mPlayer.release() }
        mServer.stop()
    }

    /**
     * The failure seen in the car, reproduced: the station goes silent without the connection
     * being closed or refused, ExoPlayer times out on it until it gives up, and the station has
     * to come back on its own once it is on the air again. Before the fix the player took the
     * timeout for a broken stream, not a lost network, and sat stopped for good.
     */
    @Test
    fun aStationResumesByItselfAfterAnOutageThatOutlastsExoPlayersOwnRetries() {
        playTheBroadcast()

        mBroadcast.goOffAir()

        val error = awaitError(OUTAGE_SECONDS)
        assertEquals(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, error.errorCode
        )
        onMain {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertTrue("The player took the outage for a broken stream", mPlayer.resumesOnReconnect())
        }
        val connectionsAtGiveUp = mBroadcast.connections()
        await("the player to try the silent station again", RETRY_SECONDS) {
            mBroadcast.connections() > connectionsAtGiveUp
        }

        mBroadcast.goOnAir()

        await("the station to play again", RETRY_SECONDS + OUTAGE_SECONDS) { onMain { mPlayer.isPlaying } }
        onMain {
            assertEquals(mBroadcast.url, mPlayer.currentMediaItem?.localConfiguration?.uri.toString())
            assertFalse(mPlayer.resumesOnReconnect())
        }
    }

    /**
     * The same retry for a connection that is refused rather than left unanswered, which is the
     * quick way for a stream to fail on a lost network: no network-connected event comes, and the
     * player prepares the stream again on its own.
     */
    @Test
    fun aStationTheNetworkStoppedIsTriedAgainWithoutANetworkEvent() {
        failForALostNetwork()
        val mark = mEvents.size

        await("the player to prepare the station again", RETRY_SECONDS) {
            eventsSince(mark).contains(Event.State(Player.STATE_BUFFERING))
        }
        onMain { assertTrue(mPlayer.playWhenReady) }
    }

    @Test
    fun aStationTheUserPausesAfterTheNetworkStoppedItIsNotTriedAgain() {
        failForALostNetwork()

        onMain { mPlayer.pause() }
        val mark = mEvents.size

        Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_SECONDS))
        assertEquals(emptyList<Event>(), eventsSince(mark))
        onMain {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertFalse(mPlayer.resumesOnReconnect())
        }
    }

    @Test
    fun aStationTheUserStopsAfterTheNetworkStoppedItIsNotTriedAgain() {
        failForALostNetwork()

        onMain { mPlayer.stop() }
        val mark = mEvents.size

        Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_SECONDS))
        assertEquals(emptyList<Event>(), eventsSince(mark))
        onMain { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    /**
     * A policy pause keeps the station off the mobile network it paused it for, so the player
     * does not try it there again. The station is still the one to resume once a network it may
     * use connects, which the service does.
     */
    @Test
    fun aPolicyPauseOfAStationTheNetworkStoppedHoldsItsRetry() {
        failForALostNetwork()

        onMain { mPlayer.pauseForNetworkPolicy() }
        val mark = mEvents.size

        Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_SECONDS))
        assertEquals(emptyList<Event>(), eventsSince(mark))
        onMain {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertTrue("The policy pause forgot the station", mPlayer.resumesOnReconnect())
        }
    }

    /**
     * Plays a stream whose connection is refused and waits until the player gives it up for a lost
     * network.
     */
    private fun failForALostNetwork() {
        onMain {
            mPlayer.setMediaItem(MediaItem.fromUri(UNREACHABLE_STREAM))
            mPlayer.prepare()
            mPlayer.play()
        }
        val error = awaitError(GIVE_UP_SECONDS)
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, error.errorCode)
        onMain {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertTrue(mPlayer.resumesOnReconnect())
        }
    }

    private fun playTheBroadcast() {
        onMain {
            mPlayer.setMediaItem(MediaItem.fromUri(mBroadcast.url))
            mPlayer.prepare()
            mPlayer.play()
        }
        await("the broadcast to play", GIVE_UP_SECONDS) { onMain { mPlayer.isPlaying } }
        mEvents.clear()
    }

    private fun awaitError(seconds: Long): PlaybackException {
        await("the player to give the stream up", seconds) {
            mEvents.any { it is Event.Error }
        }
        return mEvents.filterIsInstance<Event.Error>().first().error
    }

    private fun eventsSince(mark: Int): List<Event> {
        return mEvents.toList().drop(mark)
    }

    private fun await(what: String, seconds: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        fail("Timed out waiting for $what")
    }

    private sealed class Event {

        data class State(val state: Int) : Event()

        data class Error(val error: PlaybackException) : Event()
    }

    private inner class EventRecorder : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            mEvents.add(Event.State(playbackState))
        }

        override fun onPlayerError(error: PlaybackException) {
            mEvents.add(Event.Error(error))
        }
    }

    private companion object {

        const val UNREACHABLE_STREAM = "$UNREACHABLE_ORIGIN/live.mp3"

        /**
         * Longer than ExoPlayer keeps a silent live stream before giving it up: seven attempts that
         * each wait out the data source's eight-second timeout, and the pauses between them, which
         * took about 80 seconds on the emulator.
         */
        const val OUTAGE_SECONDS = 180L

        /**
         * Longer than ExoPlayer keeps retrying a refused connection, a few seconds.
         */
        const val GIVE_UP_SECONDS = 30L

        /**
         * Comfortably longer than the player waits before it tries a stream the network stopped
         * again.
         */
        val RETRY_SECONDS = TimeUnit.MILLISECONDS.toSeconds(OpenRadioPlayer.STREAM_RETRY_DELAY_MS) * 2

        const val POLL_MILLIS = 50L
    }
}
