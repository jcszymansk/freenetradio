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
import com.yuriy.openradio.testing.onMainThread
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
 * What the mobile-network policy pause does to the connection a station streams over.
 *
 * The policy exists to keep playback off mobile data, so the measure is the server's: whether it
 * still has a connection to write to, and whether anything more is written to it. The station is
 * a [LoopbackHttpFixture.Broadcast], which, like a radio station, sends a stream with no end at
 * the rate it plays, so a player that keeps loading keeps receiving. The service cannot be told
 * that the device is on blocked mobile data without one, which is why this drives
 * [OpenRadioPlayer] directly with the call the service makes.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerPolicyPauseTest {

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
        mPlayer = onMainThread {
            newOpenRadioPlayer(mContext)
        }
    }

    @After
    fun tearDown() {
        onMainThread { mPlayer.release() }
        mServer.stop()
    }

    @Test
    fun aPolicyPauseClosesTheConnectionAndSendsNothingMore() {
        playTheBroadcast()

        onMainThread { mPlayer.pauseForNetworkPolicy() }

        awaitConnectionsClosed()
        assertNothingMoreIsSent()
        onMainThread {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertFalse(mPlayer.isLoading)
        }
    }

    /**
     * How many connections the resume opens is the player's business. The stop keeps the
     * position, and the WAV the broadcast sends declares a length, so the player may treat it as
     * seekable: when the kept position is past the start, it asks for that position again over a
     * connection of its own, then drops the one it started on (TASK-106). What the policy pause
     * promises is that the station plays over one connection, and that the connection is new,
     * since the old one was closed before the resume.
     *
     * So the resume makes one or two connections, never more: on top of the one closed by the
     * pause, the broadcast sees two or three in all. The upper bound is what catches a resume
     * that reconnects in a loop and leaves one connection open, and the count is read again after
     * [QUIET_MILLIS] so that a loop slower than the wait for a single open connection fails too.
     */
    @Test
    fun aStationThePolicyPausedPlaysAgainOverANewConnectionWhenResumed() {
        playTheBroadcast()
        onMainThread { mPlayer.pauseForNetworkPolicy() }
        awaitConnectionsClosed()
        assertEquals(1, mBroadcast.connections())

        onMainThread {
            assertTrue("The policy pause forgot the station", mPlayer.resumesOnReconnect())
            assertEquals(1, mPlayer.mediaItemCount)
            assertEquals(mBroadcast.url, mPlayer.currentMediaItem?.localConfiguration?.uri.toString())
            mPlayer.prepare()
            mPlayer.play()
        }

        awaitPlaying()
        await("the player to keep a single connection to the broadcast") {
            mBroadcast.openConnections() == 1
        }
        Thread.sleep(QUIET_MILLIS)
        assertEquals(1, mBroadcast.openConnections())
        val connections = mBroadcast.connections()
        assertTrue(
            "The resume made ${connections - 1} connections, not one or two",
            connections in RESUMED_CONNECTIONS
        )
        onMainThread { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    @Test
    fun aPolicyPauseOfAStationTheUserPausedClosesTheConnectionAndKeepsItPaused() {
        playTheBroadcast()
        onMainThread { mPlayer.pause() }

        onMainThread { mPlayer.pauseForNetworkPolicy() }

        awaitConnectionsClosed()
        assertNothingMoreIsSent()
        onMainThread { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    @Test
    fun aRepeatedPolicyPauseKeepsTheStationToResume() {
        playTheBroadcast()
        onMainThread { mPlayer.pauseForNetworkPolicy() }
        awaitConnectionsClosed()

        onMainThread { mPlayer.pauseForNetworkPolicy() }

        assertNothingMoreIsSent()
        assertEquals(1, mBroadcast.connections())
        onMainThread { assertTrue(mPlayer.resumesOnReconnect()) }
    }

    /**
     * Why the policy pause stops the player rather than pausing it: a paused player keeps the
     * connection open and keeps reading the broadcast. This is also what shows the fixture can
     * tell the two apart, so the cases above cannot pass for a reason other than the stop.
     */
    @Test
    fun aPauseAloneKeepsReadingTheBroadcast() {
        playTheBroadcast()

        onMainThread { mPlayer.pause() }
        val sentAtPause = mBroadcast.bytesSent()
        Thread.sleep(QUIET_MILLIS)

        assertEquals(1, mBroadcast.openConnections())
        assertTrue(
            "A paused player stopped reading at $sentAtPause bytes",
            mBroadcast.bytesSent() > sentAtPause
        )
    }

    /**
     * The service stops the player when it cannot read a station url, and reads the url as a
     * playlist. The policy can pause the station before the stream the playlist names starts; the
     * user never stopped it, so it resumes, with the stream the playlist named.
     */
    @Test
    fun aPolicyPauseWhileAReplacementStreamIsFoundResumesTheStation() {
        failOnAnUnreadableStream()
        onMainThread {
            mPlayer.stopForReplacementStream()
            assertFalse(mPlayer.resumesOnReconnect())
        }

        onMainThread { mPlayer.pauseForNetworkPolicy() }

        onMainThread {
            assertTrue("The policy pause forgot the station", mPlayer.resumesOnReconnect())
            mPlayer.replaceMediaItem(0, MediaItem.fromUri(mBroadcast.url))
            mPlayer.prepare()
            mPlayer.play()
        }
        awaitPlaying()
        onMainThread { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    /**
     * The stop for a replacement stream is the service's, not the user's, so it leaves a station
     * the network stopped to be resumed. [Player.stop] is the user's, and forgets it.
     */
    @Test
    fun aStopForAReplacementStreamKeepsAStationThePolicyPaused() {
        playTheBroadcast()
        onMainThread { mPlayer.pauseForNetworkPolicy() }

        onMainThread { mPlayer.stopForReplacementStream() }
        onMainThread { assertTrue(mPlayer.resumesOnReconnect()) }

        onMainThread { mPlayer.stop() }
        onMainThread { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    @Test
    fun aPolicyPauseAfterTheUserStoppedAStationAwaitingAReplacementDoesNotResumeIt() {
        failOnAnUnreadableStream()
        onMainThread {
            mPlayer.stopForReplacementStream()
            mPlayer.stop()
        }

        onMainThread { mPlayer.pauseForNetworkPolicy() }

        onMainThread { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    @Test
    fun aPolicyPauseAfterTheUserPausedAStationAwaitingAReplacementDoesNotResumeIt() {
        failOnAnUnreadableStream()
        onMainThread {
            mPlayer.stopForReplacementStream()
            mPlayer.pause()
        }

        onMainThread { mPlayer.pauseForNetworkPolicy() }

        onMainThread { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    /**
     * Another station chosen in its place is no longer awaiting anything, and until it is
     * prepared nothing is meant to be playing.
     */
    @Test
    fun aPolicyPauseAfterAnotherStationWasSetInPlaceOfOneAwaitingAReplacementDoesNotResumeIt() {
        failOnAnUnreadableStream()
        onMainThread {
            mPlayer.stopForReplacementStream()
            mPlayer.setMediaItem(MediaItem.fromUri(mBroadcast.url))
        }

        onMainThread { mPlayer.pauseForNetworkPolicy() }

        onMainThread { assertFalse(mPlayer.resumesOnReconnect()) }
        assertEquals(0, mBroadcast.connections())
    }

    /**
     * A policy pause is resolved once: a second one, before anything prepared the station again,
     * finds it paused rather than awaiting a stream, and keeps it to resume.
     */
    @Test
    fun aRepeatedPolicyPauseOfAStationAwaitingAReplacementKeepsItToResume() {
        failOnAnUnreadableStream()
        onMainThread { mPlayer.stopForReplacementStream() }
        onMainThread { mPlayer.pauseForNetworkPolicy() }

        onMainThread { mPlayer.pauseForNetworkPolicy() }

        onMainThread { assertTrue(mPlayer.resumesOnReconnect()) }
    }

    /**
     * Plays a url whose body no extractor recognises, which is how the player fails on a station
     * url that is a playlist, and waits for that failure.
     */
    private fun failOnAnUnreadableStream() {
        val noise = mServer.serve("/noise", LoopbackHttpFixture.TEXT_PLAIN, NOT_AUDIO)
        onMainThread {
            mPlayer.setMediaItem(MediaItem.fromUri(noise))
            mPlayer.prepare()
            mPlayer.play()
        }
        await("the player to fail on the unreadable stream") {
            onMainThread { mPlayer.playerError != null }
        }
        onMainThread {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertTrue(mPlayer.playWhenReady)
        }
    }

    private fun playTheBroadcast() {
        onMainThread {
            mPlayer.setMediaItem(MediaItem.fromUri(mBroadcast.url))
            mPlayer.prepare()
            mPlayer.play()
        }
        awaitPlaying()
        assertEquals(1, mBroadcast.openConnections())
    }

    private fun awaitPlaying() {
        await("the broadcast to play") { onMainThread { mPlayer.isPlaying } }
    }

    private fun awaitConnectionsClosed() {
        await("the player to close its connection to the broadcast") {
            mBroadcast.openConnections() == 0
        }
    }

    /**
     * Once the server has noticed the connection is gone it writes nothing more, so the count
     * that matters is the one after [awaitConnectionsClosed], held for longer than the broadcast
     * takes to send a chunk.
     */
    private fun assertNothingMoreIsSent() {
        val sent = mBroadcast.bytesSent()
        Thread.sleep(QUIET_MILLIS)
        assertEquals("Bytes were sent after the policy pause", sent, mBroadcast.bytesSent())
        assertEquals(0, mBroadcast.openConnections())
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
         * Many times the 20 milliseconds between the broadcast's chunks.
         */
        const val QUIET_MILLIS = 2_000L

        /**
         * Every connection the broadcast sees in a policy pause and resume: the one the pause
         * closed, the resume's own, and the one it may open to ask for the kept position.
         */
        val RESUMED_CONNECTIONS = 2..3
    }
}
