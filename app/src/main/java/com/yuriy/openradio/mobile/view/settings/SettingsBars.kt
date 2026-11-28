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

package com.yuriy.openradio.mobile.view.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleIconButton
import com.github.jcszymansk.console.design.ConsoleTopBar
import com.github.jcszymansk.console.design.SmallPrimaryButton
import com.yuriy.openradio.mobile.R

/**
 * A sub-screen's top bar: DESIGN.md gives sub-screens a back arrow.
 */
@Composable
internal fun SubScreenBar(title: String, onBack: () -> Unit) {
    ConsoleTopBar(
        title = title,
        navigation = {
            ConsoleIconButton(
                Icons.AutoMirrored.Outlined.ArrowBack,
                stringResource(R.string.main_back),
                onBack,
                Modifier.testTag(SettingsTags.BACK),
            )
        },
    )
}

/**
 * An edit screen's top bar: a close icon and a Save button.
 */
@Composable
internal fun EditScreenBar(title: String, onClose: () -> Unit, onSave: () -> Unit) {
    ConsoleTopBar(
        title = title,
        navigation = {
            ConsoleIconButton(
                Icons.Outlined.Close,
                stringResource(R.string.settings_close),
                onClose,
                Modifier.testTag(SettingsTags.CLOSE),
            )
        },
        actions = {
            SmallPrimaryButton(
                stringResource(R.string.settings_save),
                onSave,
                Modifier.testTag(SettingsTags.SAVE),
            )
        },
    )
}

/**
 * The whole-screen frame of every settings screen, clear of the navigation bar and the keyboard.
 */
@Composable
internal fun SettingsFrame(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Console.colors.background)
            .navigationBarsPadding()
            .imePadding()
            .testTag(tag),
        content = content,
    )
}
