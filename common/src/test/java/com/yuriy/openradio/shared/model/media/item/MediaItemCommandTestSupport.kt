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

package com.yuriy.openradio.shared.model.media.item

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Bundle
import androidx.media3.common.MediaItem
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.setVariant
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.model.translation.MediaIdBuilder
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioServicePresenter
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.AppUtils
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.TreeSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Shared scaffolding for the [MediaItemCommand] tests: a context that answers resource lookups,
 * a recording presenter, a listener that can be awaited, and station fixtures.
 */

internal const val DEFAULT_COUNTRY_CODE = "PL"

internal const val FLAG_DRAWABLE_ID = 4711

/**
 * What every string resource resolves to here. The generated test R class numbers all resources
 * zero, so one value is all a test context can tell apart.
 */
internal const val STRING_RESOURCE = "string resource"

@Suppress("DEPRECATION")
internal fun testContext(): Context {
    val resources = object : Resources(null, null, null) {

        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
            return FLAG_DRAWABLE_ID
        }

        override fun getString(id: Int): String = STRING_RESOURCE

        override fun getString(id: Int, vararg formatArgs: Any?): String = STRING_RESOURCE

        override fun getText(id: Int): CharSequence = STRING_RESOURCE
    }
    return object : ContextWrapper(null) {

        override fun getResources(): Resources = resources

        override fun getPackageName(): String = "com.github.jcszymansk.freenetradio"
    }
}

internal fun station(
    id: String,
    name: String = "Station $id",
    country: String = "Poland",
    genre: String = "Jazz",
    bitrate: Int = 128,
    sortId: Int = 0,
    imageUrl: String = "https://radio.example/$id.png"
): RadioStation {
    return RadioStation.makeDefaultInstance(id).apply {
        this.name = name
        this.country = country
        this.genre = genre
        this.sortId = sortId
        this.imageUrl = imageUrl
        setVariant(bitrate, "https://radio.example/$id.mp3")
    }
}

internal fun stations(vararg ids: String): Set<RadioStation> {
    val result = TreeSet<RadioStation>()
    for ((index, id) in ids.withIndex()) {
        result.add(station(id, sortId = index))
    }
    return result
}

internal fun dependencies(
    presenter: OpenRadioServicePresenter,
    listener: RecordingCommandListener,
    parentId: String = AppUtils.EMPTY_STRING,
    countryCode: String = DEFAULT_COUNTRY_CODE,
    isSameCatalogue: Boolean = false,
    isSavedInstance: Boolean = false,
    options: Bundle = Bundle()
): MediaItemCommandDependencies {
    return MediaItemCommandDependencies(
        testContext(),
        presenter,
        countryCode,
        parentId,
        isSameCatalogue,
        isSavedInstance,
        options,
        CoroutineScope(Dispatchers.IO),
        listener
    )
}

/**
 * Records what a command delivered. Commands answer from a background coroutine, so every
 * assertion is preceded by awaiting the signal the command under test is expected to send.
 */
internal class RecordingCommandListener : OpenRadioService.ResultListener {

    private val mResultLatch = CountDownLatch(1)

    private val mErrorLatch = CountDownLatch(1)

    @Volatile
    var results = 0
        private set

    @Volatile
    var items = emptyList<MediaItem>()
        private set

    @Volatile
    var radioStations = emptySet<RadioStation>()
        private set

    @Volatile
    var pageNumber = UNSET_PAGE_NUMBER
        private set

    @Volatile
    var errors = 0
        private set

    @Volatile
    var error: String? = null
        private set

    val mediaIds: List<String>
        get() = items.map { it.mediaId }

