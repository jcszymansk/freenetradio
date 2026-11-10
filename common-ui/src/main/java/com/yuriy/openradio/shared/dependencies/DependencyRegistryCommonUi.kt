/*
 * Copyright 2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.dependencies

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.logging.LoggingLayer
import com.yuriy.openradio.shared.model.logging.LoggingLayerImpl
import com.yuriy.openradio.shared.model.media.MediaResourcesManager
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.RadioStationManagerLayer
import com.yuriy.openradio.shared.model.net.NetworkLayer
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.FileStoreManager
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.model.storage.StorageManagerLayer
import com.yuriy.openradio.shared.model.storage.StorageManagerLayerImpl
import com.yuriy.openradio.shared.presenter.main.CountrySetting
import com.yuriy.openradio.shared.presenter.main.CountrySettingImpl
import com.yuriy.openradio.shared.presenter.main.LastPlayedStation
import com.yuriy.openradio.shared.presenter.main.MainScreenModel
import com.yuriy.openradio.shared.view.dialog.AddEditStationDialogPresenter
import com.yuriy.openradio.shared.view.dialog.AddEditStationDialogPresenterImpl
import com.yuriy.openradio.shared.view.dialog.BaseAddEditStationDialog
import com.yuriy.openradio.shared.view.dialog.EditStationDialog
import com.yuriy.openradio.shared.view.dialog.EditStationPresenter
import com.yuriy.openradio.shared.view.dialog.EditStationPresenterImpl
import com.yuriy.openradio.shared.view.dialog.EqualizerDialog
import com.yuriy.openradio.shared.view.dialog.EqualizerPresenter
import com.yuriy.openradio.shared.view.dialog.EqualizerPresenterImpl
import com.yuriy.openradio.shared.view.dialog.NetworkDialog
import com.yuriy.openradio.shared.view.dialog.RemoveStationDialog
import com.yuriy.openradio.shared.view.dialog.RemoveStationDialogPresenter
import com.yuriy.openradio.shared.view.dialog.RemoveStationDialogPresenterImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicBoolean

object DependencyRegistryCommonUi :
    NetworkLayerDependency, LocationStorageDependency, FavoritesStorageDependency,
    DeviceLocalsStorageDependency, EqualizerLayerDependency, RadioStationManagerLayerDependency,
    NetworkSettingsStorageDependency, SourcesLayerDependency,
    LatestRadioStationStorageDependency {

    private const val MAIN_SCREEN_CLIENT = "MainScreen"

    private lateinit var sEditStationPresenter: EditStationPresenter
    private lateinit var sEqualizerPresenter: EqualizerPresenter
    private lateinit var sRemoveStationDialogPresenter: RemoveStationDialogPresenter
    private lateinit var sAddEditStationDialogPresenter: AddEditStationDialogPresenter
    private lateinit var sStorageManagerLayer: StorageManagerLayer
    private lateinit var sNetworkLayer: NetworkLayer
    private lateinit var sLocationStorage: LocationStorage
    private lateinit var sFavoritesStorage: FavoritesStorage
    private lateinit var sDeviceLocalsStorage: DeviceLocalsStorage
    private lateinit var sNetworkSettingsStorage: NetworkSettingsStorage
    private lateinit var sEqualizerLayer: EqualizerLayer
    private lateinit var sRadioStationManagerLayer: RadioStationManagerLayer
    private lateinit var sSourcesLayer: SourcesLayer
    private lateinit var sLoggingLayer: LoggingLayer
    private lateinit var sFileStoraManager: FileStoreManager
    private lateinit var sMainScreenModel: MainScreenModel
    private lateinit var sCountrySetting: CountrySetting
    private lateinit var sLatestRadioStationStorage: LatestRadioStationStorage

    @Volatile
    private var sInit = AtomicBoolean(false)

    /**
     * Init with application's context only!
     */
    @OptIn(UnstableApi::class)
    fun init(context: Context) {
        if (sInit.get()) {
            return
        }
        DependencyRegistryCommon.injectNetworkLayer(this)
        DependencyRegistryCommon.injectLocationStorage(this)
        DependencyRegistryCommon.injectFavoritesStorage(this)
        DependencyRegistryCommon.injectDeviceLocalsStorage(this)
        DependencyRegistryCommon.injectEqualizerLayer(this)
        DependencyRegistryCommon.injectRadioStationManagerLayer(this)
        DependencyRegistryCommon.injectNetworkSettingsStorage(this)
        DependencyRegistryCommon.injectSourcesLayer(this)
        DependencyRegistryCommon.injectLatestRadioStationStorage(this)
        sLoggingLayer = LoggingLayerImpl(context)
        val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        sMainScreenModel = MainScreenModel(
            { listener -> MediaResourcesManager(context, MAIN_SCREEN_CLIENT, listener) },
            sSourcesLayer,
            { sNetworkLayer.checkConnectivityAndNotify(context) },
            // A recreated service adopts the latest station only while this setting is on
            // (OpenRadioService.onCreate), and only then would a play request resume it.
            LastPlayedStation {
                if (AppPreferencesManager.lastKnownRadioStationEnabled(context)) {
                    sLatestRadioStationStorage.get()
                } else {
                    RadioStation.INVALID_INSTANCE
                }
            },
            uiScope
        )
        sCountrySetting = CountrySettingImpl(sLocationStorage, sMainScreenModel, uiScope)
        sEditStationPresenter = EditStationPresenterImpl(
            sFavoritesStorage,
            sDeviceLocalsStorage
        )
        sStorageManagerLayer = StorageManagerLayerImpl(
            sFavoritesStorage,
            sDeviceLocalsStorage
        )
        sEqualizerPresenter = EqualizerPresenterImpl(sEqualizerLayer)
        sRemoveStationDialogPresenter = RemoveStationDialogPresenterImpl(
            context,
            sRadioStationManagerLayer
        )
        sAddEditStationDialogPresenter = AddEditStationDialogPresenterImpl(
            context,
            sRadioStationManagerLayer
        )
        sFileStoraManager = FileStoreManager()

        sInit.set(true)
    }

    override fun configureWith(sourcesLayer: SourcesLayer) {
        sSourcesLayer = sourcesLayer
    }

    override fun configureWith(networkLayer: NetworkLayer) {
        sNetworkLayer = networkLayer
    }

    override fun configureWith(locationStorage: LocationStorage) {
        sLocationStorage = locationStorage
    }

    override fun configureWith(favoritesStorage: FavoritesStorage) {
        sFavoritesStorage = favoritesStorage
    }

    override fun configureWith(deviceLocalsStorage: DeviceLocalsStorage) {
        sDeviceLocalsStorage = deviceLocalsStorage
    }

    override fun configureWith(equalizerLayer: EqualizerLayer) {
        sEqualizerLayer = equalizerLayer
    }

    override fun configureWith(radioStationManagerLayer: RadioStationManagerLayer) {
        sRadioStationManagerLayer = radioStationManagerLayer
    }

    override fun configureWith(networkSettingsStorage: NetworkSettingsStorage) {
        sNetworkSettingsStorage = networkSettingsStorage
    }

    override fun configureWith(latestRadioStationStorage: LatestRadioStationStorage) {
        sLatestRadioStationStorage = latestRadioStationStorage
    }

    fun injectMainScreenModel(dependency: MainScreenModelDependency) {
        dependency.configureWith(sMainScreenModel)
    }

    fun injectBrowseSearch(dependency: BrowseSearchDependency) {
        dependency.configureWith(sMainScreenModel)
    }

    fun injectBrowseReload(dependency: BrowseReloadDependency) {
        dependency.configureWith(sMainScreenModel)
    }

    fun injectScreenClose(dependency: ScreenCloseDependency) {
        dependency.configureWith(sMainScreenModel)
    }

    fun injectCountrySetting(dependency: CountrySettingDependency) {
        dependency.configureWith(sCountrySetting)
    }

    fun injectStorageManagerLayer(dependency: StorageManagerDependency) {
        dependency.configureWith(sStorageManagerLayer)
    }

    fun injectFileStoreManager(dependency: FileStoreManagerDependency) {
        dependency.configureWith(sFileStoraManager)
    }

    fun inject(dependency: EditStationDialog) {
        dependency.configureWith(sEditStationPresenter)
    }

    fun inject(dependency: EqualizerDialog) {
        dependency.configureWith(sEqualizerPresenter)
    }

    fun inject(dependency: RemoveStationDialog) {
        dependency.configureWith(sRemoveStationDialogPresenter)
    }

    fun inject(dependency: BaseAddEditStationDialog) {
        dependency.configureWith(sAddEditStationDialogPresenter)
    }

    fun inject(dependency: NetworkDialog) {
        dependency.configureWith(sNetworkSettingsStorage)
    }

    fun injectServiceCommander(dependency: ServiceCommanderDependency) {
        dependency.configureWith(sMainScreenModel.serviceCommander)
    }

    fun injectLoggingLayer(dependency: LoggingLayerDependency) {
        dependency.configureWith(sLoggingLayer)
    }
}
