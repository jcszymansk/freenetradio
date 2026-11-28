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
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.settings.BufferField
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.testing.onMainThread
import com.yuriy.openradio.testing.withActivity
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fifth phone journey: what the user changed in the settings is still what the application
 * offers after Android has torn the Activity down and built it again.
 *
 * One Settings screen and its sub-screens write to three different stores: the mobile data row to
 * `NetworkSettingsStorage`, the buffering, start playback and user agent screens to
 * `AppPreferencesManager`, and the default country screen to `LocationStorage` by way of the main
 * screen's `CountrySetting`. Nothing on these screens needs a connection, which is why this
 * journey can run beside the others with networking disabled: the connectivity gate of TASK-049
 * sits on the browse list, not on the overflow menu.
 *
 * Leaving a screen is part of the journey rather than cleanup, because the user agent screen
 * stores what was typed only when it is left, and because the buffering screen stores the four
 * values only on Save.
 *
 * Two settings on the Settings screen are deliberately left alone. The Bluetooth auto-play switch
 * asks for a runtime permission on API 31 and above and would put a system prompt over the
 * Activity this journey reads. The master volume is covered by `SettingsModelTest` and
 * `OpenRadioServiceCommandTest` instead.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceJourneyTest {

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

    /**
     * The settings this journey changes live in files `ServiceStorages.clear` does not own, so
     * they have to be wiped here rather than left for whatever runs next. A chosen country alone
     * would change the last row of the root menu for every journey after this one.
     */
    @After
    fun tearDown() {
        mProfile.clearApplicationData()
        mProfile.finish()
    }

    /**
     * What a user changes on the Settings screen and its sub-screens is what gets stored.
     *
     * The defaults are asserted first. Without that, a value that was never written would be
     * indistinguishable from one that was written and happens to match, and every case below
     * builds on this one having changed something real.
     */
    @Test
    fun changingEverySettingStoresWhatTheUserChose() {
        assertTheDefaultsAreInPlace()

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())

            changeEverySetting(settings)
        }

        assertTheChangedSettingsAreStored()
    }

    /**
     * The journey's subject: Android takes the Activity down and builds it again, and the settings
     * come back up.
     *
     * Every screen is left before the recreate, so what the reopened screens show can only have
     * come from the stores. A recreate with a screen still open would show that screen's state
     * holder's draft instead, which would pass whether or not anything had been written; the
     * buffering cases below cover that half separately and on purpose.
     */
    @Test
    fun theChangedSettingsComeBackUpAfterTheActivityIsRecreated() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            changeEverySetting(settings)
            assertNull(
                "A settings screen was still open, so the recreate would show its state holder's " +
                    "draft instead of making the application read the stores",
                onMainThread { registrySettings().screens.top.value }
            )

            val before = activityIdentity(scenario)
            scenario.recreate()
            assertActivityWasRebuilt(scenario, before)

            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            readEverySettingBack(settings)
        }

        assertTheChangedSettingsAreStored()
    }

    /**
     * A recreate never leaves the process, so on its own it cannot say whether a value would
     * survive the application being started again. Every store here writes with
     * [android.content.SharedPreferences.Editor.apply], which makes a value readable through this
     * process long before it is anywhere a restart could find it, so the file on disk is what has
     * to be asserted.
     *
     * Running the journey against a genuinely new process is TASK-031.
     */
    @Test
    fun theChangedSettingsReachDiskRatherThanOnlyTheRunningProcess() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            changeEverySetting(settings)
        }

        mProfile.awaitStoredOnDisk(NETWORK_PREFERENCE_FILE, "\"IS_USE_MOBILE\" value=\"false\"")
        mProfile.awaitStoredOnDisk(APP_PREFERENCE_FILE, "\"PREFS_KEY_MIN_BUFFER\" value=\"$MIN_BUFFER\"")
        mProfile.awaitStoredOnDisk(APP_PREFERENCE_FILE, "\"PREFS_KEY_MAX_BUFFER\" value=\"$MAX_BUFFER\"")
        mProfile.awaitStoredOnDisk(
            APP_PREFERENCE_FILE, "\"PREFS_KEY_BUFFER_FOR_PLAYBACK\" value=\"$PLAY_BUFFER\""
        )
        mProfile.awaitStoredOnDisk(
            APP_PREFERENCE_FILE,
            "\"PREFS_KEY_BUFFER_FOR_REBUFFER_PLAYBACK\" value=\"$REBUFFER_BUFFER\""
        )
        mProfile.awaitStoredOnDisk(APP_PREFERENCE_FILE, "\"START_PLAYBACK\">NEVER<")
        mProfile.awaitStoredOnDisk(APP_PREFERENCE_FILE, "\"IS_CUSTOM_USER_AGENT\" value=\"true\"")
        mProfile.awaitStoredOnDisk(APP_PREFERENCE_FILE, ">${CUSTOM_USER_AGENT}<")
        mProfile.awaitStoredOnDisk(LOCATION_PREFERENCE_FILE, ">${CHOSEN_COUNTRY_CODE}<")
    }

    /**
     * The other half of the recreate: the user is still on the buffering screen when Android
     * rebuilds the Activity, which is what a rotation does.
     *
     * The screen comes back with what was typed, because its state holder survives the Activity,
     * and nothing is stored, because only Save stores.
     */
    @Test
    fun recreatingTheActivityOnTheBufferingScreenKeepsWhatTheUserTypedAndStoresNothing() {
        assertTheDefaultsAreInPlace()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            settings.openFromOverflow()
            settings.open(SettingsTags.STREAM_BUFFERING, SettingsTags.BUFFERING_SCREEN)
            typeTheBufferValues(settings)

            val before = activityIdentity(scenario)
            scenario.recreate()
            assertActivityWasRebuilt(scenario, before)

            settings.await(SettingsTags.BUFFERING_SCREEN)
            assertTheBufferFieldsRead(settings, "after the recreate")
            assertEquals(
                "Recreating the Activity stored the minimum buffer, which only Save does",
                AppPreferencesManager.DEFAULT_MIN_BUFFER_MS,
                AppPreferencesManager.getMinBuffer(mContext)
            )
            settings.click(SettingsTags.CLOSE)
            settings.await(SettingsTags.SCREEN)
            settings.back(MainScreenTags.TABS)
        }
    }

    /**
     * ✕ leaves the buffering screen without storing, whatever the fields hold.
     */
    @Test
    fun closingTheBufferingScreenStoresNothing() {
        assertTheDefaultsAreInPlace()

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            settings.openFromOverflow()
            settings.open(SettingsTags.STREAM_BUFFERING, SettingsTags.BUFFERING_SCREEN)
            typeTheBufferValues(settings)
            settings.click(SettingsTags.CLOSE)
            settings.await(SettingsTags.SCREEN)
            settings.assertValue(SettingsTags.STREAM_BUFFERING, "DEFAULT")
            settings.back(MainScreenTags.TABS)
        }

        assertEquals(
            "✕ stored the minimum buffer",
            AppPreferencesManager.DEFAULT_MIN_BUFFER_MS,
            AppPreferencesManager.getMinBuffer(mContext)
        )
        assertEquals(
            "✕ stored the maximum buffer",
            AppPreferencesManager.DEFAULT_MAX_BUFFER_MS,
            AppPreferencesManager.getMaxBuffer(mContext)
        )
        assertEquals(
            "✕ stored the playback buffer",
            AppPreferencesManager.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            AppPreferencesManager.getPlayBuffer(mContext)
        )
        assertEquals(
            "✕ stored the rebuffer buffer",
            AppPreferencesManager.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            AppPreferencesManager.getPlayBufferRebuffer(mContext)
        )
    }

    /**
     * Opens Settings, changes what each screen offers, and goes back to the main screen.
     */
    private fun changeEverySetting(settings: SettingsView) {
        settings.openFromOverflow()
        assertTrue(
            "Settings did not open on the stored mobile data value",
            settings.isOn(SettingsTags.MOBILE_DATA)
        )
        settings.click(SettingsTags.MOBILE_DATA)

        settings.open(SettingsTags.STREAM_BUFFERING, SettingsTags.BUFFERING_SCREEN)
        typeTheBufferValues(settings)
        settings.click(SettingsTags.SAVE)
        settings.await(SettingsTags.SCREEN)
        awaitStoredBuffers()

        settings.assertValue(SettingsTags.START_PLAYBACK, "IF PLAYING")
        settings.open(SettingsTags.START_PLAYBACK, SettingsTags.CHOICE_SCREEN)
        assertTrue(
            "Start playback did not open on the stored value",
            settings.isChosen(StartPlayback.IF_PLAYING.name)
        )
        settings.choose(StartPlayback.NEVER.name)
        settings.await(SettingsTags.SCREEN)

        settings.open(SettingsTags.USER_AGENT, SettingsTags.USER_AGENT_SCREEN)
        assertFalse(
            "A custom user agent was already switched on",
            settings.isOn(SettingsTags.USER_AGENT_SWITCH)
        )
        settings.click(SettingsTags.USER_AGENT_SWITCH)
        settings.type(SettingsTags.USER_AGENT_FIELD, CUSTOM_USER_AGENT)
        settings.back(SettingsTags.SCREEN)

        settings.open(SettingsTags.DEFAULT_COUNTRY, SettingsTags.CHOICE_SCREEN)
        settings.choose(CHOSEN_COUNTRY_CODE)
        settings.await(SettingsTags.SCREEN)
        awaitChosenCountry()

        settings.back(MainScreenTags.TABS)
    }

    /**
     * Reads every changed setting back off the screens, which is what a user would see after the
     * recreate.
     */
    private fun readEverySettingBack(settings: SettingsView) {
        settings.openFromOverflow()
        assertFalse(
            "Settings offers mobile data again after the recreate",
            settings.isOn(SettingsTags.MOBILE_DATA)
        )
        settings.assertValue(SettingsTags.STREAM_BUFFERING, "CUSTOM")
        settings.open(SettingsTags.STREAM_BUFFERING, SettingsTags.BUFFERING_SCREEN)
        assertTheBufferFieldsRead(settings, "after the recreate")
        settings.click(SettingsTags.CLOSE)
        settings.await(SettingsTags.SCREEN)
        settings.assertValue(SettingsTags.START_PLAYBACK, "NEVER")
        settings.assertValue(SettingsTags.USER_AGENT, "CUSTOM")
        settings.open(SettingsTags.USER_AGENT, SettingsTags.USER_AGENT_SCREEN)
        assertTrue(
            "The custom user agent is switched off again after the recreate",
            settings.isOn(SettingsTags.USER_AGENT_SWITCH)
        )
        assertEquals(CUSTOM_USER_AGENT, settings.text(SettingsTags.USER_AGENT_FIELD))
        settings.back(SettingsTags.SCREEN)
        settings.assertValue(SettingsTags.DEFAULT_COUNTRY, CHOSEN_COUNTRY_CODE)
        settings.back(MainScreenTags.TABS)
    }

    private fun typeTheBufferValues(settings: SettingsView) {
        settings.type(SettingsTags.bufferField(BufferField.MIN), MIN_BUFFER.toString())
        settings.type(SettingsTags.bufferField(BufferField.MAX), MAX_BUFFER.toString())
        settings.type(SettingsTags.bufferField(BufferField.PLAY), PLAY_BUFFER.toString())
        settings.type(SettingsTags.bufferField(BufferField.REBUFFER), REBUFFER_BUFFER.toString())
    }

    private fun assertTheBufferFieldsRead(settings: SettingsView, occasion: String) {
        assertEquals(
            "A different minimum buffer $occasion",
            MIN_BUFFER.toString(),
            settings.text(SettingsTags.bufferField(BufferField.MIN))
        )
        assertEquals(
            "A different maximum buffer $occasion",
            MAX_BUFFER.toString(),
            settings.text(SettingsTags.bufferField(BufferField.MAX))
        )
        assertEquals(
            "A different playback buffer $occasion",
            PLAY_BUFFER.toString(),
            settings.text(SettingsTags.bufferField(BufferField.PLAY))
        )
        assertEquals(
            "A different rebuffer buffer $occasion",
            REBUFFER_BUFFER.toString(),
            settings.text(SettingsTags.bufferField(BufferField.REBUFFER))
        )
    }

    private fun assertTheDefaultsAreInPlace() {
        assertTrue(
            "This journey starts from a cleared profile, and mobile data was already refused",
            networkSettings().getUseMobile()
        )
        assertEquals(
            "This journey starts from a cleared profile, and the minimum buffer was already changed",
            AppPreferencesManager.DEFAULT_MIN_BUFFER_MS,
            AppPreferencesManager.getMinBuffer(mContext)
        )
        assertEquals(
            "This journey starts from a cleared profile, and the maximum buffer was already changed",
            AppPreferencesManager.DEFAULT_MAX_BUFFER_MS,
            AppPreferencesManager.getMaxBuffer(mContext)
        )
        assertEquals(
            "This journey starts from a cleared profile, and the playback buffer was already changed",
            AppPreferencesManager.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            AppPreferencesManager.getPlayBuffer(mContext)
        )
        assertEquals(
            "This journey starts from a cleared profile, and the rebuffer buffer was already changed",
            AppPreferencesManager.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            AppPreferencesManager.getPlayBufferRebuffer(mContext)
        )
        assertEquals(
            "This journey starts from a cleared profile, and the start playback choice was already changed",
            StartPlayback.IF_PLAYING,
            AppPreferencesManager.startPlayback(mContext)
        )
        assertFalse(
            "This journey starts from a cleared profile, and a custom user agent was already on",
            AppPreferencesManager.isCustomUserAgent(mContext)
        )
        assertEquals(
            "This journey starts from a cleared profile, and a country was already chosen",
            Country.COUNTRY_CODE_DEFAULT,
            locationSettings().getCountryCode()
        )
    }

    private fun assertTheChangedSettingsAreStored() {
        assertFalse(
            "Settings did not store the refusal of mobile data",
            networkSettings().getUseMobile()
        )
        assertEquals(
            "The buffering screen did not store the minimum buffer",
            MIN_BUFFER,
            AppPreferencesManager.getMinBuffer(mContext)
        )
        assertEquals(
            "The buffering screen did not store the maximum buffer",
            MAX_BUFFER,
            AppPreferencesManager.getMaxBuffer(mContext)
        )
        assertEquals(
            "The buffering screen did not store the playback buffer",
            PLAY_BUFFER,
            AppPreferencesManager.getPlayBuffer(mContext)
        )
        assertEquals(
            "The buffering screen did not store the rebuffer buffer",
            REBUFFER_BUFFER,
            AppPreferencesManager.getPlayBufferRebuffer(mContext)
        )
        assertEquals(
            "Settings did not store the start playback choice",
            StartPlayback.NEVER,
            AppPreferencesManager.startPlayback(mContext)
        )
        assertTrue(
            "The user agent screen did not store the custom user agent being switched on",
            AppPreferencesManager.isCustomUserAgent(mContext)
        )
        assertEquals(
            "The user agent screen did not store the user agent that was typed",
            CUSTOM_USER_AGENT,
            AppPreferencesManager.getCustomUserAgent(mContext, NO_USER_AGENT_STORED)
        )
        assertEquals(
            "Settings did not store the chosen country",
            CHOSEN_COUNTRY_CODE,
            locationSettings().getCountryCode()
        )
    }

    /**
     * The recreate is the subject of this journey, so a run in which Android handed the same
     * Activity back would prove nothing while still passing every assertion after it.
     */
    private fun assertActivityWasRebuilt(scenario: ActivityScenario<MainActivity>, before: Int) {
        assertNotEquals(
            "The Activity was not rebuilt, so nothing read after this says anything about a recreate",
            before,
            activityIdentity(scenario)
        )
    }

    private fun activityIdentity(scenario: ActivityScenario<MainActivity>): Int {
        return scenario.withActivity { System.identityHashCode(it) }
    }

    /**
     * Waits for the buffer values to reach the store.
     *
     * Save stores all four together, so reading them back is what says whether the screen has
     * left yet.
     */
    private fun awaitStoredBuffers() {
        awaitStored("the buffer values") {
            AppPreferencesManager.getMinBuffer(mContext) == MIN_BUFFER &&
                AppPreferencesManager.getMaxBuffer(mContext) == MAX_BUFFER &&
                AppPreferencesManager.getPlayBuffer(mContext) == PLAY_BUFFER &&
                AppPreferencesManager.getPlayBufferRebuffer(mContext) == REBUFFER_BUFFER
        }
    }

    /**
     * Waits for the chosen country to reach the store.
     *
     * The choice screen reports the tap at once, but the write goes through `CountrySetting`, so
     * there is nothing synchronous to read.
     */
    private fun awaitChosenCountry() {
        awaitStored("the chosen country") {
            locationSettings().getCountryCode() == CHOSEN_COUNTRY_CODE
        }
    }

    private fun awaitStored(what: String, stored: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STORE_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (stored()) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("$what did not reach the store within $STORE_TIMEOUT_SECONDS seconds")
    }

    /**
     * A storage of its own rather than the registry's, so that what is asserted is what the file
     * holds rather than anything an instance the application is using might be keeping.
     */
    private fun networkSettings(): NetworkSettingsStorage {
        return NetworkSettingsStorage(WeakReference(mContext))
    }

    private fun locationSettings(): LocationStorage {
        return LocationStorage(WeakReference(mContext))
    }

    private companion object {

        /**
         * The four buffer values the buffering screen validates together: each one inside the
         * range its description quotes, the maximum no smaller than the minimum, and the minimum
         * no smaller than either playback buffer. They all differ from the Media3 defaults, so a
         * value that was never written cannot pass for one that was.
         */
        const val MIN_BUFFER = 30000

        const val MAX_BUFFER = 60000

        const val PLAY_BUFFER = 2000

        const val REBUFFER_BUFFER = 3000

        const val CUSTOM_USER_AGENT = "FreeNetRadio journey agent"

        /**
         * Read back with this as the fallback, so a store that holds nothing reads as that rather
         * than as holding the device's own user agent.
         */
        const val NO_USER_AGENT_STORED = "no user agent was stored"

        /**
         * Anything but [Country.COUNTRY_CODE_DEFAULT], and not the "use location" entry the
         * country screen carries first: choosing that one makes the Activity ask for the location
         * permission when it resumes.
         */
        const val CHOSEN_COUNTRY_CODE = "PL"

        const val APP_PREFERENCE_FILE = "OpenRadioPref"

        const val NETWORK_PREFERENCE_FILE = "NetworkSettingsStorage"

        const val LOCATION_PREFERENCE_FILE = "LocationPref"

        /**
         * Covers the store write and the screen transition that starts it.
         */
        const val STORE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L
    }
}