    /**
     * An empty catalogue is reported with the "no data" string resource, which resolves to null off
     * a device. A Kotlin implementation of the listener would reject that null before recording it,
     * so the call is taken through a proxy instead.
     */
    val playbackStateListener: MediaItemCommand.IUpdatePlaybackState = Proxy.newProxyInstance(
        MediaItemCommand.IUpdatePlaybackState::class.java.classLoader,
        arrayOf(MediaItemCommand.IUpdatePlaybackState::class.java),
        InvocationHandler { _, method, arguments ->
            if (method.name == UPDATE_PLAYBACK_STATE) {
                error = arguments?.firstOrNull() as String?
                errors++
                mErrorLatch.countDown()
            }
            null
        }
    ) as MediaItemCommand.IUpdatePlaybackState

    override fun onResult(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int) {
        this.items = items
        this.radioStations = radioStations
        this.pageNumber = pageNumber
        results++
        mResultLatch.countDown()
    }

    fun awaitResult(): RecordingCommandListener {
        assertTrue(RESULT_MISSING, mResultLatch.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS))
        return this
    }

    fun awaitError(): RecordingCommandListener {
        assertTrue(ERROR_MISSING, mErrorLatch.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS))
        return this
    }

    fun assertNoError() {
        assertFalse(
            "Command reported an error it was not expected to",
            mErrorLatch.await(SETTLE_MILLIS, TimeUnit.MILLISECONDS)
        )
    }

    /**
     * Names every media id that arrived, in order. The first one is a parameter of its own so that
     * `assertMediaIds()` does not compile: an empty expectation degenerates into comparing two
     * empty lists, which is bit for bit what a command that never ran also delivers. A test that
     * expects no items asserts what the command did instead - the page it asked the presenter for,
     * or the error it reported.
     */
    fun assertMediaIds(first: String, vararg rest: String) {
        assertEquals(listOf(first) + rest, mediaIds)
    }

    /**
     * Asserts the contract of a browse command restored onto a catalogue that is already in
     * [com.yuriy.openradio.shared.model.media.BrowseTree]: it answers on the calling thread,
     * before `execute` returns and before it reaches its coroutine, and the empty result it leaves
     * behind is what makes `OpenRadioService.callWhenSourceReady` serve the node from the tree
     * rather than from the provider.
     *
     * The delivery that has already happened is the whole claim. An empty result on its own is bit
     * for bit what a command that never ran delivers, so it is worth asserting only together with
     * the moment it arrived, which is why this has to be called before any await.
     */
    fun assertAnsweredFromCacheBeforeReturning() {
        assertEquals("A restored instance did not answer before execute returned", 1, results)
        assertEquals("A restored instance built media items of its own", emptyList<String>(), mediaIds)
        assertEquals("A restored instance carried radio stations of its own", emptySet<RadioStation>(), radioStations)
        assertEquals("A restored instance did not answer for the first page", UrlLayer.FIRST_PAGE_INDEX, pageNumber)
    }

    companion object {

        const val UNSET_PAGE_NUMBER = -1

        /**
         * Shorter than [MediaItemCommand.CMD_TIMEOUT_MS] on purpose. A command that exhausts its
         * own timeout answers with no items, the first page and no error, which is exactly the
         * state several of these tests expect of a command that ran and found nothing. Expiring
         * first turns that into a named failure instead of a pass (TASK-069).
         */
        const val AWAIT_MILLIS = MediaItemCommand.CMD_TIMEOUT_MS / 2

        const val RESULT_MISSING = "Command did not deliver a result"

        private const val ERROR_MISSING = "Command did not report an error"

        private const val UPDATE_PLAYBACK_STATE = "updatePlaybackState"

        private const val SETTLE_MILLIS = 250L
    }
}

/**
 * Canned answers plus a record of what the command asked for. Anything a browse command is not
 * meant to touch throws, so an unexpected call fails the test that made it.
 */
