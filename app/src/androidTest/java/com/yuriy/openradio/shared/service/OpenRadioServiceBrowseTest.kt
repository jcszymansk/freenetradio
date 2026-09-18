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
import androidx.media.utils.MediaConstants
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.isInvalid
import com.yuriy.openradio.shared.model.net.UrlLayerRadioBrowserImpl
import com.yuriy.openradio.shared.model.storage.cache.api.InMemoryApiCache
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiCache
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiDb
import com.yuriy.openradio.shared.model.storage.images.Image
import com.yuriy.openradio.shared.model.storage.images.ImageDao
import com.yuriy.openradio.shared.model.storage.images.ImagesDatabase
import com.yuriy.openradio.shared.model.storage.makeStation
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives a real [androidx.media3.session.MediaBrowser] through the browse half of the
 * [OpenRadioService] contract: connecting, the library root, the root children, the two stores
 * backed nodes, subscriptions and single item lookup.
 *
 * Phone and Android Auto are both MediaBrowser clients of this one service and this one tree, so
 * everything asserted here is shared by both surfaces.
 *
 * The suite runs with networking disabled. That covers the nodes that read preferences directly,
 * and a provider backed node too, by seeding the response into the Room API cache. What it cannot
 * reach is a node whose data has to be fetched; the JVM command tests cover those against fakes.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioServiceBrowseTest {

    private lateinit var mContext: Context

    private lateinit var mStorages: ServiceStorages

    private lateinit var mBrowser: ServiceBrowser

    /**
     * Whether an Activity was alive at the moment the browser connected. Sampled here rather than
     * inside the test because by then the connection has already happened, and an Activity that
     * came and went during it would leave no trace.
     */
    private var mActivityAliveAtConnect = true

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mStorages = ServiceStorages(mContext)
        mStorages.clear()
        mBrowser = ServiceBrowser()
        mActivityAliveAtConnect = anyActivityExists()
        mBrowser.connect()
        // The service caches every node but favorites and locals, and it outlives a single test.
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.forgetNotifications()
    }

    @After
    fun tearDown() {
        mStorages.clear()
        PersistentApiCache(mContext, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME).clear()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.release()
    }

    @Test
    fun connectsAndServesTheLibraryRootWithoutAnyActivity() {
        assertFalse(
            "An Activity was alive when the browser connected, so this is not a service first start",
            mActivityAliveAtConnect
        )
        assertFalse("An Activity started while the service was serving", anyActivityExists())

        val result = mBrowser.libraryRoot()

        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        val root = result.value
        assertNotNull("The service answered the root request with no item", root)
        assertEquals(MediaId.MEDIA_ID_ROOT, root!!.mediaId)
        assertEquals(true, root.mediaMetadata.isBrowsable)
        assertEquals(false, root.mediaMetadata.isPlayable)
        assertTrue(
            "The root hints must advertise search so a car head unit offers it",
            result.params?.extras?.getBoolean(
                MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED
            ) ?: false
        )
    }

    @Test
    fun rootChildrenOfferTheCatalogueAndNothingElse() {
        val children = mBrowser.children(MediaId.MEDIA_ID_ROOT)
        val ids = children.map { it.mediaId }

        assertTrue("Worldwide stations are always offered", ids.contains(MediaId.MEDIA_ID_ALL_CATEGORIES))
        assertTrue("The countries list is always offered", ids.contains(MediaId.MEDIA_ID_COUNTRIES_LIST))
        assertFalse(
            "An empty favorites store must not produce a favorites node",
            ids.contains(MediaId.MEDIA_ID_FAVORITES_LIST)
        )
        assertFalse(
            "An empty locals store must not produce a locals node",
            ids.contains(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )
        assertEquals("The root offers the same node twice", ids.size, ids.toSet().size)
        for (child in children) {
            assertEquals("${child.mediaId} is not browsable", true, child.mediaMetadata.isBrowsable)
            assertEquals("${child.mediaId} is playable", false, child.mediaMetadata.isPlayable)
            assertTrue(
                "${child.mediaId} carries no title",
                child.mediaMetadata.title.isNullOrEmpty().not()
            )
        }
    }

    @Test
    fun seededFavoritesAndLocalsBecomeBrowsableNodes() {
        val favorite = makeStation("fav-1", name = "Favorite one")
        val local = makeStation(mStorages.locals.getId(), name = "Local one", isLocal = true)
        mStorages.favorites.add(favorite)
        mStorages.locals.add(local)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)

        val ids = mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT)

        assertEquals(
            "Favorites lead the root menu",
            MediaId.MEDIA_ID_FAVORITES_LIST,
            ids.first()
        )
        assertEquals(
            "Local stations close the root menu",
            MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST,
            ids.last()
        )

        val favorites = mBrowser.children(MediaId.MEDIA_ID_FAVORITES_LIST)
        assertEquals(listOf(favorite.id), favorites.map { it.mediaId })
        assertEquals("Favorite one", favorites.first().mediaMetadata.title)
        assertEquals(true, favorites.first().mediaMetadata.isPlayable)

        val locals = mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        assertEquals(listOf(local.id), locals.map { it.mediaId })
        assertEquals("Local one", locals.first().mediaMetadata.title)
        assertEquals(true, locals.first().mediaMetadata.isPlayable)
    }

    @Test
    fun subscribesAndUnsubscribesRootAndChildNodes() {
        mStorages.favorites.add(makeStation("fav-1"))
        mStorages.locals.add(makeStation(mStorages.locals.getId(), isLocal = true))
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)

        val nodes = listOf(
            MediaId.MEDIA_ID_ROOT,
            MediaId.MEDIA_ID_FAVORITES_LIST,
            MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
        )
        for (node in nodes) {
            assertEquals(
                "Subscribing to $node failed",
                LibraryResult.RESULT_SUCCESS,
                mBrowser.subscribe(node).resultCode
            )
        }

        // A live subscription is what turns a store change into a push, so prove one arrives.
        mBrowser.forgetNotifications()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        assertEquals(
            MediaId.MEDIA_ID_ROOT,
            mBrowser.awaitChildrenChanged(MediaId.MEDIA_ID_ROOT).parentId
        )
        assertEquals(
            MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST,
            mBrowser.awaitChildrenChanged(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST).parentId
        )

        for (node in nodes) {
            assertEquals(
                "Unsubscribing from $node failed",
                LibraryResult.RESULT_SUCCESS,
                mBrowser.unsubscribe(node).resultCode
            )
        }
    }

    @Test
    fun singleItemLookupResolvesBrowsedStationsAndRejectsUnknownIds() {
        val local = makeStation(mStorages.locals.getId(), name = "Local one", isLocal = true)
        mStorages.locals.add(local)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)

        val found = mBrowser.item(local.id)
        assertEquals(LibraryResult.RESULT_SUCCESS, found.resultCode)
        assertEquals(local.id, found.value?.mediaId)

        // The service answers from the browse tree only, so an id it never served is a bad value.
        assertEquals(
            LibraryResult.RESULT_ERROR_BAD_VALUE,
            mBrowser.item("no-such-station").resultCode
        )
    }

    @Test
    fun reconnectsOnAnEmptyProfileAfterEveryStoreIsCleared() {
        mStorages.favorites.add(makeStation("fav-1"))
        mStorages.locals.add(makeStation(mStorages.locals.getId(), isLocal = true))
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        assertTrue(
            mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT).contains(MediaId.MEDIA_ID_FAVORITES_LIST)
        )

        // Android asks for a clear-data run. `pm clear` would take the instrumentation process
        // with it, so do what it does short of the process kill: empty every preference file the
        // app owns, and let CMD_CLEAR_CACHE take both API caches, the stored images and the
        // latest station.
        val preferenceFiles = clearEveryPreferenceFile()
        assertTrue("The app owns no preference files, so nothing was cleared", preferenceFiles.isNotEmpty())

        // Seed what the command itself is supposed to empty, after the wipe so the wipe cannot be
        // what removes it. The in-memory cache keeps its map in a static field, so an instance
        // built here is the one the service reads. The latest station is written back into a
        // preference file on purpose: the command has to take it away again for the
        // every-file-is-empty check below to hold, so that check cannot pass trivially either.
        //
        // It is written through a throwaway storage rather than the service's own. Adding through
        // the service's instance would also seed its in-memory copy, which no clear resets
        // (TASK-027), and the next service start would then adopt the probe as its active station
        // and browse a playlist for it.
        val persistentCache = PersistentApiCache(mContext, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME)
        val memoryCache = InMemoryApiCache()
        val images = ImagesDatabase.getInstance(mContext).rsImageDao()
        persistentCache.put(CACHE_KEY, CACHE_VALUE)
        memoryCache.put(CACHE_KEY, CACHE_VALUE)
        images.insertImage(Image(CLEAR_PROBE_STATION_ID, byteArrayOf(1, 2, 3)))
        mStorages.freshLatest().add(makeStation(CLEAR_PROBE_STATION_ID))
        assertEquals(CACHE_VALUE, persistentCache[CACHE_KEY])
        assertEquals(CACHE_VALUE, memoryCache[CACHE_KEY])
        // The probe row, not the row count: whatever else the database already holds is the app
        // data this scenario is about clearing, so its presence must not fail the setup.
        assertNotNull(images.getImage(CLEAR_PROBE_STATION_ID))
        assertEquals(CLEAR_PROBE_STATION_ID, mStorages.freshLatest().get().id)

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(OpenRadioService.CMD_CLEAR_CACHE).resultCode
        )
        awaitClearCompleted(persistentCache, memoryCache, images)
        // The service's own storage has to agree, not just the file: it holds the station the next
        // start would adopt as the active one.
        assertTrue(
            "The service still reports a latest station after the clear",
            mStorages.latest.get().isInvalid()
        )
        for (name in preferenceFiles) {
            assertTrue(
                "$name survived the wipe",
                mContext.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty()
            )
        }
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.release()

        mBrowser = ServiceBrowser()
        mBrowser.connect()

        assertEquals(
            MediaId.MEDIA_ID_ROOT,
            mBrowser.libraryRoot().value?.mediaId
        )
        val ids = mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT)
        assertTrue("The catalogue is gone after a reconnect", ids.contains(MediaId.MEDIA_ID_ALL_CATEGORIES))
        assertFalse(ids.contains(MediaId.MEDIA_ID_FAVORITES_LIST))
        assertFalse(ids.contains(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST))
    }

    /**
     * Uses the popular stations node rather than one of the two
     * [providerNodesAnswerWithAnEmptyListWhenNothingIsCached] browses, and that is deliberate. A
     * persistent cache hit is promoted into
     * [com.yuriy.openradio.shared.model.storage.cache.api.InMemoryApiCache],
     * whose map is static and process wide, so seeding a URL here would leave it answering for the
     * rest of the run. It happens to be cleared today because releasing the last browser destroys
     * the service and `onDestroy` closes the presenter, but that is Android's timing rather than
     * this suite's, so the two cases are kept off each other's URLs instead.
     */
    @Test
    fun aCachedProviderNodeIsBrowsableWhileOffline() {
        val url = UrlLayerRadioBrowserImpl().getPopularStations().toString()
        // The in-memory cache is a static map that outlives any one test, so empty it first:
        // otherwise a response left there for this URL could answer the browse and the Room
        // fixture below would never be read.
        val memoryCache = InMemoryApiCache()
        memoryCache.remove(url)
        assertTrue(memoryCache[url].isEmpty())
        PersistentApiCache(mContext, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME)
            .put(url, POPULAR_RESPONSE)
        invalidate(MediaId.MEDIA_ID_POPULAR_STATIONS)

        val children = mBrowser.children(MediaId.MEDIA_ID_POPULAR_STATIONS)

        assertEquals(listOf("popular-one", "popular-two"), children.map { it.mediaId }.sorted())
        for (child in children) {
            assertEquals("${child.mediaId} is not playable", true, child.mediaMetadata.isPlayable)
            assertEquals("${child.mediaId} is browsable", false, child.mediaMetadata.isBrowsable)
        }
        // A persistent hit is promoted into memory, so finding it there afterwards is what shows
        // the stations came from the Room fixture rather than from something already in memory.
        assertEquals(POPULAR_RESPONSE, memoryCache[url])
    }

    /**
     * A provider node with nothing to offer still has to answer. Both of these fetch their children
     * rather than read a store, so with networking off and nothing cached they come back empty,
     * which is the ordinary offline case and not an exotic one.
     *
     * The service completes `onGetChildren` from the command's result listener and from nowhere
     * else, so a command that reported the empty case only as a playback-state message used to
     * leave the browser waiting for as long as it cared to (TASK-029).
     */
    @Test
    fun providerNodesAnswerWithAnEmptyListWhenNothingIsCached() {
        for (node in listOf(MediaId.MEDIA_ID_ALL_CATEGORIES, MediaId.MEDIA_ID_COUNTRIES_LIST)) {
            // Another case may have left a cached result behind; this one is about the miss.
            invalidate(node)

            val result = mBrowser.childrenResult(node, timeoutSeconds = EMPTY_NODE_TIMEOUT_SECONDS)

            assertEquals(
                "$node did not answer with a success",
                LibraryResult.RESULT_SUCCESS,
                result.resultCode
            )
            assertTrue("$node offered children with nothing cached", result.value!!.isEmpty())
        }
    }

    /**
     * The browse tree is a fixed set of nodes, so an id outside it can only come from a client that
     * invented one. It has to be turned away rather than left pending: the service would otherwise
     * hold a request open that no command is ever going to answer.
     */
    @Test
    fun aParentIdNoCommandAnswersIsRejected() {
        val result = mBrowser.childrenResult(UNKNOWN_PARENT_ID)

        assertEquals(LibraryResult.RESULT_ERROR_BAD_VALUE, result.resultCode)
    }

    /**
     * Drops the service's cached children for [node].
     *
     * `CMD_UPDATE_TREE` only reaches the root and the locals. The sort-id command is the other
     * path that invalidates a node by name, and for a category that is neither favorites nor
     * locals it changes no sort id, so notifying is all it does.
     */
    private fun invalidate(node: String) {
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_UPDATE_SORT_IDS,
                OpenRadioStore.makeUpdateSortIdsBundle("not-a-station", 0, node)
            ).resultCode
        )
    }

    /**
     * [Stage.DESTROYED] is excluded on purpose: an Activity an earlier test already tore down is
     * gone as far as this service is concerned, and counting it would make the check depend on
     * which classes ran before. The lifecycle monitor is main thread state, so it is read there.
     */
    /**
     * `CMD_CLEAR_CACHE` hands the work to a coroutine and answers immediately, so its success code
     * says nothing about whether anything has been emptied yet.
     *
     * `OpenRadioServicePresenterImpl.clear` runs its four steps in order: the persistent API cache,
     * the in-memory one, the stored images, then the latest station. Waiting for the latest station
     * is therefore a signal that the whole operation finished rather than only the step being
     * watched, and each of the other three is then asserted on its own rather than inferred from
     * that ordering.
     */
    private fun awaitClearCompleted(
        persistentCache: PersistentApiCache,
        memoryCache: InMemoryApiCache,
        images: ImageDao
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CACHE_CLEAR_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (mStorages.freshLatest().get().isInvalid()) {
                assertTrue(
                    "The persistent cache row outlived the clear",
                    persistentCache[CACHE_KEY].isEmpty()
                )
                assertTrue(
                    "The in-memory cache row outlived the clear",
                    memoryCache[CACHE_KEY].isEmpty()
                )
                assertEquals("The stored image outlived the clear", 0, images.getCount())
                return
            }
            Thread.sleep(CACHE_POLL_MILLIS)
        }
        throw AssertionError("CMD_CLEAR_CACHE did not finish: the seeded latest station is still stored")
    }

    /**
     * Empties every preference file the app has written, found by listing `shared_prefs` rather
     * than by naming the stores, so a store added later is covered without touching this test.
     *
     * The files are cleared through [android.content.SharedPreferences] instead of being deleted:
     * Android caches one instance per file per process, and the service holds several of them, so
     * deleting the file on disk would leave the service reading the values it already has. That is
     * the one thing `pm clear` gets for free by killing the process.
     *
     * The ExoPlayer media cache under the external files directory is left alone. It is not part
     * of the browse profile and the player holds it open.
     *
     * @return the names of the files that were cleared.
     */
    private fun clearEveryPreferenceFile(): List<String> {
        val directory = java.io.File(mContext.applicationInfo.dataDir, "shared_prefs")
        val names = (directory.listFiles() ?: emptyArray())
            .map { it.name }
            .filter { it.endsWith(PREFERENCE_FILE_SUFFIX) }
            .map { it.removeSuffix(PREFERENCE_FILE_SUFFIX) }
        for (name in names) {
            mContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        return names
    }

    private fun anyActivityExists(): Boolean {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val result = AtomicBoolean()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(
                Stage.values()
                    .filter { it != Stage.DESTROYED }
                    .any { monitor.getActivitiesInStage(it).isNotEmpty() }
            )
        }
        return result.get()
    }

    private companion object {

        /**
         * Long enough to outlast the 5 second timeout a browse command applies to its own work,
         * so a node that answers only once that expires still counts as answering.
         */
        const val EMPTY_NODE_TIMEOUT_SECONDS = 8L

        /**
         * Matches no [MediaId] prefix, so the service finds no command for it.
         */
        const val UNKNOWN_PARENT_ID = "__NOT_A_NODE__"

        const val PREFERENCE_FILE_SUFFIX = ".xml"

        const val CACHE_KEY = "https://radio.example/clear-data-probe"

        const val CACHE_VALUE = """[{"stationuuid":"probe","name":"Probe","url":"https://x.test"}]"""

        const val CACHE_CLEAR_TIMEOUT_SECONDS = 10L

        const val CACHE_POLL_MILLIS = 50L

        const val CLEAR_PROBE_STATION_ID = "clear-data-probe"

        val POPULAR_RESPONSE = """
            [
              {"stationuuid":"popular-one","name":"Popular One","bitrate":128,
               "url":"https://radio.example/one","url_resolved":"https://radio.example/one"},
              {"stationuuid":"popular-two","name":"Popular Two","bitrate":128,
               "url":"https://radio.example/two","url_resolved":"https://radio.example/two"}
            ]
        """.trimIndent()
    }
}
