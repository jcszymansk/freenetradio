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

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleIconButton
import com.github.jcszymansk.console.design.ConsoleTopBar
import com.yuriy.openradio.mobile.R

/**
 * "Radio" at a tab's root, the drill-down or search title with a back arrow below it; search and
 * the overflow menu on the right. No equalizer icon: the equalizer is in the overflow menu.
 */
@Composable
internal fun MainTopBar(title: String, canGoBack: Boolean, actions: MainScreenActions) {
    var menuOpen by remember { mutableStateOf(false) }
    ConsoleTopBar(
        title = title,
        modifier = Modifier.testTag(MainScreenTags.TOP_BAR),
        navigation = if (canGoBack) {
            {
                ConsoleIconButton(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    stringResource(R.string.main_back),
                    actions::onBack,
                    Modifier.testTag(MainScreenTags.BACK),
                )
            }
        } else {
            null
        },
        actions = {
            ConsoleIconButton(
                Icons.Outlined.Search,
                stringResource(R.string.main_search),
                actions::onSearch,
                Modifier.testTag(MainScreenTags.SEARCH),
            )
            Box {
                ConsoleIconButton(
                    Icons.Outlined.MoreVert,
                    stringResource(R.string.main_more),
                    { menuOpen = true },
                    Modifier.testTag(MainScreenTags.OVERFLOW),
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Console.colors.surface1,
                ) {
                    OverflowItem.entries.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(item.labelRes), style = Console.type.body, color = Console.colors.text)
                            },
                            onClick = {
                                menuOpen = false
                                actions.onOverflow(item)
                            },
                            modifier = Modifier.testTag(MainScreenTags.overflow(item)),
                        )
                    }
                }
            }
        },
    )
}
