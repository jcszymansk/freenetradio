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

package com.yuriy.openradio.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.jcszymansk.console.design.ConsoleTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yuriy.openradio.mobile.view.main.MainScreen
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every main screen state the spec names, in dark and light. The browse baselines
 * (`main_popular_playing_*`) are the ones the human compares with
 * `console-design/design-reference/png/freenetradio-browse-*.png`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class MainScreenScreenshotTest(private val mTheme: String, private val mDark: Boolean) {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun popularPlaying() = capture("popular_playing")
    @Test fun popularPaused() = capture("popular_paused")
    @Test fun favLoaded() = capture("fav_loaded")
    @Test fun mineLoaded() = capture("mine_loaded")
    @Test fun newLoaded() = capture("new_loaded")
    @Test fun genreLoaded() = capture("genre_loaded")
    @Test fun countryYourCountry() = capture("country_your_country")
    @Test fun countryPlain() = capture("country_plain")
    @Test fun countryDrillDown() = capture("country_drill_down")
    @Test fun loading() = capture("loading", PROGRESS_FRAME_MILLIS)
    @Test fun emptyFav() = capture("empty_fav")
    @Test fun emptyMine() = capture("empty_mine")
    @Test fun error() = capture("error")

    /**
     * The menu is a popup window, which the capture draws only once the clock runs again: with the
     * clock held, it is composed but missing from the image. This state has no progress line, so
     * letting the clock run to idle is deterministic.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun overflowOpen() {
        show("popular_playing")
        composeRule.onNodeWithTag(MainScreenTags.OVERFLOW).performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/main_overflow_open_$mTheme.png")
    }

    /**
     * Captures [name] after [settleMillis] of the held clock; zero captures the first frame.
     */
    private fun capture(name: String, settleMillis: Long = 0L) {
        show(name)
        composeRule.mainClock.advanceTimeBy(settleMillis)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/main_${name}_$mTheme.png")
    }

    /**
     * The clock is held so the indeterminate progress line is drawn at one fixed frame and the
     * capture does not wait for an animation that never ends.
     */
    private fun show(name: String) {
        composeRule.mainClock.autoAdvance = false
        val state = MainScreenFixtures.states.getValue(name)
        composeRule.setContent {
            ConsoleTheme(darkTheme = mDark) {
                Box { MainScreen(state, MainScreenFixtures.noActions) }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    companion object {

        /**
         * At the first frame the indeterminate progress line has not yet drawn its bar and shows
         * only the track; half a second in, the accent bar is on screen.
         */
        private const val PROGRESS_FRAME_MILLIS = 500L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf("dark", true), arrayOf("light", false))
    }
}
