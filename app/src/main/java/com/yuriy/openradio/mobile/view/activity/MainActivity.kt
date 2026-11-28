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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Density
import androidx.lifecycle.lifecycleScope
import com.github.jcszymansk.console.design.ConsoleDimens
import com.github.jcszymansk.console.design.ConsoleTheme
import com.yuriy.openradio.mobile.view.main.ARTWORK_HEIGHT
import com.yuriy.openradio.mobile.view.main.ArtworkCache
import com.yuriy.openradio.mobile.view.main.LocalArtworkCache
import com.yuriy.openradio.mobile.view.main.LocalPlayerArtworkCache
import com.yuriy.openradio.mobile.view.main.MainScreen
import com.yuriy.openradio.mobile.view.main.MainScreenActions
import com.yuriy.openradio.mobile.view.main.NowPlayingActions
import com.yuriy.openradio.mobile.view.main.NowPlayingScreen
import com.yuriy.openradio.mobile.view.main.OverflowItem
import com.yuriy.openradio.mobile.view.main.PlayerMenuItem
import com.yuriy.openradio.mobile.view.settings.DestinationScreen
import com.yuriy.openradio.mobile.view.settings.SettingsEffectsHost
import com.yuriy.openradio.shared.dependencies.CountrySettingDependency
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.FileStoreManagerDependency
import com.yuriy.openradio.shared.dependencies.MainScreenModelDependency
import com.yuriy.openradio.shared.dependencies.ScreenCloseDependency
import com.yuriy.openradio.shared.dependencies.SettingsModels
import com.yuriy.openradio.shared.dependencies.SettingsModelsDependency
import com.yuriy.openradio.shared.dependencies.SleepTimerModelDependency
import com.yuriy.openradio.shared.model.storage.FileStoreManager
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.presenter.main.CountrySetting
import com.yuriy.openradio.shared.presenter.main.MainScreenModel
import com.yuriy.openradio.shared.presenter.main.PlayerScreen
import com.yuriy.openradio.shared.presenter.main.ScreenClose
import com.yuriy.openradio.shared.presenter.main.ScrollPosition
import com.yuriy.openradio.shared.presenter.main.Tab
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.UiUtils
import com.yuriy.openradio.shared.view.dialog.AddStationDialog
import com.yuriy.openradio.shared.view.dialog.BaseDialogFragment
import com.yuriy.openradio.shared.view.dialog.EqualizerDialog
import com.yuriy.openradio.shared.view.dialog.RSSettingsDialog
import com.yuriy.openradio.shared.view.dialog.SearchDialog
import com.yuriy.openradio.shared.view.dialog.SleepTimerDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Created with Android Studio.
 * Author: Chernyshov Yuriy - Mobile Development
 * Date: 19.12.14
 * Time: 15:13
 *
 * The main screen. It renders the top of the settings screen stack when there is one, otherwise
 * [NowPlayingScreen] while the model says a player screen is open, otherwise [MainScreen]; system
 * back pops the stack before the model's own back. It launches the dialogs and platform actions
 * their controls ask for, and starts and stops the model with its own lifecycle; the model and the
 * stack keep the tabs, stacks and open screens across a recreation.
 */
