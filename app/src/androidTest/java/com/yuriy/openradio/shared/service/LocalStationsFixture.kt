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

package com.yuriy.openradio.shared.service

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.storage.makeStation
import org.junit.Assert.assertEquals

/**
 * Puts stations the playback tests can select into the service's browse tree.
 *
 * Device-local stations are used because they are the only node whose contents a test decides
 * outright: everything else comes from a provider, and the suite runs with networking disabled.
 * Seeding them is two steps, storage and a browse, and the browse is the half that matters,
 * because a selection is answered out of the browse tree rather than out of storage.
 */
@UnstableApi
internal class LocalStationsFixture(
    private val mStorages: ServiceStorages,
    private val mBrowser: ServiceBrowser
) {

    private var mItems = emptyList<MediaItem>()

    /**
     * Adds one station per url, in the order given, and browses the node they land in.
     *
     * @return the stations, in the order the service offers them.
     */
    fun seed(vararg urls: String): List<RadioStation> {
        val stations = urls.mapIndexed { index, url ->
            val id = nextId()
            makeStation(
                id,
                name = "Fixture $id",
                url = url,
                sortId = index,
                isLocal = true
            )
        }
        for (station in stations) {
            mStorages.locals.add(station)
        }
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mItems = mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        assertEquals(
            "The seeded stations are not what the locals node offers",
            stations.map { it.id }, mItems.map { it.mediaId }
        )
        return stations
    }

    /**
     * @return the browsed item for [station], which is what selecting it in a list hands over.
     */
    fun item(station: RadioStation): MediaItem {
        return mItems.first { it.mediaId == station.id }
    }

    /**
     * Leaves the player stopped but still holding a queue.
     *
     * The service outlives every test class in the run, and playing a station leaves it holding an
     * active station it did not have before. From then on every page-0 browse with an empty queue
     * calls `maybeCreateInitialPlaylist`, which asks the provider for new stations, replaces the
     * queue with the answer and starts playing it. Offline the answer is nothing, so the queue
     * becomes the active station alone, under a browse-tree key that no later browse invalidates,
     * which is TASK-041. A queue that is not empty shuts that path, which is the state the rest of
     * the suite was written in and has to be handed back in.
     *
     * The item is one already in hand rather than one browsed for here, because the browse is
     * itself what would trigger the path this is avoiding.
     *
     * Stopping comes first and is not conditional on this fixture having seeded anything. A
     * teardown after a setup that failed before [seed] still runs, and what is playing then is
     * whatever an earlier class left, which is exactly the state that must not reach the next one.
     * All an unseeded fixture cannot do is put a queue back, because it has no item to put there.
     * That is harmless while the service holds no active station, and it fails the teardown when
     * the service does, because the next class's first browse would then start the path this
     * exists to shut. [ServiceBrowser.connect] refuses that state too, but only once the next
     * class has started, and the class that caused it is the one that should say so.
     *
     * A browser that is not connected either never connected or was refused, and in both cases
     * this class never reached the player, so there is nothing of its own to park. That is logged
     * rather than thrown: throwing would bury whatever stopped the setup.
     */
    fun parkThePlayer() {
        if (mBrowser.isConnected().not()) {
            Log.i(TAG, "Not parking the player: this class never reached it")
            return
        }
        mBrowser.stop()
        if (mBrowser.mediaItemCount() != 0) {
            return
        }
        val item = mItems.firstOrNull()
        if (item == null) {
            if (mBrowser.canABrowseStartPlayback()) {
                throw AssertionError(
                    "Could not park the player. Its queue is empty and the service holds an " +
                        "active station, so the next page-0 browse would download new stations " +
                        "and play them, and this fixture seeded nothing to put back in the queue."
                )
            }
            Log.i(TAG, "Left the queue empty: the service holds no active station")
            return
        }
        mBrowser.setMediaItem(item)
        mBrowser.awaitPlayback("the player to hold a queue again") {
            mBrowser.mediaItemCount() != 0
        }
    }

    /**
     * Station ids are unique for the whole run, not just for one test, and the name is built from
     * the id so that it is too.
     *
     * [com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage.getId] counts up from a fixed
     * value held in the same preference file the tests wipe, so it hands out the same ids to every
     * test. The browse tree keeps entries keyed by a station id that no browse invalidates, and one
     * left behind by an earlier test would then answer for a station of the same id in a later one.
     * The name matters for the same reason once a test reads one off the screen: what the phone's
     * now-playing bar shows outlives the test that put it there, so a repeated name would let a
     * stale bar pass for the one this test was waiting for.
     */
    private fun nextId(): String {
        return (FIRST_STATION_ID + sStationIds++).toString()
    }

    private companion object {

        const val TAG = "LocalStationsFixture"

        /**
         * Well clear of the ids
         * [com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage] hands out itself.
         */
        const val FIRST_STATION_ID = 1_900_000_000

        var sStationIds = 0
    }
}
