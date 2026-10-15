/*
 * Copyright 2017-2020 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.source.SourcesLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdTest {

    @Test
    fun buildsAndNormalizesSearchIds() {
        val searchId = MediaId.makeSearchId("Jazz")

        assertEquals("search:Jazz", searchId)
        assertTrue(MediaId.isFromSearch(searchId))
        assertEquals("Jazz", MediaId.normalizeFromSearchId(searchId))
        assertFalse(MediaId.isFromSearch("Jazz"))
        assertTrue(MediaId.containsId(MediaId.MEDIA_ID_ROOT))
        assertFalse(MediaId.containsId("unknown"))
    }

    @Test
    fun classifiesIdsAndCountryCodes() {
        assertEquals("", MediaId.getId("", "US"))
        assertEquals(MediaId.MEDIA_ID_ROOT, MediaId.getId(MediaId.MEDIA_ID_ROOT, "US"))
        assertEquals(
            MediaId.MEDIA_ID_COUNTRY_STATIONS,
            MediaId.getId(MediaId.MEDIA_ID_COUNTRIES_LIST + "pl", "US")
        )
        assertEquals(MediaId.MEDIA_ID_COUNTRIES_LIST, MediaId.getId(MediaId.MEDIA_ID_COUNTRIES_LIST, "US"))
        assertEquals("PL", MediaId.getCountryCode(MediaId.MEDIA_ID_COUNTRIES_LIST + "pl", "US"))
        assertEquals("US", MediaId.getCountryCode(MediaId.MEDIA_ID_COUNTRIES_LIST, "US"))
        assertEquals("US", MediaId.getCountryCode(null, "US"))
        assertEquals("US", MediaId.getCountryCode("unknown", "US"))
    }

    /**
     * The service resolves a browsed node with no default country, which is what keeps a child
     * category from being read as the stations of the default country.
     */
    @Test
    fun aChildCategoryResolvesToTheChildCategoriesNode() {
        assertEquals(
            MediaId.MEDIA_ID_CHILD_CATEGORIES,
            MediaId.getId(MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock", "")
        )
    }

    /**
     * The phone keeps the search marker on its own browse stack and never sends it to the service,
     * so it must resolve to no browse node, unlike [MediaId.MEDIA_ID_SEARCH_FROM_SERVICE].
     */
    @Test
    fun theClientSideSearchMarkerResolvesToNoBrowseNode() {
        assertEquals("", MediaId.getId(MediaId.MEDIA_ID_SEARCH_FROM_APP, "US"))
        assertEquals(
            MediaId.MEDIA_ID_SEARCH_FROM_SERVICE,
            MediaId.getId(MediaId.MEDIA_ID_SEARCH_FROM_SERVICE, "US")
        )
    }

    @Test
    fun classifiesSortableIds() {
        assertTrue(MediaId.isSortable(MediaId.MEDIA_ID_FAVORITES_LIST))
        assertTrue(MediaId.isSortable(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST))
        assertFalse(MediaId.isSortable(MediaId.MEDIA_ID_ROOT))
    }

    @Test
    fun classifiesRefreshableIdsBySource() {
        val radioBrowser = FixedSourcesLayer(Source.RADIO_BROWSER)
        val webRadio = FixedSourcesLayer(Source.WEB_RADIO)

        assertTrue(MediaId.isRefreshable(MediaId.MEDIA_ID_COUNTRY_STATIONS, radioBrowser))
        assertTrue(MediaId.isRefreshable(MediaId.MEDIA_ID_COUNTRIES_LIST + "PL", radioBrowser))
        assertTrue(MediaId.isRefreshable(MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock", radioBrowser))
        assertFalse(MediaId.isRefreshable(MediaId.MEDIA_ID_COUNTRIES_LIST, radioBrowser))
        assertFalse(MediaId.isRefreshable(MediaId.MEDIA_ID_ROOT, radioBrowser))
        assertFalse(MediaId.isRefreshable(MediaId.MEDIA_ID_COUNTRY_STATIONS, webRadio))
    }

    private class FixedSourcesLayer(private val activeSource: Source) : SourcesLayer {
        override fun getAllSources() = Source.entries.toSet()

        override fun getActiveSource() = activeSource

        override fun setActiveSource(source: Source) = Unit
    }
}
