package com.yuriy.openradio.shared.service

import android.content.Context
import com.yuriy.openradio.shared.model.ModelLayer
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.item.MediaItemAllCategories
import com.yuriy.openradio.shared.model.media.item.MediaItemBrowseCar
import com.yuriy.openradio.shared.model.media.item.MediaItemChildCategories
import com.yuriy.openradio.shared.model.media.item.MediaItemCommand
import com.yuriy.openradio.shared.model.media.item.MediaItemCountriesList
import com.yuriy.openradio.shared.model.media.item.MediaItemCountryStations
import com.yuriy.openradio.shared.model.media.item.MediaItemFavoritesList
import com.yuriy.openradio.shared.model.media.item.MediaItemLocalsList
import com.yuriy.openradio.shared.model.media.item.MediaItemNewStations
import com.yuriy.openradio.shared.model.media.item.MediaItemPopularStations
import com.yuriy.openradio.shared.model.media.item.MediaItemRoot
import com.yuriy.openradio.shared.model.media.item.MediaItemRootCar
import com.yuriy.openradio.shared.model.media.item.MediaItemSearchFromService
import com.yuriy.openradio.shared.model.net.NetworkLayer
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.model.storage.cache.api.ApiCache
import com.yuriy.openradio.shared.model.storage.images.ImagesPersistenceLayer
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.model.translation.MediaIdBuilder
import com.yuriy.openradio.shared.model.translation.MediaIdBuilderDefault
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.SortUtils
import java.util.TreeSet

