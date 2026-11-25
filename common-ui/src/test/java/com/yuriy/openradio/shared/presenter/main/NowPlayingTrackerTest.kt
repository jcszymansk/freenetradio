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
import com.yuriy.openradio.shared.model.media.setVariant
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioStore
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [NowPlayingTracker]: what the mini player and Now playing show (REQ-005, REQ-006,
 * REQ-014), the cold start, the toggle, and the one heart per station that every view shares
 * (REQ-007).
 */
class NowPlayingTrackerTest {

    // Moved from MainScreenModelPlaybackTest.

    @Test
    fun nothingIsShownUntilAStationIsChosen() {
        val t = TrackerUnderTest().connect()

        assertNull(t.tracker.miniPlayer())
        t.tracker.onMetadataChanged(MediaMetadata.Builder().build())
        assertNull("Metadata without a title is no station", t.tracker.miniPlayer())
        assertNull(t.tracker.nowPlaying())
    }

    @Test
    fun aChosenStationPutsTheMiniPlayerUp() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))

        assertEquals(MiniPlayerState("Arrow Classic Rock", "AC", "Iggy Pop - Candy", isPlaying = true), t.tracker.miniPlayer())
        assertEquals("s1", t.tracker.playingId)
    }

    @Test
    fun theStationStaysShownPausedWhenPlaybackStops() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))

        t.tracker.onPlaybackStateChanged(PlaybackState(Player.STATE_READY, false))
        t.tracker.onMetadataChanged(MediaMetadata.Builder().build())

        assertFalse(t.tracker.miniPlayer()!!.isPlaying)
        assertEquals("Arrow Classic Rock", t.tracker.nowPlaying()!!.name)
    }

    @Test
    fun aStationTheServiceHoldsIsShownWhenTheScreenConnects() {
        val t = TrackerUnderTest()
        t.lastStation = lastStation("s9", "Capital FM")
        val item = station("s1", "Arrow Classic Rock")
        t.client.currentMediaItem = item
        t.client.mediaMetadata = pushedMetadata(item, "Iggy Pop - Candy")
        t.client.playbackState = PlaybackState(Player.STATE_READY, true)

        t.tracker.onConnected()

        assertEquals(MiniPlayerState("Arrow Classic Rock", "AC", "Iggy Pop - Candy", isPlaying = true), t.tracker.miniPlayer())
        assertEquals("s1", t.tracker.playingId)
    }

    /**
     * A service created again after the process was killed has no queue and pushes no metadata,
     * which is the state the fake starts in. The station a play request would resume is the
     * latest one.
     */
    @Test
    fun aColdStartShowsTheLatestStationPaused() {
        val t = TrackerUnderTest()
        t.lastStation = lastStation("s9", "Capital FM")

        t.connect()

        assertEquals(MiniPlayerState("Capital FM", "CF", "", isPlaying = false), t.tracker.miniPlayer())
        assertEquals("s9", t.tracker.playingId)
    }

    @Test
    fun aColdStartWithoutALatestStationShowsNothing() {
        val t = TrackerUnderTest().connect()

        assertNull(t.tracker.miniPlayer())
        assertNull(t.tracker.playingId)
        assertFalse(t.tracker.hasStation)
    }

    @Test
    fun aServiceThatLostItsQueueShowsTheLatestStationInsteadOfWhatWasPlaying() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))
        t.lastStation = lastStation("s9", "Capital FM")

        t.reconnect()

        assertEquals(MiniPlayerState("Capital FM", "CF", "", isPlaying = false), t.tracker.miniPlayer())
        assertEquals("s9", t.tracker.playingId)
    }

    @Test
    fun aServiceThatLostItsQueueAndResumesNothingShowsNothing() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))

        t.reconnect()

        assertNull(t.tracker.miniPlayer())
        assertFalse(t.tracker.hasStation)
    }

    @Test
    fun theButtonTogglesTheLastPlayedStation() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))

        t.tracker.togglePlayback()

        assertEquals(OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM, t.client.commands.last().first)
        assertEquals(0, t.client.playRequests)
    }

    @Test
    fun theButtonAsksTheSessionToResumeWhenNothingIsLoaded() {
        val t = TrackerUnderTest()
        t.lastStation = lastStation("s9", "Capital FM")
        t.connect()

        t.tracker.togglePlayback()

        assertEquals(1, t.client.playRequests)
        assertTrue(t.client.commands.none { it.first == OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM })
    }

    @Test
    fun aSearchResultIdOfThePlayingStationIsCurrent() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))

        assertTrue(t.tracker.isCurrent("s1"))
        assertTrue(t.tracker.isCurrent(MediaId.makeSearchId("s1")))
        assertFalse(t.tracker.isCurrent("s2"))
    }

    // Now playing states (REQ-006, REQ-014).

    @Test
    fun bufferingAStationAskedToPlayShowsTheProgressLineAndPause() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))

        t.tracker.onPlaybackStateChanged(PlaybackState(Player.STATE_BUFFERING, true))

        assertTrue(t.tracker.nowPlaying()!!.isBuffering)
        assertTrue(t.tracker.nowPlaying()!!.isPlaying)
        assertTrue(t.tracker.miniPlayer()!!.isPlaying)
    }

    @Test
    fun leavingBufferingTakesTheProgressLineAway() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))
        t.tracker.onPlaybackStateChanged(PlaybackState(Player.STATE_BUFFERING, true))

        t.tracker.onPlaybackStateChanged(PlaybackState(Player.STATE_READY, true))

        assertFalse(t.tracker.nowPlaying()!!.isBuffering)
        assertTrue(t.tracker.nowPlaying()!!.isPlaying)
    }

    @Test
    fun aPausedPlayerOffersPlay() {
        val t = TrackerUnderTest().connect()
            .play(station("s1", "Arrow Classic Rock"), state = PlaybackState(Player.STATE_READY, false))

        assertFalse(t.tracker.nowPlaying()!!.isPlaying)
        assertFalse(t.tracker.nowPlaying()!!.isBuffering)
    }

    @Test
    fun aPlaybackErrorArrivesAsTheTrackText() {
        val item = station("s1", "Arrow Classic Rock")
        val t = TrackerUnderTest().connect().play(item)

        t.client.playbackState = PlaybackState(Player.STATE_IDLE, false)
        t.tracker.onMetadataChanged(pushedMetadata(item, "Network Failed"))
        t.tracker.onPlaybackStateChanged(PlaybackState(Player.STATE_IDLE, false))

        assertEquals("Network Failed", t.tracker.nowPlaying()!!.track)
        assertFalse(t.tracker.nowPlaying()!!.isPlaying)
    }

    @Test
    fun nowPlayingCarriesTheCurrentItemsDetailsAndListedStreamUrl() {
        val t = TrackerUnderTest().connect().play(MediaItemBuilder.buildPlayable(arrow()))

        val shown = t.tracker.nowPlaying()!!

        assertEquals("Arrow Classic Rock", shown.name)
        assertEquals("AC", shown.initials)
        assertEquals("http://stream.arrow.nl/arrow.pls", shown.streamUrl)
        assertEquals(StationDetails.rows(StationDetails.of(arrow())), shown.details)
        assertNull("A station without an image url has no artwork", shown.artworkUri)
    }

    @Test
    fun aColdStartTakesTheDetailsAndTheHeartFromTheRestoredStation() {
        val t = TrackerUnderTest()
        t.lastStation = arrow()
        t.favoriteIds += "ar"

        t.connect()

        val shown = t.tracker.nowPlaying()!!
        assertEquals(StationDetails.rows(StationDetails.of(arrow())), shown.details)
        assertEquals("http://stream.arrow.nl/arrow.pls", shown.streamUrl)
        assertTrue(shown.isFavorite)
        assertFalse(shown.isPlaying)
    }

    @Test
    fun anotherStationReplacesTheShownOne() {
        val t = TrackerUnderTest().connect().play(MediaItemBuilder.buildPlayable(arrow()))

        t.play(station("s2", "Capital FM"), track = "")

        assertEquals("Capital FM", t.tracker.nowPlaying()!!.name)
        assertEquals("", t.tracker.nowPlaying()!!.track)
        assertEquals(emptyList<StationDetail>(), t.tracker.nowPlaying()!!.details)
        assertEquals("s2", t.tracker.playingId)
    }

    // One heart per station (REQ-007).

    @Test
    fun theHeartOfTheShownStationMarksItWithoutAnyList() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))

        assertTrue(t.tracker.toggleCurrentFavorite())

        assertTrue(t.tracker.nowPlaying()!!.isFavorite)
        assertEquals(OpenRadioService.CMD_MARK_FAVORITE, t.client.commands.last().first)
        assertEquals("s1", OpenRadioStore.extractMediaId(t.client.commands.last().second))
    }

    @Test
    fun theHeartUnmarksAMarkedShownStation() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock", favorite = true))

        t.tracker.toggleCurrentFavorite()

        assertFalse(t.tracker.nowPlaying()!!.isFavorite)
        assertEquals(OpenRadioService.CMD_UNMARK_FAVORITE, t.client.commands.last().first)
    }

    @Test
    fun withNothingShownTheHeartDoesNothing() {
        val t = TrackerUnderTest().connect()

        assertFalse(t.tracker.toggleCurrentFavorite())
        assertTrue(t.client.commands.isEmpty())
    }

    @Test
    fun aRowHeartOfTheShownStationShowsOnNowPlaying() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))

        t.tracker.toggleFavorite(station("s1"))

        assertTrue(t.tracker.nowPlaying()!!.isFavorite)
    }

    @Test
    fun theNowPlayingHeartShowsOnTheStationsRow() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))

        t.tracker.toggleCurrentFavorite()

        assertTrue(t.tracker.isFavorite(station("s1", favorite = false)))
    }

    @Test
    fun aSearchResultOfTheCurrentStationSharesItsHeart() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))
        val searchRow = station(MediaId.makeSearchId("s1"))

        t.tracker.toggleFavorite(searchRow)

        assertTrue(t.tracker.nowPlaying()!!.isFavorite)
        assertTrue(t.tracker.isFavorite(station("s1")))
        assertEquals(MediaId.makeSearchId("s1"), OpenRadioStore.extractMediaId(t.client.commands.last().second))
    }

    @Test
    fun aFreshAnswerHoldingTheShownStationWinsAndIsLearnt() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock"))
        t.tracker.toggleCurrentFavorite()

        t.tracker.onAnswer(listOf(station("s1", favorite = true)))
        t.tracker.onAnswer(listOf(station("other")))

        assertTrue("The answer's flag is the station's flag now", t.tracker.nowPlaying()!!.isFavorite)

        t.tracker.onAnswer(listOf(station("s1", favorite = false)))

        assertFalse(t.tracker.nowPlaying()!!.isFavorite)
        assertFalse(t.tracker.isFavorite(station("s1", favorite = false)))
    }

    @Test
    fun anAnswerBeforeTheMetadataGivesTheShownStationItsHeart() {
        val t = TrackerUnderTest().connect()
        t.tracker.onAnswer(listOf(station("s1", favorite = true)))

        t.play(station("s1", "Arrow Classic Rock", favorite = false))

        assertTrue(t.tracker.nowPlaying()!!.isFavorite)
    }

    @Test
    fun anAnswerBeforeTheMetadataOutlinesAStationItsItemMarks() {
        val t = TrackerUnderTest().connect()
        t.tracker.onAnswer(listOf(station("s1", favorite = false)))

        t.play(station("s1", "Arrow Classic Rock", favorite = true))

        assertFalse(t.tracker.nowPlaying()!!.isFavorite)
    }

    @Test
    fun theMetadataBeforeAnAnswerTakesTheAnswersHeart() {
        val t = TrackerUnderTest().connect().play(station("s1", "Arrow Classic Rock", favorite = false))

        t.tracker.onAnswer(listOf(station("s1", favorite = true)))

        assertTrue(t.tracker.nowPlaying()!!.isFavorite)
    }

    @Test
    fun aLaterAnswerReplacesAnEarlierOne() {
        val t = TrackerUnderTest().connect()
        t.tracker.onAnswer(listOf(station("s1", favorite = true)))
        t.tracker.onAnswer(listOf(station("s1", favorite = false)))

        t.play(station("s1", "Arrow Classic Rock", favorite = true))

        assertFalse(t.tracker.nowPlaying()!!.isFavorite)
    }

    @Test
    fun aHeartTappedSinceTheLastAnswerStillShowsTheTap() {
        val t = TrackerUnderTest().connect()
        t.tracker.onAnswer(listOf(station("s1", favorite = true)))
        t.play(station("s1", "Arrow Classic Rock", favorite = false))

        t.tracker.toggleCurrentFavorite()

        assertFalse(t.tracker.nowPlaying()!!.isFavorite)

        val item = station("s1", "Arrow Classic Rock", favorite = false)
        t.play(item, track = "Iggy Pop - Candy (live)")

        assertFalse("A later metadata push keeps the tap", t.tracker.nowPlaying()!!.isFavorite)
    }

    @Test
    fun anAnswerOfASearchResultGivesThePlainStationItsHeart() {
        val t = TrackerUnderTest().connect()
        t.tracker.onAnswer(listOf(station(MediaId.makeSearchId("s1"), favorite = true)))

        t.play(station("s1", "Arrow Classic Rock", favorite = false))

        assertTrue(t.tracker.nowPlaying()!!.isFavorite)
    }

    private class TrackerUnderTest {

        var lastStation: RadioStation = RadioStation.INVALID_INSTANCE
        val favoriteIds = mutableSetOf<String>()
        var client = FakeLibraryClient(SILENT_LISTENER)
        val tracker = NowPlayingTracker(
            { lastStation },
            { favoriteIds.contains(it.id) },
            CoroutineScope(Dispatchers.Unconfined)
        )

        init {
            tracker.attach(client)
        }

        fun connect(): TrackerUnderTest {
            tracker.onConnected()
            return this
        }

        fun play(
            item: MediaItem,
            track: String = "Iggy Pop - Candy",
            state: PlaybackState = PlaybackState(Player.STATE_READY, true),
        ): TrackerUnderTest {
            client.currentMediaItem = item
            client.playbackState = state
            tracker.onMetadataChanged(pushedMetadata(item, track))
            return this
        }

        /** A service created again: a new client with an empty queue, connected. */
        fun reconnect() {
            tracker.detach()
            client = FakeLibraryClient(SILENT_LISTENER)
            tracker.attach(client)
            tracker.onConnected()
        }
    }

    private companion object {

        fun lastStation(id: String, name: String): RadioStation {
            return RadioStation.makeDefaultInstance(id).apply { this.name = name }
        }

        fun arrow(): RadioStation = RadioStation.makeDefaultInstance("ar").apply {
            name = "Arrow Classic Rock"
            country = "Netherlands"
            genre = "Classic rock"
            codec = "MP3"
            homePage = "https://arrow.nl"
            setVariant(192, "http://stream.arrow.nl/arrow.pls")
        }
    }
}
