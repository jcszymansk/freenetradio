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
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import com.yuriy.openradio.mobile.view.main.OverflowItem
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.presenter.main.SectionKind
import com.yuriy.openradio.shared.presenter.main.Tab
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.service.location.LocationService
import com.yuriy.openradio.shared.view.dialog.GeneralSettingsDialog
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The country the user picks in the general dialog reaches the COUNTRY tab's "Your country"
 * section, whichever tab the user is on when picking it.
 *
 * The section's row is the service root's country stations row, which `MediaItemRoot` titles
 * after the stored country. The service caches the root it built, so a country change has to drop
 * that copy and the screen has to read the root again, or the section keeps the old country for
 * as long as the service lives. Both cases assert the row the user sees under "Your country",
 * which is what the cache would get wrong.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CountryChangeJourneyTest {

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
     * The chosen country lives in a file `ServiceStorages.clear` does not own, and it changes the
     * root of every journey after this one, so it is wiped here.
     */
    @After
    fun tearDown() {
        mProfile.clearApplicationData()
        mProfile.finish()
    }

    /**
     * Picking a country while COUNTRY is on screen rewrites its "Your country" row in place.
     */
    @Test
    fun choosingACountryShowsItUnderYourCountry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val dialogs = JourneyDialogs(scenario, mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())

            try {
                navigation.selectTab(Tab.COUNTRY)
                awaitYourCountry(view, Country.COUNTRY_CODE_DEFAULT)

                chooseTheCountry(dialogs)

                awaitYourCountry(view, CHOSEN_COUNTRY_CODE)
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    /**
     * Picking a country while another tab is on screen leaves the user on that tab, and COUNTRY
     * carries the new country once the user switches to it.
     *
     * MINE is the tab chosen from because a device with networking disabled can render it.
     */
    @Test
    fun choosingACountryOnAnotherTabShowsItOnCountry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val dialogs = JourneyDialogs(scenario, mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())

            try {
                navigation.selectTab(Tab.MINE)
                view.awaitEmptyLine(mContext.getString(R.string.main_empty_stations))

                chooseTheCountry(dialogs)
                assertEquals(
                    "Choosing a country moved the user off the tab they were on",
                    Tab.MINE,
                    navigation.snapshot().selectedTab
                )

                navigation.selectTab(Tab.COUNTRY)
                awaitYourCountry(view, CHOSEN_COUNTRY_CODE)
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    private fun chooseTheCountry(dialogs: JourneyDialogs) {
        val general = dialogs.openFromOverflow(OverflowItem.GENERAL, GENERAL_TAG)
        dialogs.chooseCountry(general, CHOSEN_COUNTRY_CODE)
        awaitChosenCountry()
        dialogs.dismiss(GENERAL_TAG)
    }

    /**
     * Waits for COUNTRY's first row, the one under "Your country", to be titled after
     * [countryCode], and checks that the section heading it is shown.
     */
    private fun awaitYourCountry(view: MainScreenView, countryCode: String) {
        val expected = BrowseRow(
            MediaId.MEDIA_ID_COUNTRY_STATIONS,
            LocationService.COUNTRY_CODE_TO_NAME.getValue(countryCode)
        )
        view.awaitRows("$expected under Your country") { rows -> rows.first() == expected }
        assertTrue(
            "COUNTRY lists the user's country without the Your country heading. " + view.describe(),
            view.isShown(MainScreenTags.section(SectionKind.YOUR_COUNTRY))
        )
    }

    /**
     * Waits for the chosen country to reach the store, so that a section still showing the old
     * country means the root was not rebuilt rather than that the spinner never reported the
     * selection. A spinner reports it from its next layout pass, not from the call that set it.
     */
    private fun awaitChosenCountry() {
        val storage = LocationStorage(WeakReference(mContext))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STORE_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (storage.getCountryCode() == CHOSEN_COUNTRY_CODE) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("The chosen country did not reach the store within $STORE_TIMEOUT_SECONDS seconds")
    }

    private companion object {

        val GENERAL_TAG = GeneralSettingsDialog.DIALOG_TAG

        /**
         * Anything but the default country, and not the "use location" entry the spinner carries
         * first: choosing that one makes the Activity ask for the location permission when it
         * resumes.
         */
        const val CHOSEN_COUNTRY_CODE = "PL"

        const val STORE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L
    }
}
