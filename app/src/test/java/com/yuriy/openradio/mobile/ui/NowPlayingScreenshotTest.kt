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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.jcszymansk.console.design.ConsoleTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.yuriy.openradio.mobile.view.main.ArtworkCache
import com.yuriy.openradio.mobile.view.main.LocalPlayerArtworkCache
import com.yuriy.openradio.mobile.view.main.NowPlayingScreen
import com.yuriy.openradio.mobile.view.main.NowPlayingTags
import com.yuriy.openradio.shared.model.storage.images.ImagesStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every Now playing and Station details state the spec names, in dark and light. The
 * `now_playing_playing_*` baselines are the ones the owner compares with
 * `console-design/design-reference/png/freenetradio-now-playing-*.png`, apart from D1, D2 and D6.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class NowPlayingScreenshotTest(private val mTheme: String, private val mDark: Boolean) {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun playing() = capture("playing")
    @Test fun artwork() = capture("artwork")
    @Test fun paused() = capture("paused")
    @Test fun buffering() = capture("buffering", PROGRESS_FRAME_MILLIS)
    @Test fun error() = capture("error")
    @Test fun noTrack() = capture("no_track")
    @Test fun notFavorite() = capture("not_favorite")
    @Test fun detailsAll() = capture("details_all")
    @Test fun detailsMissing() = capture("details_missing")

    /**
     * The menu is a popup window, which the capture draws only once the clock runs again. This
     * state has no progress line, so letting the clock run to idle is deterministic.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun overflowOpen() {
        show("playing")
        composeRule.onNodeWithTag(NowPlayingTags.OVERFLOW).performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/now_playing_overflow_open_$mTheme.png")
    }

    private fun capture(name: String, settleMillis: Long = 0L) {
        show(name)
        composeRule.mainClock.advanceTimeBy(settleMillis)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/now_playing_${name}_$mTheme.png")
    }

    /**
     * The clock is held so the progress line is drawn at one fixed frame. The artwork is loaded
     * into the cache before the first frame, so the box shows it at once.
     */
    private fun show(name: String) {
        composeRule.mainClock.autoAdvance = false
        val (state, screen) = NowPlayingFixtures.states.getValue(name)
        val cache = ArtworkCache({ logo() })
        val uri = NowPlayingFixtures.artworkUri
        runBlocking { cache.load(ImagesStore.getId(Uri.parse(uri)), uri) }
        composeRule.setContent {
            ConsoleTheme(darkTheme = mDark) {
                CompositionLocalProvider(LocalPlayerArtworkCache provides cache) {
                    Box { NowPlayingScreen(state, screen, NowPlayingFixtures.noActions) }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    companion object {

        /** Half a second in, the indeterminate line's accent bar is on screen. */
        private const val PROGRESS_FRAME_MILLIS = 500L

        /** A square stand-in logo: a dark field with a lighter disc. */
        private fun logo() = Bitmap.createBitmap(LOGO_PX, LOGO_PX, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(0x20, 0x3A, 0x5C))
            Canvas(this).drawCircle(LOGO_PX / 2f, LOGO_PX / 2f, LOGO_PX / 3f, Paint().apply { color = Color.rgb(0xE8, 0xB0, 0x40) })
        }.asImageBitmap()

        private const val LOGO_PX = 300

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf("dark", true), arrayOf("light", false))
    }
}
