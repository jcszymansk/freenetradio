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

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.isInvalid
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The guard that keeps a page-0 browse from starting `maybeCreateInitialPlaylist`: the refusal in
 * [ServiceBrowser.connect] and the loud failure in [LocalStationsFixture.parkThePlayer].
 *
 * Every other instrumented class relies on these to be handed a service that a browse cannot
 * start playing, and a guard that never fires looks exactly like one that is never needed. So the
 * state it guards against is built here on purpose: a station played and then taken out of the
 * queue, which leaves the service with an active station and nothing queued. Nothing browses
 * while it is in that state, and the teardown parks the player before anything else runs.
 *
 * The fixtures are plain fields because constructing them connects to nothing, and a teardown
 * after a failed setup must still find them.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class InitialPlaylistGuardTest {

    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val mStorages = ServiceStorages(mContext)

    private val mBrowser = ServiceBrowser()

    private val mAudio = LocalAudioFixture(mContext)

    private val mStations = LocalStationsFixture(mStorages, mBrowser)

    @Before
    fun setUp() {
        mStorages.clear()
        mBrowser.connect()
        mBrowser.stop()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
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
    }

    @Test
    fun aServiceHoldingAQueueIsHandedOver() {
        playThenStop()

        assertFalse(mBrowser.canABrowseStartPlayback())
        val other = ServiceBrowser()
        other.connect()
        assertTrue(other.isConnected())
        other.release()
    }

    /**
     * The stored station is cleared first because the guard must not depend on it: a service that
     * is already running keeps the station it adopted whatever the store says, and a guard that
     * read the store would pass this service.
     */
    @Test
    fun connectRefusesAnActiveStationWithAnEmptyQueue() {
        armTheService()
        mStorages.latest.clear()
        assertTrue(mStorages.freshLatest().get().isInvalid())

        val other = ServiceBrowser()
        val refusal = assertThrows(AssertionError::class.java) { other.connect() }

        assertTrue(refusal.message, refusal.message.orEmpty().contains("empty queue"))
        assertFalse("A refused connection was kept", other.isConnected())
        assertThrows(AssertionError::class.java) { ServiceBrowser.assertABrowseCannotStartPlayback() }
    }

    @Test
    fun parkingPutsTheQueueBack() {
        armTheService()

        mStations.parkThePlayer()

        assertNotEquals(0, mBrowser.mediaItemCount())
        assertFalse(mBrowser.isPlaying())
        assertFalse(mBrowser.canABrowseStartPlayback())
    }

    @Test
    fun parkingWithNothingSeededFailsWhenABrowseCouldStartPlayback() {
        armTheService()
        val unseeded = LocalStationsFixture(mStorages, mBrowser)

        val failure = assertThrows(AssertionError::class.java) { unseeded.parkThePlayer() }

        assertTrue(failure.message, failure.message.orEmpty().startsWith("Could not park the player"))
    }

    @Test
    fun parkingWithNothingSeededKeepsAQueueItFinds() {
        playThenStop()
        val queue = mBrowser.queueMediaIds()
        val unseeded = LocalStationsFixture(mStorages, mBrowser)

        unseeded.parkThePlayer()

        assertEquals(queue, mBrowser.queueMediaIds())
        assertFalse(mBrowser.isPlaying())
    }

    /**
     * A browser that never connected is what a teardown finds after a setup that failed first,
     * and throwing there would replace that failure.
     */
    @Test
    fun parkingThroughABrowserThatNeverConnectedDoesNothing() {
        val unconnected = ServiceBrowser()

        LocalStationsFixture(mStorages, unconnected).parkThePlayer()

        assertFalse(unconnected.isConnected())
    }

    private fun playThenStop() {
        playAStation()
        mBrowser.stop()
        mBrowser.awaitPlaybackState(Player.STATE_IDLE)
    }

    /**
     * Leaves the service with an active station and an empty queue, the state a page-0 browse
     * would start playing from.
     */
    private fun armTheService() {
        playThenStop()
        mBrowser.clearMediaItems()
        mBrowser.awaitPlayback("the service to hold an active station and an empty queue") {
            mBrowser.canABrowseStartPlayback()
        }
    }

    private fun playAStation() {
        val station = mStations.seed(mAudio.wav(WAV_NAME)).first()
        mBrowser.setMediaItem(mStations.item(station))
        mBrowser.prepareAndPlay()
        mBrowser.awaitPlaying()
    }

    private companion object {

        const val WAV_NAME = "initial-playlist-guard.wav"
    }
}
