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
        assertEquals("US", MediaId.getCountryCode(null, "US"))
        assertEquals("US", MediaId.getCountryCode("unknown", "US"))
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
