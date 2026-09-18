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
import com.yuriy.openradio.shared.model.net.UrlLayerRadioBrowserImpl
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiCache
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiDb
import com.yuriy.openradio.shared.model.storage.makeStation
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
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

        // Seed the cache the reset is supposed to empty, so the reset has something to prove.
        val cache = PersistentApiCache(mContext, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME)
        cache.put(CACHE_KEY, CACHE_VALUE)
        assertEquals(CACHE_VALUE, cache[CACHE_KEY])

        // Android asks for a clear-data run. `pm clear` would take the instrumentation process
        // with it, so do what it does short of the process kill: empty every preference file the
        // app owns, and let CMD_CLEAR_CACHE take both API caches and the stored images.
        val preferenceFiles = clearEveryPreferenceFile()
        assertTrue("The app owns no preference files, so nothing was cleared", preferenceFiles.isNotEmpty())
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(OpenRadioService.CMD_CLEAR_CACHE).resultCode
        )
        // That command answers before it has done the work, so wait for the seeded row to go.
        // Reaching this point is what proves the presenter's clear actually ran to completion.
        awaitCacheCleared(cache)
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

    @Test
    fun aCachedProviderNodeIsBrowsableWhileOffline() {
        PersistentApiCache(mContext, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME)
            .put(UrlLayerRadioBrowserImpl().getAllCategoriesUrl().toString(), CATEGORIES_RESPONSE)
        invalidate(MediaId.MEDIA_ID_ALL_CATEGORIES)

        val children = mBrowser.children(MediaId.MEDIA_ID_ALL_CATEGORIES)

        assertEquals(
            listOf(
                MediaId.MEDIA_ID_CHILD_CATEGORIES + "jazz",
                MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock"
            ),
            children.map { it.mediaId }.sorted()
        )
        for (child in children) {
            assertEquals(true, child.mediaMetadata.isBrowsable)
            assertEquals(false, child.mediaMetadata.isPlayable)
        }
    }

    /**
     * Pins TASK-029. [com.yuriy.openradio.shared.model.media.item.MediaItemAllCategories] and
     * [com.yuriy.openradio.shared.model.media.item.MediaItemCountriesList] return without calling
     * their result listener when there is nothing to show, which is what an offline miss produces.
     * The future behind `onGetChildren` is then never set and the browser waits forever.
     * Delete this test and assert an empty list once the two commands report the empty case.
     */
    @Test
    fun providerNodesNeverAnswerWhenNothingIsCached() {
        for (node in listOf(MediaId.MEDIA_ID_ALL_CATEGORIES, MediaId.MEDIA_ID_COUNTRIES_LIST)) {
            // Another case may have left a cached result behind; this one is about the miss.
            invalidate(node)
            assertThrows(
                "$node answered with nothing cached, so TASK-029 is fixed and this test is stale",
                TimeoutException::class.java
            ) {
                mBrowser.childrenResult(node, timeoutSeconds = HUNG_NODE_TIMEOUT_SECONDS)
            }
        }
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
     * says nothing about whether the caches are empty yet. Waiting for the seeded row to disappear
     * is the synchronization point, and failing here means the reset never finished.
     */
    private fun awaitCacheCleared(cache: PersistentApiCache) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CACHE_CLEAR_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (cache[CACHE_KEY].isEmpty()) {
                return
            }
            Thread.sleep(CACHE_POLL_MILLIS)
        }
        throw AssertionError("The seeded cache row outlived CMD_CLEAR_CACHE")
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
         * Long enough to outlast the 5 second command timeout the browse commands apply, short
         * enough that pinning a hang does not dominate the run.
         */
        const val HUNG_NODE_TIMEOUT_SECONDS = 8L

        const val PREFERENCE_FILE_SUFFIX = ".xml"

        const val CACHE_KEY = "https://radio.example/clear-data-probe"

        const val CACHE_VALUE = """[{"stationuuid":"probe","name":"Probe","url":"https://x.test"}]"""

        const val CACHE_CLEAR_TIMEOUT_SECONDS = 10L

        const val CACHE_POLL_MILLIS = 50L

        val CATEGORIES_RESPONSE = """
            [{"name":"rock","stationcount":12},{"name":"jazz","stationcount":7}]
        """.trimIndent()
    }
}
