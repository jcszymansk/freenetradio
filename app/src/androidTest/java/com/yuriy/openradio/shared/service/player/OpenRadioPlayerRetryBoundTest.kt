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
import com.yuriy.openradio.R
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The bound on the player's own retries of a stream ExoPlayer gave up for a lost network
 * (fnr-u5nr).
 *
 * A station whose host refuses connections fails the way a lost network does, so the player
 * cannot tell it from a dead zone and tries it again. On a phone with a working network nothing
 * else would ever stop those retries, so once the schedule is spent the player says the station
 * cannot be played and leaves it. The device's networking is disabled here, which changes nothing
 * the player decides: it never asks whether the phone has a network.
 *
 * The player gets a schedule of two short retries, so spending it takes seconds rather than the
 * minutes the service's schedule allows a dead zone.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerRetryBoundTest {

    private lateinit var mContext: Context

    private lateinit var mPlayer: OpenRadioPlayer

    private val mErrors = CopyOnWriteArrayList<PlaybackException>()

    private val mPreparations = AtomicInteger()

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mPlayer = onMain {
            newOpenRadioPlayer(mContext, StreamRetrySchedule(RETRY_DELAYS_MS))
                .also { it.addListener(Recorder()) }
        }
    }

    @After
    fun tearDown() {
        onMain { mPlayer.release() }
    }

    @Test
    fun aStationThatKeepsFailingForTheNetworkIsGivenUpOnceTheRetriesAreSpent() {
        play()

        awaitUnplayable()
        Thread.sleep(SETTLE_MILLIS)

        assertEquals(ATTEMPTS, mPreparations.get())
        assertEquals(List(ATTEMPTS) { CONNECTION_FAILED }, mErrors.map { it.errorCode })
        onMain { assertEquals(Player.STATE_IDLE, mPlayer.playbackState) }
    }

    /**
     * Giving the station up stops the player's own retries, not the station's claim on the next
     * network: the outage may have been a real one that outlasted the schedule, and a network that
     * connects is worth one more try. That try is the service preparing the station again, and it
     * gets a schedule of its own.
     */
    @Test
    fun aStationGivenUpIsStillResumedOnReconnectWithItsRetriesStartedOver() {
        play()
        awaitUnplayable()
        Thread.sleep(SETTLE_MILLIS)
        onMain { assertTrue("Giving up forgot the station", mPlayer.resumesOnReconnect()) }

        onMain {
            mPlayer.prepare()
            mPlayer.play()
        }

        await("the player to spend the retries of the resumed station") {
            mErrors.size == 2 * ATTEMPTS
        }
        Thread.sleep(SETTLE_MILLIS)
        assertEquals(2 * ATTEMPTS, mPreparations.get())
        assertEquals(2 * ATTEMPTS, mErrors.size)
    }

    private fun play() {
        onMain {
            mPlayer.setMediaItem(MediaItem.fromUri(UNREACHABLE_STREAM))
            mPlayer.prepare()
            mPlayer.play()
        }
    }

    private fun awaitUnplayable() {
        val message = mContext.getString(R.string.media_stream_unplayable)
        await("the player to say the station cannot be played") {
            onMain { mPlayer.mediaMetadata.subtitle?.toString() } == message
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
                "errors ${mErrors.map { it.errorCodeName }}"
        )
    }

    private inner class Recorder : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                mPreparations.incrementAndGet()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            mErrors.add(error)
        }
    }

    private companion object {

        const val UNREACHABLE_STREAM = "$UNREACHABLE_ORIGIN/live.mp3"

        const val CONNECTION_FAILED = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        val RETRY_DELAYS_MS = listOf(200L, 400L)

        /**
         * The attempt the test starts and one for each retry in the schedule.
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
