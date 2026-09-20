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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.view.dialog.GeneralSettingsDialog
import com.yuriy.openradio.shared.view.dialog.NetworkDialog
import com.yuriy.openradio.shared.view.dialog.StreamBufferingDialog
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.yuriy.openradio.shared.R as DialogR

/**
 * The fifth phone journey: what the user changed in the settings is still what the application
 * offers after Android has torn the Activity down and built it again.
 *
 * The three dialogs the navigation drawer opens for settings are driven the way a user drives
 * them, through the widgets themselves, and they write to three different stores: the network
 * dialog to `NetworkSettingsStorage`, the buffering dialog and two thirds of the general dialog to
 * `AppPreferencesManager`, and the general dialog's country spinner to `LocationStorage` by way of
 * the presenter. None of them needs a connection, which is why this journey can run beside the
 * others with networking disabled: the connectivity gate of TASK-049 sits on the browse list, not
 * on the drawer.
 *
 * Two of the writes only happen when the dialog is left, so leaving it is part of the journey
 * rather than cleanup. The buffering dialog writes all four values in `onPause` after validating
 * them together, and the general dialog writes the custom user agent there too.
 *
 * Two settings on those dialogs are deliberately left alone. The Bluetooth auto-play box asks for
 * a runtime permission on API 31 and above and would put a system prompt over the Activity this
 * journey reads. The master volume bar persists only from `onStopTrackingTouch`, which a
 * programmatic progress never fires, so driving it would mean calling the listener rather than
 * using the widget; `OpenRadioServiceCommandTest` covers the command it sends instead.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceJourneyTest {

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
     * What a user changes is what gets stored: one setting in the network dialog, all four in the
     * buffering dialog, and three in the general dialog.
     *
     * The defaults are asserted first. Without that, a value that was never written would be
     * indistinguishable from one that was written and happens to match, and every case below
     * builds on this one having changed something real.
     */
    @Test
    fun changingTheNetworkBufferingAndGeneralSettingsStoresWhatTheUserTyped() {
        assertTheDefaultsAreInPlace()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialogs = JourneyDialogs(scenario)
            BrowseListView(scenario).awaitRows()

            changeEverySetting(scenario, dialogs)
        }

        assertTheChangedSettingsAreStored()
    }

    /**
     * The journey's subject: Android takes the Activity down and builds it again, and the settings
     * come back up.
     *
     * Every dialog is left before the recreate, so what the reopened dialogs show can only have
     * come from the stores. Recreating with a dialog still up would restore the dialog's own view
     * state, which would pass whether or not anything had been written, and
     * [recreatingTheActivityWithTheBufferingDialogUpStoresWhatTheUserHadTyped] covers that half
     * separately and on purpose.
     */
    @Test
    fun theChangedSettingsComeBackUpInTheDialogsAfterTheActivityIsRecreated() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialogs = JourneyDialogs(scenario)
            BrowseListView(scenario).awaitRows()
            changeEverySetting(scenario, dialogs)
            assertTrue(
                "A dialog was still up, so the recreate would restore its views instead of " +
                    "making the application read the stores",
                dialogs.noneIsUp()
            )

            val before = activityIdentity(scenario)
            scenario.recreate()
            assertActivityWasRebuilt(scenario, before)

            BrowseListView(scenario).awaitRows()
            readEverySettingBackFromTheDialogs(scenario, dialogs)
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialogs = JourneyDialogs(scenario)
            BrowseListView(scenario).awaitRows()
            changeEverySetting(scenario, dialogs)
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
        mProfile.awaitStoredOnDisk(
            APP_PREFERENCE_FILE, "\"LAST_KNOWN_RADIO_STATION_ENABLED\" value=\"false\""
        )
        mProfile.awaitStoredOnDisk(APP_PREFERENCE_FILE, "\"IS_CUSTOM_USER_AGENT\" value=\"true\"")
        mProfile.awaitStoredOnDisk(APP_PREFERENCE_FILE, ">${CUSTOM_USER_AGENT}<")
        mProfile.awaitStoredOnDisk(LOCATION_PREFERENCE_FILE, ">${CHOSEN_COUNTRY_CODE}<")
    }

    /**
     * The other half of the recreate: the user is still in the buffering dialog when Android
     * rebuilds the Activity, which is what a rotation does.
     *
     * The dialog only writes in `onPause`, and the recreate is what runs it, so this is the path
     * on which what the user typed reaches the store without the user ever closing anything. The
     * rebuilt dialog then has to show those values rather than the defaults it started from.
     */
    @Test
    fun recreatingTheActivityWithTheBufferingDialogUpStoresWhatTheUserHadTyped() {
        assertTheDefaultsAreInPlace()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialogs = JourneyDialogs(scenario)
            BrowseListView(scenario).awaitRows()
            typeTheBufferValues(scenario, dialogs, dialogs.openFromDrawer(R.id.nav_buffering, BUFFERING_TAG))

            val before = activityIdentity(scenario)
            scenario.recreate()
            assertActivityWasRebuilt(scenario, before)

            awaitStoredBuffers()
            BrowseListView(scenario).awaitRows()
            assertTheBufferFieldsRead(
                scenario, dialogs, dialogs.awaitDialog(BUFFERING_TAG), "after the recreate"
            )
        }
    }

    /**
     * Opens each of the three dialogs in turn, changes what it offers and leaves it again.
     */
    private fun changeEverySetting(
        scenario: ActivityScenario<MainActivity>,
        dialogs: JourneyDialogs
    ) {
        val network = dialogs.openFromDrawer(R.id.nav_network, NETWORK_TAG)
        scenario.onActivity {
            val useMobile = dialogs.field<CheckBox>(network, DialogR.id.use_mobile_network_check_box)
            assertTrue("The network dialog did not open on the stored value", useMobile.isChecked)
            useMobile.performClick()
        }
        dialogs.dismiss(NETWORK_TAG)

        typeTheBufferValues(scenario, dialogs, dialogs.openFromDrawer(R.id.nav_buffering, BUFFERING_TAG))
        dialogs.dismiss(BUFFERING_TAG)
        awaitStoredBuffers()

        val general = dialogs.openFromDrawer(R.id.nav_general, GENERAL_TAG)
        scenario.onActivity {
            val lastKnown = dialogs.field<CheckBox>(
                general, DialogR.id.settings_dialog_enable_last_known_radio_station_check_view
            )
            assertTrue("The general dialog did not open on the stored value", lastKnown.isChecked)
            lastKnown.performClick()

            val customUserAgent = dialogs.field<CheckBox>(general, DialogR.id.user_agent_check_view)
            assertFalse("A custom user agent was already switched on", customUserAgent.isChecked)
            customUserAgent.performClick()
            dialogs.field<EditText>(general, DialogR.id.user_agent_input_view).setText(CUSTOM_USER_AGENT)

            val countries = dialogs.field<Spinner>(general, DialogR.id.default_country_spinner)
            countries.setSelection(positionOf(countries, CHOSEN_COUNTRY_CODE))
        }
        awaitChosenCountry()
        dialogs.dismiss(GENERAL_TAG)
    }

    /**
     * Reads every changed setting back off the widgets of freshly opened dialogs, which is what a
     * user looking at the settings after the recreate would see.
     */
    private fun readEverySettingBackFromTheDialogs(
        scenario: ActivityScenario<MainActivity>,
        dialogs: JourneyDialogs
    ) {
        val network = dialogs.openFromDrawer(R.id.nav_network, NETWORK_TAG)
        scenario.onActivity {
            assertFalse(
                "The network dialog offers mobile data again after the recreate",
                dialogs.field<CheckBox>(network, DialogR.id.use_mobile_network_check_box).isChecked
            )
        }
        dialogs.dismiss(NETWORK_TAG)

        assertTheBufferFieldsRead(
            scenario, dialogs, dialogs.openFromDrawer(R.id.nav_buffering, BUFFERING_TAG),
            "after the recreate"
        )
        dialogs.dismiss(BUFFERING_TAG)

        val general = dialogs.openFromDrawer(R.id.nav_general, GENERAL_TAG)
        scenario.onActivity {
            assertFalse(
                "The general dialog offers the last known station again after the recreate",
                dialogs.field<CheckBox>(
                    general, DialogR.id.settings_dialog_enable_last_known_radio_station_check_view
                ).isChecked
            )
            assertTrue(
                "The custom user agent is switched off again after the recreate",
                dialogs.field<CheckBox>(general, DialogR.id.user_agent_check_view).isChecked
            )
            assertEquals(
                "The general dialog shows a different user agent after the recreate",
                CUSTOM_USER_AGENT,
                dialogs.field<EditText>(general, DialogR.id.user_agent_input_view).text.toString()
            )
            val countries = dialogs.field<Spinner>(general, DialogR.id.default_country_spinner)
            assertEquals(
                "The general dialog shows a different country after the recreate",
                CHOSEN_COUNTRY_CODE,
                (countries.selectedItem as Country).code
            )
        }
        dialogs.dismiss(GENERAL_TAG)
    }

    private fun typeTheBufferValues(
        scenario: ActivityScenario<MainActivity>,
        dialogs: JourneyDialogs,
        buffering: DialogFragment
    ) {
        scenario.onActivity {
            dialogs.field<EditText>(buffering, DialogR.id.min_buffer_edit_view)
                .setText(MIN_BUFFER.toString())
            dialogs.field<EditText>(buffering, DialogR.id.max_buffer_edit_view)
                .setText(MAX_BUFFER.toString())
            dialogs.field<EditText>(buffering, DialogR.id.play_buffer_edit_view)
                .setText(PLAY_BUFFER.toString())
            dialogs.field<EditText>(buffering, DialogR.id.play_buffer_after_rebuffer_edit_view)
                .setText(REBUFFER_BUFFER.toString())
        }
    }

    private fun assertTheBufferFieldsRead(
        scenario: ActivityScenario<MainActivity>,
        dialogs: JourneyDialogs,
        buffering: DialogFragment,
        occasion: String
    ) {
        scenario.onActivity {
            assertEquals(
                "The buffering dialog shows a different minimum buffer $occasion",
                MIN_BUFFER.toString(),
                dialogs.field<EditText>(buffering, DialogR.id.min_buffer_edit_view).text.toString()
            )
            assertEquals(
                "The buffering dialog shows a different maximum buffer $occasion",
                MAX_BUFFER.toString(),
                dialogs.field<EditText>(buffering, DialogR.id.max_buffer_edit_view).text.toString()
            )
            assertEquals(
                "The buffering dialog shows a different playback buffer $occasion",
                PLAY_BUFFER.toString(),
                dialogs.field<EditText>(buffering, DialogR.id.play_buffer_edit_view).text.toString()
            )
            assertEquals(
                "The buffering dialog shows a different rebuffer buffer $occasion",
                REBUFFER_BUFFER.toString(),
                dialogs.field<EditText>(
                    buffering, DialogR.id.play_buffer_after_rebuffer_edit_view
                ).text.toString()
            )
        }
    }

    private fun assertTheDefaultsAreInPlace() {
        assertTrue(
            "This journey starts from a cleared profile, and mobile data was already refused",
            networkSettings().getUseMobile()
        )
        assertEquals(
            "This journey starts from a cleared profile, and the minimum buffer was already changed",
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            AppPreferencesManager.getMinBuffer(mContext)
        )
        assertEquals(
            "This journey starts from a cleared profile, and the maximum buffer was already changed",
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            AppPreferencesManager.getMaxBuffer(mContext)
        )
        assertEquals(
            "This journey starts from a cleared profile, and the playback buffer was already changed",
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            AppPreferencesManager.getPlayBuffer(mContext)
        )
        assertEquals(
            "This journey starts from a cleared profile, and the rebuffer buffer was already changed",
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            AppPreferencesManager.getPlayBufferRebuffer(mContext)
        )
        assertTrue(
            "This journey starts from a cleared profile, and the last known station was already off",
            AppPreferencesManager.lastKnownRadioStationEnabled(mContext)
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
            "The network dialog did not store the refusal of mobile data",
            networkSettings().getUseMobile()
        )
        assertEquals(
            "The buffering dialog did not store the minimum buffer",
            MIN_BUFFER,
            AppPreferencesManager.getMinBuffer(mContext)
        )
        assertEquals(
            "The buffering dialog did not store the maximum buffer",
            MAX_BUFFER,
            AppPreferencesManager.getMaxBuffer(mContext)
        )
        assertEquals(
            "The buffering dialog did not store the playback buffer",
            PLAY_BUFFER,
            AppPreferencesManager.getPlayBuffer(mContext)
        )
        assertEquals(
            "The buffering dialog did not store the rebuffer buffer",
            REBUFFER_BUFFER,
            AppPreferencesManager.getPlayBufferRebuffer(mContext)
        )
        assertFalse(
            "The general dialog did not store the last known station being switched off",
            AppPreferencesManager.lastKnownRadioStationEnabled(mContext)
        )
        assertTrue(
            "The general dialog did not store the custom user agent being switched on",
            AppPreferencesManager.isCustomUserAgent(mContext)
        )
        assertEquals(
            "The general dialog did not store the user agent that was typed",
            CUSTOM_USER_AGENT,
            AppPreferencesManager.getCustomUserAgent(mContext, NO_USER_AGENT_STORED)
        )
        assertEquals(
            "The general dialog did not store the chosen country",
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
        val identity = AtomicInteger(0)
        scenario.onActivity { identity.set(System.identityHashCode(it)) }
        return identity.get()
    }

    /**
     * @return the position of the country [code] in the spinner the general dialog builds, which
     *   is sorted by name and carries a "use location" entry ahead of every real country.
     */
    private fun positionOf(spinner: Spinner, code: String): Int {
        for (position in 0 until spinner.adapter.count) {
            if ((spinner.adapter.getItem(position) as Country).code == code) {
                return position
            }
        }
        throw AssertionError("The country spinner offers no entry for '$code'")
    }

    /**
     * Waits for the buffer values to reach the store.
     *
     * The dialog writes them in `onPause`, which runs while it is being taken off the Activity,
     * so there is a transaction between the last thing this test did and the write.
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
     * A spinner reports a selection from its next layout pass rather than from the call that set
     * it, and the write then goes through the presenter, so there is nothing synchronous to read.
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

        val NETWORK_TAG = NetworkDialog.DIALOG_TAG

        val BUFFERING_TAG = StreamBufferingDialog.DIALOG_TAG

        val GENERAL_TAG = GeneralSettingsDialog.DIALOG_TAG

        /**
         * The four buffer values the dialog validates together: each one inside the range its
         * description quotes, the maximum no smaller than the minimum, and the minimum no smaller
         * than either playback buffer. They all differ from the Media3 defaults, so a value that
         * was never written cannot pass for one that was.
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
         * spinner carries first: choosing that one makes the Activity ask for the location
         * permission when it resumes.
         */
        const val CHOSEN_COUNTRY_CODE = "PL"

        const val APP_PREFERENCE_FILE = "OpenRadioPref"

        const val NETWORK_PREFERENCE_FILE = "NetworkSettingsStorage"

        const val LOCATION_PREFERENCE_FILE = "LocationPref"

        /**
         * Covers the fragment transaction a dialog is written from and the write itself.
         */
        const val STORE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L
    }
}
