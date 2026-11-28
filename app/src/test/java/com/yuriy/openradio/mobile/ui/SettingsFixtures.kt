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

package com.yuriy.openradio.mobile.ui

import com.yuriy.openradio.mobile.view.settings.SettingsActions
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.settings.AboutRow
import com.yuriy.openradio.shared.presenter.settings.BufferField
import com.yuriy.openradio.shared.presenter.settings.SourceChoiceState
import com.yuriy.openradio.shared.presenter.settings.aboutRows
import com.yuriy.openradio.shared.service.location.Country
import java.io.File

/**
 * Fixed states for the settings screenshots; every value is one a user can reach.
 */
internal object SettingsFixtures {

    const val VERSION = "1.4.0.27"

    val countries: List<Country> = listOf(
        Country("Use Location", "Use Location"),
        Country("Austria", "AT"),
        Country("Belgium", "BE"),
        Country("Canada", "CA"),
        Country("Germany", "DE"),
        Country("Poland", "PL"),
    )

    val sourceIdle = SourceChoiceState(
        sources = Source.entries,
        active = Source.RADIO_BROWSER,
        restartPending = false,
    )

    val sourceRestart = SourceChoiceState(
        sources = Source.entries,
        active = Source.WEB_RADIO,
        restartPending = true,
    )

    val about: List<AboutRow> = aboutRows(VERSION, "1.11.1")

    val noActions: SettingsActions = object : SettingsActions {

        override fun onBack() = Unit
        override fun onOpenStartPlayback() = Unit
        override fun onOpenMasterVolume() = Unit
        override fun onBluetoothAutoPlay(on: Boolean) = Unit
        override fun onOpenStreamBuffering() = Unit
        override fun onOpenSource() = Unit
        override fun onOpenCountry() = Unit
        override fun onExportStations() = Unit
        override fun onImportStations() = Unit
        override fun onUseMobileData(on: Boolean) = Unit
        override fun onOpenUserAgent() = Unit
        override fun onClearCache() = Unit
        override fun onOpenDiagnostics() = Unit
        override fun onOpenAbout() = Unit
        override fun onChooseStartPlayback(value: StartPlayback) = Unit
        override fun onChooseSource(source: Source) = Unit
        override fun onRestart() = Unit
        override fun onChooseCountry(code: String) = Unit
        override fun onStepMasterVolume(delta: Int) = Unit
        override fun onEditBuffer(field: BufferField, text: String) = Unit
        override fun onRestoreBufferDefaults() = Unit
        override fun onSaveBuffering() = Unit
        override fun onCloseBuffering() = Unit
        override fun onCustomUserAgent(on: Boolean) = Unit
        override fun onEditUserAgent(text: String) = Unit
        override fun onCreateReport() = Unit
        override fun onOpenLink(url: String) = Unit
    }
}
