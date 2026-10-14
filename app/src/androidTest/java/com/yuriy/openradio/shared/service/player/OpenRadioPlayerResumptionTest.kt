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

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What [OpenRadioPlayer.getResumption] offers back to a play request that finds the queue empty:
 * the queue as it stood just before it was emptied, positioned on the item that was current then.
 *
 * Media3 only asks for a resumption when the queue is empty, so every way of emptying it has to
 * leave the queue behind. Nothing is prepared, so no url is ever opened.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerResumptionTest {

    private lateinit var mPlayer: OpenRadioPlayer

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        mPlayer = onMain { newOpenRadioPlayer(context) }
    }

    @After
    fun tearDown() {
        onMain { mPlayer.release() }
    }

    @Test
    fun aPlayerNeverGivenAQueueHasNothingToResume() {
        assertResumption(emptyList(), 0)
    }

    @Test
    fun aClearedQueueResumesAtTheItemThatWasCurrent() {
        load(STATIONS, current = 2)

        onMain { mPlayer.clearMediaItems() }

        assertEmptyQueue()
        assertResumption(STATIONS, 2)
    }

    @Test
    fun aQueueReplacedByNothingResumesAtTheItemThatWasCurrent() {
        load(STATIONS, current = 1)

        onMain { mPlayer.setMediaItems(mutableListOf()) }

        assertEmptyQueue()
        assertResumption(STATIONS, 1)
    }

    @Test
    fun aQueueReplacedByNothingFromAPositionResumesAtTheItemThatWasCurrent() {
        load(STATIONS, current = 1)

        onMain { mPlayer.setMediaItems(mutableListOf(), 0, C.TIME_UNSET) }

        assertEmptyQueue()
        assertResumption(STATIONS, 1)
    }

    @Test
    fun aQueueReplacedByNothingWithAResetResumesAtTheItemThatWasCurrent() {
        load(STATIONS, current = 1)

        onMain { mPlayer.setMediaItems(mutableListOf(), true) }

        assertEmptyQueue()
        assertResumption(STATIONS, 1)
    }

    @Test
    fun aQueueRemovedAsARangeResumesAtTheItemThatWasCurrent() {
        load(STATIONS, current = 4)

        onMain { mPlayer.removeMediaItems(0, STATIONS.size) }

        assertEmptyQueue()
        assertResumption(STATIONS, 4)
    }

    @Test
    fun aQueueReplacedByNothingAsARangeResumesAtTheItemThatWasCurrent() {
        load(STATIONS, current = 3)

        onMain { mPlayer.replaceMediaItems(0, STATIONS.size, mutableListOf()) }

        assertEmptyQueue()
        assertResumption(STATIONS, 3)
    }

    @Test
    fun aQueueWhoseLastItemIsRemovedResumesThatItem() {
        load(listOf("a"), current = 0)

        onMain { mPlayer.removeMediaItem(0) }

        assertEmptyQueue()
        assertResumption(listOf("a"), 0)
    }

    /**
     * Only the queue that was emptied last is remembered, so a newer queue the player was given
     * and then lost wins over an older one.
     */
    @Test
    fun theQueueEmptiedLastIsTheOneThatResumes() {
        load(STATIONS, current = 2)
        onMain { mPlayer.clearMediaItems() }
        load(listOf("x", "y"), current = 1)

        onMain { mPlayer.clearMediaItems() }

        assertResumption(listOf("x", "y"), 1)
    }

    private fun load(ids: List<String>, current: Int) {
        onMain {
            mPlayer.setMediaItems(ids.map(::station).toMutableList())
            mPlayer.seekTo(current, C.TIME_UNSET)
        }
        assertEquals("The current item", current, onMain { mPlayer.currentMediaItemIndex })
    }

    private fun assertEmptyQueue() {
        assertEquals("Items left in the queue", 0, onMain { mPlayer.mediaItemCount })
    }

    private fun assertResumption(expected: List<String>, startIndex: Int) {
        val resumption = onMain { mPlayer.getResumption() }
        assertEquals("The resumed queue", expected, resumption.mediaItems.map { it.mediaId })
        assertEquals("The resumed item", startIndex, resumption.startIndex)
        assertEquals("The resumed position", C.TIME_UNSET, resumption.startPositionMs)
    }

    private companion object {

        val STATIONS = listOf("a", "b", "c", "d", "e")

        fun station(id: String): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setUri("$UNREACHABLE_ORIGIN/$id")
                .build()
        }
    }
}