class MainActivity : AppCompatActivity(), MainScreenModelDependency, CountrySettingDependency,
    SleepTimerModelDependency, SettingsModelsDependency, FileStoreManagerDependency,
    ScreenCloseDependency, MainScreenActions, NowPlayingActions {

    private lateinit var mModel: MainScreenModel
    private lateinit var mCountrySetting: CountrySetting
    private lateinit var mSleepTimerModel: SleepTimerModel
    private lateinit var mSettings: SettingsModels
    private lateinit var mFileStoreManager: FileStoreManager
    private lateinit var mScreenClose: ScreenClose
    private lateinit var mPlatform: MainScreenPlatform
    private lateinit var mSettingsPlatform: SettingsPlatform
    private lateinit var mPlayerPlatform: NowPlayingPlatform

    override fun configureWith(model: MainScreenModel) {
        mModel = model
    }

    override fun configureWith(countrySetting: CountrySetting) {
        mCountrySetting = countrySetting
    }

    override fun configureWith(sleepTimerModel: SleepTimerModel) {
        mSleepTimerModel = sleepTimerModel
    }

    override fun configureWith(models: SettingsModels) {
        mSettings = models
    }

    override fun configureWith(fileStoreManager: FileStoreManager) {
        mFileStoreManager = fileStoreManager
    }

    override fun configureWith(screenClose: ScreenClose) {
        mScreenClose = screenClose
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
        DependencyRegistryCommonUi.injectSettingsModels(this)
        DependencyRegistryCommonUi.injectFileStoreManager(this)
        DependencyRegistryCommonUi.injectScreenClose(this)
        val density = Density(this)
        val artworkCache = ArtworkCache.forProvider(applicationContext, with(density) { ConsoleDimens.tileSize.roundToPx() })
        val playerArtworkCache = ArtworkCache.forProvider(applicationContext, with(density) { ARTWORK_HEIGHT.roundToPx() })
        mPlatform = MainScreenPlatform(this, mScreenClose, mCountrySetting, mSleepTimerModel, listOf(artworkCache, playerArtworkCache))
        mSettingsPlatform = SettingsPlatform(this, mSettings, mFileStoreManager)
        mPlayerPlatform = NowPlayingPlatform(this)
        mPlatform.attach()
        mModel.start()
        // The screen stack first, then the main screen's own back: the one place the order is decided.
        onBackPressedDispatcher.addCallback(this) {
            if (!mSettings.screens.backThen(mModel::back)) {
                mModel.leave()
                finish()
            }
        }
        // Watched outside the composition: its frame clock is paused while the activity is
        // stopped, and the sleep timer and the notification's close action both fire then.
        lifecycleScope.launch {
            mModel.state.first { it.isClosed }
            finish()
        }
        setContent {
            val state by mModel.state.collectAsState()
            val destination by mSettings.screens.top.collectAsState()
            ConsoleTheme {
                CompositionLocalProvider(
                    LocalArtworkCache provides artworkCache,
                    LocalPlayerArtworkCache provides playerArtworkCache,
                ) {
                    SettingsEffectsHost(mSettings, mSettingsPlatform)
                    val top = destination
                    val nowPlaying = state.nowPlaying
                    when {
                        top != null -> DestinationScreen(top, mSettings, mSettingsPlatform)
                        state.playerScreen != PlayerScreen.NONE && nowPlaying != null ->
                            NowPlayingScreen(nowPlaying, state.playerScreen, this@MainActivity)
                        else -> MainScreen(state, this@MainActivity)
                    }
                }
            }
        }
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
            OverflowItem.SETTINGS -> mSettings.settings.open()
            OverflowItem.SLEEP_TIMER -> showDialog(SleepTimerDialog::class.java.name, SleepTimerDialog.DIALOG_TAG)
            OverflowItem.EQUALIZER -> showDialog(EqualizerDialog::class.java.name, EqualizerDialog.DIALOG_TAG)
            OverflowItem.ABOUT -> mSettings.settings.openAbout()
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

    override fun onClosePlayer() = mModel.closePlayer()

    override fun onToggleCurrentFavorite() = mModel.toggleCurrentFavorite()

    override fun onCloseStationDetails() {
        mModel.back()
    }

    override fun onOpenHomePage(url: String) = mPlayerPlatform.openHomePage(url)

    override fun onPlayerMenu(item: PlayerMenuItem) {
        val nowPlaying = mModel.state.value.nowPlaying ?: return
        when (item) {
            PlayerMenuItem.STATION_DETAILS -> mModel.openStationDetails()
            PlayerMenuItem.COPY_STREAM_URL -> mPlayerPlatform.copyStreamUrl(nowPlaying.streamUrl)
            PlayerMenuItem.SHARE_STREAM_URL -> mPlayerPlatform.shareStreamUrl(nowPlaying.name, nowPlaying.streamUrl)
            PlayerMenuItem.SLEEP_TIMER -> showDialog(SleepTimerDialog::class.java.name, SleepTimerDialog.DIALOG_TAG)
            PlayerMenuItem.EQUALIZER -> showDialog(EqualizerDialog::class.java.name, EqualizerDialog.DIALOG_TAG)
        }
    }

    override fun onOpenNowPlaying() = mModel.openNowPlaying()

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
