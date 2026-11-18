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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleIconButton
import com.github.jcszymansk.console.design.ConsoleShapes
import com.github.jcszymansk.console.design.ConsoleTopBar
import com.github.jcszymansk.console.design.GhostIconButton
import com.github.jcszymansk.console.design.PrimaryButton
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.presenter.main.NowPlayingState
import com.yuriy.openradio.shared.presenter.main.PlayerScreen

/** The artwork box's height, from the reference's `.art`; the player's artwork cache samples to it. */
internal val ARTWORK_HEIGHT = 200.dp

/**
 * The width `flex:1` gives Pause in the reference's 320 dp button row, which also holds three
 * 56 dp ghost buttons and three 10 dp gaps. Only the heart is here (difference D1), and Pause
 * keeps its reference width, so the row is left-aligned and shorter.
 */
private val PAUSE_WIDTH = 122.dp

/**
 * Now playing, or its Station details when [screen] says so. A function of [state]; every
 * control reports to [actions].
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingState,
    screen: PlayerScreen,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
) {
    if (screen == PlayerScreen.STATION_DETAILS) {
        StationDetailsScreen(state, actions, modifier)
        return
    }
    val c = Console.colors
    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .navigationBarsPadding()
            .testTag(NowPlayingTags.SCREEN)
    ) {
        NowPlayingTopBar(state.streamUrl, actions)
        if (state.isBuffering) {
            ProgressLine(Modifier.testTag(NowPlayingTags.PROGRESS))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp)
        ) {
            Artwork(state)
            Text(
                state.name,
                style = Console.type.headline,
                color = c.text,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .testTag(NowPlayingTags.NAME),
            )
            if (state.track.isNotEmpty()) {
                Text(
                    state.track,
                    style = Console.type.body,
                    color = c.icon,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .testTag(NowPlayingTags.TRACK),
                )
            }
            Controls(state, actions)
        }
    }
}

/**
 * The chevron that closes the player ("Close player", difference D4), the dimmed title, and the
 * overflow menu.
 */
@Composable
private fun NowPlayingTopBar(streamUrl: String, actions: NowPlayingActions) {
    var menuOpen by remember { mutableStateOf(false) }
    ConsoleTopBar(
        title = stringResource(R.string.now_playing_title),
        titleStyle = Console.type.screenTitle.copy(fontSize = 16.sp),
        titleColor = Console.colors.textSecondary,
        navigation = {
            ConsoleIconButton(
                Icons.Outlined.ExpandMore,
                stringResource(R.string.now_playing_close),
                actions::onClosePlayer,
                Modifier.testTag(NowPlayingTags.CLOSE),
            )
        },
        actions = {
            Box {
                ConsoleIconButton(
                    Icons.Outlined.MoreVert,
                    stringResource(R.string.main_more),
                    { menuOpen = true },
                    Modifier.testTag(NowPlayingTags.OVERFLOW),
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Console.colors.surface1,
                ) {
                    PlayerMenuItem.offeredFor(streamUrl).forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(item.labelRes), style = Console.type.body, color = Console.colors.text)
                            },
                            onClick = {
                                menuOpen = false
                                actions.onPlayerMenu(item)
                            },
                            modifier = Modifier.testTag(NowPlayingTags.menu(item)),
                        )
                    }
                }
            }
        },
    )
}

/**
 * The station's artwork, shown whole on `surface1`, or its initials in large accent mono text.
 */
@Composable
private fun Artwork(state: NowPlayingState) {
    val uri = state.artworkUri
    val artwork = if (uri == null) null else rememberArtwork(uri, LocalPlayerArtworkCache.current)
    Box(
        Modifier
            .fillMaxWidth()
            .height(ARTWORK_HEIGHT)
            .clip(ConsoleShapes.large)
            .background(Console.colors.surface1)
            .testTag(NowPlayingTags.ARTWORK),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(artwork, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                state.initials,
                style = Console.type.data.copy(fontSize = 52.sp, lineHeight = 60.sp),
                color = Console.colors.accent,
            )
        }
    }
}

/**
 * Pause or Play, and the heart; nothing else (difference D1).
 */
@Composable
private fun Controls(state: NowPlayingState, actions: NowPlayingActions) {
    val c = Console.colors
    Row(
        Modifier.padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryButton(
            text = stringResource(if (state.isPlaying) R.string.main_pause else R.string.main_play),
            onClick = actions::onTogglePlayback,
            modifier = Modifier
                .width(PAUSE_WIDTH)
                .testTag(NowPlayingTags.TOGGLE),
            icon = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
        )
        GhostIconButton(
            icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (state.isFavorite) R.string.main_favorite_remove else R.string.main_favorite_add
            ),
            onClick = actions::onToggleCurrentFavorite,
            modifier = Modifier.testTag(NowPlayingTags.FAVORITE),
            tint = if (state.isFavorite) c.accent else c.icon,
        )
    }
}
