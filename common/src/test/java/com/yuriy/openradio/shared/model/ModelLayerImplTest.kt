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

    private class RecordingDownloader : DownloaderLayer {
        var calls = 0

        override fun downloadDataFromUri(
            context: Context,
            uri: Uri,
            parameters: List<Pair<String, String>>,
            contentTypeFilter: String?
        ): ByteArray {
            calls++
            return ByteArray(0)
        }
    }

    private class RecordingApiCache(private val data: String = "") : ApiCache {
        var operations = 0

        override fun get(key: String): String {
            operations++
            return data
        }

        override fun put(key: String, data: String) {
            operations++
        }

        override fun remove(key: String) {
            operations++
        }

        override fun clear() {
            operations++
        }
    }
}
