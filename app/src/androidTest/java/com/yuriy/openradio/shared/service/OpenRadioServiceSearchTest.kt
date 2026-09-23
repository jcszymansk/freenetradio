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
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.net.UrlLayerRadioBrowserImpl
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiCache
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiDb
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the search half of the [OpenRadioService] contract through a real
 * [androidx.media3.session.MediaBrowser]: `search` has to reach the provider command, answer, and
 * push a result count, and `getSearchResult` has to answer from what that left behind.
 *
 * The response the provider would have returned is seeded into the Room API cache, keyed exactly
 * the way [com.yuriy.openradio.shared.model.ModelLayerImpl] keys it, so the whole parse, browse
 * tree and result path runs without a request leaving the device.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioServiceSearchTest {

    private lateinit var mContext: Context

    private lateinit var mStorages: ServiceStorages

    private lateinit var mCache: PersistentApiCache

    private lateinit var mBrowser: ServiceBrowser

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mStorages = ServiceStorages(mContext)
        mStorages.clear()
        mCache = PersistentApiCache(mContext, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME)
        mCache.clear()
        mBrowser = ServiceBrowser()
        mBrowser.connect()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.forgetNotifications()
        assertRadioBrowserIsBound()
    }

    /**
     * The fixtures here are Radio Browser payloads keyed to Radio Browser URLs, so the test is only
     * meaningful if that is the provider the service is using.
     *
     * Reading `SourcePreferences` would not tell us: `DependencyRegistryCommon.init` reads the
     * selection once, at process start, and binds both the URL layer and the root command from it,
     * so the preference only decides what a *future* process will use. What was actually bound
     * does show, in the root menu: the two Radio Browser only nodes are added by the same `Source`
     * value that chose the URL layer. Asserting those is therefore a real check, where asserting
     * the preference would pass no matter what the service bound.
     */
    private fun assertRadioBrowserIsBound() {
        val rootIds = mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT)
        assertTrue(
            "The service did not bind the Radio Browser provider, so these fixtures key the " +
                "wrong URLs and would silently answer nothing. Root offered $rootIds",
            rootIds.containsAll(
                listOf(MediaId.MEDIA_ID_NEW_STATIONS, MediaId.MEDIA_ID_POPULAR_STATIONS)
            )
        )
    }

    @After
    fun tearDown() {
        mCache.clear()
        mStorages.clear()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.release()
    }

    @Test
    fun searchAnswersFromTheSeededCacheWithoutLeavingTheDevice() {
        mCache.put(searchUrl(QUERY), SEARCH_RESPONSE)

        val search = mBrowser.search(QUERY)
        assertEquals(LibraryResult.RESULT_SUCCESS, search.resultCode)

        val pushed = mBrowser.awaitSearchResultChanged(QUERY)
        assertEquals("The seeded station was not counted", 1, pushed.itemCount)

        val result = mBrowser.searchResult(QUERY)
        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        val items = result.value.orEmpty()
        assertEquals(1, items.size)
        val station = items.single()
        assertEquals(MediaId.makeSearchId(STATION_ID), station.mediaId)
        assertEquals("Seeded Search Station", station.mediaMetadata.title)
        assertEquals(true, station.mediaMetadata.isPlayable)
        assertEquals(false, station.mediaMetadata.isBrowsable)
    }

    @Test
    fun searchResultCanBeRequestedWithoutAPrecedingSearch() {
        // A phone client goes straight to getSearchResult; only a head unit calls search first.
        mCache.put(searchUrl(DIRECT_QUERY), SEARCH_RESPONSE)

        val result = mBrowser.searchResult(DIRECT_QUERY)

        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        assertEquals(
            listOf(MediaId.makeSearchId(STATION_ID)),
            result.value.orEmpty().map { it.mediaId }
        )
    }

    @Test
    fun aSearchMissLeavesTheCacheAloneAndAnswersEmpty() {
        mCache.put(searchUrl(QUERY), SEARCH_RESPONSE)

        // A different query keys a different cache row, and nothing may be downloaded offline.
        val result = mBrowser.searchResult(MISS_QUERY)

        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        assertTrue(result.value.isNullOrEmpty())
        assertEquals(SEARCH_RESPONSE, mCache[searchUrl(QUERY)])
    }

    @Test
    fun anEmptyQueryIsAnsweredRatherThanRejected() {
        val result = mBrowser.searchResult(BLANK_QUERY)

        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        assertTrue(result.value.isNullOrEmpty())
    }

    /**
     * The cache key is the provider URL, which [UrlLayerRadioBrowserImpl] builds before any DNS
     * mirror is resolved, so it can be reproduced here without touching the network.
     */
    private fun searchUrl(query: String): String {
        return UrlLayerRadioBrowserImpl().getSearchUrl(query).toString()
    }

    private companion object {

        /**
         * The service files search results in its browse tree under the query string, and nothing
         * ever invalidates those entries: `CMD_UPDATE_TREE` reaches the root and the locals only.
         * Each case therefore owns a query no other case uses, so a result left behind by one
         * cannot answer another.
         */
        const val QUERY = "Jazz & Blues"

        const val DIRECT_QUERY = "Jazz & Blues, asked for directly"

        const val MISS_QUERY = "Nothing is seeded for this"

        const val BLANK_QUERY = " "

        const val STATION_ID = "seeded-search-station"

        val SEARCH_RESPONSE = """
            [{
              "stationuuid": "$STATION_ID",
              "name": "Seeded Search Station",
              "country": "Poland",
              "countrycode": "PL",
              "bitrate": 128,
              "lastcheckok": 1,
              "url": "$UNREACHABLE_ORIGIN/stream",
              "url_resolved": "$UNREACHABLE_ORIGIN/resolved"
            }]
        """.trimIndent()
    }
}
