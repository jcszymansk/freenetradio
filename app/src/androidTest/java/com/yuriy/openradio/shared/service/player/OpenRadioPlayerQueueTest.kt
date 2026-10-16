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

package com.yuriy.openradio.shared.service.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import com.yuriy.openradio.testing.onMainThread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How [OpenRadioPlayer] keeps the queue it reports in step with the queue of the ExoPlayer it
 * wraps when a controller reorders it, replaces part of it or removes part of it.
 *
 * The wrapper keeps its own copy of the queue, from which it reads the metadata it publishes, while
 * the wrapped player decides what plays. Each case reads both through the [Player] interface: the
 * wrapped player's through [OpenRadioPlayer.getMediaItemAt], which forwards, and the wrapper's
 * through the metadata it publishes to a [Player.Listener] for each item made current in turn,
 * every station carrying its id as its title. Nothing is prepared, so no url is ever opened.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerQueueTest {

    private lateinit var mPlayer: OpenRadioPlayer

    /**
     * The metadata published since the wrapper was last asked to publish it. Touched only on the
     * main thread.
     */
    private val mPublished = arrayListOf<MediaMetadata>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        mPlayer = onMainThread { newOpenRadioPlayer(context) }
        onMainThread {
            mPlayer.addListener(Recorder())
            mPlayer.setMediaItems(STATIONS.map(::station).toMutableList())
        }
    }

    @After
    fun tearDown() {
        onMainThread { mPlayer.release() }
    }

    @Test
    fun aRangeMovedTowardsTheEndIsMovedInBothQueues() {
        onMainThread { mPlayer.moveMediaItems(1, 3, 3) }

        assertQueues(listOf("a", "d", "e", "b", "c"))
    }

    @Test
    fun aRangeMovedTowardsTheStartIsMovedInBothQueues() {
        onMainThread { mPlayer.moveMediaItems(3, 5, 0) }

        assertQueues(listOf("d", "e", "a", "b", "c"))
    }

    /**
     * [androidx.media3.common.Player.moveMediaItems] moves a range whose new index is past the
     * end of the queue to the end.
     */
    @Test
    fun aRangeMovedPastTheEndEndsBothQueues() {
        onMainThread { mPlayer.moveMediaItems(0, 2, 10) }

        assertQueues(listOf("c", "d", "e", "a", "b"))
    }

    /**
     * [androidx.media3.common.Player.moveMediaItems] moves a range that runs past the end of the
     * queue as far as the end.
     */
    @Test
    fun aRangeRunningPastTheEndIsMovedAsFarAsTheEndInBothQueues() {
        onMainThread { mPlayer.moveMediaItems(3, 10, 0) }

        assertQueues(listOf("d", "e", "a", "b", "c"))
    }

    @Test
    fun anItemMovedAloneIsMovedInBothQueues() {
        onMainThread { mPlayer.moveMediaItem(0, 3) }

        assertQueues(listOf("b", "c", "d", "a", "e"))
    }

    @Test
    fun anItemReplacedIsReplacedInBothQueues() {
        onMainThread { mPlayer.replaceMediaItem(2, station("x")) }

        assertQueues(listOf("a", "b", "x", "d", "e"))
    }

    @Test
    fun aRangeReplacedIsReplacedInBothQueues() {
        onMainThread { mPlayer.replaceMediaItems(1, 3, mutableListOf(station("x"))) }

        assertQueues(listOf("a", "x", "d", "e"))
    }

    /**
     * [androidx.media3.common.Player.replaceMediaItems] replaces a range that runs past the end of
     * the queue as far as the end.
     */
    @Test
    fun aRangeRunningPastTheEndIsReplacedAsFarAsTheEndInBothQueues() {
        onMainThread { mPlayer.replaceMediaItems(3, 10, mutableListOf(station("x"), station("y"))) }

        assertQueues(listOf("a", "b", "c", "x", "y"))
    }

    /**
     * [androidx.media3.common.Player.replaceMediaItems] ignores a range that starts past the end of
     * the queue.
     */
    @Test
    fun aRangeStartingPastTheEndIsIgnoredByBothQueues() {
        onMainThread { mPlayer.replaceMediaItems(7, 9, mutableListOf(station("x"))) }

        assertQueues(STATIONS)
    }

    @Test
    fun anItemRemovedIsRemovedFromBothQueues() {
        onMainThread { mPlayer.removeMediaItem(2) }

        assertQueues(listOf("a", "b", "d", "e"))
    }

    /**
     * [androidx.media3.common.Player.removeMediaItem] ignores an index past the end of the queue.
     */
    @Test
    fun anItemPastTheEndIsIgnoredByBothQueues() {
        onMainThread { mPlayer.removeMediaItem(5) }

        assertQueues(STATIONS)
    }

    @Test
    fun aRangeRemovedIsRemovedFromBothQueues() {
        onMainThread { mPlayer.removeMediaItems(1, 3) }

        assertQueues(listOf("a", "d", "e"))
    }

    /**
     * [androidx.media3.common.Player.removeMediaItems] removes a range that runs past the end of
     * the queue as far as the end.
     */
    @Test
    fun aRangeRunningPastTheEndIsRemovedAsFarAsTheEndFromBothQueues() {
        onMainThread { mPlayer.removeMediaItems(3, 10) }

        assertQueues(listOf("a", "b", "c"))
    }

    /**
     * [androidx.media3.common.Player.removeMediaItems] ignores a range that starts past the end of
     * the queue.
     */
    @Test
    fun aRemovedRangeStartingPastTheEndIsIgnoredByBothQueues() {
        onMainThread { mPlayer.removeMediaItems(7, 9) }

        assertQueues(STATIONS)
    }

    /**
     * Reading the wrapper's copy item by item only covers the indices the wrapped player has, so a
     * copy that kept surplus items at its tail would pass. Appending a sentinel to both queues and
     * reading it back at the end of the expected queue catches that: a copy with a stale tail
     * publishes a stale station there instead. The sentinel changes the queue, so it comes last.
     */
    private fun assertQueues(expected: List<String>) {
        val played = onMainThread {
            (0 until mPlayer.mediaItemCount).map { mPlayer.getMediaItemAt(it).mediaId }
        }
        assertEquals("The wrapped player's queue", expected, played)
        assertEquals("The wrapper's queue", expected, expected.indices.map(::publishedTitleAt))
        onMainThread { mPlayer.addMediaItem(station(SENTINEL)) }
        assertEquals("The end of the wrapper's queue", SENTINEL, publishedTitleAt(expected.size))
    }

    /**
     * The title the wrapper publishes when asked to while the item at [index] is current, read from
     * its own copy of the queue; null when the copy has no item there. The publication is posted to
     * the main thread, so the block that reads it, posted after, runs once it is done.
     */
    private fun publishedTitleAt(index: Int): String? {
        onMainThread { mPlayer.seekToDefaultPosition(index) }
        onMainThread {
            mPublished.clear()
            mPlayer.invalidateMetaData()
        }
        return onMainThread { mPublished.firstOrNull()?.title?.toString() }
    }

    private inner class Recorder : Player.Listener {

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            mPublished.add(mediaMetadata)
        }
    }

    private companion object {

        val STATIONS = listOf("a", "b", "c", "d", "e")

        const val SENTINEL = "end"

        fun station(id: String): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setUri("$UNREACHABLE_ORIGIN/$id")
                .setMediaMetadata(MediaMetadata.Builder().setTitle(id).build())
                .build()
        }
    }
}
