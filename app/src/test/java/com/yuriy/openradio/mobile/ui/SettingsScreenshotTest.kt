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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.ConsoleTheme
import com.github.jcszymansk.console.design.PrimaryButton
import com.github.takahirom.roborazzi.captureRoboImage
import com.yuriy.openradio.mobile.view.settings.AboutScreen
import com.yuriy.openradio.mobile.view.settings.ChoiceScreen
import com.yuriy.openradio.mobile.view.settings.MasterVolumeScreen
import com.yuriy.openradio.mobile.view.settings.SettingsScreen
import com.yuriy.openradio.mobile.view.settings.SettingsTags
import com.yuriy.openradio.mobile.view.settings.countryRows
import com.yuriy.openradio.mobile.view.settings.sourceRows
import com.yuriy.openradio.mobile.view.settings.startPlaybackRows
import com.yuriy.openradio.shared.model.storage.StartPlayback
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every settings screen state REQ-022 names, in dark and light. There are no reference images for
 * these screens, so the owner reviews them against DESIGN.md and the `ntrecv-settings-*` and
 * `msgsched-edit-*` references.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class SettingsScreenshotTest(private val mTheme: String, private val mDark: Boolean) {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun choiceStartPlayback() = capture("choice_start_playback") {
        ChoiceScreen(
            "Start playback",
            startPlaybackRows(StartPlayback.IF_PLAYING, ::startPlaybackLabel),
            {},
            {},
        )
    }

    @Test
    fun choiceSource() = capture("choice_source") {
        ChoiceScreen("Source", sourceRows(SettingsFixtures.sourceIdle), {}, {})
    }

    @Test
    fun choiceSourceRestart() = capture("choice_source_restart") {
        ChoiceScreen(
            "Source",
            sourceRows(SettingsFixtures.sourceRestart),
            {},
            {},
            footer = {
                PrimaryButton(
                    "Restart to apply",
                    {},
                    Modifier.padding(16.dp).fillMaxWidth().testTag(SettingsTags.RESTART),
                )
            },
        )
    }

    @Test
    fun choiceCountry() = capture("choice_country") {
        ChoiceScreen("Default country", countryRows(SettingsFixtures.countries, "PL"), {}, {})
    }

    @Test
    fun volumeAtZero() = capture("volume_min") {
        MasterVolumeScreen(0, SettingsFixtures.noActions)
    }

    @Test
    fun volumeInBetween() = capture("volume_mid") {
        MasterVolumeScreen(73, SettingsFixtures.noActions)
    }

    @Test
    fun volumeAtAHundred() = capture("volume_max") {
        MasterVolumeScreen(100, SettingsFixtures.noActions)
    }

    @Test
    fun about() = capture("about") {
        AboutScreen(SettingsFixtures.about, SettingsFixtures.noActions)
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    fun settingsWithDefaults() = capture("defaults") {
        SettingsScreen(SettingsFixtures.defaults, transferring = false, actions = SettingsFixtures.noActions)
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    fun settingsWithChangedValues() = capture("changed") {
        SettingsScreen(SettingsFixtures.changed, transferring = false, actions = SettingsFixtures.noActions)
    }

    @Test
    fun settingsWhileAFileIsWritten() = capture("export_progress", PROGRESS_FRAME_MILLIS) {
        SettingsScreen(SettingsFixtures.defaults, transferring = true, actions = SettingsFixtures.noActions)
    }

    /**
     * Captures [content] after [settleMillis] of the held clock, so an indeterminate progress line
     * is drawn at one fixed frame; zero captures the first frame.
     */
    private fun capture(name: String, settleMillis: Long = 0L, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ConsoleTheme(darkTheme = mDark) {
                Box { content() }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(settleMillis)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/settings_${name}_$mTheme.png")
    }

    companion object {

        private const val PROGRESS_FRAME_MILLIS = 500L

        private fun startPlaybackLabel(value: StartPlayback): String = when (value) {
            StartPlayback.ALWAYS -> "Always"
            StartPlayback.IF_PLAYING -> "If it was playing"
            StartPlayback.NEVER -> "Never"
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf("dark", true), arrayOf("light", false))
    }
}
