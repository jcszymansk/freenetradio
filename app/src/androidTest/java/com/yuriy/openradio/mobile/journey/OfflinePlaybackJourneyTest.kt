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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.service.LocalAudioFixture
import com.yuriy.openradio.shared.service.LocalStationsFixture
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fourth phone journey: listening to a station, with the device offline.
 *
 * The station plays a WAV generated into the application's cache directory and reached over
 * `file://`, which is what the testing roadmap asks playback tests to use. Nothing about the
 * player is stubbed: the real [com.yuriy.openradio.shared.service.player.OpenRadioPlayer] opens
 * the file through the same data source it builds for a stream, and the emulator needs no audio
 * output to do it.
 *
 * What this journey owns that the service tests do not is the screen. The phone's whole playback
 * UI is one bar above the browse list: the station's name, the line the player writes about the
 * stream, a favorite box, and the bar itself, which is the only transport control the application
 * has. `OpenRadioServicePlaybackTest` drives the same player through a bare
 * [androidx.media3.session.MediaBrowser] and renders nothing; what is asserted here is the bar.
 *
 * The tap that starts playback cannot be performed offline. `MediaPresenterImpl.handleItemSelected`
 * refuses a playable row for the same missing connection that makes it refuse a browsable one, so
 * [theStationRowStartsNothingWithoutANetwork] pins that refusal and [JourneyPlayback] makes the
 * call that tap would have made. Everything after the selection is the application's own.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OfflinePlaybackJourneyTest {

    private lateinit var mContext: Context

    private lateinit var mProfile: JourneyProfile

    private lateinit var mAudio: LocalAudioFixture

    private lateinit var mStations: LocalStationsFixture

    private lateinit var mPlayback: JourneyPlayback

    private lateinit var mStation: RadioStation

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mAudio = LocalAudioFixture(mContext)
        mProfile = JourneyProfile(mContext)
        mProfile.start()
        // The player outlives every test class in the run, so a case that asserts what playing
        // does has to start from a player that is not.
        mProfile.browser.stop()
        mStations = LocalStationsFixture(mProfile.storages, mProfile.browser)
        mStation = mStations.seed(mAudio.wav(WAV_NAME)).first()
        mPlayback = JourneyPlayback(mContext)
        mPlayback.connect()
    }

    @After
    fun tearDown() {
        mPlayback.release()
        mStations.parkThePlayer()
        mProfile.finish()
        mAudio.delete()
    }

    /**
     * The journey itself: a station of the user's own is picked out of the list it is in, and the
     * audio it points at is what comes out of the player.
     *
     * The url is asserted as well as the station, because a player that is ready on some other
     * item, or on a playlist the service built for itself, would otherwise read as this station
     * playing.
     */
    @Test
    fun aSelectedStationPlaysTheAudioItPointsAt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitSeededRoot(list)

            selectTheStationFromTheLocalsList(scenario, list)

            mProfile.browser.awaitPlaying()
            assertEquals(
                "The station is playing but the player never became ready",
                Player.STATE_READY,
                mProfile.browser.playbackState()
            )
            assertEquals(
                "The player is on some other item than the station that was selected",
                mStation.id,
                mProfile.browser.currentMediaId()
            )
            assertEquals(
                "The player opened something other than the station's own stream",
                mStation.getStreamUrlFixed(),
                mProfile.browser.currentMediaItemUri()
            )
            assertNull("The player reported an error", mProfile.browser.playerError())
        }
    }

    /**
     * The bar the phone puts up when something is playing, which is the whole of its playback UI.
     *
     * It starts out gone and the application is what raises it, on the first metadata the session
     * pushes back, so nothing between the selection and the bar is done here. The name comes from
     * the station; the description is written by the player rather than by the station, and it
     * settles on the default stream line once the file is open.
     *
     * The bar is asserted not to be showing this station before the selection, because what it
     * shows outlives the Activity that put it there: a bar left up by an earlier case would
     * otherwise answer for the one this case is waiting for.
     */
    @Test
    fun theNowPlayingBarShowsTheStationThatIsPlaying() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            val nowPlaying = NowPlayingView(scenario)
            awaitSeededRoot(list)
            assertNotEquals(
                "The now-playing bar is already showing this station, so waiting for it proves " +
                    "nothing",
                mStation.name,
                nowPlaying.title()
            )

            selectTheStationFromTheLocalsList(scenario, list)

            nowPlaying.awaitTitle(mStation.name)
            nowPlaying.awaitDescription(liveStreamDescription())
            assertTrue(
                "The now-playing bar went back down while the station was still playing. " +
                    nowPlaying.describe(),
                nowPlaying.isVisible()
            )
        }
    }

    /**
     * The bar belongs to the session rather than to the screen that started playback.
     *
     * A user who leaves the application and comes back while a station is playing has to find it
     * where they left it. The second Activity here is handed nothing by the test: it connects to
     * the session on its own and builds the bar from the metadata it is pushed.
     */
    @Test
    fun theNowPlayingBarComesBackUpForAnActivityThatDidNotStartPlayback() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitSeededRoot(list)
            selectTheStationFromTheLocalsList(scenario, list)
            mProfile.browser.awaitPlaying()
            NowPlayingView(scenario).awaitTitle(mStation.name)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val nowPlaying = NowPlayingView(scenario)
            nowPlaying.awaitTitle(mStation.name)
            assertEquals(
                "The relaunched bar shows the station but not what the player says about it",
                liveStreamDescription(),
                nowPlaying.description()
            )
            assertTrue(
                "The relaunched bar is showing a station that is not playing",
                mProfile.browser.isPlaying()
            )
        }
    }

    /**
     * The phone's transport control, which is the bar itself: there is no play button anywhere in
     * the application.
     *
     * The click sends `CMD_TOGGLE_LAST_PLAYED_ITEM` and the service works out from the player's
     * state whether that means pause or play, so the same gesture has to do both.
     */
    @Test
    fun tappingTheNowPlayingBarPausesAndResumesTheStation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            val nowPlaying = NowPlayingView(scenario)
            awaitSeededRoot(list)
            selectTheStationFromTheLocalsList(scenario, list)
            mProfile.browser.awaitPlaying()
            nowPlaying.awaitTitle(mStation.name)

            nowPlaying.tap()

            mProfile.browser.awaitPlayback("the station to pause") { !mProfile.browser.isPlaying() }
            assertEquals(
                "Pausing dropped the station instead of holding it",
                mStation.id,
                mProfile.browser.currentMediaId()
            )

            nowPlaying.tap()

            mProfile.browser.awaitPlaying()
            assertEquals(
                "Resuming moved the player onto some other station",
                mStation.id,
                mProfile.browser.currentMediaId()
            )
        }
    }

    /**
     * The other control on the bar: the box that marks what is playing.
     *
     * It is the same gesture as the one on a station's row, made where a listener would make it,
     * and it is bound from the metadata the session pushed rather than from the row the station
     * was selected in. The box is read before it is clicked, so a station that arrived already
     * marked fails here rather than passing the case it sets up, and the store is read back
     * through an instance that has cached no answers.
     */
    @Test
    fun theNowPlayingBarMarksTheStationThatIsPlaying() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            val nowPlaying = NowPlayingView(scenario)
            awaitSeededRoot(list)
            selectTheStationFromTheLocalsList(scenario, list)
            mProfile.browser.awaitPlaying()
            nowPlaying.awaitTitle(mStation.name)
            assertFalse(
                "The station is already marked, so marking it proves nothing. " +
                    nowPlaying.describe(),
                nowPlaying.favorite()
            )

            nowPlaying.tapFavorite()

            awaitFavorites(listOf(mStation.id))
            nowPlaying.awaitFavorite(true)

            nowPlaying.tapFavorite()

            awaitFavorites(emptyList())
            nowPlaying.awaitFavorite(false)
        }
    }

    /**
     * The gate this journey ran into, asserted rather than described.
     *
     * `MediaPresenterImpl.handleItemSelected` returns before it looks at what was tapped when
     * `NetworkLayer.checkConnectivityAndNotify` says there is no connection, and a station's row
     * goes through it like every other. So an offline user is shown a station that is on their own
     * device and cannot start it: the tap raises the no-connection toast and nothing plays.
     * TASK-049 owns that gate and carries this case as a criterion.
     */
    @Test
    fun theStationRowStartsNothingWithoutANetwork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            val nowPlaying = NowPlayingView(scenario)
            awaitSeededRoot(list)
            val navigation = JourneyNavigation(scenario)
            try {
                navigation.open(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
                awaitTheStationsRow(list)

                list.tapRow(mStation.id)

                assertNothingStarted(
                    nowPlaying,
                    "Tapping a station's row offline started it. TASK-049 asks for exactly that, " +
                        "so this assertion is what has to change, not the fix."
                )
            } finally {
                navigation.returnToRoot()
            }
        }
    }

    /**
     * Waits for the list to be the root of the profile every case here starts from: a cleared
     * install holding one station of the user's own.
     *
     * This is a precondition rather than a restatement of the cold launch journey. The presenter
     * is one of the registry's singletons and its node stack outlives the Activity, so a case that
     * left the stack inside a node would send the next case's Activity straight back there. Phase
     * 6 asks for a suite with no order assumptions in it, so every case asserts where it starts.
     */
    private fun awaitSeededRoot(list: BrowseListView) {
        assertEquals(
            "This case did not start at the root of the seeded profile. A case that walks into a " +
                "node leaves the presenter's stack there, and it is a registry singleton that " +
                "outlives the Activity, so the next case launches back into that node.",
            mProfile.cleanInstallRoot() + mProfile.localsRow(),
            list.awaitRows("the root of the seeded profile") {
                it == mProfile.cleanInstallRoot() + mProfile.localsRow()
            }
        )
    }

    /**
     * Opens the locals list, selects the station on its own rendered row, and walks back out to
     * the root.
     *
     * The item handed over is the one the adapter bound that row from, and the parent is the node
     * the presenter reports standing in, so neither argument of the selection is invented here.
     */
    private fun selectTheStationFromTheLocalsList(
        scenario: ActivityScenario<MainActivity>,
        list: BrowseListView
    ) {
        val navigation = JourneyNavigation(scenario)
        try {
            navigation.open(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            awaitTheStationsRow(list)
            val item = list.rowItem(mStation.id)
            assertNotNull(
                "The station's row is on screen but holds no item to select. " + list.describe(),
                item
            )
            mPlayback.select(item!!, navigation.currentCategory())
        } finally {
            navigation.returnToRoot()
        }
    }

    private fun awaitTheStationsRow(list: BrowseListView) {
        assertEquals(
            "The locals list does not show the seeded station",
            listOf(BrowseRow(mStation.id, mStation.name)),
            list.awaitRows("the station's own row") { it.size == 1 }
        )
    }

    /**
     * Asserts that nothing starts playing and that the bar does not come up for this station, and
     * goes on asserting it.
     *
     * A single read cannot tell "nothing happened" from "it has not happened yet", and a
     * selection crosses a coroutine, a browse and a process boundary before it reaches the player.
     */
    private fun assertNothingStarted(nowPlaying: NowPlayingView, reason: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS)
        while (System.nanoTime() < deadline) {
            assertFalse("$reason " + mProfile.browser.currentMediaId(), mProfile.browser.isPlaying())
            assertNotEquals(reason, mStation.id, mProfile.browser.currentMediaId())
            assertNotEquals("$reason " + nowPlaying.describe(), mStation.name, nowPlaying.title())
            Thread.sleep(POLL_MILLIS)
        }
    }

    /**
     * Waits for the favorites store to hold exactly [expected], read through an instance that has
     * cached no answers, because the writer answers out of a map it fills as it goes.
     */
    private fun awaitFavorites(expected: List<String>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STORE_TIMEOUT_SECONDS)
        var stored = emptyList<String>()
        while (System.nanoTime() < deadline) {
            stored = mProfile.storages.freshFavorites().getAll().map { it.id }
            if (stored == expected) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "The favorites store held $stored rather than $expected within " +
                "$STORE_TIMEOUT_SECONDS seconds."
        )
    }

    /**
     * @return what the player writes on the description line of a stream that is open and has
     *   told it nothing else about itself, which is every moment of a generated WAV.
     */
    private fun liveStreamDescription(): String {
        return mContext.getString(com.yuriy.openradio.R.string.media_description_default)
    }

    private companion object {

        /**
         * The extension decides the format the player infers, so it has to be the real one.
         */
        const val WAV_NAME = "journey-offline-playback.wav"

        /**
         * How long the player and the bar are watched before "nothing started" is believed.
         */
        const val SETTLE_SECONDS = 5L

        /**
         * Covers the hop onto the main thread the command is sent from, the round trip to the
         * service, and the write.
         */
        const val STORE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L
    }
}
