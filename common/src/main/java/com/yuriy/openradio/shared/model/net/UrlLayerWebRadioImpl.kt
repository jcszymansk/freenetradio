/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import android.net.Uri

/**
 * Use single url to get whole dataset and parse it into the data object.
 */
class UrlLayerWebRadioImpl : UrlLayer {

    override fun getAllCategoriesUrl(): Uri {
        return URI
    }

    override fun getStationsInCategory(categoryId: String, pageNumber: Int): Uri {
        val url = "$URL$KEY_CATEGORY_ID${Uri.encode(categoryId)}"
        return Uri.parse(url)
    }

    override fun getStationsByCountry(countryCode: String, pageNumber: Int): Uri {
        val url = "$URL$KEY_COUNTRY_ID${Uri.encode(countryCode)}"
        return Uri.parse(url)
    }

    override fun getPopularStations(): Uri {
        return URI
    }

    override fun getNewStations(): Uri {
        return URI
    }

    override fun getSearchUrl(query: String): Uri {
        val url = "$URL$KEY_SEARCH_ID${Uri.encode(query)}"
        return Uri.parse(url)
    }

    override fun getAllCountries(): Uri {
        return URI_COUNTRIES
    }

    companion object {

        private const val URL = "https://jcorporation.github.io/webradiodb/db/index/webradios.min.json"
        private val URI = Uri.parse(URL)
        private val URI_COUNTRIES = Uri.parse(
            "https://jcorporation.github.io/webradiodb/db/index/countries.min.json"
        )

        const val KEY_CATEGORY_ID = "?categoryId="
        const val KEY_COUNTRY_ID = "?countryId="
        const val KEY_SEARCH_ID = "?searchId="
    }
}
