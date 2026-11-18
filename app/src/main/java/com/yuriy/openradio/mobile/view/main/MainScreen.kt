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

package com.yuriy.openradio.mobile.view.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleFab
import com.github.jcszymansk.console.design.MonoTabs
import com.github.jcszymansk.console.design.SectionHeader
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.presenter.main.EmptyKind
import com.yuriy.openradio.shared.presenter.main.ListContent
import com.yuriy.openradio.shared.presenter.main.MainScreenState
import com.yuriy.openradio.shared.presenter.main.RowState
import com.yuriy.openradio.shared.presenter.main.ScrollPosition
import com.yuriy.openradio.shared.presenter.main.SectionKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * The main screen: top bar, tabs, the selected tab's list and the mini player. A function of
 * [state]; every control reports to [actions].
 */
@Composable
fun MainScreen(state: MainScreenState, actions: MainScreenActions, modifier: Modifier = Modifier) {
    val c = Console.colors
    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .navigationBarsPadding()
    ) {
        MainTopBar(state.title ?: stringResource(R.string.main_title), state.canGoBack, actions)
        MonoTabs(
            tabs = state.tabs.map { stringResource(it.labelRes) },
            selected = state.tabs.indexOf(state.selectedTab),
            onSelect = { actions.onSelectTab(state.tabs[it]) },
            modifier = Modifier.testTag(MainScreenTags.TABS),
        )
        if (state.content == ListContent.Loading || state.isRefreshing) {
            ProgressLine(Modifier.testTag(MainScreenTags.PROGRESS))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val content = state.content) {
                ListContent.Loading -> Unit
                ListContent.Error -> ErrorLine(actions)
                is ListContent.Empty -> EmptyLine(content.kind)
                is ListContent.Loaded -> RowsList(content.rows, state, actions)
            }
            if (state.showAddFab) {
                ConsoleFab(
                    stringResource(R.string.main_add),
                    Icons.Outlined.Add,
                    actions::onAddStation,
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag(MainScreenTags.FAB_ADD),
                )
            }
        }
        state.miniPlayer?.let { MiniPlayer(it, actions::onTogglePlayback) }
    }
}

@Composable
private fun RowsList(rows: List<RowState>, state: MainScreenState, actions: MainScreenActions) {
    val levelKey = state.levelKey
    val listState = remember(levelKey) { LazyListState(state.scroll.index, state.scroll.offset) }
    LaunchedEffect(listState, levelKey) {
        snapshotFlow { ScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { actions.onScroll(levelKey, it) }
    }
    LaunchedEffect(listState, levelKey) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { actions.onLoadMore() }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(MainScreenTags.LIST)) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is RowState.Station -> StationRow(row, actions)
                is RowState.Category -> CategoryRow(row, actions)
                is RowState.Section -> SectionHeader(
                    stringResource(row.section.labelRes()),
                    Modifier.testTag(MainScreenTags.section(row.section)),
                )
            }
        }
        if (state.isLoadingPage) {
            item(key = "page-progress") {
                ProgressLine(
                    Modifier
                        .padding(vertical = 12.dp)
                        .testTag(MainScreenTags.PAGE_PROGRESS)
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp),
        color = Console.colors.accent,
        trackColor = Console.colors.divider,
    )
}

@Composable
private fun EmptyLine(kind: EmptyKind) {
    Text(
        stringResource(
            when (kind) {
                EmptyKind.NO_FAVORITES -> R.string.main_empty_favorites
                EmptyKind.NO_STATIONS -> R.string.main_empty_stations
                EmptyKind.NOTHING_FOUND -> R.string.main_empty_nothing
            }
        ),
        style = Console.type.body,
        color = Console.colors.textSecondary,
        modifier = Modifier
            .padding(16.dp)
            .testTag(MainScreenTags.EMPTY),
    )
}

@Composable
private fun ErrorLine(actions: MainScreenActions) {
    Column(
        Modifier
            .padding(16.dp)
            .testTag(MainScreenTags.ERROR)
    ) {
        Text(stringResource(R.string.main_error), style = Console.type.body, color = Console.colors.textSecondary)
        Text(
            stringResource(R.string.main_retry),
            style = Console.type.button,
            color = Console.colors.accent,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(role = Role.Button, onClick = actions::onRetry)
                .testTag(MainScreenTags.RETRY),
        )
    }
}

private fun SectionKind.labelRes(): Int = when (this) {
    SectionKind.YOUR_COUNTRY -> R.string.main_section_your_country
    SectionKind.ALL_COUNTRIES -> R.string.main_section_all_countries
}
