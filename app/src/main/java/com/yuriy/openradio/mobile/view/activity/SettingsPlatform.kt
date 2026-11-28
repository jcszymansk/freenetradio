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

package com.yuriy.openradio.mobile.view.activity

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.yuriy.openradio.mobile.view.settings.SettingsActions
import com.yuriy.openradio.mobile.view.settings.SettingsEffects
import com.yuriy.openradio.shared.dependencies.SettingsModels
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.storage.FileStoreManager
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.settings.BufferField
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.IntentUtils
import com.yuriy.openradio.shared.R as SharedR
import java.io.File

/**
 * What the settings screens need from the Activity, which their state holders must not touch: the
 * document UIs and the Bluetooth permission through activity result launchers, the report's share
 * chooser and the About links. Every other action goes straight to the state holder it belongs
 * to. Built in `onCreate`, before the activity is started, as activity result launchers must be;
 * a result that arrives after a recreation reaches the launchers of the new activity.
 */
internal class SettingsPlatform(
    private val mActivity: AppCompatActivity,
    private val mModels: SettingsModels,
    private val mFiles: FileStoreManager,
) : SettingsActions, SettingsEffects {

    private val mExportLauncher: ActivityResultLauncher<Intent> =
        mActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            mModels.transfers.exportTo(FileStoreManager.documentOf(result.resultCode, result.data))
        }

    private val mImportLauncher: ActivityResultLauncher<Intent> =
        mActivity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            mModels.transfers.importFrom(FileStoreManager.documentOf(result.resultCode, result.data))
        }

    private val mBluetoothLauncher: ActivityResultLauncher<String> =
        mActivity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            mModels.settings.onBluetoothPermission(granted)
        }

    override fun onBack() {
        mModels.screens.pop()
    }

    override fun onOpenStartPlayback() = mModels.settings.openStartPlayback()

    override fun onOpenMasterVolume() = mModels.settings.openMasterVolume()

    override fun onBluetoothAutoPlay(on: Boolean) = mModels.settings.setBluetoothAutoPlay(on)

    override fun onOpenStreamBuffering() = mModels.buffering.open()

    override fun onOpenSource() = mModels.source.open()

    override fun onOpenCountry() = mModels.settings.openCountry()

    override fun onExportStations() = launch(mExportLauncher, mFiles.exportIntent())

    override fun onImportStations() = launch(mImportLauncher, mFiles.importIntent())

    override fun onUseMobileData(on: Boolean) = mModels.settings.setUseMobileData(on)

    override fun onOpenUserAgent() = mModels.userAgent.open()

    override fun onClearCache() = mModels.settings.clearCache()

    override fun onOpenDiagnostics() = mModels.diagnostics.open()

    override fun onOpenAbout() = mModels.settings.openAbout()

    override fun onChooseStartPlayback(value: StartPlayback) = mModels.settings.chooseStartPlayback(value)

    override fun onChooseSource(source: Source) = mModels.source.choose(source)

    override fun onRestart() = mModels.source.restart()

    override fun onChooseCountry(code: String) = mModels.settings.chooseCountry(code)

    override fun onStepMasterVolume(delta: Int) = mModels.settings.stepMasterVolume(delta)

    override fun onEditBuffer(field: BufferField, text: String) = mModels.buffering.edit(field, text)

    override fun onRestoreBufferDefaults() = mModels.buffering.restoreDefaults()

    override fun onSaveBuffering() = mModels.buffering.save()

    override fun onCloseBuffering() = mModels.buffering.close()

    override fun onCustomUserAgent(on: Boolean) = mModels.userAgent.setCustom(on)

    override fun onEditUserAgent(text: String) = mModels.userAgent.edit(text)

    override fun onCreateReport() = mModels.diagnostics.create()

    override fun onOpenLink(url: String) =
        IntentUtils.startActivitySafe(mActivity, IntentUtils.makeUrlBrowsableIntent(url))

    override fun onRequestBluetoothPermission() {
        mModels.settings.bluetoothRequestStarted()
        mBluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    override fun onShareReport(report: File) = mModels.diagnostics.reportHandled(share(report))

    private fun launch(launcher: ActivityResultLauncher<Intent>, intent: Intent) {
        try {
            launcher.launch(intent)
        } catch (exception: ActivityNotFoundException) {
            AppLogger.e("$TAG no activity for ${intent.action}", exception)
            mModels.transfers.failedToStart()
        }
    }

    /**
     * Opens the per-report chooser with the zip through the `FileProvider`, as the general dialog
     * did.
     */
    private fun share(zip: File): Boolean {
        val uri = FileProvider.getUriForFile(mActivity, "${mActivity.packageName}.provider", zip)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_SUBJECT, mActivity.getString(SharedR.string.diagnostic_report_subject))
            putExtra(Intent.EXTRA_TEXT, mActivity.getString(SharedR.string.diagnostic_report_text))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            mActivity.startActivity(Intent.createChooser(intent, mActivity.getString(SharedR.string.diagnostic_report_share)))
            true
        } catch (exception: ActivityNotFoundException) {
            AppLogger.e("$TAG no chooser for the report", exception)
            false
        }
    }

    private companion object {

        const val TAG = "SettingsPlatform"
    }
}
