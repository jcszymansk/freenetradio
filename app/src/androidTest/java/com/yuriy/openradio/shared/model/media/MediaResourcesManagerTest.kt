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

package com.yuriy.openradio.shared.model.media

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.MediaStream.Companion.BIT_RATE_DEFAULT
import com.yuriy.openradio.shared.model.net.UrlLayerRadioBrowserImpl
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.SleepTimerStorage
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiCache
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiDb
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class MediaResourcesManagerTest {

    private lateinit var context: Context
    private lateinit var favoritesStorage: FavoritesStorage
    private lateinit var localsStorage: DeviceLocalsStorage
    private lateinit var latestRadioStationStorage: LatestRadioStationStorage
    private lateinit var sleepTimerStorage: SleepTimerStorage
    private lateinit var apiCache: PersistentApiCache
    private var resourcesManager: MediaResourcesManager? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val contextRef = WeakReference(context)
        latestRadioStationStorage = LatestRadioStationStorage(contextRef)
        favoritesStorage = FavoritesStorage(contextRef)
        localsStorage = DeviceLocalsStorage(
            contextRef,
            favoritesStorage,
            latestRadioStationStorage
        )
        sleepTimerStorage = SleepTimerStorage(contextRef)
        apiCache = PersistentApiCache(context, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME)
        clearState()
    }

    @After
    fun tearDown() {
        resourcesManager?.let { manager ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync(manager::clean)
        }
        clearState()
    }

    private fun clearState() {
        apiCache.clear()
        sleepTimerStorage.clear()
        latestRadioStationStorage.clear()
        localsStorage.clear()
        favoritesStorage.clear()
    }

    @Test
    fun addingFirstLocalRefreshesRootSubscription() {
        val manager = connectedManager()
        val results = LinkedBlockingQueue<ChildrenResult>()

        manager.subscribe(MediaId.MEDIA_ID_ROOT, subscription(results))

        val initial = awaitChildren(results) { true }
        assertFalse("Initial root load failed", initial.error)
        assertTrue("Initial root load must replace the spinner state", initial.replace)
        assertFalse(initial.containsLocals())

        val station = RadioStation.makeDefaultInstance(localsStorage.getId())
        station.name = "Regression station"
        station.setVariant(BIT_RATE_DEFAULT, "https://example.test/stream")
        station.isLocal = true
        localsStorage.add(station)

        val commandSent = runBlocking(Dispatchers.Main) {
            manager.sendCommand(OpenRadioService.CMD_UPDATE_TREE, Bundle())
        }
        assertTrue("Browse-tree update command was not sent", commandSent)

        val refreshed = awaitChildren(results, ChildrenResult::containsLocals)
        assertFalse("Root refresh failed", refreshed.error)
        assertTrue("Root refresh must replace existing rows", refreshed.replace)
    }

    /**
     * The phone's search, end to end over a real connection: [com.yuriy.openradio.shared.view.dialog.SearchDialog]
     * pushes [MediaId.MEDIA_ID_SEARCH_FROM_APP] onto the browse stack and
     * [com.yuriy.openradio.shared.presenter.MediaPresenter.addMediaItemToStack] hands it straight to
     * [MediaResourcesManager.subscribe] with the query bundle, which is what is reproduced here.
     *
     * Nothing in the service browses that id, so the manager has to answer it with `getSearchResult`
     * instead: the ids that come back carry the `search:` prefix only
     * [com.yuriy.openradio.shared.model.media.item.MediaItemSearchFromService] adds.
     *
     * The provider response is seeded into the Room API cache, keyed the way
     * [com.yuriy.openradio.shared.model.ModelLayerImpl] keys it, so nothing leaves the device.
     */
    @Test
    fun searchingFromTheAppIsAnsweredWithTaggedStations() {
        val manager = connectedManager()
        assertRadioBrowserIsBound(manager)
        apiCache.put(searchUrl(SEARCH_QUERY), SEARCH_RESPONSE)
        val results = LinkedBlockingQueue<ChildrenResult>()

        manager.subscribe(
            MediaId.MEDIA_ID_SEARCH_FROM_APP,
            subscription(results),
            bundle = AppUtils.makeSearchQueryBundle(SEARCH_QUERY)
        )

        val loaded = awaitChildren(results) { it.parentId == MediaId.MEDIA_ID_SEARCH_FROM_APP }
        assertFalse("Search from the app failed", loaded.error)
        assertTrue("Search results must replace whatever the list showed", loaded.replace)
        assertEquals(
            listOf(MediaId.makeSearchId(SEARCH_STATION_ID)),
            loaded.children.map { it.mediaId }
        )
        assertEquals(SEARCH_STATION_NAME, loaded.children.single().mediaMetadata.title)
    }

    /**
     * The fixture is a Radio Browser payload keyed to a Radio Browser URL, so it only answers if
     * that is the provider the service bound at process start. What was bound shows in the root
     * menu: the two Radio Browser only nodes come from the same `Source` value that chose the URL
     * layer, where reading the preference would pass whatever the service actually used.
     */
    private fun assertRadioBrowserIsBound(manager: MediaResourcesManager) {
        val results = LinkedBlockingQueue<ChildrenResult>()
        manager.subscribe(MediaId.MEDIA_ID_ROOT, subscription(results))
        val root = awaitChildren(results) { it.parentId == MediaId.MEDIA_ID_ROOT }
        val rootIds = root.children.map { it.mediaId }
        assertTrue(
            "The service did not bind the Radio Browser provider, so this fixture keys the wrong " +
                "URL and would silently answer nothing. Root offered $rootIds",
            rootIds.containsAll(
                listOf(MediaId.MEDIA_ID_NEW_STATIONS, MediaId.MEDIA_ID_POPULAR_STATIONS)
            )
        )
    }

    /**
     * The cache key is the provider URL, which [UrlLayerRadioBrowserImpl] builds before any DNS
     * mirror is resolved, so it can be reproduced here without touching the network.
     */
    private fun searchUrl(query: String): String {
        return UrlLayerRadioBrowserImpl().getSearchUrl(query).toString()
    }

    private fun connectedManager(): MediaResourcesManager {
        val connected = CountDownLatch(1)
        val manager = MediaResourcesManager(
            context,
            javaClass.simpleName,
            object : MediaResourceManagerListener {
                override fun onConnected() {
                    connected.countDown()
                }

                override fun onPlaybackStateChanged(state: PlaybackState) = Unit

                override fun onMetadataChanged(metadata: MediaMetadata) = Unit
            }
        )
        resourcesManager = manager
        assertTrue("Media browser did not connect", connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return manager
    }

    private fun subscription(results: LinkedBlockingQueue<ChildrenResult>): MediaItemsSubscription {
        return object : MediaItemsSubscription {
            override fun onChildrenLoaded(
                parentId: String,
                children: List<MediaItem>,
                replace: Boolean
            ) {
                results.add(ChildrenResult(parentId, children, replace, false))
            }

            override fun onError(parentId: String) {
                results.add(ChildrenResult(parentId, emptyList(), false, true))
            }
        }
    }

    private fun awaitChildren(
        results: LinkedBlockingQueue<ChildrenResult>,
        predicate: (ChildrenResult) -> Boolean
    ): ChildrenResult {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val remaining = deadline - System.nanoTime()
            val result = results.poll(remaining, TimeUnit.NANOSECONDS) ?: break
            if (predicate(result)) {
                return result
            }
        }
        throw AssertionError("Expected media children callback was not received")
    }

    private data class ChildrenResult(
        val parentId: String,
        val children: List<MediaItem>,
        val replace: Boolean,
        val error: Boolean
    ) {
        fun containsLocals(): Boolean {
            return parentId == MediaId.MEDIA_ID_ROOT &&
                children.any { it.mediaId == MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST }
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L

        /**
         * The service files a search under its query string in the browse tree and never
         * invalidates it, so this query is owned by this case alone.
         */
        const val SEARCH_QUERY = "Jazz & Blues, searched from the app"

        const val SEARCH_STATION_ID = "app-search-station"

        const val SEARCH_STATION_NAME = "Seeded App Search Station"

        val SEARCH_RESPONSE = """
            [{
              "stationuuid": "$SEARCH_STATION_ID",
              "name": "$SEARCH_STATION_NAME",
              "country": "Poland",
              "countrycode": "PL",
              "bitrate": 128,
              "lastcheckok": 1,
              "url": "https://radio.example/stream",
              "url_resolved": "https://radio.example/resolved"
            }]
        """.trimIndent()
    }
}
