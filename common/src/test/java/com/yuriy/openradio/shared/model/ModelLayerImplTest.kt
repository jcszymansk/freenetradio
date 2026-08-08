package com.yuriy.openradio.shared.model

import android.content.Context
import android.content.ContextWrapper
import android.net.TestUri
import android.net.Uri
import androidx.core.util.Pair
import com.yuriy.openradio.shared.model.filter.FilterImpl
import com.yuriy.openradio.shared.model.net.DownloaderLayer
import com.yuriy.openradio.shared.model.net.NetworkLayer
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.model.parser.ParserLayerRadioBrowserImpl
import com.yuriy.openradio.shared.model.storage.cache.api.ApiCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLayerImplTest {

    @Test
    fun noConnectivityReturnsNoDataWithoutTouchingCachesOrDownloader() {
        val network = RecordingNetworkLayer(false)
        val downloader = RecordingDownloader()
        val persistentCache = RecordingApiCache()
        val memoryCache = RecordingApiCache()
        val model = ModelLayerImpl(
            ContextWrapper(null),
            ParserLayerRadioBrowserImpl(FilterImpl()),
            network,
            downloader,
            persistentCache,
            memoryCache
        )

        val categories = model.getAllCategories(TestUri("https://radio.example/categories"))

        assertTrue(categories.isEmpty())
        assertEquals(1, network.connectivityChecks)
        assertEquals(0, persistentCache.operations)
        assertEquals(0, memoryCache.operations)
        assertEquals(0, downloader.calls)
    }

    @Test
    fun memoryHitBypassesPersistentCacheAndDownloader() {
        val network = RecordingNetworkLayer(true)
        val downloader = RecordingDownloader()
        val persistentCache = RecordingApiCache()
        val memoryCache = RecordingApiCache("""[{"name":"rock","stationcount":1}]""")
        val model = ModelLayerImpl(
            ContextWrapper(null),
            ParserLayerRadioBrowserImpl(FilterImpl()),
            network,
            downloader,
            persistentCache,
            memoryCache
        )

        val categories = model.getAllCategories(TestUri("https://radio.example/categories"))

        assertEquals("rock", categories.single().id)
        assertEquals(1, network.connectivityChecks)
        assertEquals(0, persistentCache.operations)
        assertEquals(1, memoryCache.operations)
        assertEquals(0, downloader.calls)
    }
    @Test
    fun persistentHitIsPromotedToMemory() {
        val data = """[{"name":"rock","stationcount":1}]"""
        val network = RecordingNetworkLayer(true)
        val downloader = RecordingDownloader()
        val persistentCache = RecordingApiCache(data)
        val memoryCache = RecordingApiCache()
        val model = ModelLayerImpl(
            ContextWrapper(null),
            ParserLayerRadioBrowserImpl(FilterImpl()),
            network,
            downloader,
            persistentCache,
            memoryCache
        )

        val categories = model.getAllCategories(TestUri("https://radio.example/categories"))

        assertEquals("rock", categories.single().id)
        assertEquals(data, memoryCache.lastPutData)
        assertEquals(1, network.connectivityChecks)
        assertEquals(1, persistentCache.gets)
        assertEquals(1, memoryCache.gets)
        assertEquals(1, memoryCache.removes)
        assertEquals(1, memoryCache.puts)
        assertEquals(0, downloader.calls)
    }
    @Test
    fun emptyAndArrayCacheValuesAreMisses() {
        val data = """[{"name":"rock","stationcount":1}]"""
        for (cachedData in listOf("", "[]")) {
            val network = RecordingNetworkLayer(true)
            val downloader = RecordingDownloader(data)
            val persistentCache = RecordingApiCache(cachedData)
            val memoryCache = RecordingApiCache(cachedData)
            val model = ModelLayerImpl(
                ContextWrapper(null),
                ParserLayerRadioBrowserImpl(FilterImpl()),
                network,
                downloader,
                persistentCache,
                memoryCache
            )

            val categories = model.getAllCategories(TestUri("https://radio.example/categories"))

            assertEquals("rock", categories.single().id)
            assertEquals(1, persistentCache.gets)
            assertEquals(1, memoryCache.gets)
            assertEquals(1, downloader.calls)
            assertEquals(data, persistentCache.lastPutData)
            assertEquals(data, memoryCache.lastPutData)
        }
    }
    @Test
    fun successfulDownloadReplacesBothCaches() {
        val data = """[{"name":"rock","stationcount":1}]"""
        val network = RecordingNetworkLayer(true)
        val downloader = RecordingDownloader(data)
        val persistentCache = RecordingApiCache()
        val memoryCache = RecordingApiCache()
        val model = ModelLayerImpl(
            ContextWrapper(null),
            ParserLayerRadioBrowserImpl(FilterImpl()),
            network,
            downloader,
            persistentCache,
            memoryCache
        )

        val categories = model.getAllCategories(TestUri("https://radio.example/categories"))

        assertEquals("rock", categories.single().id)
        assertEquals(1, downloader.calls)
        assertEquals(1, persistentCache.removes)
        assertEquals(1, persistentCache.puts)
        assertEquals(data, persistentCache.lastPutData)
        assertEquals(1, memoryCache.removes)
        assertEquals(1, memoryCache.puts)
        assertEquals(data, memoryCache.lastPutData)
    }





    private class RecordingNetworkLayer(private val connected: Boolean) : NetworkLayer {
        var connectivityChecks = 0

        override fun startMonitor(context: Context, listener: NetworkMonitorListener) = Unit

        override fun stopMonitor(context: Context) = Unit

        override fun checkConnectivityAndNotify(context: Context): Boolean {
            connectivityChecks++
            return connected
        }

        override fun isMobileNetwork() = false
    }

    private class RecordingDownloader(private val data: String = "") : DownloaderLayer {
        var calls = 0

        override fun downloadDataFromUri(
            context: Context,
            uri: Uri,
            parameters: List<Pair<String, String>>,
            contentTypeFilter: String?
        ): ByteArray {
            calls++
            return data.toByteArray()
        }
    }

    private class RecordingApiCache(private val data: String = "") : ApiCache {
        var gets = 0
        var puts = 0
        var removes = 0
        var clears = 0
        var lastPutData = ""
        val operations: Int
            get() = gets + puts + removes + clears

        override fun get(key: String): String {
            gets++
            return data
        }

        override fun put(key: String, data: String) {
            puts++
            lastPutData = data
        }

        override fun remove(key: String) {
            removes++
        }

        override fun clear() {
            clears++
        }
    }
}