class OpenRadioServicePresenterImpl(
    isCar: Boolean,
    source: Source,
    private val mUrlLayer: UrlLayer,
    private val mNetworkLayer: NetworkLayer,
    private val mModelLayer: ModelLayer,
    private var mFavoritesStorage: FavoritesStorage,
    private val mDeviceLocalsStorage: DeviceLocalsStorage,
    private val mLatestRadioStationStorage: LatestRadioStationStorage,
    private val mNetworkSettingsStorage: NetworkSettingsStorage,
    private val mLocationStorage: LocationStorage,
    private var mImagesPersistenceLayer: ImagesPersistenceLayer,
    private val mEqualizerLayer: EqualizerLayer,
    private val mApiCachePersistent: ApiCache,
    private val mApiCacheInMemory: ApiCache,
    private val mSleepTimerModel: SleepTimerModel,
    private val mCountriesCache:TreeSet<Country>
) : OpenRadioServicePresenter {

    /**
     * Map of the Media Item commands that responsible for the Media Items List creation.
     */
    private val mMediaItemCommands = HashMap<String, MediaItemCommand>()

    init {
        if (isCar) {
            mMediaItemCommands[MediaId.MEDIA_ID_ROOT] = MediaItemRootCar(source)
            mMediaItemCommands[MediaId.MEDIA_ID_BROWSE_CAR] = MediaItemBrowseCar(source)
        } else {
            mMediaItemCommands[MediaId.MEDIA_ID_ROOT] = MediaItemRoot(source)
        }
        mMediaItemCommands[MediaId.MEDIA_ID_ALL_CATEGORIES] = MediaItemAllCategories()
        mMediaItemCommands[MediaId.MEDIA_ID_COUNTRIES_LIST] = MediaItemCountriesList()
        mMediaItemCommands[MediaId.MEDIA_ID_COUNTRY_STATIONS] = MediaItemCountryStations()
        mMediaItemCommands[MediaId.MEDIA_ID_CHILD_CATEGORIES] = MediaItemChildCategories()
        mMediaItemCommands[MediaId.MEDIA_ID_FAVORITES_LIST] = MediaItemFavoritesList()
        mMediaItemCommands[MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST] = MediaItemLocalsList()
        mMediaItemCommands[MediaId.MEDIA_ID_SEARCH_FROM_SERVICE] = MediaItemSearchFromService()
        mMediaItemCommands[MediaId.MEDIA_ID_POPULAR_STATIONS] = MediaItemPopularStations()
        mMediaItemCommands[MediaId.MEDIA_ID_NEW_STATIONS] = MediaItemNewStations()
    }

    override fun getMediaItemCommand(commandId: String): MediaItemCommand? {
        return mMediaItemCommands[commandId]
    }

    override fun startNetworkMonitor(context: Context, listener: NetworkMonitorListener) {
        mNetworkLayer.startMonitor(context, listener)
    }

    override fun stopNetworkMonitor(context: Context) {
        mNetworkLayer.stopMonitor(context)
    }

    override fun isMobileNetwork(): Boolean {
        return mNetworkLayer.isMobileNetwork()
    }

    override fun getUseMobile(): Boolean {
        return mNetworkSettingsStorage.getUseMobile()
    }

    override fun getStationsInCategory(categoryId: String, pageNumber: Int): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getStationsInCategory(
                categoryId,
                pageNumber
            ),
            MediaIdBuilderDefault()
        )
    }

    override fun getStationsByCountry(countryCode: String, pageNumber: Int): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getStationsByCountry(
                countryCode,
                pageNumber
            ),
            MediaIdBuilderDefault()
        )
    }

    override fun getNewStations(): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getNewStations(),
            MediaIdBuilderDefault()
        )
    }

    override fun getPopularStations(): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getPopularStations(),
            MediaIdBuilderDefault()
        )
    }

    override fun getSearchStations(query: String, mediaIdBuilder: MediaIdBuilder): Set<RadioStation> {
        return mModelLayer.getStations(
            mUrlLayer.getSearchUrl(query), mediaIdBuilder
        )
    }

    override fun getAllCategories(): Set<Category> {
        return mModelLayer.getAllCategories(mUrlLayer.getAllCategoriesUrl())
    }

    @Synchronized
    override fun getAllCountries(): Set<Country> {
        if (mCountriesCache.isEmpty().not()) {
            return mCountriesCache
        }
        mCountriesCache.addAll(mModelLayer.getAllCountries(mUrlLayer.getAllCountries()))
        return mCountriesCache
    }

    override fun getAllFavorites(): Set<RadioStation> {
        return mFavoritesStorage.getAll()
    }

    override fun getAllDeviceLocal(): Set<RadioStation> {
        return mDeviceLocalsStorage.getAll()
    }

    override fun getLastRadioStation(): RadioStation {
        return mLatestRadioStationStorage.get()
    }

    override fun setLastRadioStation(radioStation: RadioStation) {
        mLatestRadioStationStorage.add(radioStation)
    }

    override fun getCountryCode(): String {
        return mLocationStorage.getCountryCode()
    }

    override fun isRadioStationFavorite(radioStation: RadioStation): Boolean {
        return mFavoritesStorage.isFavorite(radioStation)
    }

    override fun updateRadioStationFavorite(radioStation: RadioStation) {
        updateRadioStationFavorite(radioStation, isRadioStationFavorite(radioStation).not())
    }

    override fun updateRadioStationFavorite(radioStation: RadioStation, isFavorite: Boolean) {
        if (isFavorite) {
            mFavoritesStorage.add(radioStation)
        } else {
            mFavoritesStorage.remove(radioStation)
        }
    }

    override fun updateSortIds(
        mediaId: String,
        sortId: Int,
        categoryMediaId: String
    ) {
        SortUtils.updateSortIds(
            mediaId, sortId, categoryMediaId,
            mFavoritesStorage, mDeviceLocalsStorage
        )
    }

    override fun getEqualizerLayer(): EqualizerLayer {
        return mEqualizerLayer
    }

    override fun clear() {
        mApiCachePersistent.clear()
        mApiCacheInMemory.clear()
        mImagesPersistenceLayer.deleteAll()
        mLatestRadioStationStorage.clear()
    }

    override fun close() {
        mApiCacheInMemory.clear()
    }

    override fun getSleepTimerModel(): SleepTimerModel {
        return mSleepTimerModel
    }
}
