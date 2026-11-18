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

/**
 * Everything the main screen shows, as one value. The screen is a function of it.
 *
 * @param tabs the tabs the active provider can fill, in screen order.
 * @param title the drill-down or search title, or null at a tab's root, where the screen shows
 *   its own app title.
 * @param canGoBack whether the selected tab is deeper than its root.
 * @param levelKey identifies the shown stack level; the list keeps one scroll state per key.
 * @param scroll where the list stood when this level was last shown.
 * @param isRefreshing a first page is being fetched while the level's rows are on screen.
 * @param isLoadingPage a further page is being fetched.
 * @param playingId the media id of the player's current item, or null before anything was chosen.
 * @param miniPlayer the mini player, or null while it is hidden.
 * @param showAddFab whether "+ Add" is offered, which is when the locals node is on top.
 * @param isClosed the screen was closed (sleep timer, notification close); the activity finishes.
 */
data class MainScreenState(
    val tabs: List<Tab>,
    val selectedTab: Tab,
    val title: String?,
    val canGoBack: Boolean,
    val levelKey: String,
    val scroll: ScrollPosition,
    val content: ListContent,
    val isRefreshing: Boolean,
    val isLoadingPage: Boolean,
    val playingId: String?,
    val miniPlayer: MiniPlayerState?,
    val showAddFab: Boolean,
    val isClosed: Boolean,
)

data class ScrollPosition(val index: Int = 0, val offset: Int = 0)

sealed interface ListContent {

    data object Loading : ListContent

    data class Loaded(val rows: List<RowState>) : ListContent

    data class Empty(val kind: EmptyKind) : ListContent

    data object Error : ListContent
}

/**
 * Which one-line text an empty list shows.
 */
enum class EmptyKind { NO_FAVORITES, NO_STATIONS, NOTHING_FOUND }

sealed interface RowState {

    /** Unique within one list; the list's item key. */
    val key: String

    data class Station(
        val id: String,
        val name: String,
        val initials: String,
        val meta: String,
        val bitrate: String?,
        val isFavorite: Boolean,
        val isCurrent: Boolean,
        val canLongPress: Boolean,
        val artworkUri: String?,
    ) : RowState {
        override val key: String get() = id
    }

    data class Category(val id: String, val title: String, val iconResId: Int?) : RowState {
        override val key: String get() = id
    }

    data class Section(val section: SectionKind) : RowState {
        override val key: String get() = "section:${section.name}"
    }
}

enum class SectionKind { YOUR_COUNTRY, ALL_COUNTRIES }

data class MiniPlayerState(
    val name: String,
    val initials: String,
    val track: String,
    val isPlaying: Boolean,
)

/**
 * The selected tab and every tab's stack as media ids, bottom first. Read by tests to check and
 * restore where the singleton model stands.
 */
data class BrowseSnapshot(val selectedTab: Tab, val stacks: Map<Tab, List<String>>)

/**
 * What the station settings dialog needs for a long-pressed row.
 */
data class StationSettingsTarget(val item: MediaItem, val parentId: String, val itemsCount: Int)

/**
 * Which player screen covers the main screen, if any. Station details is a level inside Now
 * playing: back from it returns to Now playing.
 */
enum class PlayerScreen { NONE, NOW_PLAYING, STATION_DETAILS }

/**
 * Now playing and its Station details, as one value.
 *
 * @param track the stream's text as the stream sends it, empty when it sends none.
 * @param artworkUri the station's artwork, or null when it has none and the initials are shown.
 * @param isPlaying the button offers Pause: the player plays, or buffers a station it was asked
 *   to play.
 * @param isBuffering the player is filling its buffer for a station it was asked to play.
 * @param isFavorite the heart, as the lists show it for the same station.
 * @param streamUrl the station's listed stream address, empty when it has none.
 * @param details the Station details rows, in screen order.
 */
data class NowPlayingState(
    val name: String,
    val initials: String,
    val track: String,
    val artworkUri: String?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val isFavorite: Boolean,
    val streamUrl: String,
    val details: List<StationDetail>,
)

/**
 * The rows Station details can show, in screen order.
 */
enum class DetailKind { COUNTRY, GENRE, STREAM, URL, WEB }

/**
 * One row of Station details. [link] is the address a WEB row opens; no other row has one.
 */
data class StationDetail(val kind: DetailKind, val value: String, val link: String? = null)
