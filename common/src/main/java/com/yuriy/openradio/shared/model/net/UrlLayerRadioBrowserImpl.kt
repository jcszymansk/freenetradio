package com.yuriy.openradio.shared.model.net

import android.net.Uri
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon

class UrlLayerRadioBrowserImpl : UrlLayer {

    override fun getAllCategoriesUrl(): Uri {
        return Uri.parse(BASE_URL + "tags?reverse=true&hidebroken=true")
    }

    override fun getStationsInCategory(categoryId: String, pageNumber: Int): Uri {
        return Uri.parse(
            BASE_URL + "stations/bytag/" + encodeValue(categoryId) + "?hidebroken=true&order=name"
                    + "&offset=" + (pageNumber * (DependencyRegistryCommon.PAGE_SIZE + 1))
                    + "&limit=" + DependencyRegistryCommon.PAGE_SIZE
        )
    }

    override fun getStationsByCountry(countryCode: String, pageNumber: Int): Uri {
        return Uri.parse(
            BASE_URL + "stations/bycountrycodeexact/" + countryCode
                    + "?offset=" + (pageNumber * (DependencyRegistryCommon.PAGE_SIZE + 1))
                    + "&limit=" + DependencyRegistryCommon.PAGE_SIZE
        )
    }

    override fun getPopularStations(): Uri {
        return Uri.parse(BASE_URL + "stations/topclick/" + DependencyRegistryCommon.PAGE_SIZE)
    }

    override fun getNewStations(): Uri {
        return Uri.parse(BASE_URL + "stations/lastchange/" + DependencyRegistryCommon.PAGE_SIZE)
    }

    override fun getSearchUrl(query: String): Uri {
        return Uri.parse(
            BASE_URL + "stations/search?name=" + encodeValue(query)
                    + "&offset=" + 0
                    + "&limit=" + DependencyRegistryCommon.PAGE_SIZE
        )
    }

    override fun getAllCountries(): Uri {
        return Uri.parse(BASE_URL + "countries")
    }

    /**
     * Method to encode a string value using UTF-8 encoding scheme.
     *
     * @param value
     * @return
     */
    private fun encodeValue(value: String): String {
        return Uri.encode(value)
    }

    companion object {

        /**
         * Stands in for the mirror that has not been discovered yet. Every address built here
         * carries it, and [DnsMirrorUrlResolver] replaces it with a live host at connection time.
         */
        internal const val BASE_URL_PREFIX = "https://do-look-up-dns-first"

        /**
         * Base URL for the API requests.
         */
        private const val BASE_URL = "${BASE_URL_PREFIX}/json/"
    }
}
