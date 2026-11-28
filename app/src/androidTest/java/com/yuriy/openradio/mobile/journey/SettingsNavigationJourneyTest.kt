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
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import com.yuriy.openradio.mobile.view.settings.SettingsTags
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.dependencies.SourcesLayerDependency
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.presenter.main.Tab
import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.testing.onMainThread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Moving between the settings screens: back order, About from the overflow, a rotation, and the
 * source choice up to "Restart to apply", which is never tapped, because a restart ends the
 * process the test runs in.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SettingsNavigationJourneyTest {

    @get:Rule
    val mStack = BrowseStacksRule()

    @get:Rule
    val mCompose = createEmptyComposeRule()

    private lateinit var mContext: Context

    private lateinit var mProfile: JourneyProfile

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mProfile = JourneyProfile(mContext)
        mProfile.start()
    }

    /** A failed source case must not leave another source stored for the next run of the app. */
    @After
    fun tearDown() {
        mProfile.clearApplicationData()
        mProfile.finish()
    }

    @Test
    fun backFromEverySubScreenReturnsToSettingsAndFromSettingsToTheMainScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            settings.openFromOverflow()

            for ((row, screen) in SUB_SCREENS) {
                settings.open(row, screen)
                settings.systemBack(SettingsTags.SCREEN)
                settings.open(row, screen)
                if (screen == SettingsTags.BUFFERING_SCREEN) {
                    settings.click(SettingsTags.CLOSE)
                    settings.await(SettingsTags.SCREEN)
                } else {
                    settings.back(SettingsTags.SCREEN)
                }
            }

            settings.systemBack(MainScreenTags.TABS)
            assertEquals(emptyList<Destination>(), onMainThread { registrySettings().screens.snapshot() })
        }
    }

    @Test
    fun theMainScreenIsAsItWasAfterSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            val main = MainScreenView(mCompose)
            val settings = SettingsView(mCompose)
            main.awaitTabs(mProfile.cleanInstallTabs())
            main.selectTab(Tab.MINE)
            val before = onMainThread { registryModel().snapshot() }
            try {
                settings.openFromOverflow()
                settings.open(SettingsTags.SOURCE, SettingsTags.CHOICE_SCREEN)
                settings.systemBack(SettingsTags.SCREEN)
                settings.systemBack(MainScreenTags.TABS)

                assertEquals(before, onMainThread { registryModel().snapshot() })
            } finally {
                main.selectTab(Tab.FAV)
            }
        }
    }

    @Test
    fun aboutFromTheOverflowReturnsToTheMainScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())

            settings.openAboutFromOverflow()
            settings.back(MainScreenTags.TABS)

            assertFalse(settings.isShown(SettingsTags.SCREEN))
        }
    }

    @Test
    fun aRotationKeepsTheOpenScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            settings.openFromOverflow()
            settings.open(SettingsTags.ABOUT, SettingsTags.ABOUT_SCREEN)

            scenario.recreate()

            settings.await(SettingsTags.ABOUT_SCREEN)
            settings.systemBack(SettingsTags.SCREEN)
            settings.systemBack(MainScreenTags.TABS)
        }
    }

    @Test
    fun anotherSourceOffersTheRestartAndBackPutsTheOriginalBack() {
        val sources = registrySources()
        val original = onMainThread { sources.getActiveSource() }
        val other = Source.entries.first { it != original }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            settings.openFromOverflow()
            settings.open(SettingsTags.SOURCE, SettingsTags.CHOICE_SCREEN)
            assertFalse(settings.isShown(SettingsTags.RESTART))

            settings.choose(other.name)
            settings.await(SettingsTags.RESTART)
            assertEquals(other, onMainThread { sources.getActiveSource() })

            settings.choose(original.name)
            settings.awaitGone(SettingsTags.RESTART)
            settings.choose(other.name)
            settings.await(SettingsTags.RESTART)

            settings.systemBack(SettingsTags.SCREEN)
            assertEquals(original, onMainThread { sources.getActiveSource() })
            awaitSettingsSource(original)
            settings.assertValue(SettingsTags.SOURCE, original.srcName.uppercase())
            settings.systemBack(MainScreenTags.TABS)
        }
    }

    /**
     * Waits for the Settings screen's state holder to read the store again, which leaving the
     * source choice makes it do on a coroutine. Without the wait the row may still show the value
     * from before the leave.
     */
    private fun awaitSettingsSource(expected: Source) {
        mCompose.waitUntil(TIMEOUT_MILLIS) {
            onMainThread { registrySettings().settings.state.value.source } == expected
        }
    }

    private fun registrySources(): SourcesLayer {
        lateinit var result: SourcesLayer
        DependencyRegistryCommon.injectSourcesLayer(
            object : SourcesLayerDependency {
                override fun configureWith(sourcesLayer: SourcesLayer) {
                    result = sourcesLayer
                }
            }
        )
        return result
    }

    private companion object {

        const val TIMEOUT_MILLIS = 10_000L

        /** Each Settings row that opens a sub-screen, with the tag of that sub-screen. */
        val SUB_SCREENS = listOf(
            SettingsTags.START_PLAYBACK to SettingsTags.CHOICE_SCREEN,
            SettingsTags.MASTER_VOLUME to SettingsTags.VOLUME_SCREEN,
            SettingsTags.STREAM_BUFFERING to SettingsTags.BUFFERING_SCREEN,
            SettingsTags.SOURCE to SettingsTags.CHOICE_SCREEN,
            SettingsTags.DEFAULT_COUNTRY to SettingsTags.CHOICE_SCREEN,
            SettingsTags.USER_AGENT to SettingsTags.USER_AGENT_SCREEN,
            SettingsTags.DIAGNOSTICS to SettingsTags.DIAGNOSTICS_SCREEN,
            SettingsTags.ABOUT to SettingsTags.ABOUT_SCREEN,
        )
    }
}
