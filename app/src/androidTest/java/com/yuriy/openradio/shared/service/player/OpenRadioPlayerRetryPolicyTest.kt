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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The mobile-network policy applied to the player's own retries of a stream ExoPlayer gave up for
 * a lost network (fnr-7shj).
 *
 * Wi-Fi dies, the stream fails and a retry is scheduled; then the phone moves to mobile data the
 * user disallowed. The service pauses the station for the policy when the network monitor tells
 * it, but nothing makes that callback arrive before the retry, and the default network can change
 * without the event the monitor listens for. So the retry asks the policy itself, the same one a
 * play request asks, and leaves the stream alone when it forbids playback.
 *
 * The device's networking is disabled here, and the player cannot tell: what it asks is the
 * policy the test hands it, which turns blocking the moment the stream fails, as the phone would
 * if mobile data took over from the Wi-Fi that went.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerRetryPolicyTest {

    private lateinit var mContext: Context

    private lateinit var mPlayer: OpenRadioPlayer

    private val mIsBlocked = AtomicBoolean(false)

    private val mErrors = CopyOnWriteArrayList<PlaybackException>()

    private val mPreparations = AtomicInteger()

    private val mBlockedRetries = AtomicInteger()

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mPlayer = onMain {
            newOpenRadioPlayer(
                mContext,
                StreamRetrySchedule(RETRY_DELAYS_MS),
                ServiceStandIn(),
                mIsBlocked::get
            ).also { it.addListener(Recorder()) }
        }
    }

    @After
    fun tearDown() {
        onMain { mPlayer.release() }
    }

    @Test
    fun aRetryDueOnABlockedMobileNetworkDoesNotStreamAndKeepsTheStationToResume() {
        play()

        await("the retry to come due") { mBlockedRetries.get() > 0 || mPreparations.get() > 1 }
        Thread.sleep(SETTLE_MILLIS)

        assertEquals("The retry streamed on blocked mobile data", 1, mPreparations.get())
        assertEquals(listOf(CONNECTION_FAILED), mErrors.map { it.errorCode })
        assertEquals(1, mBlockedRetries.get())
        onMain {
            assertEquals(Player.STATE_IDLE, mPlayer.playbackState)
            assertFalse(mPlayer.playWhenReady)
            assertTrue("The blocked retry forgot the station", mPlayer.resumesOnReconnect())
        }
    }

    /**
     * A network the station may use resumes it, as it would have after the service's own policy
     * pause, and the resumed station gets the full schedule of retries.
     */
    @Test
    fun aStationWhoseRetryWasBlockedResumesOnANetworkItMayUse() {
        play()
        await("the retry to find the network blocked") { mBlockedRetries.get() > 0 }

        mIsBlocked.set(false)
        // What OpenRadioService.handlePlayRequestUiThread does when a network it may use connects.
        onMain {
            mPlayer.prepare()
            mPlayer.play()
        }

        await("the player to spend the retries of the resumed station") {
            mErrors.size == 1 + ATTEMPTS
        }
        Thread.sleep(SETTLE_MILLIS)
        assertEquals(1 + ATTEMPTS, mPreparations.get())
        assertEquals(1, mBlockedRetries.get())
    }

    private fun play() {
        onMain {
            mPlayer.setMediaItem(MediaItem.fromUri(UNREACHABLE_STREAM))
            mPlayer.prepare()
            mPlayer.play()
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
        fail(
            "Timed out waiting for $what: preparations ${mPreparations.get()}, " +
                "errors ${mErrors.map { it.errorCodeName }}, blocked retries ${mBlockedRetries.get()}"
        )
    }

    /**
     * Does what OpenRadioService does with a blocked retry: the pause it makes whenever the
     * policy forbids playback.
     */
    private inner class ServiceStandIn : IgnoringListener() {

        override fun onStreamRetryBlocked() {
            mBlockedRetries.incrementAndGet()
            mPlayer.pauseForNetworkPolicy()
        }
    }

    private inner class Recorder : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                mPreparations.incrementAndGet()
            }
        }

        /**
         * The phone moves to blocked mobile data as the stream fails for the Wi-Fi it lost, before
         * the retry is due and with no word to the service.
         */
        override fun onPlayerError(error: PlaybackException) {
            mErrors.add(error)
            if (mErrors.size == 1) {
                mIsBlocked.set(true)
            }
        }
    }

    private companion object {

        const val UNREACHABLE_STREAM = "$UNREACHABLE_ORIGIN/live.mp3"

        const val CONNECTION_FAILED = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        val RETRY_DELAYS_MS = listOf(200L, 400L)

        /**
         * The attempt a resume starts and one for each retry in the schedule.
         */
        val ATTEMPTS = 1 + RETRY_DELAYS_MS.size

        /**
         * Long enough for a retry the player should not have made to start: far longer than any
         * delay in the schedule, and a retry shows as buffering as soon as it prepares.
         */
        const val SETTLE_MILLIS = 3_000L

        /**
         * Longer than ExoPlayer keeps retrying a refused connection, a few seconds, for every
         * attempt the schedule allows.
         */
        const val TIMEOUT_SECONDS = 90L

        const val POLL_MILLIS = 50L
    }
}
