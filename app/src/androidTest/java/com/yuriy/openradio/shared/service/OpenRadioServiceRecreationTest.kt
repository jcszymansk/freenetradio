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

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * fnr-h34o. What a play request restores once [OpenRadioService] has been created again, as it is
 * when a media button or the system media controls reach a process that was killed.
 *
 * A new service remembers no queue, so the only record of what was playing is the latest station,
 * which `onCreate` adopts as the active station while the "last known station" setting is on. A
 * play request with the queue empty is answered by building the same playlist around it that a
 * page-0 browse builds. That playlist comes out of the favorites, or out of the provider's new
 * stations when there are none, so every case here seeds favorites and nothing asks the network.
 *
 * The stations are device-local ones pointing at generated WAV files, as in
 * [OpenRadioServicePlaybackTest], and [ServiceRecreation] stands in for the killed process.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioServiceRecreationTest {

    private lateinit var mContext: Context

    private lateinit var mStorages: ServiceStorages

    private lateinit var mBrowser: ServiceBrowser

    private lateinit var mAudio: LocalAudioFixture

    private lateinit var mStations: LocalStationsFixture

    private var mWasLastKnownStationEnabled = true

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mWasLastKnownStationEnabled = AppPreferencesManager.lastKnownRadioStationEnabled(mContext)
        AppPreferencesManager.lastKnownRadioStationEnabled(mContext, true)
        mAudio = LocalAudioFixture(mContext)
        mStorages = ServiceStorages(mContext)
        mStorages.clear()
        mBrowser = ServiceBrowser()
        mStations = LocalStationsFixture(mStorages, mBrowser)
        mBrowser.connect()
        mBrowser.stop()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.forgetNotifications()
        mBrowser.forgetPlayerEvents()
    }

    /**
     * A case that failed between destroying the service and connecting to the new one has no
     * connection, and the new service holds an active station with an empty queue, which is the
     * state the next class's first browse must not find. Connecting without the check is what
     * lets the player be parked anyway.
     */
    @After
    fun tearDown() {
        if (mBrowser.isConnected().not()) {
            mBrowser.connectUnchecked()
        }
        mStations.parkThePlayer()
        mStorages.clear()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.release()
        mAudio.delete()
        AppPreferencesManager.lastKnownRadioStationEnabled(mContext, mWasLastKnownStationEnabled)
    }

    /**
     * The latest station is a favorite, so the restored playlist is the favorites, positioned on
     * it rather than on the first of them.
     */
    @Test
    fun aPlayRequestAfterTheServiceIsRecreatedPlaysTheLatestStation() {
        val stations = seedStations(3)
        stations.forEach { mStorages.favorites.add(it) }
        playUntilLatest(stations[1])

        recreateTheService(stations[1])
        mBrowser.play()

        mBrowser.awaitPlaying()
        assertEquals(stations.map { it.id }, mBrowser.queueMediaIds())
        assertEquals(stations[1].id, mBrowser.currentMediaId())
        assertEquals(stations[1].getStreamUrlFixed(), mBrowser.currentMediaItemUri())
    }

    /**
     * The latest station is no favorite, so it heads the restored playlist, ahead of the
     * favorites, and plays from the copy the service restored rather than from any list.
     */
    @Test
    fun aLatestStationThatIsNoFavoriteHeadsThePlaylistARecreatedServiceRestores() {
        val stations = seedStations(3)
        mStorages.favorites.add(stations[0])
        mStorages.favorites.add(stations[2])
        playUntilLatest(stations[1])

        recreateTheService(stations[1])
        mBrowser.play()

        mBrowser.awaitPlaying()
        assertEquals(listOf(stations[1], stations[0], stations[2]).map { it.id }, mBrowser.queueMediaIds())
        assertEquals(stations[1].id, mBrowser.currentMediaId())
        assertEquals(stations[1].getStreamUrlFixed(), mBrowser.currentMediaItemUri())
    }

    private fun seedStations(count: Int): List<RadioStation> {
        val urls = (0 until count).map { mAudio.wav("fixture-$it.wav") }.toTypedArray()
        return mStations.seed(*urls)
    }

    /**
     * Plays [station] the way selecting it in a list does, and waits for it to be stored as the
     * latest station, which is written from a background coroutine after the player reports it.
     */
    private fun playUntilLatest(station: RadioStation) {
        mBrowser.setMediaItem(mStations.item(station))
        mBrowser.prepareAndPlay()
        mBrowser.awaitPlaying()
        mBrowser.awaitPlayback("'${station.id}' to be stored as the latest station") {
            mStorages.freshLatest().get().id == station.id
        }
    }

    /**
     * Stops playback, destroys the service and connects to a new one, which holds [latest] as its
     * active station and an empty queue. Both are checked, because a queue left over would mean
     * the old service was still the one answering.
     */
    private fun recreateTheService(latest: RadioStation) {
        mBrowser.stop()
        mBrowser.awaitPlaybackState(Player.STATE_IDLE)
        mBrowser.release()
        ServiceRecreation(mContext).destroyTheService()
        mBrowser.connectUnchecked()
        assertEquals("The recreated service holds a queue", 0, mBrowser.mediaItemCount())
        assertEquals(
            "The recreated service did not adopt the latest station",
            latest.id, mBrowser.activeStationId()
        )
        mBrowser.forgetPlayerEvents()
    }
}
