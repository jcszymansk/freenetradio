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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.PrimaryButton
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.dependencies.SettingsModels
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.screen.ChoiceKind
import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.shared.R as SharedR

/**
 * The screen of [destination], rendered from its state holder in [models].
 */
@Composable
internal fun DestinationScreen(destination: Destination, models: SettingsModels, actions: SettingsActions) {
    when (destination) {
        Destination.Settings -> {
            val state by models.settings.state.collectAsState()
            val transfers by models.transfers.state.collectAsState()
            SettingsScreen(state, transfers.running, actions)
        }

        is Destination.SettingsChoice -> ChoiceDestination(destination.kind, models, actions)

        Destination.MasterVolume -> {
            val state by models.settings.state.collectAsState()
            MasterVolumeScreen(state.masterVolume, actions)
        }

        Destination.StreamBuffering -> {
            val state by models.buffering.state.collectAsState()
            StreamBufferingScreen(state, models.buffering.bounds, actions)
        }

        Destination.UserAgent -> {
            val state by models.userAgent.state.collectAsState()
            UserAgentScreen(state, actions)
        }

        Destination.Diagnostics -> {
            val state by models.diagnostics.state.collectAsState()
            DiagnosticsScreen(state, actions)
        }

        Destination.About -> AboutScreen(models.about, actions)
    }
}

/**
 * Hands [effects] what the settings state asks for once, whichever screen shows: the Bluetooth
 * permission request, and a collected report, which reaches the chooser even when the user left
 * the diagnostics screen while it was being collected.
 */
@Composable
internal fun SettingsEffectsHost(models: SettingsModels, effects: SettingsEffects) {
    val settings by models.settings.state.collectAsState()
    val diagnostics by models.diagnostics.state.collectAsState()
    LaunchedEffect(settings.bluetoothRequest) {
        if (settings.bluetoothRequest) {
            effects.onRequestBluetoothPermission()
        }
    }
    val report = diagnostics.report
    LaunchedEffect(report) {
        if (report != null) {
            effects.onShareReport(report)
        }
    }
}

@Composable
private fun ChoiceDestination(kind: ChoiceKind, models: SettingsModels, actions: SettingsActions) {
    when (kind) {
        ChoiceKind.START_PLAYBACK -> {
            val state by models.settings.state.collectAsState()
            val labels = StartPlayback.entries.associateWith { stringResource(it.labelRes()) }
            ChoiceScreen(
                stringResource(R.string.settings_start_playback),
                startPlaybackRows(state.startPlayback) { labels.getValue(it) },
                onChoose = { index -> actions.onChooseStartPlayback(StartPlayback.entries[index]) },
                onBack = actions::onBack,
            )
        }

        ChoiceKind.SOURCE -> {
            val state by models.source.state.collectAsState()
            ChoiceScreen(
                stringResource(R.string.settings_source),
                sourceRows(state),
                onChoose = { index -> actions.onChooseSource(state.sources[index]) },
                onBack = actions::onBack,
                footer = if (state.restartPending) {
                    {
                        PrimaryButton(
                            stringResource(R.string.settings_restart),
                            actions::onRestart,
                            Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .testTag(SettingsTags.RESTART),
                        )
                    }
                } else {
                    null
                },
            )
        }

        ChoiceKind.DEFAULT_COUNTRY -> {
            val state by models.settings.state.collectAsState()
            val countries = remember { models.settings.countries() }
            ChoiceScreen(
                stringResource(R.string.settings_default_country),
                countryRows(countries, state.countryCode),
                onChoose = { index -> actions.onChooseCountry(countries[index].code) },
                onBack = actions::onBack,
            )
        }
    }
}

private fun StartPlayback.labelRes(): Int = when (this) {
    StartPlayback.ALWAYS -> SharedR.string.start_playback_always
    StartPlayback.IF_PLAYING -> SharedR.string.start_playback_if_playing
    StartPlayback.NEVER -> SharedR.string.start_playback_never
}
