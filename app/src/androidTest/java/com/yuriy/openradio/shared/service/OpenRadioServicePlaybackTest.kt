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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.R
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.storage.makeStation
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

    /**
     * An item the player can be left holding when a test empties its queue. See [parkThePlayer].
     */
    private var mParkingItem: MediaItem? = null

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mAudio = LocalAudioFixture(mContext)
        mStorages = ServiceStorages(mContext)
        mStorages.clear()
        mBrowser = ServiceBrowser()
        mBrowser.connect()
        mBrowser.stop()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.forgetNotifications()
        mBrowser.forgetPlayerEvents()
    }

    @After
    fun tearDown() {
        parkThePlayer()
        mStorages.clear()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.release()
        mAudio.delete()
    }

    /**
     * Leaves the player stopped but still holding a queue, for the tests that come after this
     * class as much as for the ones in it.
     *
     * The service outlives every test class in the run, and playing a station leaves it holding
     * an active station it did not have before. From then on every page-0 browse with an empty
     * queue calls `maybeCreateInitialPlaylist`, which asks the provider for new stations, replaces
     * the queue with whatever comes back and starts playing it. Offline it comes back with
     * nothing, so the queue becomes the active station alone, under a browse-tree key that no
     * later browse invalidates, which is TASK-041. A queue that is not empty shuts that path,
     * which is the state the rest of the suite was written in and has to be handed back in.
     *
     * The item is one this test already holds rather than one browsed for here, because the
     * browse is itself what would trigger the path this is avoiding.
     */
    private fun parkThePlayer() {
        mBrowser.stop()
        if (mBrowser.mediaItemCount() != 0) {
            return
        }
        val item = mParkingItem ?: return
        mBrowser.setMediaItem(item)
        mBrowser.awaitPlayback("the player to hold a queue again") {
            mBrowser.mediaItemCount() != 0
        }
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
        val stations = (0 until count).map { index ->
            makeStation(
                nextStationId(),
                name = "Fixture ${index + 1}",
                url = mAudio.wav("fixture-$index.wav"),
                sortId = index,
                isLocal = true
            )
        }
        for (station in stations) {
            mStorages.locals.add(station)
        }
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        val browsed = mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        assertEquals(
            "The seeded stations are not what the locals node offers",
            stations.map { it.id }, browsed.map { it.mediaId }
        )
        mParkingItem = browsed.first()
        return stations
    }

    /**
     * Station ids are unique for the whole run, not just for one test.
     *
     * [com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage.getId] counts up from a fixed
     * value in the same preference file the tests wipe, so it hands out the same ids to every
     * test. The browse tree keeps entries keyed by a station id that no browse invalidates, and
     * one of those left behind by an earlier test would then answer for a station of the same id
     * in a later one.
     */
    private fun nextStationId(): String {
        return (FIRST_STATION_ID + sStationIds++).toString()
    }

    /**
     * Does what selecting a station in the list does: hand the session that one item and press
     * play. The expansion into a playlist is the service's answer, not the caller's doing.
     */
    private fun selectAndPlay(station: RadioStation) {
        val item = mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            .first { it.mediaId == station.id }
        mBrowser.setMediaItem(item)
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

    private companion object {

        /**
         * Well clear of the ids
         * [com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage] hands out itself.
         */
        const val FIRST_STATION_ID = 1_900_000_000

        var sStationIds = 0
    }
}
