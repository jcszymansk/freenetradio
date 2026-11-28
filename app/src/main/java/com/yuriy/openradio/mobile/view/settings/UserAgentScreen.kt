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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.ConsoleTextField
import com.github.jcszymansk.console.design.FieldLabel
import com.github.jcszymansk.console.design.SettingSwitchRow
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.R as SharedR
import com.yuriy.openradio.shared.presenter.settings.UserAgentState

private const val SCREEN_PADDING = 16

private const val FIELD_LINES = 3

/**
 * The user agent sub-screen (REQ-013): the "Custom user agent" switch and a three-line mono field
 * that takes focus only while the switch is on. While the switch is on and the field holds
 * nothing, the error line under it says the value will not be saved.
 */
@Composable
internal fun UserAgentScreen(state: UserAgentState, actions: SettingsActions) {
    val focusManager = LocalFocusManager.current
    SettingsFrame(SettingsTags.USER_AGENT_SCREEN) {
        SubScreenBar(stringResource(R.string.settings_user_agent), actions::onBack)
        SettingSwitchRow(
            stringResource(R.string.settings_user_agent_custom),
            state.custom,
            { on ->
                if (!on) {
                    focusManager.clearFocus()
                }
                actions.onCustomUserAgent(on)
            },
            Modifier.testTag(SettingsTags.USER_AGENT_SWITCH),
        )
        Column(Modifier.padding(SCREEN_PADDING.dp)) {
            FieldLabel(stringResource(R.string.settings_user_agent))
            ConsoleTextField(
                state.text,
                actions::onEditUserAgent,
                Modifier
                    .disabledLook(!state.custom)
                    .focusProperties { canFocus = state.custom }
                    .testTag(SettingsTags.USER_AGENT_FIELD),
                mono = true,
                singleLine = false,
                minLines = FIELD_LINES,
            )
            if (state.emptyWarning) {
                FieldError(
                    stringResource(SharedR.string.user_agent_empty_warning),
                    Modifier.testTag(SettingsTags.USER_AGENT_ERROR),
                )
            }
        }
    }
}
