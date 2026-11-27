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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleShapes
import com.github.jcszymansk.console.design.InitialsTile
import com.github.jcszymansk.console.design.Tag
import com.github.jcszymansk.console.design.TagKind
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.presenter.main.MiniPlayerState

/**
 * The persistent mini player: tile, name, LIVE and the track, and the square play/pause button.
 * A tap anywhere but the button opens Now playing; the button only toggles playback, and its
 * click does not reach the row.
 */
@Composable
internal fun MiniPlayer(state: MiniPlayerState, onOpen: () -> Unit, onToggle: () -> Unit) {
    val c = Console.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(c.surface1)
            .drawBehind { drawLine(c.divider, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f) }
            .clickable(onClickLabel = stringResource(R.string.now_playing_open), onClick = onOpen)
            .padding(start = 16.dp, end = 10.dp)
            .testTag(MainScreenTags.MINI_PLAYER),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialsTile(state.initials, active = state.isPlaying)
        Column(Modifier.weight(1f)) {
            Text(
                state.name,
                style = Console.type.supporting.copy(fontWeight = FontWeight.SemiBold),
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(MainScreenTags.MINI_NAME),
            )
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(stringResource(R.string.main_live), TagKind.Accent)
                Spacer(Modifier.width(6.dp))
                Text(
                    state.track,
                    style = Console.type.dataSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(MainScreenTags.MINI_TRACK),
                )
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(ConsoleShapes.large)
                .background(c.accent)
                .clickable(role = Role.Button, onClick = onToggle)
                .testTag(MainScreenTags.MINI_TOGGLE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = stringResource(if (state.isPlaying) R.string.main_pause else R.string.main_play),
                tint = c.onAccent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
