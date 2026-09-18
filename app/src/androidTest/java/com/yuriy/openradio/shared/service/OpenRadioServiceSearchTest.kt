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
import com.yuriy.openradio.shared.model.net.UrlLayerRadioBrowserImpl
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiCache
import com.yuriy.openradio.shared.model.storage.cache.api.PersistentApiDb
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
 * the way [com.yuriy.openradio.shared.model.ModelLayerImpl] keys it, so no request leaves the
 * device. That cache is currently unreachable while offline, which is what
 * [searchAnswersFromTheProviderPathWithoutLeavingTheDevice] pins.
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
    }

    @After
    fun tearDown() {
        mCache.clear()
        mStorages.clear()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.release()
    }

    /**
     * Pins TASK-030. [com.yuriy.openradio.shared.model.ModelLayerImpl.downloadData] asks the network
     * layer for connectivity before it looks in either cache, so a fresh cached response is thrown
     * away whenever the device is offline, which is the one situation the cache exists for. Until
     * that ordering is fixed, an offline search answers with nothing.
     *
     * Once TASK-030 lands, this test should assert that the seeded station comes back instead.
     */
    @Test
    fun searchAnswersFromTheProviderPathWithoutLeavingTheDevice() {
        mCache.put(searchUrl(QUERY), SEARCH_RESPONSE)

        val search = mBrowser.search(QUERY)
        assertEquals(LibraryResult.RESULT_SUCCESS, search.resultCode)

        val pushed = mBrowser.awaitSearchResultChanged(QUERY)
        assertEquals(
            "The seeded cache was used, so TASK-030 is fixed and this test is stale",
            0,
            pushed.itemCount
        )

        val result = mBrowser.searchResult(QUERY)
        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        assertTrue(result.value.isNullOrEmpty())

        assertEquals(
            "The cached response was consumed rather than skipped",
            SEARCH_RESPONSE,
            mCache[searchUrl(QUERY)]
        )
    }

    @Test
    fun searchResultCanBeRequestedWithoutAPrecedingSearch() {
        // A phone client goes straight to getSearchResult; only a head unit calls search first.
        val result = mBrowser.searchResult(QUERY)

        assertEquals(LibraryResult.RESULT_SUCCESS, result.resultCode)
        assertTrue(result.value.isNullOrEmpty())
    }

    @Test
    fun anEmptyQueryIsAnsweredRatherThanRejected() {
        val result = mBrowser.searchResult(" ")

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

        const val QUERY = "Jazz & Blues"

        val SEARCH_RESPONSE = """
            [{
              "stationuuid": "seeded-search-station",
              "name": "Seeded Search Station",
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
