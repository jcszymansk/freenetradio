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
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.presenter.main.BrowseSnapshot
import com.yuriy.openradio.shared.service.LocalAudioFixture
import com.yuriy.openradio.shared.service.LocalStationsFixture
import com.yuriy.openradio.shared.service.ServiceRecreation
import com.yuriy.openradio.testing.withActivity
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
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
 * What this journey owns that the service tests do not is the screen. The phone's playback UI is
 * the mini player under the list: the station's name, the line the player writes about the
 * stream, and one play and pause button, the only transport control the application has.
 * `OpenRadioServicePlaybackTest` drives the same player through a bare
 * [androidx.media3.session.MediaBrowser] and renders nothing; what is asserted here is the mini
 * player, and the heart on the playing station's row.
 *
 * The tap that starts playback cannot be performed offline. `MainScreenModel.open` refuses a row
 * tap without a connection, a station's row like any other, so
 * [theStationRowStartsNothingWithoutANetwork] pins that refusal and [JourneyPlayback] makes the
 * call that tap makes once past the gate, with the item and the node MINE is showing. Everything
 * after the selection is the application's own.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OfflinePlaybackJourneyTest {

    @get:Rule
    val mStack = BrowseStacksRule()

    @get:Rule
    val mCompose = createEmptyComposeRule()

    private lateinit var mContext: Context

    private lateinit var mProfile: JourneyProfile

    private lateinit var mAudio: LocalAudioFixture

    private lateinit var mStations: LocalStationsFixture

    private lateinit var mPlayback: JourneyPlayback

    private lateinit var mStation: RadioStation

    private var mWasLastKnownStationEnabled = true

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mAudio = LocalAudioFixture(mContext)
        mProfile = JourneyProfile(mContext)
        mProfile.start()
        mWasLastKnownStationEnabled = AppPreferencesManager.lastKnownRadioStationEnabled(mContext)
        AppPreferencesManager.lastKnownRadioStationEnabled(mContext, true)
        // The player outlives every test class in the run, so a case that asserts what playing
        // does has to start from a player that is not.
        mProfile.browser.stop()
        mStations = LocalStationsFixture(mProfile.storages, mProfile.browser)
        mStation = mStations.seed(mAudio.wav(WAV_NAME)).first()
        mPlayback = JourneyPlayback(mContext)
        mPlayback.connect()
    }

    /**
     * A restart case releases the profile's browser to let the service go, and reconnects it to
     * the new one; one that failed in between leaves it released, and parking the player needs it.
     */
    @After
    fun tearDown() {
        mPlayback.release()
        if (!mProfile.browser.isConnected()) {
            mProfile.browser.connectUnchecked()
        }
        mStations.parkThePlayer()
        mProfile.finish()
        mAudio.delete()
        AppPreferencesManager.lastKnownRadioStationEnabled(mContext, mWasLastKnownStationEnabled)
    }

    /**
     * The journey itself: a station of the user's own is picked out of MINE, and the audio it
     * points at is what comes out of the player.
     *
     * The url is asserted as well as the station, because a player that is ready on some other
     * item, or on a playlist the service built for itself, would otherwise read as this station
     * playing.
     */
    @Test
    fun aSelectedStationPlaysTheAudioItPointsAt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            selectTheStationFromMine(scenario)

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
     * The mini player the phone puts up when something is playing.
     *
     * The application raises it on the metadata the session pushes back, so nothing between the
     * selection and the mini player is done here. The name comes from the station; the track line
     * is written by the player rather than by the station, and it settles on the default stream
     * line once the file is open.
     *
     * The mini player is asserted not to be showing this station before the selection, because
     * what it shows outlives the Activity that put it there: one left up by an earlier case would
     * otherwise answer for the one this case is waiting for.
     */
    @Test
    fun theMiniPlayerShowsTheStationThatIsPlaying() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val miniPlayer = MiniPlayerView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            assertNotEquals(
                "The mini player is already showing this station, so waiting for it proves nothing",
                mStation.name,
                miniPlayer.name()
            )

            selectTheStationFromMine(scenario)

            miniPlayer.awaitName(mStation.name)
            miniPlayer.awaitTrack(mAudio.liveStreamDescription())
            assertTrue(
                "The mini player went back down while the station was still playing. " + miniPlayer.describe(),
                miniPlayer.isVisible()
            )
        }
    }

    /**
     * The mini player belongs to the session rather than to the screen that started playback.
     *
     * A user who leaves the application and comes back while a station is playing has to find it
     * where they left it. The second Activity here is handed nothing by the test: it connects to
     * the session on its own and builds the mini player from what the session holds.
     */
    @Test
    fun theMiniPlayerComesBackUpForAnActivityThatDidNotStartPlayback() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            selectTheStationFromMine(scenario)
            mProfile.browser.awaitPlaying()
            MiniPlayerView(mCompose).awaitName(mStation.name)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val miniPlayer = MiniPlayerView(mCompose)
            miniPlayer.awaitName(mStation.name)
            miniPlayer.awaitTrack(mAudio.liveStreamDescription())
            miniPlayer.awaitPlaying(true)
            assertTrue(
                "The relaunched mini player is showing a station that is not playing",
                mProfile.browser.isPlaying()
            )
        }
    }

    /**
     * The phone's transport control, the mini player's one button.
     *
     * The tap sends `CMD_TOGGLE_LAST_PLAYED_ITEM` and the service works out from the player's
     * state whether that means pause or play, so the same gesture has to do both, and the button
     * has to say which one it will do next.
     *
     * The station's name is a tap target that acts on nothing until a Now playing screen exists
     * (REQ-008). It is tapped while the station is paused, where anything it started would show.
     */
    @Test
    fun theMiniPlayerButtonPausesAndResumesTheStation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val miniPlayer = MiniPlayerView(mCompose)
            selectTheStationFromMine(scenario)
            mProfile.browser.awaitPlaying()
            miniPlayer.awaitName(mStation.name)
            miniPlayer.awaitPlaying(true)

            miniPlayer.tapButton()

            mProfile.browser.awaitPlayback("the station to pause") { !mProfile.browser.isPlaying() }
            miniPlayer.awaitPlaying(false)
            assertEquals(
                "Pausing dropped the station instead of holding it",
                mStation.id,
                mProfile.browser.currentMediaId()
            )

            val before = JourneyNavigation(scenario, MainScreenView(mCompose)).snapshot()
            miniPlayer.tapName()
            assertNameTapDoesNothing(scenario, miniPlayer, before)

            miniPlayer.tapButton()

            mProfile.browser.awaitPlaying()
            miniPlayer.awaitPlaying(true)
            assertEquals(
                "Resuming moved the player onto some other station",
                mStation.id,
                mProfile.browser.currentMediaId()
            )
        }
    }

    /**
     * Marking what is playing is the heart on its row: the mini player has no favorite control of
     * its own.
     *
     * The heart is read before it is tapped, so a station that arrived already marked fails here
     * rather than passing the case it sets up, and the store is read back through an instance that
     * has cached no answers.
     */
    @Test
    fun thePlayingStationsRowHeartMarksIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            selectTheStationFromMine(scenario)
            mProfile.browser.awaitPlaying()
            MiniPlayerView(mCompose).awaitName(mStation.name)
            try {
                awaitTheStationOnMine(view)
                assertEquals(
                    "The station is already marked, so marking it proves nothing. " + view.describe(),
                    false,
                    view.rowFavorite(mStation.id)
                )

                view.tapFavorite(mStation.id)

                mProfile.awaitFavorites(listOf(mStation.id))
                view.awaitRowFavorite(mStation.id, true)

                view.tapFavorite(mStation.id)

                mProfile.awaitFavorites(emptyList())
                view.awaitRowFavorite(mStation.id, false)
                assertTrue("Marking the station stopped it", mProfile.browser.isPlaying())
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    /**
     * The gate this journey ran into, asserted rather than described.
     *
     * `MainScreenModel.open` returns before it looks at what was tapped when the connectivity
     * check says there is no connection, and a station's row goes through it like every other. So
     * an offline user is shown a station that is on their own device and cannot start it: nothing
     * plays. TASK-049 owns that gate and carries this case as a criterion.
     */
    @Test
    fun theStationRowStartsNothingWithoutANetwork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            try {
                awaitTheStationOnMine(view)

                view.tapRow(mStation.id)

                assertNothingStarted(
                    MiniPlayerView(mCompose),
                    "Tapping a station's row offline started it. TASK-049 asks for exactly that, " +
                        "so this assertion is what has to change, not the fix."
                )
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    /**
     * REQ-008's restart, with the "last known station" setting on, as it is by default. The new
     * service holds no queue and adopts the latest station, and the mini player comes up on it.
     *
     * It does not stay paused. With the setting on, the service starts the latest station by
     * itself on the first non-empty page-0 browse it stores (`onFirstPageStored` →
     * `maybeCreateInitialPlaylist`), and the screen browses the root as soon as it connects. That
     * is the setting's own promise ("autoplay last known Radio Station on app start"), and the
     * service is not changed (REQ-015). Decision 14 records the conflict with the spec's
     * "paused" for the owner.
     */
    @Test
    fun aRecreatedServiceBringsTheLatestStationBackOnTheMiniPlayer() {
        mProfile.storages.favorites.add(mStation)
        playTheStationUntilItIsTheLatest()

        recreateTheService()
        assertEquals(
            "The recreated service did not adopt the latest station",
            mStation.id, mProfile.browser.activeStationId()
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            val miniPlayer = MiniPlayerView(mCompose)
            miniPlayer.awaitName(mStation.name)
            mProfile.browser.awaitPlaying()
            assertEquals(
                "The service started some other station than the latest one",
                mStation.id, mProfile.browser.currentMediaId()
            )
            miniPlayer.awaitPlaying(true)

            miniPlayer.tapButton()

            mProfile.browser.awaitPlayback("the station to pause") { !mProfile.browser.isPlaying() }
            miniPlayer.awaitPlaying(false)
            miniPlayer.awaitName(mStation.name)

            miniPlayer.tapButton()

            mProfile.browser.awaitPlaying()
            assertEquals(
                "Resuming moved the player onto some other station",
                mStation.id, mProfile.browser.currentMediaId()
            )
        }
    }

    /**
     * With the setting off, a recreated service adopts no station, so a play request would resume
     * nothing. The mini player stays down rather than offer a button that does nothing, and
     * nothing starts.
     */
    @Test
    fun aRecreatedServiceWithTheLastStationSettingOffLeavesTheMiniPlayerDown() {
        mProfile.storages.favorites.add(mStation)
        playTheStationUntilItIsTheLatest()
        AppPreferencesManager.lastKnownRadioStationEnabled(mContext, false)

        recreateTheService()
        assertFalse(
            "The recreated service adopted a station although the setting is off",
            mProfile.browser.canABrowseStartPlayback()
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            val view = MainScreenView(mCompose)
            val miniPlayer = MiniPlayerView(mCompose)
            view.awaitRows("FAV to show the favorite, so the screen is connected") { rows ->
                rows.any { it.mediaId == mStation.id }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS)
            while (System.nanoTime() < deadline) {
                assertFalse("The mini player came up. " + miniPlayer.describe(), miniPlayer.isVisible())
                assertFalse("Something started playing", mProfile.browser.isPlaying())
                Thread.sleep(POLL_MILLIS)
            }
        }
    }

    /**
     * Plays the station from MINE in an activity of its own, and waits until the service has
     * stored it as the latest station, which it does from a background coroutine.
     */
    private fun playTheStationUntilItIsTheLatest() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            selectTheStationFromMine(scenario)
            mProfile.browser.awaitPlaying()
            mProfile.browser.awaitPlayback("the station to be stored as the latest one") {
                mProfile.storages.freshLatest().get().id == mStation.id
            }
        }
    }

    /**
     * Stops playback and destroys the service, so the next connection creates a new one with an
     * empty queue. [ServiceRecreation] needs every binding released. The screen's binding went
     * when its activity was destroyed, because the model's `stop()` cleans its client. This
     * class's own two bindings are released here.
     */
    private fun recreateTheService() {
        mProfile.browser.stop()
        mProfile.browser.awaitPlaybackState(Player.STATE_IDLE)
        mPlayback.release()
        mProfile.browser.release()
        ServiceRecreation(mContext).destroyTheService()
        mProfile.browser.connectUnchecked()
        assertEquals("The recreated service holds a queue", 0, mProfile.browser.mediaItemCount())
    }

    /**
     * Selects the station on its own rendered row on MINE, and puts the tabs back.
     *
     * The item handed over is the one MINE's row shows and the parent is the node MINE stands in,
     * both read from the screen's model the way its long-press reads them, so neither argument of
     * the selection is invented here.
     */
    private fun selectTheStationFromMine(scenario: ActivityScenario<MainActivity>) {
        val view = MainScreenView(mCompose)
        val navigation = JourneyNavigation(scenario, view)
        try {
            awaitTheStationOnMine(view)
            val model = registryModel()
            val target = scenario.withActivity { model.longPressTarget(mStation.id) }
            assertNotNull(
                "The station's row is on MINE but the model holds no item for it. " + view.describe(),
                target
            )
            mPlayback.select(target!!.item, target.parentId)
        } finally {
            navigation.returnToRoots()
        }
    }

    /**
     * Selects MINE and waits for it to list the seeded station, under its own name.
     */
    private fun awaitTheStationOnMine(view: MainScreenView) {
        view.awaitTabs(mProfile.cleanInstallTabs())
        assertEquals(
            "MINE does not show the seeded station",
            listOf(BrowseRow(mStation.id, mStation.name)),
            mProfile.awaitSeededMine(view, mStation)
        )
    }

    /**
     * Asserts that nothing starts playing and that the mini player does not come up for this
     * station, and goes on asserting it.
     *
     * A single read cannot tell "nothing happened" from "it has not happened yet", and a
     * selection crosses a coroutine, a browse and a process boundary before it reaches the player.
     */
    private fun assertNothingStarted(miniPlayer: MiniPlayerView, reason: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS)
        while (System.nanoTime() < deadline) {
            assertFalse("$reason " + mProfile.browser.currentMediaId(), mProfile.browser.isPlaying())
            assertNotEquals(reason, mStation.id, mProfile.browser.currentMediaId())
            assertNotEquals("$reason " + miniPlayer.describe(), mStation.name, miniPlayer.name())
            Thread.sleep(POLL_MILLIS)
        }
    }

    /**
     * Asserts that the tap on the station's name, made while the station is paused, goes on
     * changing nothing: the station stays paused and held, the mini player stays as it was, and
     * the screen stays where it was.
     */
    private fun assertNameTapDoesNothing(
        scenario: ActivityScenario<MainActivity>,
        miniPlayer: MiniPlayerView,
        before: BrowseSnapshot
    ) {
        val navigation = JourneyNavigation(scenario, MainScreenView(mCompose))
        val reason = "Tapping the station's name in the mini player did something"
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS)
        while (System.nanoTime() < deadline) {
            assertFalse("$reason: the station started", mProfile.browser.isPlaying())
            assertEquals("$reason: the player moved", mStation.id, mProfile.browser.currentMediaId())
            assertEquals("$reason. " + miniPlayer.describe(), mStation.name, miniPlayer.name())
            assertEquals("$reason: the screen moved", before, navigation.snapshot())
            Thread.sleep(POLL_MILLIS)
        }
    }

    private companion object {

        /**
         * The extension decides the format the player infers, so it has to be the real one.
         */
        const val WAV_NAME = "journey-offline-playback.wav"

        /**
         * How long the player and the mini player are watched before "nothing started" is
         * believed.
         */
        const val SETTLE_SECONDS = 5L

        const val POLL_MILLIS = 50L
    }
}
