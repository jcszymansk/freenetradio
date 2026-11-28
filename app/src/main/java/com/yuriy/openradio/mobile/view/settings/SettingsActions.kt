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

import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.settings.BufferField
import java.io.File

/**
 * What the settings screens' controls ask for, which `SettingsPlatform` answers and screenshot
 * tests answer with nothing.
 */
interface SettingsActions {

    fun onBack()

    fun onOpenStartPlayback()

    fun onOpenMasterVolume()

    fun onBluetoothAutoPlay(on: Boolean)

    fun onOpenStreamBuffering()

    fun onOpenSource()

    fun onOpenCountry()

    fun onExportStations()

    fun onImportStations()

    fun onUseMobileData(on: Boolean)

    fun onOpenUserAgent()

    fun onClearCache()

    fun onOpenDiagnostics()

    fun onOpenAbout()

    fun onChooseStartPlayback(value: StartPlayback)

    fun onChooseSource(source: Source)

    fun onRestart()

    fun onChooseCountry(code: String)

    fun onStepMasterVolume(delta: Int)

    fun onEditBuffer(field: BufferField, text: String)

    fun onRestoreBufferDefaults()

    fun onSaveBuffering()

    fun onCloseBuffering()

    fun onCustomUserAgent(on: Boolean)

    fun onEditUserAgent(text: String)

    fun onCreateReport()

    fun onOpenLink(url: String)
}

/**
 * What a settings screen's state asks the platform to do once, things only an Activity can do.
 * [onRequestBluetoothPermission] is called when `SettingsState.bluetoothRequest` is set and asks
 * for `BLUETOOTH_CONNECT`; [onShareReport] is called when `DiagnosticsState.report` is set and
 * opens the share chooser for it.
 */
interface SettingsEffects {

    fun onRequestBluetoothPermission()

    fun onShareReport(report: File)
}
