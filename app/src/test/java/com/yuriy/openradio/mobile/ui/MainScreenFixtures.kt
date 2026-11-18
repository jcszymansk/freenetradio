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

import com.yuriy.openradio.mobile.view.main.MainScreenActions
import com.yuriy.openradio.mobile.view.main.OverflowItem
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.presenter.main.EmptyKind
import com.yuriy.openradio.shared.presenter.main.ListContent
import com.yuriy.openradio.shared.presenter.main.MainScreenRows
import com.yuriy.openradio.shared.presenter.main.MainScreenState
import com.yuriy.openradio.shared.presenter.main.MiniPlayerState
import com.yuriy.openradio.shared.presenter.main.PlayerScreen
import com.yuriy.openradio.shared.presenter.main.RowState
import com.yuriy.openradio.shared.presenter.main.ScrollPosition
import com.yuriy.openradio.shared.presenter.main.SectionKind
import com.yuriy.openradio.shared.presenter.main.Tab
import com.yuriy.openradio.R as CommonR

/**
 * Fixed main screen states for the screenshot tests. The POPULAR list is the one in
 * `console-design/design-reference/png/freenetradio-browse-*.png`.
 */
internal object MainScreenFixtures {

    val reference: List<RowState> = listOf(
        station("ar", "Arrow Classic Rock", "NL", "MP3", 192, favorite = true, current = true),
        station("hr", "Hard Rock Radio FM", "US", "MP3", 128),
        station("ra", "ROCKANTENNE Alternative", "DE", "MP3", 128, favorite = true),
        station("rd", "RdMix Classic Rock 70s 80s 90s", "CA", "AAC", 128),
        station("ib", "100 Best Ibiza Deep House", "CA", "MP3", 128),
        station("dj", "DJ & Club Charts", "DE", "MP3", 192),
        station("50", "50s 60s Retro Hits", "IT", "MP3", 128),
        station("ca", "Capital", "GB", "AAC", 48),
        station("sj", "SmoothJazz.com", "US", "AAC", 64),
        station("rp", "Radio Paradise", "US", "FLAC", 320),
    )

    val playing = MiniPlayerState(
        name = "Arrow Classic Rock",
        initials = MainScreenRows.initials("Arrow Classic Rock"),
        track = "Iggy Pop & Kate Pierson — Candy",
        isPlaying = true,
    )

    private val countries: List<RowState> = listOf(
        country(MediaId.MEDIA_ID_COUNTRIES_LIST + "AT", "Austria", CommonR.drawable.flag_at),
        country(MediaId.MEDIA_ID_COUNTRIES_LIST + "BE", "Belgium", CommonR.drawable.flag_be),
        country(MediaId.MEDIA_ID_COUNTRIES_LIST + "DE", "Germany", CommonR.drawable.flag_de),
        country(MediaId.MEDIA_ID_COUNTRIES_LIST + "NL", "Netherlands", CommonR.drawable.flag_nl),
        country(MediaId.MEDIA_ID_COUNTRIES_LIST + "PL", "Poland", CommonR.drawable.flag_pl),
    )

    val states: Map<String, MainScreenState> = mapOf(
        "popular_playing" to state(Tab.POPULAR, ListContent.Loaded(reference), miniPlayer = playing, playingId = "ar"),
        "popular_paused" to state(Tab.POPULAR, ListContent.Loaded(reference), miniPlayer = playing.copy(isPlaying = false), playingId = "ar"),
        "fav_loaded" to state(
            Tab.FAV,
            ListContent.Loaded(listOf(reference[0], reference[2]).map { (it as RowState.Station).copy(canLongPress = true, isCurrent = false) }),
        ),
        "mine_loaded" to state(
            Tab.MINE,
            ListContent.Loaded(listOf(station("m1", "Radio Kampus", "PL", "MP3", 128, longPress = true), station("m2", "Home stream", "", "", 0, longPress = true))),
        ),
        "new_loaded" to state(Tab.NEW, ListContent.Loaded(reference.drop(5).map { (it as RowState.Station).copy(isCurrent = false) })),
        "genre_loaded" to state(
            Tab.GENRE,
            ListContent.Loaded(listOf("Ambient", "Classical", "Jazz", "News", "Pop", "Rock").map { RowState.Category(MediaId.MEDIA_ID_CHILD_CATEGORIES + it, it, null) }),
        ),
        "country_your_country" to state(
            Tab.COUNTRY,
            ListContent.Loaded(
                listOf(RowState.Section(SectionKind.YOUR_COUNTRY), country(MediaId.MEDIA_ID_COUNTRY_STATIONS, "Poland", CommonR.drawable.flag_pl), RowState.Section(SectionKind.ALL_COUNTRIES)) + countries
            ),
        ),
        "country_plain" to state(Tab.COUNTRY, ListContent.Loaded(countries)),
        "country_drill_down" to state(
            Tab.COUNTRY,
            ListContent.Loaded(listOf(station("p1", "Radio Nowy Świat", "PL", "MP3", 128), station("p2", "Polskie Radio Program III", "PL", "AAC", 96))),
            title = "Poland",
        ),
        "loading" to state(Tab.POPULAR, ListContent.Loading),
        "empty_fav" to state(Tab.FAV, ListContent.Empty(EmptyKind.NO_FAVORITES)),
        "empty_mine" to state(Tab.MINE, ListContent.Empty(EmptyKind.NO_STATIONS)),
        "error" to state(Tab.POPULAR, ListContent.Error),
    )

    val noActions = object : MainScreenActions {
        override fun onSelectTab(tab: Tab) = Unit
        override fun onBack() = Unit
        override fun onSearch() = Unit
        override fun onOverflow(item: OverflowItem) = Unit
        override fun onRowClick(rowId: String) = Unit
        override fun onRowLongClick(rowId: String) = Unit
        override fun onFavoriteClick(rowId: String) = Unit
        override fun onLoadMore() = Unit
        override fun onRetry() = Unit
        override fun onTogglePlayback() = Unit
        override fun onAddStation() = Unit
        override fun onScroll(levelKey: String, position: ScrollPosition) = Unit
    }

    private fun state(
        tab: Tab,
        content: ListContent,
        title: String? = null,
        miniPlayer: MiniPlayerState? = null,
        playingId: String? = null,
    ) = MainScreenState(
        tabs = Tab.entries.toList(),
        selectedTab = tab,
        title = title,
        canGoBack = title != null,
        levelKey = "fixture",
        scroll = ScrollPosition(),
        content = content,
        isRefreshing = false,
        isLoadingPage = false,
        playingId = playingId,
        miniPlayer = miniPlayer,
        showAddFab = tab == Tab.MINE,
        isClosed = false,
        playerScreen = PlayerScreen.NONE,
        nowPlaying = null,
    )

    private fun station(
        id: String,
        name: String,
        countryCode: String,
        codec: String,
        kbps: Int,
        favorite: Boolean = false,
        current: Boolean = false,
        longPress: Boolean = false,
    ) = RowState.Station(
        id = id,
        name = name,
        initials = MainScreenRows.initials(name),
        meta = MainScreenRows.meta(countryCode, codec),
        bitrate = MainScreenRows.bitrate(kbps),
        isFavorite = favorite,
        isCurrent = current,
        canLongPress = longPress,
        artworkUri = null,
    )

    private fun country(id: String, title: String, flag: Int) = RowState.Category(id, title, flag)
}
