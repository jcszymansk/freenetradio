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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.PrimaryButton
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.main.ProgressLine
import com.yuriy.openradio.shared.R as SharedR
import com.yuriy.openradio.shared.presenter.settings.DiagnosticsState

private const val SCREEN_PADDING = 16

private const val CONTENT_SPACING = 24

/**
 * The diagnostic report sub-screen (REQ-018): what a report contains and the one primary action.
 * While logs are collected a progress line shows under the top bar and the button is dimmed; the
 * model ignores the tap as well.
 */
@Composable
internal fun DiagnosticsScreen(state: DiagnosticsState, actions: SettingsActions) {
    SettingsFrame(SettingsTags.DIAGNOSTICS_SCREEN) {
        SubScreenBar(stringResource(R.string.settings_diagnostics_title), actions::onBack)
        if (state.collecting) {
            ProgressLine(Modifier.testTag(SettingsTags.DIAGNOSTICS_PROGRESS))
        }
        Column(
            Modifier.padding(SCREEN_PADDING.dp),
            verticalArrangement = Arrangement.spacedBy(CONTENT_SPACING.dp),
        ) {
            Text(
                stringResource(SharedR.string.diagnostic_report_warning),
                style = Console.type.body,
                color = Console.colors.text,
            )
            PrimaryButton(
                stringResource(R.string.settings_diagnostics_create),
                actions::onCreateReport,
                Modifier
                    .fillMaxWidth()
                    .disabledLook(!state.canCreate)
                    .testTag(SettingsTags.CREATE_REPORT),
            )
        }
    }
}
