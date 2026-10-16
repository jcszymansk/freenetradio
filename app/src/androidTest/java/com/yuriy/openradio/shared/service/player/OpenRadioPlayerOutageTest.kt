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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.service.LocalAudioFixture
import com.yuriy.openradio.shared.service.LoopbackHttpFixture
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import com.yuriy.openradio.testing.onMainThread
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
 *
 * Each wait is bounded by what ExoPlayer is configured to spend on the stream, not by how long it
 * took on one emulator, and waits for the playback state the player reports rather than for
 * [Player.isPlaying], which the device's audio focus decides as well. A wait that times out
 * reports what the player did meanwhile: under a full suite run this test once timed out with
 * nothing more to go on.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerOutageTest {

    private lateinit var mContext: Context

    private lateinit var mServer: LoopbackHttpFixture

    private lateinit var mBroadcast: LoopbackHttpFixture.Broadcast

    private lateinit var mPlayer: OpenRadioPlayer

    private val mEvents = CopyOnWriteArrayList<Event>()

    /**
     * Everything the player reported, with when it did, for the message of a wait that timed out.
     * [mEvents] keeps only what the assertions compare.
     */
    private val mTrace = CopyOnWriteArrayList<String>()

    private val mStartNanos = System.nanoTime()

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
        mPlayer = onMainThread {
            newOpenRadioPlayer(mContext).also { it.addListener(EventRecorder()) }
        }
    }

    @After
    fun tearDown() {
        onMainThread { mPlayer.release() }
        mServer.stop()
    }

    /**
     * The failure seen in the car, reproduced: the station goes silent without the connection
     * being closed or refused, ExoPlayer times out on it until it gives up, and the station has
     * to come back on its own once it is on the air again. Before the fix the player took the
     * timeout for a broken stream, not a lost network, and sat stopped for good.
     *
     * Coming back is the stream reaching the ready state with the play request still standing,
     * which is everything the player decides. Whether audio then comes out is the audio focus's
     * call, and another app may hold it.
     */
    @Test
    fun aStationResumesByItselfAfterAnOutageThatOutlastsExoPlayersOwnRetries() {
        playTheBroadcast()

        mBroadcast.goOffAir()
        trace("off the air")

        val error = awaitError(GIVE_UP_LIVE_SECONDS)
        assertEquals(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, error.errorCode
        )
        onMainThread {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertTrue("The player took the outage for a broken stream", mPlayer.resumesOnReconnect())
        }
        val connectionsAtGiveUp = mBroadcast.connections()
        await("the player to try the silent station again", RETRY_SECONDS) {
            mBroadcast.connections() > connectionsAtGiveUp
        }

        val mark = mEvents.size
        mBroadcast.goOnAir()
        trace("on the air")

        await("the station to be ready again", RESUME_SECONDS) {
            eventsSince(mark).contains(Event.State(Player.STATE_READY))
        }
        onMainThread {
            assertTrue("The play request did not survive the outage", mPlayer.playWhenReady)
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
        onMainThread { assertTrue(mPlayer.playWhenReady) }
    }

    @Test
    fun aStationTheUserPausesAfterTheNetworkStoppedItIsNotTriedAgain() {
        failForALostNetwork()

        onMainThread { mPlayer.pause() }
        val mark = mEvents.size

        Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_SECONDS))
        assertEquals(emptyList<Event>(), eventsSince(mark))
        onMainThread {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertFalse(mPlayer.resumesOnReconnect())
        }
    }

    @Test
    fun aStationTheUserStopsAfterTheNetworkStoppedItIsNotTriedAgain() {
        failForALostNetwork()

        onMainThread { mPlayer.stop() }
        val mark = mEvents.size

        Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_SECONDS))
        assertEquals(emptyList<Event>(), eventsSince(mark))
        onMainThread { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    /**
     * A policy pause keeps the station off the mobile network it paused it for, so the player
     * does not try it there again. The station is still the one to resume once a network it may
     * use connects, which the service does.
     */
    @Test
    fun aPolicyPauseOfAStationTheNetworkStoppedHoldsItsRetry() {
        failForALostNetwork()

        onMainThread { mPlayer.pauseForNetworkPolicy() }
        val mark = mEvents.size

        Thread.sleep(TimeUnit.SECONDS.toMillis(RETRY_SECONDS))
        assertEquals(emptyList<Event>(), eventsSince(mark))
        onMainThread {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertTrue("The policy pause forgot the station", mPlayer.resumesOnReconnect())
        }
    }

    /**
     * Plays a stream whose connection is refused and waits until the player gives it up for a lost
     * network.
     */
    private fun failForALostNetwork() {
        onMainThread {
            mPlayer.setMediaItem(MediaItem.fromUri(UNREACHABLE_STREAM))
            mPlayer.prepare()
            mPlayer.play()
        }
        val error = awaitError(GIVE_UP_SECONDS)
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, error.errorCode)
        onMainThread {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertTrue(mPlayer.resumesOnReconnect())
        }
    }

    private fun playTheBroadcast() {
        onMainThread {
            mPlayer.setMediaItem(MediaItem.fromUri(mBroadcast.url))
            mPlayer.prepare()
            mPlayer.play()
        }
        await("the broadcast to be ready", GIVE_UP_SECONDS) {
            mEvents.contains(Event.State(Player.STATE_READY))
        }
        onMainThread { assertTrue(mPlayer.playWhenReady) }
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
        fail("Timed out waiting for $what after $seconds s\n${describe()}")
    }

    private fun describe(): String {
        val state = onMainThread {
            "playbackState=${mPlayer.playbackState} playWhenReady=${mPlayer.playWhenReady} " +
                "suppression=${mPlayer.playbackSuppressionReason} isPlaying=${mPlayer.isPlaying} " +
                "resumesOnReconnect=${mPlayer.resumesOnReconnect()}"
        }
        return "$state connections=${mBroadcast.connections()} " +
            "open=${mBroadcast.openConnections()} bytesSent=${mBroadcast.bytesSent()}\n" +
            mTrace.joinToString("\n")
    }

    private fun trace(what: String) {
        val millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - mStartNanos)
        mTrace.add("$millis ms: $what (connections ${mBroadcast.connections()})")
    }

    private sealed class Event {

        data class State(val state: Int) : Event()

        data class Error(val error: PlaybackException) : Event()
    }

    private inner class EventRecorder : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            trace("state $playbackState")
            mEvents.add(Event.State(playbackState))
        }

        override fun onPlayerError(error: PlaybackException) {
            trace("error ${error.errorCodeName}")
            mEvents.add(Event.Error(error))
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            trace("playWhenReady $playWhenReady, reason $reason")
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            trace("suppression $playbackSuppressionReason")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            trace("isPlaying $isPlaying")
        }
    }

    private companion object {

        const val UNREACHABLE_STREAM = "$UNREACHABLE_ORIGIN/live.mp3"

        /**
         * How much longer than ExoPlayer's own budget a wait allows, for a device the rest of the
         * suite is slowing down. ExoPlayer's timeouts are wall-clock time, so a slow device
         * stretches only the work between them.
         */
        const val SLACK_FACTOR = 3

        /**
         * The longest pause ExoPlayer's default load error policy makes before a retry: a second
         * per failure so far, up to this.
         */
        const val MAX_RETRY_PAUSE_MILLIS = 5_000L

        /**
         * How long ExoPlayer keeps a silent live stream before it gives it up: every attempt the
         * default load error policy allows a live progressive stream waits out the data source's
         * read timeout, with the pauses between them. 71 seconds with Media3 1.2.1's defaults,
         * which the app does not change.
         */
        val GIVE_UP_LIVE_MILLIS = giveUpMillis(
            DefaultLoadErrorHandlingPolicy.DEFAULT_MIN_LOADABLE_RETRY_COUNT_PROGRESSIVE_LIVE
        )

        val GIVE_UP_LIVE_SECONDS = SLACK_FACTOR * toSeconds(GIVE_UP_LIVE_MILLIS)

        /**
         * Longer than the station takes to be ready once it is back on the air. The attempt that
         * found it silent times out and the next one plays it, ten seconds or so. This allows for
         * the worst case too: every attempt ExoPlayer makes in the player's first retry failing
         * until it gives the stream up, the player trying it again after its second delay, and
         * that attempt buffering enough to play. The schedule allows more retries than that, so
         * the station comes back well within its bound.
         */
        val RESUME_SECONDS = SLACK_FACTOR * toSeconds(
            GIVE_UP_LIVE_MILLIS
                + StreamRetrySchedule.DEFAULT_DELAYS_MS[1]
                + DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
        )

        /**
         * Longer than ExoPlayer keeps retrying a refused connection, a few seconds.
         */
        const val GIVE_UP_SECONDS = 30L

        /**
         * Comfortably longer than the player waits before it tries a stream the network stopped
         * again.
         */
        val RETRY_SECONDS =
            TimeUnit.MILLISECONDS.toSeconds(StreamRetrySchedule.DEFAULT_DELAYS_MS.first()) * 2

        const val POLL_MILLIS = 50L

        /**
         * How long ExoPlayer spends on a load that never gets an answer before it reports the
         * error: one attempt and [retries] more, each waiting out the read timeout, and the pause
         * the default load error policy makes before each retry.
         */
        fun giveUpMillis(retries: Int): Long {
            val attempts = DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS.toLong() * (retries + 1)
            val pauses = (1..retries).sumOf { failure ->
                minOf((failure - 1) * 1_000L, MAX_RETRY_PAUSE_MILLIS)
            }
            return attempts + pauses
        }

        fun toSeconds(millis: Long): Long {
            return TimeUnit.MILLISECONDS.toSeconds(millis + TimeUnit.SECONDS.toMillis(1) - 1)
        }
    }
}
