/*
 * Copyright 2017-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import android.app.assist.AssistContent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.github.jcszymansk.console.design.ConsoleTheme
import com.yuriy.openradio.mobile.view.main.MainScreen
import com.yuriy.openradio.mobile.view.main.MainScreenActions
import com.yuriy.openradio.mobile.view.main.OverflowItem
import com.yuriy.openradio.shared.dependencies.CountrySettingDependency
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.MainScreenModelDependency
import com.yuriy.openradio.shared.dependencies.SleepTimerModelDependency
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.presenter.main.CountrySetting
import com.yuriy.openradio.shared.presenter.main.MainScreenModel
import com.yuriy.openradio.shared.presenter.main.ScrollPosition
import com.yuriy.openradio.shared.presenter.main.Tab
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.UiUtils
import com.yuriy.openradio.shared.view.dialog.AboutDialog
import com.yuriy.openradio.shared.view.dialog.AddStationDialog
import com.yuriy.openradio.shared.view.dialog.BaseDialogFragment
import com.yuriy.openradio.shared.view.dialog.BatteryOptimizationDialog
import com.yuriy.openradio.shared.view.dialog.EqualizerDialog
import com.yuriy.openradio.shared.view.dialog.FileStorageDialog
import com.yuriy.openradio.shared.view.dialog.GeneralSettingsDialog
import com.yuriy.openradio.shared.view.dialog.NetworkDialog
import com.yuriy.openradio.shared.view.dialog.RSSettingsDialog
import com.yuriy.openradio.shared.view.dialog.SearchDialog
import com.yuriy.openradio.shared.view.dialog.SleepTimerDialog
import com.yuriy.openradio.shared.view.dialog.SourceDialog
import com.yuriy.openradio.shared.view.dialog.StreamBufferingDialog

/**
 * Created with Android Studio.
 * Author: Chernyshov Yuriy - Mobile Development
 * Date: 19.12.14
 * Time: 15:13
 *
 * The main screen. It renders [MainScreen] from the registry's [MainScreenModel], launches the
 * dialogs its controls ask for, and starts and stops the model with its own lifecycle; the model
 * keeps the tabs and stacks across a recreation.
 */
class MainActivity : AppCompatActivity(), MainScreenModelDependency, CountrySettingDependency,
    SleepTimerModelDependency, MainScreenActions {

    private lateinit var mModel: MainScreenModel
    private lateinit var mCountrySetting: CountrySetting
    private lateinit var mSleepTimerModel: SleepTimerModel
    private lateinit var mPlatform: MainScreenPlatform

    override fun configureWith(model: MainScreenModel) {
        mModel = model
    }

    override fun configureWith(countrySetting: CountrySetting) {
        mCountrySetting = countrySetting
    }

    override fun configureWith(sleepTimerModel: SleepTimerModel) {
        mSleepTimerModel = sleepTimerModel
    }

    override fun onProvideAssistContent(outContent: AssistContent?) {
        super.onProvideAssistContent(outContent)
        AppLogger.i("$CLASS_NAME assistant content $outContent")
    }

    override fun onProvideAssistData(data: Bundle?) {
        super.onProvideAssistData(data)
        AppLogger.i("$CLASS_NAME assistant data $data")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppLogger.d("$CLASS_NAME OnCreate:$savedInstanceState")
        DependencyRegistryCommonUi.injectMainScreenModel(this)
        DependencyRegistryCommonUi.injectCountrySetting(this)
        DependencyRegistryCommon.injectSleepTimerModel(this)
        mPlatform = MainScreenPlatform(this, mModel, mCountrySetting, mSleepTimerModel)
        mPlatform.attach()
        mModel.start()
        onBackPressedDispatcher.addCallback(this) {
            if (!mModel.back()) {
                mModel.leave()
                finish()
            }
        }
        setContent {
            val state by mModel.state.collectAsState()
            LaunchedEffect(state.isClosed) {
                if (state.isClosed) {
                    finish()
                }
            }
            ConsoleTheme { MainScreen(state, this@MainActivity) }
        }
        BatteryOptimizationDialog.handle(applicationContext, supportFragmentManager)
    }

    override fun onResume() {
        super.onResume()
        mPlatform.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        mPlatform.detach()
        mModel.stop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mPlatform.onPermissionsResult(permissions, grantResults)
    }

    override fun onSelectTab(tab: Tab) = mModel.selectTab(tab)

    override fun onBack() {
        mModel.back()
    }

    override fun onSearch() = showDialog(SearchDialog::class.java.name, SearchDialog.DIALOG_TAG)

    override fun onOverflow(item: OverflowItem) {
        when (item) {
            OverflowItem.GENERAL -> showDialog(GeneralSettingsDialog::class.java.name, GeneralSettingsDialog.DIALOG_TAG)
            OverflowItem.SOURCE -> showDialog(SourceDialog::class.java.name, SourceDialog.DIALOG_TAG)
            OverflowItem.NETWORK -> showDialog(NetworkDialog::class.java.name, NetworkDialog.DIALOG_TAG)
            OverflowItem.BUFFERING -> showDialog(StreamBufferingDialog::class.java.name, StreamBufferingDialog.DIALOG_TAG)
            OverflowItem.SLEEP_TIMER -> showDialog(SleepTimerDialog::class.java.name, SleepTimerDialog.DIALOG_TAG)
            OverflowItem.EQUALIZER -> showDialog(EqualizerDialog::class.java.name, EqualizerDialog.DIALOG_TAG)
            OverflowItem.FILE_STORAGE -> showDialog(FileStorageDialog::class.java.name, FileStorageDialog.DIALOG_TAG)
            OverflowItem.ABOUT -> showDialog(AboutDialog::class.java.name, AboutDialog.DIALOG_TAG)
        }
    }

    override fun onRowClick(rowId: String) = mModel.open(rowId)

    override fun onRowLongClick(rowId: String) {
        val target = mModel.longPressTarget(rowId) ?: return
        val arguments = Bundle()
        RSSettingsDialog.provideMediaItem(arguments, target.item, target.parentId, target.itemsCount)
        showDialog(RSSettingsDialog::class.java.name, RSSettingsDialog.DIALOG_TAG, arguments)
    }

    override fun onFavoriteClick(rowId: String) = mModel.toggleFavorite(rowId)

    override fun onLoadMore() = mModel.loadMore()

    override fun onRetry() = mModel.reload()

    override fun onTogglePlayback() = mModel.togglePlayback()

    override fun onAddStation() = showDialog(AddStationDialog::class.java.name, AddStationDialog.DIALOG_TAG)

    override fun onScroll(levelKey: String, position: ScrollPosition) = mModel.rememberScroll(levelKey, position)

    /**
     * Shows a dialog in place of any other, as the drawer and the toolbar did.
     */
    private fun showDialog(className: String, tag: String, arguments: Bundle? = null) {
        if (supportFragmentManager.isStateSaved) {
            AppLogger.w("$CLASS_NAME can not show $tag after the state was saved")
            return
        }
        val transaction = supportFragmentManager.beginTransaction()
        UiUtils.clearDialogs(supportFragmentManager, transaction)
        BaseDialogFragment.newInstance(className, arguments).show(transaction, tag)
    }

    private companion object {
        val CLASS_NAME: String = MainActivity::class.java.simpleName
    }
}
