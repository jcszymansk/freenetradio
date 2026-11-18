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

package com.yuriy.openradio.mobile.ui

import com.yuriy.openradio.mobile.view.main.NowPlayingActions
import com.yuriy.openradio.mobile.view.main.PlayerMenuItem
import com.yuriy.openradio.shared.model.storage.images.ImagesStore
import com.yuriy.openradio.shared.presenter.main.MainScreenRows
import com.yuriy.openradio.shared.presenter.main.NowPlayingState
import com.yuriy.openradio.shared.presenter.main.PlayerScreen
import com.yuriy.openradio.shared.presenter.main.StationDetails
import com.yuriy.openradio.shared.presenter.main.StationFacts

/**
 * Fixed Now playing states for the screenshot tests. `playing` is the state of
 * `console-design/design-reference/png/freenetradio-now-playing-*.png`: no artwork, a favourite,
 * a track line.
 */
internal object NowPlayingFixtures {

    private val everyFact = StationFacts(
        country = "Netherlands",
        genre = "Classic rock",
        codec = "MP3",
        bitrate = 192,
        streamUrl = "http://stream.arrow.nl/arrow.pls",
        homePage = "https://arrow.nl",
    )

    val playing = NowPlayingState(
        name = "Arrow Classic Rock",
        initials = MainScreenRows.initials("Arrow Classic Rock"),
        track = "Candy — Iggy Pop & Kate Pierson",
        artworkUri = null,
        isPlaying = true,
        isBuffering = false,
        isFavorite = true,
        streamUrl = everyFact.streamUrl,
        details = StationDetails.rows(everyFact),
    )

    /** An images-provider uri, as a station with a logo carries; the test's cache answers it. */
    val artworkUri: String = ImagesStore.buildImageUri("ar", "https://arrow.nl/logo.png").toString()

    val states: Map<String, Pair<NowPlayingState, PlayerScreen>> = mapOf(
        "playing" to (playing to PlayerScreen.NOW_PLAYING),
        "artwork" to (playing.copy(artworkUri = artworkUri) to PlayerScreen.NOW_PLAYING),
        "paused" to (playing.copy(isPlaying = false) to PlayerScreen.NOW_PLAYING),
        "buffering" to (playing.copy(isBuffering = true, track = "Buffering…") to PlayerScreen.NOW_PLAYING),
        "error" to (playing.copy(isPlaying = false, track = "Network Failed") to PlayerScreen.NOW_PLAYING),
        "no_track" to (playing.copy(track = "") to PlayerScreen.NOW_PLAYING),
        "not_favorite" to (playing.copy(isFavorite = false) to PlayerScreen.NOW_PLAYING),
        "details_all" to (playing to PlayerScreen.STATION_DETAILS),
        "details_missing" to (
            playing.copy(details = StationDetails.rows(everyFact.copy(genre = "", bitrate = 0, homePage = ""))) to
                PlayerScreen.STATION_DETAILS
            ),
    )

    val noActions = object : NowPlayingActions {
        override fun onClosePlayer() = Unit
        override fun onTogglePlayback() = Unit
        override fun onToggleCurrentFavorite() = Unit
        override fun onPlayerMenu(item: PlayerMenuItem) = Unit
        override fun onCloseStationDetails() = Unit
        override fun onOpenHomePage(url: String) = Unit
    }
}
