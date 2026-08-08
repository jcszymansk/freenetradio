package com.yuriy.openradio.shared.model.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UrlLayerTest {

    @Test
    fun radioBrowserEncodesQueriesAndPaginates() {
        val urlLayer = UrlLayerRadioBrowserImpl()
        val pageSize = DependencyRegistryCommon.PAGE_SIZE

        assertEquals(
            "https://do-look-up-dns-first/json/stations/bytag/Rock%20%26%20Pop" +
                    "?hidebroken=true&order=name&offset=502&limit=$pageSize",
            urlLayer.getStationsInCategory("Rock & Pop", 2).toString()
        )
        assertEquals(
            "https://do-look-up-dns-first/json/stations/bycountrycodeexact/PL" +
                    "?offset=251&limit=$pageSize",
            urlLayer.getStationsByCountry("PL", 1).toString()
        )
        assertEquals(
            "https://do-look-up-dns-first/json/stations/search?name=Jazz%20%26%20Blues" +
                    "&offset=0&limit=$pageSize",
            urlLayer.getSearchUrl("Jazz & Blues").toString()
        )
    }

    @Test
    fun webRadioEncodesQueriesAndIgnoresPagination() {
        val urlLayer = UrlLayerWebRadioImpl()
        val categoryPage = urlLayer.getStationsInCategory("Rock & Pop", 0).toString()

        assertEquals(
            "https://jcorporation.github.io/webradiodb/db/index/webradios.min.json" +
                    "?categoryId=Rock%20%26%20Pop",
            categoryPage
        )
        assertEquals(categoryPage, urlLayer.getStationsInCategory("Rock & Pop", 3).toString())
        assertEquals(
            "https://jcorporation.github.io/webradiodb/db/index/webradios.min.json" +
                    "?countryId=PL",
            urlLayer.getStationsByCountry("PL", 1).toString()
        )
        assertEquals(
            "https://jcorporation.github.io/webradiodb/db/index/webradios.min.json" +
                    "?searchId=Jazz%20%26%20Blues",
            urlLayer.getSearchUrl("Jazz & Blues").toString()
        )
    }
}
