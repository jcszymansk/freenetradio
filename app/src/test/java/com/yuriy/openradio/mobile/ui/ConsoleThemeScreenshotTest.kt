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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleDimens
import com.github.jcszymansk.console.design.ConsoleTheme
import com.github.jcszymansk.console.design.KeyValueRow
import com.github.jcszymansk.console.design.KeyValueTable
import com.github.jcszymansk.console.design.SectionHeader
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves that the console-design theme, its fonts and its string resources render inside the
 * app's own resources, under the real application class. The recorded images are compared with
 * the baselines in `app/src/test/screenshots/` by `:app:verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ConsoleThemeScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun consoleThemeDark() = capture("dark", darkTheme = true)

    @Test fun consoleThemeLight() = capture("light", darkTheme = false)

    private fun capture(name: String, darkTheme: Boolean) {
        composeRule.setContent { StreamInfoSample(darkTheme) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/console_theme_$name.png")
    }
}

@Composable
private fun StreamInfoSample(darkTheme: Boolean) {
    ConsoleTheme(darkTheme = darkTheme) {
        Column(Modifier.fillMaxSize().background(Console.colors.background)) {
            SectionHeader("Stream info")
            KeyValueTable(
                rows = listOf(
                    KeyValueRow("Station", "KEXP"),
                    KeyValueRow("Bitrate", "128 kbps"),
                    KeyValueRow("Codec", "MP3"),
                    KeyValueRow("Web", "kexp.org", onClick = {}),
                ),
                modifier = Modifier.padding(horizontal = ConsoleDimens.screenPadding),
            )
        }
    }
}
