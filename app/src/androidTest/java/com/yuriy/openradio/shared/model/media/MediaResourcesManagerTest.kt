package com.yuriy.openradio.shared.model.media

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.MediaStream.Companion.BIT_RATE_DEFAULT
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.service.OpenRadioService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class MediaResourcesManagerTest {

    private lateinit var context: Context
    private lateinit var localsStorage: DeviceLocalsStorage
    private var resourcesManager: MediaResourcesManager? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val contextRef = WeakReference(context)
        localsStorage = DeviceLocalsStorage(
            contextRef,
            FavoritesStorage(contextRef),
            LatestRadioStationStorage(contextRef)
        )
        localsStorage.clear()
    }

    @After
    fun tearDown() {
        resourcesManager?.let { manager ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync(manager::clean)
        }
        localsStorage.clear()
    }

    @Test
    fun addingFirstLocalRefreshesRootSubscription() {
        val connected = CountDownLatch(1)
        val results = LinkedBlockingQueue<ChildrenResult>()
        val manager = MediaResourcesManager(
            context,
            javaClass.simpleName,
            object : MediaResourceManagerListener {
                override fun onConnected() {
                    connected.countDown()
                }

                override fun onPlaybackStateChanged(state: PlaybackState) = Unit

                override fun onMetadataChanged(metadata: MediaMetadata) = Unit
            }
        )
        resourcesManager = manager
        assertTrue("Media browser did not connect", connected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        manager.subscribe(
            MediaId.MEDIA_ID_ROOT,
            object : MediaItemsSubscription {
                override fun onChildrenLoaded(
                    parentId: String,
                    children: List<MediaItem>,
                    replace: Boolean
                ) {
                    results.add(ChildrenResult(parentId, children, replace, false))
                }

                override fun onError(parentId: String) {
                    results.add(ChildrenResult(parentId, emptyList(), false, true))
                }
            }
        )

        val initial = awaitChildren(results) { true }
        assertFalse("Initial root load failed", initial.error)
        assertTrue("Initial root load must replace the spinner state", initial.replace)
        assertFalse(initial.containsLocals())

        val station = RadioStation.makeDefaultInstance(localsStorage.getId())
        station.name = "Regression station"
        station.setVariant(BIT_RATE_DEFAULT, "https://example.test/stream")
        station.isLocal = true
        localsStorage.add(station)

        val commandSent = runBlocking(Dispatchers.Main) {
            manager.sendCommand(OpenRadioService.CMD_UPDATE_TREE, Bundle())
        }
        assertTrue("Browse-tree update command was not sent", commandSent)

        val refreshed = awaitChildren(results, ChildrenResult::containsLocals)
        assertFalse("Root refresh failed", refreshed.error)
        assertTrue("Root refresh must replace existing rows", refreshed.replace)
    }

    private fun awaitChildren(
        results: LinkedBlockingQueue<ChildrenResult>,
        predicate: (ChildrenResult) -> Boolean
    ): ChildrenResult {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val remaining = deadline - System.nanoTime()
            val result = results.poll(remaining, TimeUnit.NANOSECONDS) ?: break
            if (predicate(result)) {
                return result
            }
        }
        throw AssertionError("Expected media children callback was not received")
    }

    private data class ChildrenResult(
        val parentId: String,
        val children: List<MediaItem>,
        val replace: Boolean,
        val error: Boolean
    ) {
        fun containsLocals(): Boolean {
            return parentId == MediaId.MEDIA_ID_ROOT &&
                children.any { it.mediaId == MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST }
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
