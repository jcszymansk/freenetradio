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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.github.jcszymansk.console.design.SectionHeader
import com.github.jcszymansk.console.design.SettingRow
import com.github.jcszymansk.console.design.SettingSwitchRow
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.main.ProgressLine
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.settings.SettingsState

/**
 * The Settings screen of REQ-004: the PLAYBACK, STATIONS, NETWORK, DIAGNOSTICS and ABOUT sections,
 * every value in mono on the right. [transferring] draws the progress line between the top bar and
 * the list while a stations file is written or read.
 */
@Composable
internal fun SettingsScreen(state: SettingsState, transferring: Boolean, actions: SettingsActions) {
    SettingsFrame(SettingsTags.SCREEN) {
        SubScreenBar(stringResource(R.string.settings_title), actions::onBack)
        if (transferring) {
            ProgressLine(Modifier.testTag(SettingsTags.PROGRESS))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .testTag(SettingsTags.LIST),
        ) {
            SectionHeader(stringResource(R.string.settings_section_playback))
            SettingRow(
                stringResource(R.string.settings_start_playback),
                stringResource(state.startPlayback.labelRes()).uppercase(),
                actions::onOpenStartPlayback,
                Modifier.testTag(SettingsTags.START_PLAYBACK),
            )
            SettingRow(
                stringResource(R.string.settings_master_volume),
                stringResource(R.string.settings_volume_value, state.masterVolume),
                actions::onOpenMasterVolume,
                Modifier.testTag(SettingsTags.MASTER_VOLUME),
            )
            SettingSwitchRow(
                stringResource(R.string.settings_bt_auto_play),
                state.btAutoPlay,
                actions::onBluetoothAutoPlay,
                Modifier.testTag(SettingsTags.BT_AUTO_PLAY),
            )
            SettingRow(
                stringResource(R.string.settings_stream_buffering),
                customValue(state.bufferingCustom),
                actions::onOpenStreamBuffering,
                Modifier.testTag(SettingsTags.STREAM_BUFFERING),
            )
            SectionHeader(stringResource(R.string.settings_section_stations))
            SettingRow(
                stringResource(R.string.settings_source),
                state.source.srcName.uppercase(),
                actions::onOpenSource,
                Modifier.testTag(SettingsTags.SOURCE),
            )
            SettingRow(
                stringResource(R.string.settings_default_country),
                state.countryCode.uppercase(),
                actions::onOpenCountry,
                Modifier.testTag(SettingsTags.DEFAULT_COUNTRY),
            )
            SettingRow(
                stringResource(R.string.settings_export),
                null,
                actions::onExportStations,
                Modifier.testTag(SettingsTags.EXPORT),
            )
            SettingRow(
                stringResource(R.string.settings_import),
                null,
                actions::onImportStations,
                Modifier.testTag(SettingsTags.IMPORT),
            )
            SectionHeader(stringResource(R.string.settings_section_network))
            SettingSwitchRow(
                stringResource(R.string.settings_mobile_data),
                state.useMobileData,
                actions::onUseMobileData,
                Modifier.testTag(SettingsTags.MOBILE_DATA),
            )
            SettingRow(
                stringResource(R.string.settings_user_agent),
                customValue(state.userAgentCustom),
                actions::onOpenUserAgent,
                Modifier.testTag(SettingsTags.USER_AGENT),
            )
            SettingRow(
                stringResource(R.string.settings_clear_cache),
                null,
                actions::onClearCache,
                Modifier.testTag(SettingsTags.CLEAR_CACHE),
            )
            SectionHeader(stringResource(R.string.settings_section_diagnostics))
            SettingRow(
                stringResource(R.string.settings_diagnostics),
                null,
                actions::onOpenDiagnostics,
                Modifier.testTag(SettingsTags.DIAGNOSTICS),
            )
            SectionHeader(stringResource(R.string.settings_section_about))
            SettingRow(
                stringResource(R.string.settings_about),
                state.version,
                actions::onOpenAbout,
                Modifier.testTag(SettingsTags.ABOUT),
            )
        }
    }
}

@Composable
private fun customValue(custom: Boolean): String = stringResource(
    if (custom) R.string.settings_value_custom else R.string.settings_value_default
).uppercase()

private fun StartPlayback.labelRes(): Int = when (this) {
    StartPlayback.ALWAYS -> R.string.settings_value_always
    StartPlayback.IF_PLAYING -> R.string.settings_value_if_playing
    StartPlayback.NEVER -> R.string.settings_value_never
}