internal class RecordingPresenter(
    private val mCategories: Set<Category> = emptySet(),
    private val mCountries: Set<Country> = emptySet(),
    private val mFavorites: Set<RadioStation> = emptySet(),
    private val mDeviceLocals: Set<RadioStation> = emptySet(),
    private val mNewStations: Set<RadioStation> = emptySet(),
    private val mPopularStations: Set<RadioStation> = emptySet(),
    private val mCategoryStations: Set<RadioStation> = emptySet(),
    private val mCountryStations: Set<RadioStation> = emptySet(),
    private val mSearchStations: Set<RadioStation> = emptySet(),
    private val mFavoriteIds: Set<String> = emptySet()
) : OpenRadioServicePresenter {

    val categoryRequests = mutableListOf<Pair<String, Int>>()

    val countryRequests = mutableListOf<Pair<String, Int>>()

    val searchRequests = mutableListOf<String>()

    val favoriteChecks = mutableListOf<String>()

    var categoriesRequests = 0
        private set

    var countriesRequests = 0
        private set

    var favoritesRequests = 0
        private set

    var deviceLocalsRequests = 0
        private set

    var newStationsRequests = 0
        private set

    var popularStationsRequests = 0
        private set

    override fun getStationsInCategory(categoryId: String, pageNumber: Int): Set<RadioStation> {
        categoryRequests.add(categoryId to pageNumber)
        return pageOf(mCategoryStations, pageNumber)
    }

    override fun getStationsByCountry(countryCode: String, pageNumber: Int): Set<RadioStation> {
        countryRequests.add(countryCode to pageNumber)
        return pageOf(mCountryStations, pageNumber)
    }

    override fun getNewStations(): Set<RadioStation> {
        newStationsRequests++
        return mNewStations
    }

    override fun getPopularStations(): Set<RadioStation> {
        popularStationsRequests++
        return mPopularStations
    }

    override fun getSearchStations(query: String, mediaIdBuilder: MediaIdBuilder): Set<RadioStation> {
        searchRequests.add(query)
        val result = TreeSet<RadioStation>()
        for (radioStation in mSearchStations) {
            val built = RadioStation.makeCopyInstance(radioStation)
            built.id = mediaIdBuilder.build(radioStation.id)
            result.add(built)
        }
        return result
    }

    override fun getAllCategories(): Set<Category> {
        categoriesRequests++
        return mCategories
    }

    override fun getAllCountries(): Set<Country> {
        countriesRequests++
        return mCountries
    }

    override fun getAllFavorites(): Set<RadioStation> {
        favoritesRequests++
        return mFavorites
    }

    override fun getAllDeviceLocal(): Set<RadioStation> {
        deviceLocalsRequests++
        return mDeviceLocals
    }

    override fun isRadioStationFavorite(radioStation: RadioStation): Boolean {
        favoriteChecks.add(radioStation.id)
        return mFavoriteIds.contains(radioStation.id)
    }

    private fun pageOf(all: Set<RadioStation>, pageNumber: Int): Set<RadioStation> {
        if (pageNumber >= all.size) {
            return emptySet()
        }
        return setOf(all.toList()[pageNumber])
    }

    override fun getMediaItemCommand(commandId: String) = unexpected()

    override fun startNetworkMonitor(context: Context, listener: NetworkMonitorListener) = unexpected()

    override fun stopNetworkMonitor(context: Context) = unexpected()

    override fun isPlaybackBlockedByMobileNetwork() = unexpected()

    override fun getLastRadioStation() = unexpected()

    override fun getEqualizerLayer(): EqualizerLayer = unexpected()

    override fun setLastRadioStation(radioStation: RadioStation) = unexpected()

    override fun getCountryCode() = unexpected()

    override fun updateRadioStationFavorite(radioStation: RadioStation) = unexpected()

    override fun updateRadioStationFavorite(radioStation: RadioStation, isFavorite: Boolean) = unexpected()

    override fun updateSortIds(mediaId: String, sortId: Int, categoryMediaId: String) = unexpected()

    override fun getSleepTimerModel(): SleepTimerModel = unexpected()

    override fun clear() = unexpected()

    override fun close() = unexpected()

    private fun unexpected(): Nothing {
        throw AssertionError("Browse command reached a presenter call it has no business making")
    }
}
