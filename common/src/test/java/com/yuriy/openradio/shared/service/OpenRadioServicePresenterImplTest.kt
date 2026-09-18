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

package com.yuriy.openradio.shared.service

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.core.util.Pair
import com.yuriy.openradio.shared.model.ModelLayer
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.eq.EqualizerState
import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.isInvalid
import com.yuriy.openradio.shared.model.media.item.MediaItemAllCategories
import com.yuriy.openradio.shared.model.media.item.MediaItemBrowseCar
import com.yuriy.openradio.shared.model.media.item.MediaItemChildCategories
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
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.model.storage.cache.api.ApiCache
import com.yuriy.openradio.shared.model.storage.images.ImagesPersistenceLayer
import com.yuriy.openradio.shared.model.timer.SleepTimerListener
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.model.translation.MediaIdBuilder
import com.yuriy.openradio.shared.model.translation.MediaIdBuilderDefault
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.AppUtils
import java.lang.ref.WeakReference
import java.net.URL
import java.util.Date
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRadioServicePresenterImplTest {

    @Test
    fun thePhoneRootIsTheOnlyRootWithoutACarBrowseNode() {
        val presenter = presenter(isCar = false)

        assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_ROOT) is MediaItemRoot)
        assertNull(presenter.getMediaItemCommand(MediaId.MEDIA_ID_BROWSE_CAR))
    }

    @Test
    fun theCarRootIsProjectedAlongsideItsBrowseNode() {
        val presenter = presenter(isCar = true)

        assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_ROOT) is MediaItemRootCar)
        assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_BROWSE_CAR) is MediaItemBrowseCar)
    }

    @Test
    fun everyOtherBrowseNodeIsRegisteredTheSameWayOnBothClients() {
        for (presenter in listOf(presenter(isCar = false), presenter(isCar = true))) {
            assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_ALL_CATEGORIES) is MediaItemAllCategories)
            assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_COUNTRIES_LIST) is MediaItemCountriesList)
            assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_COUNTRY_STATIONS) is MediaItemCountryStations)
            assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_CHILD_CATEGORIES) is MediaItemChildCategories)
            assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_FAVORITES_LIST) is MediaItemFavoritesList)
            assertTrue(
                presenter.getMediaItemCommand(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST) is MediaItemLocalsList
            )
            assertTrue(
                presenter.getMediaItemCommand(MediaId.MEDIA_ID_SEARCH_FROM_SERVICE) is MediaItemSearchFromService
            )
            assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_POPULAR_STATIONS) is MediaItemPopularStations)
            assertTrue(presenter.getMediaItemCommand(MediaId.MEDIA_ID_NEW_STATIONS) is MediaItemNewStations)
            assertNull(presenter.getMediaItemCommand("__NOT_A_NODE__"))
        }
    }

    /**
     * The phone's search marker stays on the client: it is answered with `getSearchResult`, which
     * the service serves under [MediaId.MEDIA_ID_SEARCH_FROM_SERVICE]. Binding a command to it
     * would put a second, differently built copy of the search results one lookup away.
     */
    @Test
    fun theSearchMarkerThePhoneKeepsOnItsStackReachesNoBrowseCommand() {
        for (presenter in listOf(presenter(isCar = false), presenter(isCar = true))) {
            assertNull(presenter.getMediaItemCommand(MediaId.MEDIA_ID_SEARCH_FROM_APP))
            assertEquals(
                AppUtils.EMPTY_STRING,
                MediaId.getId(MediaId.MEDIA_ID_SEARCH_FROM_APP, Country.COUNTRY_CODE_DEFAULT)
            )
        }
    }

    @Test
    fun aBrowseNodeKeepsItsIdentityBetweenLookups() {
        val presenter = presenter(isCar = false)

        assertSame(
            presenter.getMediaItemCommand(MediaId.MEDIA_ID_CHILD_CATEGORIES),
            presenter.getMediaItemCommand(MediaId.MEDIA_ID_CHILD_CATEGORIES)
        )
    }

    @Test
    fun eachCatalogueRequestCarriesItsOwnUrlToTheModel() {
        val urlLayer = RecordingUrlLayer()
        val modelLayer = RecordingModelLayer(stations = setOf(RadioStation.makeDefaultInstance("station")))
        val presenter = presenter(urlLayer = urlLayer, modelLayer = modelLayer)

        assertEquals(1, presenter.getStationsInCategory("rock", 2).size)
        presenter.getStationsByCountry("PL", 3)
        presenter.getNewStations()
        presenter.getPopularStations()
        presenter.getSearchStations("jazz")

        assertEquals(
            listOf("category:rock:2", "country:PL:3", "new", "popular", "search:jazz"),
            modelLayer.stationUris.map { it.toString() }
        )
        assertEquals(listOf("rock" to 2), urlLayer.categoryRequests)
        assertEquals(listOf("PL" to 3), urlLayer.countryRequests)
        assertEquals(listOf("jazz"), urlLayer.searchRequests)
    }

    @Test
    fun aSearchWithoutABuilderStillTagsNothingOntoTheStationIds() {
        val modelLayer = RecordingModelLayer()
        val presenter = presenter(modelLayer = modelLayer)

        presenter.getSearchStations("jazz")

        assertTrue(modelLayer.mediaIdBuilders.single() is MediaIdBuilderDefault)
        assertEquals("station", modelLayer.mediaIdBuilders.single().build("station"))
    }

    @Test
    fun aSearchFromTheServicePassesItsOwnBuilderThrough() {
        val modelLayer = RecordingModelLayer()
        val presenter = presenter(modelLayer = modelLayer)
        val builder = object : MediaIdBuilder {

            override fun build(value: String) = MediaId.makeSearchId(value)
        }

        presenter.getSearchStations("jazz", builder)

        assertSame(builder, modelLayer.mediaIdBuilders.single())
    }

    @Test
    fun categoriesAreFetchedEveryTimeButCountriesAreRememberedOnce() {
        val modelLayer = RecordingModelLayer(
            categories = setOf(Category("rock", "Rock", 1)),
            countries = setOf(Country("Poland", "PL"))
        )
        val presenter = presenter(modelLayer = modelLayer)

        presenter.getAllCategories()
        presenter.getAllCategories()
        val countries = presenter.getAllCountries()

        assertEquals(2, modelLayer.categoriesRequests)
        assertEquals(setOf(Country("Poland", "PL")), countries)
        assertEquals(1, modelLayer.countriesRequests)

        assertEquals(countries, presenter.getAllCountries())
        assertEquals(1, modelLayer.countriesRequests)
    }

    @Test
    fun aSeededCountryCacheIsServedWithoutTouchingTheModel() {
        val modelLayer = RecordingModelLayer(countries = setOf(Country("Germany", "DE")))
        val cache = TreeSet<Country>()
        cache.add(Country("Poland", "PL"))
        val presenter = presenter(modelLayer = modelLayer, countriesCache = cache)

        assertEquals(setOf(Country("Poland", "PL")), presenter.getAllCountries())
        assertEquals(0, modelLayer.countriesRequests)
    }

    @Test
    fun markingAStationAsFavoriteIsVisibleToTheNextLookup() {
        val presenter = presenter()
        val station = RadioStation.makeDefaultInstance("station")

        assertFalse(presenter.isRadioStationFavorite(station))

        presenter.updateRadioStationFavorite(station)
        assertTrue(presenter.isRadioStationFavorite(station))

        presenter.updateRadioStationFavorite(station)
        assertFalse(presenter.isRadioStationFavorite(station))

        presenter.updateRadioStationFavorite(station, true)
        assertTrue(presenter.isRadioStationFavorite(station))
    }

    @Test
    fun clearingDropsEveryCacheAndTheLatestStation() {
        val images = RecordingImagesPersistenceLayer()
        val persistentCache = RecordingApiCache()
        val memoryCache = RecordingApiCache()
        val presenter = presenter(
            images = images,
            persistentCache = persistentCache,
            memoryCache = memoryCache
        )
        val station = RadioStation.makeDefaultInstance("station")

        presenter.setLastRadioStation(station)
        assertEquals("station", presenter.getLastRadioStation().id)

        presenter.clear()

        assertEquals(1, persistentCache.clears)
        assertEquals(1, memoryCache.clears)
        assertEquals(1, images.deleteAllCalls)
        // The storage caches the station it was given, and the registry hands out one instance per
        // process, so a clear that left the cache behind would go unnoticed by the presenter and
        // the next service start would adopt the station anyway. Was TASK-027.
        assertTrue(presenter.getLastRadioStation().isInvalid())
    }

    @Test
    fun closingOnlyDropsWhatIsHeldInMemory() {
        val persistentCache = RecordingApiCache()
        val memoryCache = RecordingApiCache()
        val images = RecordingImagesPersistenceLayer()
        val presenter = presenter(
            images = images,
            persistentCache = persistentCache,
            memoryCache = memoryCache
        )

        presenter.close()

        assertEquals(1, memoryCache.clears)
        assertEquals(0, persistentCache.clears)
        assertEquals(0, images.deleteAllCalls)
    }

    @Test
    fun networkQuestionsAreForwardedToTheNetworkLayer() {
        val networkLayer = RecordingNetworkLayer(mobile = true)
        val presenter = presenter(networkLayer = networkLayer)
        val context = ContextWrapper(null)
        val listener = object : NetworkMonitorListener {

            override fun onConnectivityChange(isConnected: Boolean) = Unit
        }

        presenter.startNetworkMonitor(context, listener)
        assertTrue(presenter.isMobileNetwork())
        presenter.stopNetworkMonitor(context)

        assertEquals(1, networkLayer.monitorsStarted)
        assertEquals(1, networkLayer.monitorsStopped)
        assertSame(listener, networkLayer.listener)
    }

    @Test
    fun settingsAnswerWithTheirStoredDefaults() {
        val presenter = presenter()

        assertTrue(presenter.getUseMobile())
        assertEquals(Country.COUNTRY_CODE_DEFAULT, presenter.getCountryCode())
    }

    @Test
    fun theEqualizerAndSleepTimerAreHandedBackUntouched() {
        val equalizer = RecordingEqualizerLayer()
        val sleepTimer = RecordingSleepTimerModel()
        val presenter = presenter(equalizer = equalizer, sleepTimer = sleepTimer)

        assertSame(equalizer, presenter.getEqualizerLayer())
        assertSame(sleepTimer, presenter.getSleepTimerModel())
        assertFalse(equalizer.initialized)
    }

    private fun presenter(
        isCar: Boolean = false,
        source: Source = Source.RADIO_BROWSER,
        urlLayer: RecordingUrlLayer = RecordingUrlLayer(),
        networkLayer: RecordingNetworkLayer = RecordingNetworkLayer(),
        modelLayer: RecordingModelLayer = RecordingModelLayer(),
        images: RecordingImagesPersistenceLayer = RecordingImagesPersistenceLayer(),
        equalizer: RecordingEqualizerLayer = RecordingEqualizerLayer(),
        persistentCache: RecordingApiCache = RecordingApiCache(),
        memoryCache: RecordingApiCache = RecordingApiCache(),
        sleepTimer: RecordingSleepTimerModel = RecordingSleepTimerModel(),
        countriesCache: TreeSet<Country> = TreeSet()
    ): OpenRadioServicePresenter {
        val contextRef = WeakReference<Context>(ContextWrapper(null))
        val favoritesStorage = FavoritesStorage(contextRef)
        val latestRadioStationStorage = LatestRadioStationStorage(contextRef)
        return OpenRadioServicePresenterImpl(
            isCar,
            source,
            urlLayer,
            networkLayer,
            modelLayer,
            favoritesStorage,
            DeviceLocalsStorage(contextRef, favoritesStorage, latestRadioStationStorage),
            latestRadioStationStorage,
            NetworkSettingsStorage(contextRef),
            LocationStorage(contextRef),
            images,
            equalizer,
            persistentCache,
            memoryCache,
            sleepTimer,
            countriesCache
        )
    }

    private class RecordingUrlLayer : com.yuriy.openradio.shared.model.net.UrlLayer {

        val categoryRequests = mutableListOf<kotlin.Pair<String, Int>>()

        val countryRequests = mutableListOf<kotlin.Pair<String, Int>>()

        val searchRequests = mutableListOf<String>()

        override fun getConnectionUrl(uri: Uri, parameters: List<Pair<String, String>>): URL? = null

        override fun getAllCategoriesUrl(): Uri = Uri.parse("categories")

        override fun getStationsInCategory(categoryId: String, pageNumber: Int): Uri {
            categoryRequests.add(categoryId to pageNumber)
            return Uri.parse("category:$categoryId:$pageNumber")
        }

        override fun getStationsByCountry(countryCode: String, pageNumber: Int): Uri {
            countryRequests.add(countryCode to pageNumber)
            return Uri.parse("country:$countryCode:$pageNumber")
        }

        override fun getPopularStations(): Uri = Uri.parse("popular")

        override fun getNewStations(): Uri = Uri.parse("new")

        override fun getSearchUrl(query: String): Uri {
            searchRequests.add(query)
            return Uri.parse("search:$query")
        }

        override fun getAllCountries(): Uri = Uri.parse("countries")
    }

    private class RecordingModelLayer(
        private val categories: Set<Category> = emptySet(),
        private val countries: Set<Country> = emptySet(),
        private val stations: Set<RadioStation> = emptySet()
    ) : ModelLayer {

        val stationUris = mutableListOf<Uri>()

        val mediaIdBuilders = mutableListOf<MediaIdBuilder>()

        var categoriesRequests = 0
            private set

        var countriesRequests = 0
            private set

        override fun getAllCategories(uri: Uri): Set<Category> {
            categoriesRequests++
            return categories
        }

        override fun getAllCountries(uri: Uri): Set<Country> {
            countriesRequests++
            return countries
        }

        override fun getStations(uri: Uri, mediaIdBuilder: MediaIdBuilder): Set<RadioStation> {
            stationUris.add(uri)
            mediaIdBuilders.add(mediaIdBuilder)
            return stations
        }
    }

    private class RecordingNetworkLayer(private val mobile: Boolean = false) : NetworkLayer {

        var monitorsStarted = 0
            private set

        var monitorsStopped = 0
            private set

        var listener: NetworkMonitorListener? = null
            private set

        override fun startMonitor(context: Context, listener: NetworkMonitorListener) {
            monitorsStarted++
            this.listener = listener
        }

        override fun stopMonitor(context: Context) {
            monitorsStopped++
        }

        override fun checkConnectivityAndNotify(context: Context) = true

        override fun isMobileNetwork() = mobile
    }

    private class RecordingApiCache : ApiCache {

        var clears = 0
            private set

        override fun get(key: String) = ""

        override fun put(key: String, data: String) = Unit

        override fun remove(key: String) = Unit

        override fun clear() {
            clears++
        }
    }

    private class RecordingImagesPersistenceLayer : ImagesPersistenceLayer {

        var deleteAllCalls = 0
            private set

        override fun open(uri: Uri) = null

        override fun delete(uri: Uri) = Unit

        override fun delete(mediaId: String) = Unit

        override fun deleteAll() {
            deleteAllCalls++
        }
    }

    private class RecordingEqualizerLayer : EqualizerLayer {

        var initialized = false
            private set

        override fun init(audioSessionId: Int) {
            initialized = true
        }

        override fun deinit() = Unit

        override fun isInit() = initialized

        override fun saveState(state: EqualizerState) = Unit

        override fun loadState() = EqualizerState()

        override fun applyState(state: EqualizerState, onSuccess: (state: EqualizerState) -> Unit) = Unit
    }

    private class RecordingSleepTimerModel : SleepTimerModel {

        override fun init() = Unit

        override fun isEnabled() = false

        override fun setEnabled(value: Boolean) = Unit

        override fun updateTime(enabled: Boolean) = Unit

        override fun updateTimer(time: Long, enabled: Boolean) = Unit

        override fun setDate(year: Int, month: Int, day: Int) = Unit

        override fun setTime(hourOfDay: Int, minute: Int) = Unit

        override fun getTime() = Date(0)

        override fun getTimestamp() = 0L

        override fun isTimestampNotValid(value: Long) = true

        override fun addSleepTimerListener(listener: SleepTimerListener) = Unit

        override fun removeSleepTimerListener(listener: SleepTimerListener) = Unit
    }
}
