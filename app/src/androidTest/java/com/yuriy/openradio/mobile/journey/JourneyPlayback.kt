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

package com.yuriy.openradio.mobile.journey

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.MediaResourceManagerListener
import com.yuriy.openradio.shared.model.media.MediaResourcesManager
import com.yuriy.openradio.shared.model.media.PlaybackState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue

/**
 * Starting playback from a journey, the way selecting a station does.
 *
 * Offline, no journey can start playback by tapping a station's row.
 * `MediaPresenterImpl.handleItemSelected` refuses every tap without a connection, browsable and
 * playable alike, and it is the only tap-driven way in. TASK-049 lifts that, and
 * [OfflinePlaybackJourneyTest.theStationRowStartsNothingWithoutANetwork] pins the refusal until
 * it does.
 *
 * So this calls the step that tap performs once past the gate:
 * [MediaResourcesManager.playFromMediaId], the same class the presenter drives, given the item
 * the adapter bound the row from and the node the presenter is standing in. Everything after it -
 * the playlist the selection expands into, the stream the service opens, the metadata it pushes
 * back and the bar the Activity puts up because of it - is the application's own.
 *
 * What this manager does not do is render anything. It is a second controller of the same
 * session, next to the one the Activity built for itself, and it is the Activity's that the
 * journey reads.
 */
@UnstableApi
internal class JourneyPlayback(private val mContext: Context) {

    private val mInstrumentation = InstrumentationRegistry.getInstrumentation()

    private val mConnected = CountDownLatch(1)

    private val mListener = object : MediaResourceManagerListener {

        override fun onConnected() {
            mConnected.countDown()
        }

        override fun onPlaybackStateChanged(state: PlaybackState) = Unit

        override fun onMetadataChanged(metadata: MediaMetadata) = Unit
    }

    private var mManager: MediaResourcesManager? = null

    /**
     * Connects to the service and waits for the session to answer, because a selection made
     * before that has no player to reach.
     */
    fun connect() {
        mInstrumentation.runOnMainSync { mManager = MediaResourcesManager(mContext, TAG, mListener) }
        assertTrue(
            "The journey did not connect to the media session within $TIMEOUT_SECONDS seconds, " +
                "so nothing it selects can play",
            mConnected.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        )
    }

    /**
     * Selects [item] from inside [parentId], which is what a tap on that row does once past the
     * connectivity gate.
     *
     * On the main looper, because that is where the presenter makes this call and because
     * `playFromMediaId` reads the controller's state before it hands the work to a coroutine;
     * Media3 rejects that read from any other thread.
     */
    fun select(item: MediaItem, parentId: String) {
        mInstrumentation.runOnMainSync { manager().playFromMediaId(item, parentId) }
    }

    /**
     * Releases the controller on the looper it was built on, which is the only thread Media3
     * allows it to be released from.
     */
    fun release() {
        val manager = mManager ?: return
        mManager = null
        mInstrumentation.runOnMainSync { manager.clean() }
    }

    private fun manager(): MediaResourcesManager {
        return mManager ?: throw IllegalStateException("The journey is not connected to the session")
    }

    private companion object {

        const val TAG = "JourneyPlayback"

        const val TIMEOUT_SECONDS = 15L
    }
}
