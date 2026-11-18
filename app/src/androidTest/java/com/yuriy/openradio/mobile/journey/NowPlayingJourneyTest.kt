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

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.mobile.view.main.PlayerMenuItem
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getListedStreamUrl
import com.yuriy.openradio.shared.model.media.getStreamBitrate
import com.yuriy.openradio.shared.presenter.main.StationDetails
import com.yuriy.openradio.shared.presenter.main.Tab
import com.yuriy.openradio.shared.service.LocalAudioFixture
import com.yuriy.openradio.shared.service.LocalStationsFixture
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import com.yuriy.openradio.testing.withActivity
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Now playing, from the mini player to Station details, with the device offline.
 *
 * The station is one of the user's own, playing a WAV from the application's cache over
 * `file://`, as in [OfflinePlaybackJourneyTest], selected the way that class explains. Its home
 * page points at [UNREACHABLE_ORIGIN], and the browser and the share chooser are caught by a
 * [LaunchRecorder] before they open, so nothing leaves the device.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class NowPlayingJourneyTest {

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
    private val mLaunches = LaunchRecorder(Intent.ACTION_CHOOSER, Intent.ACTION_VIEW)

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mAudio = LocalAudioFixture(mContext)
        mProfile = JourneyProfile(mContext)
        mProfile.start()
        mProfile.browser.stop()
        mStations = LocalStationsFixture(mProfile.storages, mProfile.browser)
        mStation = mStations.seed(mAudio.wav(WAV_NAME)) {
            country = "Poland"
            genre = "Jazz"
            codec = "WAV"
            homePage = HOME_PAGE
        }.first()
        mPlayback = JourneyPlayback(mContext)
        mPlayback.connect()
        mLaunches.install()
    }

    @After
    fun tearDown() {
        mLaunches.remove()
        mPlayback.release()
        mStations.parkThePlayer()
        mProfile.finish()
        mAudio.delete()
    }

    @Test
    fun theMiniPlayerOpensNowPlayingAndItsButtonDoesNot() {
        launchPlaying { _, miniPlayer, nowPlaying ->
            miniPlayer.tapButton()
            mProfile.browser.awaitPlayback("the station to pause") { !mProfile.browser.isPlaying() }
            miniPlayer.awaitPlaying(false)
            assertFalse("The mini player's button opened Now playing", nowPlaying.isShown())

            miniPlayer.tapName()

            nowPlaying.awaitShown()
            nowPlaying.awaitName(mStation.name)
            assertFalse("The mini player is shown over Now playing", miniPlayer.isVisible())
        }
    }

    @Test
    fun pauseAndPlayOnNowPlayingAgreeWithTheMiniPlayer() {
        launchPlaying { _, miniPlayer, nowPlaying ->
            miniPlayer.tapName()
            nowPlaying.awaitPlaying(true)

            nowPlaying.tapToggle()

            mProfile.browser.awaitPlayback("the station to pause") { !mProfile.browser.isPlaying() }
            nowPlaying.awaitPlaying(false)
            nowPlaying.tapClose()
            miniPlayer.awaitPlaying(false)

            miniPlayer.tapButton()

            mProfile.browser.awaitPlaying()
            miniPlayer.awaitPlaying(true)
            miniPlayer.tapName()
            nowPlaying.awaitPlaying(true)
        }
    }

    @Test
    fun theHeartOnNowPlayingShowsInFavAndOnTheStationsRow() {
        launchPlaying { scenario, miniPlayer, nowPlaying ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            navigation.selectTab(Tab.MINE)
            view.awaitRowFavorite(mStation.id, false)
            miniPlayer.tapName()
            nowPlaying.awaitFavorite(false)

            nowPlaying.tapFavorite()

            nowPlaying.awaitFavorite(true)
            mProfile.awaitFavorites(listOf(mStation.id))
            nowPlaying.tapClose()
            view.awaitRowFavorite(mStation.id, true)
            navigation.selectTab(Tab.FAV)
            view.awaitRows("the station in FAV") { rows -> rows.any { it.mediaId == mStation.id } }

            navigation.selectTab(Tab.MINE)
            miniPlayer.tapName()
            nowPlaying.tapFavorite()

            nowPlaying.awaitFavorite(false)
            mProfile.awaitFavorites(emptyList())
            nowPlaying.tapClose()
            view.awaitRowFavorite(mStation.id, false)
            assertTrue("Marking the station stopped it", mProfile.browser.isPlaying())
        }
    }

    @Test
    fun theChevronAndSystemBackReturnToTheMainScreenAsItWas() {
        launchPlaying { scenario, miniPlayer, nowPlaying ->
            val navigation = JourneyNavigation(scenario, MainScreenView(mCompose))
            navigation.selectTab(Tab.MINE)
            navigation.search("fixture")
            val before = navigation.snapshot()

            miniPlayer.tapName()
            nowPlaying.awaitShown()
            nowPlaying.tapClose()

            nowPlaying.awaitGone()
            assertEquals("The chevron moved the main screen", before, navigation.snapshot())
            assertTrue("The mini player did not come back", miniPlayer.isVisible())

            miniPlayer.tapName()
            nowPlaying.awaitShown()
            Espresso.pressBack()

            nowPlaying.awaitGone()
            assertEquals("System back moved the main screen", before, navigation.snapshot())
        }
    }

    @Test
    fun copyPutsTheListedStreamUrlOnTheClipboard() {
        launchPlaying { scenario, miniPlayer, nowPlaying ->
            miniPlayer.tapName()
            nowPlaying.awaitShown()

            nowPlaying.choose(PlayerMenuItem.COPY_STREAM_URL)

            awaitClipboard(scenario, mStation.getListedStreamUrl())
        }
    }

    @Test
    fun shareStartsAChooserWithTheUrlAndTheNameAndNothingElse() {
        launchPlaying { _, miniPlayer, nowPlaying ->
            miniPlayer.tapName()
            nowPlaying.awaitShown()

            nowPlaying.choose(PlayerMenuItem.SHARE_STREAM_URL)

            val chooser = mLaunches.await()
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                ?: throw AssertionError("The chooser was started without the intent to share")
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("text/plain", send.type)
            assertEquals(mStation.getListedStreamUrl(), send.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(mStation.name, send.getStringExtra(Intent.EXTRA_SUBJECT))
            assertEquals(setOf(Intent.EXTRA_TEXT, Intent.EXTRA_SUBJECT), send.extras!!.keySet())
        }
    }

    @Test
    fun stationDetailsShowsItsRowsAndWebOpensTheHomePage() {
        launchPlaying { _, miniPlayer, nowPlaying ->
            miniPlayer.tapName()
            nowPlaying.awaitShown()

            nowPlaying.choose(PlayerMenuItem.STATION_DETAILS)

            nowPlaying.awaitDetails()
            val host = StationDetails.host(HOME_PAGE)
            assertEquals(
                listOf(
                    "COUNTRY", "Poland",
                    "GENRE", "Jazz",
                    "STREAM", StationDetails.stream(mStation.codec, mStation.getStreamBitrate()),
                    "URL", mStation.getListedStreamUrl(),
                    "WEB", host,
                ),
                nowPlaying.detailTexts()
            )

            nowPlaying.tapLink(host)

            val view = mLaunches.await()
            assertEquals(Intent.ACTION_VIEW, view.action)
            assertEquals(HOME_PAGE, view.dataString)

            Espresso.pressBack()
            nowPlaying.awaitShown()
        }
    }

    /**
     * Launches the main screen with the seeded station playing and its mini player up, runs
     * [case], and leaves every tab at its root and the player screen shut.
     */
    private fun launchPlaying(case: (ActivityScenario<MainActivity>, MiniPlayerView, NowPlayingView) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val miniPlayer = MiniPlayerView(mCompose)
            selectTheStationFromMine(scenario, mCompose, mProfile, mPlayback, mStation)
            mProfile.browser.awaitPlaying()
            miniPlayer.awaitName(mStation.name)
            val navigation = JourneyNavigation(scenario, MainScreenView(mCompose))
            try {
                case(scenario, miniPlayer, NowPlayingView(mCompose))
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    /**
     * Waits for the clipboard to hold [expected]. Android lets only the focused application read
     * the clipboard, which the activity under test is, so it is read through it.
     */
    private fun awaitClipboard(scenario: ActivityScenario<MainActivity>, expected: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MainScreenView.TIMEOUT_SECONDS)
        var held: String? = null
        while (System.nanoTime() < deadline) {
            held = scenario.withActivity { activity ->
                activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
            }
            if (held == expected) {
                return
            }
            Thread.sleep(MainScreenView.POLL_MILLIS)
        }
        throw AssertionError("The clipboard held '$held' rather than '$expected'")
    }

    private companion object {

        const val WAV_NAME = "journey-now-playing.wav"

        /** Unreachable from the device, and never opened: the recorder cancels the view. */
        const val HOME_PAGE = "$UNREACHABLE_ORIGIN/home"
    }
}
