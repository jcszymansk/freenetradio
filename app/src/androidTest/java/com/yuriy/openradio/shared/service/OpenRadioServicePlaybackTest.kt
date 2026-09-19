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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.R
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plays a generated local WAV file through the real [OpenRadioService] over a real
 * [androidx.media3.session.MediaBrowser], and covers what the service does around that: how a
 * single selected item becomes a playlist, what the transport controls do to it, what the session
 * reports back, and what it remembers afterwards.
 *
 * The stations are device-local ones pointing at `file://` urls, so the whole suite runs with
 * networking disabled and nothing here can reach a real stream. Local stations are also the only
 * browse node whose contents a test can decide outright, which is what makes the expansion of a
 * selection into its sibling list assertable.
 *
 * [OpenRadioService.CMD_STOP_SERVICE] is never sent, for the reason
 * [OpenRadioServiceCommandTest] records: it ends in `Process.killProcess`, in the process this
 * test is running in.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioServicePlaybackTest {

    private lateinit var mContext: Context

    private lateinit var mStorages: ServiceStorages

    private lateinit var mBrowser: ServiceBrowser

    private lateinit var mAudio: LocalAudioFixture

    private lateinit var mStations: LocalStationsFixture

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mAudio = LocalAudioFixture(mContext)
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
    }

    @Test
    fun aLocalFileStationPlays() {
        val station = seedStations(1).first()

        selectAndPlay(station)

        mBrowser.awaitPlaying()
        assertEquals(Player.STATE_READY, mBrowser.playbackState())
        assertEquals(station.id, mBrowser.currentMediaId())
        assertEquals(station.getStreamUrlFixed(), mBrowser.currentMediaItemUri())
        assertNull(mBrowser.playerError())
    }

    /**
     * Selecting one station hands the session exactly one item, and the service is what turns it
     * back into the list the user was looking at, positioned on the item they picked. Without
     * that, "next" from a station list would have nowhere to go.
     */
    @Test
    fun aSelectedItemIsExpandedToItsParentPlaylist() {
        val stations = seedStations(3)
        val chosen = stations[1]

        selectAndPlay(chosen)
        mBrowser.awaitPlaying()

        assertEquals(stations.map { it.id }, mBrowser.queueMediaIds())
        assertEquals(1, mBrowser.currentMediaItemIndex())
        assertEquals(chosen.id, mBrowser.currentMediaId())
    }

    @Test
    fun switchingStationsMovesPlaybackToTheNewOne() {
        val stations = seedStations(2)

        selectAndPlay(stations[0])
        mBrowser.awaitPlaying()
        assertEquals(stations[0].id, mBrowser.currentMediaId())

        selectAndPlay(stations[1])
        mBrowser.awaitPlayback("the second station to be current") {
            mBrowser.currentMediaId() == stations[1].id
        }
        mBrowser.awaitPlaying()

        assertEquals(stations[1].id, mBrowser.currentMediaId())
        assertEquals(stations[1].getStreamUrlFixed(), mBrowser.currentMediaItemUri())
    }

    @Test
    fun pauseAndResumeKeepTheCurrentStation() {
        val station = seedStations(1).first()

        selectAndPlay(station)
        mBrowser.awaitPlaying()

        mBrowser.pause()
        mBrowser.awaitPlayback("playback to pause") { mBrowser.isPlaying().not() }
        assertEquals(
            "Pausing must not give up the prepared stream",
            Player.STATE_READY, mBrowser.playbackState()
        )
        assertEquals(station.id, mBrowser.currentMediaId())

        mBrowser.play()
        mBrowser.awaitPlaying()
        assertEquals(station.id, mBrowser.currentMediaId())
    }

    /**
     * Stopping gives up the stream but not the queue: the station stays selected so the user can
     * start it again, which is what [OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM] relies on.
     */
    @Test
    fun stopEndsPlaybackAndKeepsTheQueue() {
        val stations = seedStations(2)

        selectAndPlay(stations[0])
        mBrowser.awaitPlaying()

        mBrowser.stop()
        mBrowser.awaitPlaybackState(Player.STATE_IDLE)

        assertFalse(mBrowser.isPlaying())
        assertEquals(2, mBrowser.mediaItemCount())
        assertEquals(stations[0].id, mBrowser.currentMediaId())
    }

    @Test
    fun nextAndPreviousStepThroughThePlaylist() {
        val stations = seedStations(3)

        selectAndPlay(stations[0])
        mBrowser.awaitPlaying()

        mBrowser.seekToNext()
        mBrowser.awaitPlayback("the next station to be current") {
            mBrowser.currentMediaItemIndex() == 1
        }
        assertEquals(stations[1].id, mBrowser.currentMediaId())

        mBrowser.seekToNext()
        mBrowser.awaitPlayback("the last station to be current") {
            mBrowser.currentMediaItemIndex() == 2
        }
        assertEquals(stations[2].id, mBrowser.currentMediaId())

        mBrowser.seekToPrevious()
        mBrowser.awaitPlayback("the middle station to be current again") {
            mBrowser.currentMediaItemIndex() == 1
        }
        assertEquals(stations[1].id, mBrowser.currentMediaId())
    }

    /**
     * The session has to tell its clients which item is playing and what the stream is doing, or
     * the phone UI and Android Auto both show a stale station. The subtitle is where the player
     * puts the stream's state, and it has to reach a connected controller.
     */
    @Test
    fun theSessionReportsTheCurrentItemAndItsMetadata() {
        val stations = seedStations(2)

        selectAndPlay(stations[0])
        mBrowser.awaitPlaying()
        mBrowser.awaitMetadataSubtitle(liveStreamLabel())

        assertTrue(
            "No metadata reached the controller",
            mBrowser.metadataUpdates().isNotEmpty()
        )
        assertTrue(
            "The station's own title never reached the controller",
            mBrowser.metadataUpdates().any { it.title?.toString() == stations[0].name }
        )

        mBrowser.forgetPlayerEvents()
        mBrowser.seekToNext()
        mBrowser.awaitPlayback("a transition to the second station") {
            mBrowser.mediaItemTransitions().contains(stations[1].id)
        }
        assertEquals(stations[1].id, mBrowser.currentMediaId())
    }

    /**
     * The station the user last listened to is what the service restores on the next cold start,
     * so it has to be written while it plays rather than on the way out.
     */
    @Test
    fun theStationBeingPlayedBecomesTheLatestOne() {
        val stations = seedStations(2)

        selectAndPlay(stations[1])
        mBrowser.awaitPlaying()

        awaitLatestStation(stations[1].id)
        assertEquals(
            stations[1].getStreamUrlFixed(),
            mStorages.freshLatest().get().getStreamUrlFixed()
        )
    }

    @Test
    fun steppingToTheNextStationUpdatesTheLatestOne() {
        val stations = seedStations(2)

        selectAndPlay(stations[0])
        mBrowser.awaitPlaying()
        awaitLatestStation(stations[0].id)

        mBrowser.seekToNext()
        mBrowser.awaitPlayback("the next station to be current") {
            mBrowser.currentMediaItemIndex() == 1
        }

        awaitLatestStation(stations[1].id)
    }

    /**
     * A play request is only routed through
     * [androidx.media3.session.MediaSession.Callback.onPlaybackResumption] when the player has no
     * current item, so a loaded playlist is the case that must never go near it: play resumes the
     * station that is already selected, and the queue around it stays exactly as it was.
     */
    @Test
    fun playingWithALoadedPlaylistResumesItInPlace() {
        val stations = seedStations(3)

        selectAndPlay(stations[1])
        mBrowser.awaitPlaying()
        mBrowser.pause()
        mBrowser.awaitPlayback("playback to pause") { mBrowser.isPlaying().not() }

        mBrowser.play()
        mBrowser.awaitPlaying()

        assertEquals(stations.map { it.id }, mBrowser.queueMediaIds())
        assertEquals(stations[1].id, mBrowser.currentMediaId())
    }

    /**
     * With nothing loaded, the same play request is the resumption path, and the session answers
     * it out of the playlist the player remembers. That playlist is cleared along with the
     * player's own queue, so the session has nothing left to offer and nothing starts.
     *
     * This is the behaviour as it stands, not the behaviour that was intended: TASK-040 records
     * that the remembered playlist can never outlive the queue, which leaves the restoring branch
     * of `onPlaybackResumption` unable to restore anything.
     */
    @Test
    fun aPlayRequestWithNothingLoadedRestoresNothing() {
        val stations = seedStations(2)

        selectAndPlay(stations[0])
        mBrowser.awaitPlaying()
        clearTheQueue()

        mBrowser.play()

        mBrowser.awaitPlayback("the play request to be answered") {
            mBrowser.playbackState() != Player.STATE_IDLE
        }
        assertEquals(0, mBrowser.mediaItemCount())
        assertNull(mBrowser.currentMediaId())
        assertFalse(mBrowser.isPlaying())
    }

    /**
     * Seeds [count] device-local stations pointing at generated WAV files, browses them so the
     * service holds them in its browse tree, and hands them back in browse order.
     */
    private fun seedStations(count: Int): List<RadioStation> {
        val urls = (0 until count).map { mAudio.wav("fixture-$it.wav") }.toTypedArray()
        return mStations.seed(*urls)
    }

    /**
     * Does what selecting a station in the list does: hand the session that one item and press
     * play. The expansion into a playlist is the service's answer, not the caller's doing.
     */
    private fun selectAndPlay(station: RadioStation) {
        mBrowser.setMediaItem(mStations.item(station))
        mBrowser.prepareAndPlay()
    }

    /**
     * Empties the player's queue the way a released session would, so the next resumption request
     * has nothing loaded to protect.
     */
    private fun clearTheQueue() {
        mBrowser.stop()
        mBrowser.awaitPlaybackState(Player.STATE_IDLE)
        mBrowser.clearMediaItems()
        mBrowser.awaitPlayback("the queue to empty") { mBrowser.mediaItemCount() == 0 }
    }

    /**
     * The latest station is written from a background coroutine after the player reports the new
     * item, so it arrives shortly after playback rather than with it.
     */
    private fun awaitLatestStation(id: String) {
        mBrowser.awaitPlayback("'$id' to be stored as the latest station") {
            mStorages.freshLatest().get().id == id
        }
        assertEquals(id, mStorages.freshLatest().get().id)
    }

    private fun liveStreamLabel(): String {
        return mContext.getString(R.string.media_description_default)
    }
}
