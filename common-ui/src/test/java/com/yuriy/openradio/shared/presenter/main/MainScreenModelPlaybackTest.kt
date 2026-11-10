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

package com.yuriy.openradio.shared.presenter.main

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.PlaybackState
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.storage.images.ImagesStore
import com.yuriy.openradio.shared.service.OpenRadioService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenModelPlaybackTest {

    @Test
    fun theMiniPlayerIsHiddenUntilAStationIsChosen() {
        val m = ModelUnderTest().startAndConnect()

        assertNull(m.state.miniPlayer)
        m.client.listener.onMetadataChanged(MediaMetadata.Builder().build())
        assertNull("Metadata without a title is no station", m.state.miniPlayer)
    }

    @Test
    fun aChosenStationPutsTheMiniPlayerUp() {
        val m = ModelUnderTest().startAndConnect()
        m.client.currentMediaItem = MediaItem.Builder().setMediaId("s1").build()
        m.client.isPlaying = true

        m.client.listener.onMetadataChanged(metadata("Arrow Classic Rock", "Iggy Pop - Candy"))

        assertEquals(MiniPlayerState("Arrow Classic Rock", "AC", "Iggy Pop - Candy", isPlaying = true), m.state.miniPlayer)
        assertEquals("s1", m.state.playingId)
    }

    @Test
    fun theMiniPlayerStaysUpPausedWhenPlaybackStops() {
        val m = playing()

        m.client.listener.onPlaybackStateChanged(PlaybackState(Player.STATE_READY, false))
        m.client.listener.onMetadataChanged(MediaMetadata.Builder().build())

        assertFalse(m.state.miniPlayer!!.isPlaying)
        assertEquals("Arrow Classic Rock", m.state.miniPlayer!!.name)
    }

    @Test
    fun aStationTheServiceHoldsIsOnTheMiniPlayerWhenTheScreenConnects() {
        val m = ModelUnderTest()
        m.lastStation = lastStation("s9", "Capital FM")
        m.model.start()
        m.client.currentMediaItem = MediaItem.Builder().setMediaId("s1").build()
        m.client.mediaMetadata = metadata("Arrow Classic Rock", "Iggy Pop - Candy")
        m.client.isPlaying = true

        m.client.listener.onConnected()

        assertEquals(MiniPlayerState("Arrow Classic Rock", "AC", "Iggy Pop - Candy", isPlaying = true), m.state.miniPlayer)
        assertEquals("s1", m.state.playingId)
    }

    /**
     * A service created again after the process was killed has no queue and pushes no metadata,
     * which is the state the fake starts in. The station a play request would resume is the
     * latest one.
     */
    @Test
    fun aColdStartShowsTheLatestStationPaused() {
        val m = ModelUnderTest()
        m.lastStation = lastStation("s9", "Capital FM")

        m.startAndConnect()

        assertEquals(MiniPlayerState("Capital FM", "CF", "", isPlaying = false), m.state.miniPlayer)
        assertEquals("s9", m.state.playingId)
    }

    @Test
    fun aColdStartWithoutALatestStationLeavesTheMiniPlayerHidden() {
        val m = ModelUnderTest().startAndConnect()

        assertNull(m.state.miniPlayer)
        assertNull(m.state.playingId)
    }

    /**
     * The model outlives the activity, and the service can be created again in between, which is
     * also how the journey stands in for a killed process: what the model showed before is not what
     * the new service would resume.
     */
    @Test
    fun aServiceThatLostItsQueueShowsTheLatestStationInsteadOfWhatWasPlaying() {
        val m = playing()
        m.lastStation = lastStation("s9", "Capital FM")
        m.model.stop()

        m.startAndConnect()

        assertEquals(MiniPlayerState("Capital FM", "CF", "", isPlaying = false), m.state.miniPlayer)
        assertEquals("s9", m.state.playingId)
    }

    @Test
    fun aServiceThatLostItsQueueAndResumesNothingTakesTheMiniPlayerDown() {
        val m = playing()
        m.model.stop()

        m.startAndConnect()

        assertNull(m.state.miniPlayer)
    }

    @Test
    fun theButtonTogglesTheLastPlayedStation() {
        val m = playing()

        m.model.togglePlayback()

        assertEquals(OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM, m.client.commands.last().first)
        assertEquals(0, m.client.playRequests)
    }

    @Test
    fun theButtonAsksTheSessionToResumeWhenNothingIsLoaded() {
        val m = ModelUnderTest()
        m.lastStation = lastStation("s9", "Capital FM")
        m.startAndConnect()

        m.model.togglePlayback()

        assertEquals(1, m.client.playRequests)
        assertTrue(m.client.commands.none { it.first == OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM })
    }

    @Test
    fun thePlayingStationsRowIsMarked() {
        val m = playing()
        m.model.selectTab(Tab.MINE)

        m.client.answer(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST, listOf(station("s1"), station("s2")))

        assertEquals(listOf(true, false), m.rows().map { (it as RowState.Station).isCurrent })
    }

    @Test
    fun aSearchResultRowOfThePlayingStationIsMarked() {
        val m = playing()
        m.model.search("rock")

        m.client.answer(MediaId.MEDIA_ID_SEARCH_FROM_APP, listOf(station(MediaId.makeSearchId("s1"))))

        assertTrue((m.rows().single() as RowState.Station).isCurrent)
    }

    @Test
    fun artworkThatArrivesLaterBumpsTheRowsRevision() {
        val m = ModelUnderTest().startAndConnect()
        val item = station("s1").let {
            it.buildUpon().setMediaMetadata(
                it.mediaMetadata.buildUpon().setArtworkUri(ImagesStore.buildImageUri("s1", "https://img.invalid/a.png")).build()
            ).build()
        }
        m.client.answer(MediaId.MEDIA_ID_FAVORITES_LIST, listOf(item))

        m.model.artworkChanged("s1")

        assertEquals(1, (m.rows().single() as RowState.Station).artworkRevision)
    }

    @Test
    fun closeResetsEveryTabAndAsksTheActivityToFinish() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.GENRE)
        m.model.search("rock")

        m.model.close()

        assertTrue(m.state.isClosed)
        assertEquals(Tab.FAV, m.model.snapshot().selectedTab)
        assertTrue(m.model.snapshot().stacks.values.all { it.size == 1 })
    }

    @Test
    fun aStartAfterCloseIsOpenAgain() {
        val m = ModelUnderTest().startAndConnect()
        m.model.close()
        m.model.stop()

        m.model.start()

        assertFalse(m.state.isClosed)
    }

    @Test
    fun leavingStopsTheService() {
        val m = ModelUnderTest().startAndConnect()

        m.model.leave()

        assertEquals(OpenRadioService.CMD_STOP_SERVICE, m.client.commands.last().first)
    }

    @Test
    fun theServiceCommanderAnswersFalseWithoutAClient() {
        val m = ModelUnderTest()

        assertFalse(runBlocking { m.model.serviceCommander.sendCommand(OpenRadioService.CMD_UPDATE_TREE) })
        m.startAndConnect()
        assertTrue(runBlocking { m.model.serviceCommander.sendCommand(OpenRadioService.CMD_UPDATE_TREE) })
        assertEquals(OpenRadioService.CMD_UPDATE_TREE, m.client.commands.last().first)
    }

    private fun playing(): ModelUnderTest {
        val m = ModelUnderTest().startAndConnect()
        m.client.currentMediaItem = MediaItem.Builder().setMediaId("s1").build()
        m.client.isPlaying = true
        m.client.listener.onMetadataChanged(metadata("Arrow Classic Rock", "Iggy Pop - Candy"))
        return m
    }

    private fun metadata(title: String, track: String): MediaMetadata {
        return MediaMetadata.Builder().setTitle(title).setSubtitle(track).build()
    }

    private fun lastStation(id: String, name: String): RadioStation {
        return RadioStation.makeDefaultInstance(id).apply { this.name = name }
    }
}
