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

package com.yuriy.openradio.shared.model.net

import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers every address both [UrlLayer] implementations build. Since [DnsMirrorUrlResolver] took
 * the DNS lookup away, the two are string work over constants, so the whole surface is checked
 * here rather than on a device.
 *
 * The two answer the same interface in opposite shapes. Radio Browser encodes the query into the
 * path and pages server side; WebRadioDB hands back one dataset address per menu, carrying the
 * filter as a query parameter the parser applies while reading, which is why paging cannot change
 * what it returns.
 */
class UrlLayerTest {

    @Test
    fun radioBrowserAddressesTheListsThatTakeNoArgument() {
        val urlLayer = UrlLayerRadioBrowserImpl()

        assertEquals("$RADIO_BROWSER/tags?reverse=true&hidebroken=true", urlLayer.getAllCategoriesUrl().toString())
        assertEquals("$RADIO_BROWSER/stations/topclick/$PAGE_SIZE", urlLayer.getPopularStations().toString())
        assertEquals("$RADIO_BROWSER/stations/lastchange/$PAGE_SIZE", urlLayer.getNewStations().toString())
        assertEquals("$RADIO_BROWSER/countries", urlLayer.getAllCountries().toString())
    }

    @Test
    fun radioBrowserEncodesTheCategoryAndPagesByOneMoreThanThePageSize() {
        val urlLayer = UrlLayerRadioBrowserImpl()

        assertEquals(
            "$RADIO_BROWSER/stations/bytag/Rock%20%26%20Pop" +
                    "?hidebroken=true&order=name&offset=502&limit=$PAGE_SIZE",
            urlLayer.getStationsInCategory(CATEGORY, 2).toString()
        )
    }

    @Test
    fun radioBrowserStartsTheFirstPageAtZero() {
        val urlLayer = UrlLayerRadioBrowserImpl()

        assertEquals(
            "$RADIO_BROWSER/stations/bytag/Rock%20%26%20Pop" +
                    "?hidebroken=true&order=name&offset=0&limit=$PAGE_SIZE",
            urlLayer.getStationsInCategory(CATEGORY, UrlLayer.FIRST_PAGE_INDEX).toString()
        )
    }

    /**
     * Radio Browser is the one place a caller supplied value reaches an address unencoded, and the
     * assertion says so rather than hiding it behind an ISO code that encodes to itself. Nothing
     * guarantees two letters: the code is the tail of a browse id, so any MediaBrowser client can
     * name one. TASK-074 carries the fix; until it lands this is the behaviour.
     */
    @Test
    fun radioBrowserLeavesTheCountryCodeUnencoded() {
        val urlLayer = UrlLayerRadioBrowserImpl()

        assertEquals(
            "$RADIO_BROWSER/stations/bycountrycodeexact/$AMBIGUOUS_COUNTRY_CODE" +
                    "?offset=251&limit=$PAGE_SIZE",
            urlLayer.getStationsByCountry(AMBIGUOUS_COUNTRY_CODE, 1).toString()
        )
    }

    @Test
    fun radioBrowserEncodesTheSearchQueryAndAsksForOnePage() {
        val urlLayer = UrlLayerRadioBrowserImpl()

        assertEquals(
            "$RADIO_BROWSER/stations/search?name=Jazz%20%26%20Blues&offset=0&limit=$PAGE_SIZE",
            urlLayer.getSearchUrl(QUERY).toString()
        )
    }

    @Test
    fun webRadioServesEveryUnfilteredListFromTheOneDataset() {
        val urlLayer = UrlLayerWebRadioImpl()

        assertEquals(WEB_RADIO_STATIONS, urlLayer.getAllCategoriesUrl().toString())
        assertEquals(WEB_RADIO_STATIONS, urlLayer.getPopularStations().toString())
        assertEquals(WEB_RADIO_STATIONS, urlLayer.getNewStations().toString())
    }

    @Test
    fun webRadioKeepsCountriesInTheirOwnDataset() {
        val urlLayer = UrlLayerWebRadioImpl()

        assertEquals(
            "https://jcorporation.github.io/webradiodb/db/index/countries.min.json",
            urlLayer.getAllCountries().toString()
        )
    }

    @Test
    fun webRadioEncodesTheCategoryAndIgnoresThePageNumber() {
        val urlLayer = UrlLayerWebRadioImpl()
        val firstPage = urlLayer.getStationsInCategory(CATEGORY, UrlLayer.FIRST_PAGE_INDEX).toString()

        assertEquals("$WEB_RADIO_STATIONS?categoryId=Rock%20%26%20Pop", firstPage)
        assertEquals(firstPage, urlLayer.getStationsInCategory(CATEGORY, 3).toString())
    }

    /**
     * The encoding is what keeps the filter one parameter: an ampersand left alone would end
     * countryId early and hand the dataset an empty filter.
     */
    @Test
    fun webRadioEncodesTheCountryCode() {
        val urlLayer = UrlLayerWebRadioImpl()

        assertEquals(
            "$WEB_RADIO_STATIONS?countryId=A%26",
            urlLayer.getStationsByCountry(AMBIGUOUS_COUNTRY_CODE, 1).toString()
        )
    }

    @Test
    fun webRadioEncodesTheSearchQuery() {
        val urlLayer = UrlLayerWebRadioImpl()

        assertEquals(
            "$WEB_RADIO_STATIONS?searchId=Jazz%20%26%20Blues",
            urlLayer.getSearchUrl(QUERY).toString()
        )
    }

    private companion object {

        const val PAGE_SIZE = DependencyRegistryCommon.PAGE_SIZE

        const val RADIO_BROWSER = "${UrlLayerRadioBrowserImpl.BASE_URL_PREFIX}/json"

        const val WEB_RADIO_STATIONS =
            "https://jcorporation.github.io/webradiodb/db/index/webradios.min.json"

        const val CATEGORY = "Rock & Pop"

        const val QUERY = "Jazz & Blues"

        /**
         * Two characters, like every real code, and the second one means something in an address.
         */
        const val AMBIGUOUS_COUNTRY_CODE = "A&"
    }
}
