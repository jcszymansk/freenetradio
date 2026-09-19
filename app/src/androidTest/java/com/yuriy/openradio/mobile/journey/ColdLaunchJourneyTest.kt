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
import android.view.View
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.storage.makeStation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first phone journey: what a user sees on a first run, with the application data cleared and
 * the device offline.
 *
 * This is deliberately the whole stack rather than the service alone. The list is read out of the
 * [RecyclerView] the user looks at, by adapter position, so an item the service serves but the UI
 * never renders fails here. What the service answers for the same node is
 * `OpenRadioServiceBrowseTest`'s subject and is not repeated.
 *
 * Nothing in the root menu needs a network: [com.yuriy.openradio.shared.model.media.item.MediaItemRoot]
 * reads the favorites and locals stores and builds static entries from them. Walking into any of
 * those entries does fetch, which is why this journey stays at the root.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ColdLaunchJourneyTest {

    private lateinit var mContext: Context

    private lateinit var mProfile: JourneyProfile

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mProfile = JourneyProfile(mContext)
        mProfile.start()
    }

    @After
    fun tearDown() {
        mProfile.finish()
    }

    @Test
    fun aColdLaunchListsTheCatalogueWithNothingStoredAndNoNetwork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(mProfile.cleanInstallRoot(), BrowseListView(scenario).awaitRows())
        }
    }

    /**
     * An offline first run has no stations to show under any of the entries it lists, and the app
     * has one screen that says so. Showing it at the root would tell a new user the application is
     * broken, so the list has to arrive with both the spinner and that message down.
     *
     * The add-station button belongs to the same screen state. The list callback shows it for the
     * root and hides it everywhere else, and adding a station by hand is the only thing a user
     * with no connection can actually do here, so the offline root has to keep offering it.
     */
    @Test
    fun aColdLaunchShowsTheAddButtonAndNeitherTheSpinnerNorTheNoDataMessage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            BrowseListView(scenario).awaitRows()

            scenario.onActivity { activity ->
                assertEquals(
                    "The progress bar is still up after the root list arrived",
                    View.GONE,
                    activity.findViewById<View>(R.id.progress_bar_view).visibility
                )
                assertEquals(
                    "The root list arrived and the no-data message is showing anyway",
                    View.GONE,
                    activity.findViewById<View>(R.id.no_data_view).visibility
                )
                assertEquals(
                    "The root list hid the only way to add a station without a connection",
                    View.VISIBLE,
                    activity.findViewById<View>(R.id.add_station_btn).visibility
                )
            }
        }
    }

    /**
     * Android's clear-data scenario, from the user's side: an app that has been used, then cleared,
     * has to come back up as a fresh install rather than with the stores it used to have.
     */
    @Test
    fun clearingTheApplicationDataTakesTheStoredNodesOutOfTheColdLaunch() {
        val storages = mProfile.storages
        val favorite = makeStation(FAVORITE_STATION_ID, name = "Favorite one")
        storages.favorites.add(favorite)
        storages.locals.add(
            makeStation(storages.locals.getId(), name = "Local one", isLocal = true)
        )
        storages.latest.add(favorite)
        mProfile.refreshTree()
        val storedIds = mProfile.browser.mediaIds(MediaId.MEDIA_ID_ROOT)
        assertTrue("The seeded favorite produced no node to clear", storedIds.contains(MediaId.MEDIA_ID_FAVORITES_LIST))
        assertTrue(
            "The seeded local station produced no node to clear",
            storedIds.contains(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )

        mProfile.assertEveryPreferenceFileIsEmpty(mProfile.clearApplicationData())
        mProfile.refreshTree()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(mProfile.cleanInstallRoot(), BrowseListView(scenario).awaitRows())
        }
    }

    private companion object {

        const val FAVORITE_STATION_ID = "cold-launch-favorite"
    }
}
