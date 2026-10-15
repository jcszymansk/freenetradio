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
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.storage.makeStation
import com.yuriy.openradio.shared.service.location.LocationService
import com.yuriy.openradio.shared.view.dialog.GeneralSettingsDialog
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The country the user picks in the general dialog reaches the root menu, whichever node the user
 * is standing in when picking it.
 *
 * The root is the one node whose rows depend on the stored country: `MediaItemRoot` titles its
 * country stations row after it. The service caches the root it built, so a country change has to
 * drop that copy, or the root keeps the old country's row for as long as the service lives. Both
 * cases assert the row the user sees on the root, which is what the cache would get wrong.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class CountryChangeJourneyTest {

    @get:Rule
    val mStack = PresenterStackRule()

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
     * Picking a country while the root is on screen rewrites the root's country row in place.
     */
    @Test
    fun choosingACountryAtTheRootRetitlesItsCountryRow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            val dialogs = JourneyDialogs(scenario)
            list.awaitRows("the root of a cleared profile") { it == mProfile.cleanInstallRoot() }

            val general = dialogs.openFromDrawer(R.id.nav_general, GENERAL_TAG)
            dialogs.chooseCountry(general, CHOSEN_COUNTRY_CODE)
            awaitChosenCountry()
            dialogs.dismiss(GENERAL_TAG)

            val expected = rootFor(CHOSEN_COUNTRY_CODE)
            assertEquals(
                "The root still offers the country it had before the change",
                expected,
                list.awaitRows("the root with the chosen country") { it == expected }
            )
        }
    }

    /**
     * Picking a country while another node is on screen leaves that node alone, and the root the
     * user walks back to carries the new country.
     *
     * The locals list is the node walked into because it is the one a device with networking
     * disabled can render.
     */
    @Test
    fun choosingACountryInsideAnotherNodeRetitlesTheRootOnTheWayBack() {
        val locals = mProfile.storages.locals
        locals.add(makeStation(locals.getId(), name = STATION_NAME, isLocal = true))
        mProfile.refreshTree()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            val dialogs = JourneyDialogs(scenario)
            val navigation = JourneyNavigation(scenario)
            list.awaitRows("the root with the locals node") {
                it == mProfile.cleanInstallRoot() + mProfile.localsRow()
            }

            try {
                navigation.open(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
                list.awaitRendered(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST, 1)

                val general = dialogs.openFromDrawer(R.id.nav_general, GENERAL_TAG)
                dialogs.chooseCountry(general, CHOSEN_COUNTRY_CODE)
                awaitChosenCountry()
                dialogs.dismiss(GENERAL_TAG)
                assertEquals(
                    "Choosing a country moved the user out of the node they were standing in",
                    MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST,
                    navigation.currentCategory()
                )
            } finally {
                navigation.returnToRoot()
            }

            val expected = rootFor(CHOSEN_COUNTRY_CODE) + mProfile.localsRow()
            assertEquals(
                "The root walked back to still offers the country it had before the change",
                expected,
                list.awaitRows("the root with the chosen country") { it == expected }
            )
        }
    }

    /**
     * Waits for the chosen country to reach the store, so that a root still showing the old
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

    /**
     * @return the root of a cleared profile once [countryCode] has been chosen: the same rows,
     *   with the country stations row titled after that country.
     */
    private fun rootFor(countryCode: String): List<BrowseRow> {
        return mProfile.cleanInstallRoot().map { row ->
            if (row.mediaId == MediaId.MEDIA_ID_COUNTRY_STATIONS) {
                BrowseRow(row.mediaId, LocationService.COUNTRY_CODE_TO_NAME.getValue(countryCode))
            } else {
                row
            }
        }
    }

    private companion object {

        val GENERAL_TAG = GeneralSettingsDialog.DIALOG_TAG

        /**
         * Anything but the default country, and not the "use location" entry the spinner carries
         * first: choosing that one makes the Activity ask for the location permission when it
         * resumes.
         */
        const val CHOSEN_COUNTRY_CODE = "PL"

        const val STATION_NAME = "Country change local"

        const val STORE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L
    }
}
