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

package com.yuriy.openradio.shared.model.storage.images

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.core.util.Pair
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.yuriy.openradio.shared.model.net.DownloaderLayer
import com.yuriy.openradio.shared.model.net.HTTPDownloaderImpl
import com.yuriy.openradio.shared.model.net.NetworkLayer
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.utils.NetworkPermit
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers what [ImagesPersistenceLayerImpl] does on an artwork miss, against a [RecordingDownloader]
 * and an in-memory [FakeImagesDatabase], so no case reaches the network.
 *
 * The layer runs on [Dispatchers.Unconfined], so the download a miss starts has finished by the
 * time [ImagesPersistenceLayerImpl.open] returns.
 */
class ImagesPersistenceLayerImplTest {

    private val mContext: Context = ContextWrapper(null)

    private val mDownloader = RecordingDownloader()

    private val mDao = InMemoryImageDao()

    /**
     * Artwork loads in the background whenever a list or the player shows a station, so offline
     * every miss would be a string of failed connections and, through the toasting check, a
     * notice the user never asked for.
     */
    @Test
    fun anArtworkMissWhileOfflineMakesNoConnectionAttemptAndShowsNoToast() {
        val network = RecordingNetworkLayer(connected = false)

        val descriptor = layer(network).open(ImagesStore.buildImageUri(STATION_ID, IMAGE_URL))

        assertNull("a miss has nothing to hand out yet", descriptor)
        assertEquals("offline, nothing may be downloaded", emptyList<String>(), mDownloader.urls)
        assertEquals("the toasting check may not be asked", 0, network.notifyingChecks)
        assertEquals(1, network.silentChecks)
        assertEquals(emptyList<String>(), mDao.inserted)
    }

    /**
     * A local station's artwork is a file on the device, so the offline gate must not reach it.
     * The file does not exist, so the read fails quietly after the gate has been passed.
     */
    @Test
    fun aLocalArtworkMissWhileOfflineIsNotGatedOnTheNetwork() {
        val network = RecordingNetworkLayer(connected = false)

        layer(network).open(ImagesStore.buildImageUri(STATION_ID, LOCAL_IMAGE_PATH))

        assertEquals("a local file needs no network", 0, network.silentChecks)
        assertEquals("the toasting check may not be asked", 0, network.notifyingChecks)
        assertEquals("a local file is never downloaded", emptyList<String>(), mDownloader.urls)
    }

    @Test
    fun anArtworkMissWhileOnlineDownloadsTheImage() {
        val network = RecordingNetworkLayer(connected = true)

        val descriptor = layer(network).open(ImagesStore.buildImageUri(STATION_ID, IMAGE_URL))

        assertNull("a miss has nothing to hand out yet", descriptor)
        assertEquals(listOf(IMAGE_URL), mDownloader.urls)
        assertEquals(listOf(HTTPDownloaderImpl.CONTENT_TYPE_IMG), mDownloader.contentTypeFilters)
        assertEquals("the toasting check may not be asked", 0, network.notifyingChecks)
    }

    private fun layer(network: NetworkLayer) = ImagesPersistenceLayerImpl(
        mContext, mDownloader, FakeImagesDatabase(mDao), network, Dispatchers.Unconfined
    )

    /** Answers [connected] and counts which of the two connectivity questions was asked. */
    private class RecordingNetworkLayer(private val connected: Boolean) : NetworkLayer {

        var silentChecks = 0
            private set

        var notifyingChecks = 0
            private set

        override fun isConnected(): Boolean {
            silentChecks++
            return connected
        }

        override fun checkConnectivityAndNotify(context: Context): Boolean {
            notifyingChecks++
            return connected
        }

        override fun startMonitor(context: Context, listener: NetworkMonitorListener) = Unit

        override fun stopMonitor(context: Context) = Unit

        override fun isMobileNetwork() = false
    }

    /** Records every download it is asked for and answers each with no bytes. */
    private class RecordingDownloader : DownloaderLayer {

        val urls = mutableListOf<String>()

        val contentTypeFilters = mutableListOf<String?>()

        override fun downloadDataFromUri(
            context: Context,
            uri: Uri,
            parameters: List<Pair<String, String>>,
            contentTypeFilter: String?,
            maxBytes: Int,
            permit: NetworkPermit
        ): ByteArray {
            urls.add(uri.toString())
            contentTypeFilters.add(contentTypeFilter)
            return ByteArray(0)
        }
    }

    private class InMemoryImageDao : ImageDao {

        private val mImages = mutableMapOf<String, Image>()

        val inserted = mutableListOf<String>()

        override fun getImage(rsId: String) = mImages[rsId]

        override fun insertImage(rsImage: Image) {
            inserted.add(rsImage.mRsId)
            mImages[rsImage.mRsId] = rsImage
        }

        override fun delete(rsId: String) {
            mImages.remove(rsId)
        }

        override fun getCount() = mImages.size

        override fun deleteAll() = mImages.clear()
    }

    /**
     * Hands out [mDao] and nothing else. Room only reaches the three framework hooks when a real
     * database is built, which this one never is.
     */
    private class FakeImagesDatabase(private val mDao: ImageDao) : ImagesDatabase() {

        override fun rsImageDao() = mDao

        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
            throw AssertionError("The fake database is never opened")
        }

        override fun createInvalidationTracker() = InvalidationTracker(this)

        override fun clearAllTables() {
            throw AssertionError("The fake database is never cleared")
        }
    }

    companion object {
        private const val STATION_ID = "station-1"
        private const val IMAGE_URL = "http://127.0.0.1:1/logo.png"
        private const val LOCAL_IMAGE_PATH = "/nonexistent/freenetradio/logo.png"
    }
}
