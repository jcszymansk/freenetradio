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
import com.yuriy.openradio.shared.service.LocalAudioFixture
import com.yuriy.openradio.shared.service.LoopbackHttpFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * What the player does about a playback error with no specific cause, one that is neither the
 * network, nor the server refusing the stream, nor a format the player does not recognize
 * (TASK-039).
 *
 * ExoPlayer reports only errors it has given up on: it retries a failing load by itself first,
 * and once it reports one it has stopped the stream. So nothing brings the station back unless
 * the player prepares it again, which it does for as long as the error budget holds, and once
 * the budget is spent it stops and tells the user.
 *
 * The station is a WAV served over loopback whose format chunk names an encoding ExoPlayer does
 * not read. ExoPlayer recognizes the file and fails to parse it, which it does not retry, so
 * every attempt is one request and one reported error.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerToleratedErrorTest {

    private lateinit var mContext: Context

    private lateinit var mAudio: LocalAudioFixture

    private lateinit var mServer: LoopbackHttpFixture

    private lateinit var mPlayer: OpenRadioPlayer

    private val mErrors = CopyOnWriteArrayList<PlaybackException>()

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mAudio = LocalAudioFixture(mContext)
        mServer = LoopbackHttpFixture()
        mServer.start()
        mPlayer = onMain {
            newOpenRadioPlayer(mContext).also { it.addListener(ErrorRecorder()) }
        }
    }

    @After
    fun tearDown() {
        onMain { mPlayer.release() }
        mServer.stop()
    }

    @Test
    fun aStationThatFailsOnceIsPreparedAgainAndPlays() {
        val url = mServer.serveInTurn(
            STATION_PATH, LoopbackHttpFixture.AUDIO_WAV, listOf(mAudio.unreadableWavBytes(), mAudio.wavBytes())
        )

        play(url)

        await("the station to play after its first attempt failed") { onMain { mPlayer.isPlaying } }
        assertEquals(
            listOf(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED), mErrors.map { it.errorCode }
        )
        assertEquals(listOf(STATION_PATH, STATION_PATH), mServer.requestedPaths())
    }

    @Test
    fun aStationThatKeepsFailingIsGivenUpOnceTheErrorBudgetIsSpent() {
        val url = mServer.serve(STATION_PATH, LoopbackHttpFixture.AUDIO_WAV, mAudio.unreadableWavBytes())

        play(url)

        await("the player to spend the error budget") { mErrors.size > TOLERATED_ERRORS }
        val message = mContext.getString(R.string.media_stream_error)
        await("the player to say the stream failed") {
            onMain { mPlayer.mediaMetadata.subtitle?.toString() } == message
        }
        Thread.sleep(SETTLE_MILLIS)
        assertEquals(TOLERATED_ERRORS + 1, mErrors.size)
        assertEquals(List(TOLERATED_ERRORS + 1) { STATION_PATH }, mServer.requestedPaths())
        onMain { assertEquals(Player.STATE_IDLE, mPlayer.playbackState) }
    }

    private fun play(url: String) {
        onMain {
            mPlayer.setMediaItem(MediaItem.fromUri(url))
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
        fail("Timed out waiting for $what")
    }

    private inner class ErrorRecorder : Player.Listener {

        override fun onPlayerError(error: PlaybackException) {
            mErrors.add(error)
        }
    }

    private companion object {

        const val STATION_PATH = "/station.wav"

        /**
         * Number of errors the player tolerates before it gives the stream up, the same budget
         * PlaybackErrorClassifierTest pins. Kept here so a change to the production budget fails
         * this test instead of passing quietly.
         */
        const val TOLERATED_ERRORS = 6

        /**
         * Long enough for a retry the player should not have made to reach the server.
         */
        const val SETTLE_MILLIS = 2_000L

        const val TIMEOUT_SECONDS = 30L

        const val POLL_MILLIS = 50L
    }
}
