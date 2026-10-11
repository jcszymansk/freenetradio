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
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.eq.EqualizerState
import com.yuriy.openradio.shared.model.media.BrowseTree
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
        mPlayer = onMain {
            OpenRadioPlayer(mContext, IgnoringListener(), InertEqualizerLayer(), BrowseTree())
        }
    }

    @After
    fun tearDown() {
        onMain { mPlayer.release() }
        mServer.stop()
    }

    @Test
    fun aPolicyPauseClosesTheConnectionAndSendsNothingMore() {
        playTheBroadcast()

        onMain { mPlayer.pauseForNetworkPolicy() }

        awaitConnectionsClosed()
        assertNothingMoreIsSent()
        onMain {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertFalse(mPlayer.isLoading)
        }
    }

    @Test
    fun aStationThePolicyPausedPlaysAgainOverANewConnectionWhenResumed() {
        playTheBroadcast()
        onMain { mPlayer.pauseForNetworkPolicy() }
        awaitConnectionsClosed()

        onMain {
            assertTrue("The policy pause forgot the station", mPlayer.resumesOnReconnect())
            assertEquals(1, mPlayer.mediaItemCount)
            assertEquals(mBroadcast.url, mPlayer.currentMediaItem?.localConfiguration?.uri.toString())
            mPlayer.prepare()
            mPlayer.play()
        }

        awaitPlaying()
        assertEquals(2, mBroadcast.connections())
        assertEquals(1, mBroadcast.openConnections())
        onMain { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    @Test
    fun aPolicyPauseOfAStationTheUserPausedClosesTheConnectionAndKeepsItPaused() {
        playTheBroadcast()
        onMain { mPlayer.pause() }

        onMain { mPlayer.pauseForNetworkPolicy() }

        awaitConnectionsClosed()
        assertNothingMoreIsSent()
        onMain { assertFalse(mPlayer.resumesOnReconnect()) }
    }

    @Test
    fun aRepeatedPolicyPauseKeepsTheStationToResume() {
        playTheBroadcast()
        onMain { mPlayer.pauseForNetworkPolicy() }
        awaitConnectionsClosed()

        onMain { mPlayer.pauseForNetworkPolicy() }

        assertNothingMoreIsSent()
        assertEquals(1, mBroadcast.connections())
        onMain { assertTrue(mPlayer.resumesOnReconnect()) }
    }

    /**
     * Why the policy pause stops the player rather than pausing it: a paused player keeps the
     * connection open and keeps reading the broadcast. This is also what shows the fixture can
     * tell the two apart, so the cases above cannot pass for a reason other than the stop.
     */
    @Test
    fun aPauseAloneKeepsReadingTheBroadcast() {
        playTheBroadcast()

        onMain { mPlayer.pause() }
        val sentAtPause = mBroadcast.bytesSent()
        Thread.sleep(QUIET_MILLIS)

        assertEquals(1, mBroadcast.openConnections())
        assertTrue(
            "A paused player stopped reading at $sentAtPause bytes",
            mBroadcast.bytesSent() > sentAtPause
        )
    }

    private fun playTheBroadcast() {
        onMain {
            mPlayer.setMediaItem(MediaItem.fromUri(mBroadcast.url))
            mPlayer.prepare()
            mPlayer.play()
        }
        awaitPlaying()
        assertEquals(1, mBroadcast.openConnections())
    }

    private fun awaitPlaying() {
        await("the broadcast to play") { onMain { mPlayer.isPlaying } }
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

    /**
     * ExoPlayer is bound to the looper it was built on, and the service builds it on the main one.
     */
    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runCatching(block)
        }
        return result?.getOrThrow() ?: throw IllegalStateException("The main thread ran nothing")
    }

    private class IgnoringListener : OpenRadioPlayer.Listener {

        override fun onHandledError(error: PlaybackException) = Unit

        override fun onPlaybackStateChanged(mediaItem: MediaItem) = Unit
    }

    /**
     * An equalizer that is never attached: the audio effect is not what is under test, and the
     * real one changes the device's stored equalizer state.
     */
    private class InertEqualizerLayer : EqualizerLayer {

        override fun init(audioSessionId: Int) = Unit

        override fun deinit() = Unit

        override fun isInit(): Boolean = false

        override fun saveState(state: EqualizerState) = Unit

        override fun loadState(): EqualizerState = EqualizerState()

        override fun applyState(state: EqualizerState, onSuccess: (state: EqualizerState) -> Unit) = Unit
    }

    private companion object {

        const val TIMEOUT_SECONDS = 15L

        const val POLL_MILLIS = 50L

        /**
         * Many times the 20 milliseconds between the broadcast's chunks.
         */
        const val QUIET_MILLIS = 2_000L
    }
}
