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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleDimens
import com.github.jcszymansk.console.design.bottomHairline
import com.yuriy.openradio.shared.presenter.main.RowState

/**
 * A country or genre: its name, and a country's flag where the item has one. No heart, no
 * long-press; a tap drills down.
 */
@Composable
internal fun CategoryRow(row: RowState.Category, actions: MainScreenActions) {
    val c = Console.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(ConsoleDimens.listRowHeight)
            .bottomHairline(c.dividerRow)
            .clickable { actions.onRowClick(row.id) }
            .padding(horizontal = 16.dp)
            .testTag(MainScreenTags.row(row.id)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.iconResId?.let { icon ->
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(ConsoleDimens.tileSize))
        }
        Text(
            row.title,
            style = Console.type.bodyStrong,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(MainScreenTags.rowTitle(row.id)),
        )
    }
}
