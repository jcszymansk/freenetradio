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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.storage.makeStation
import com.yuriy.openradio.shared.presenter.main.Tab
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first phone journey: what a user sees on a first run, with the application data cleared and
 * the device offline.
 *
 * This is deliberately the whole stack rather than the service alone. The screen is read through
 * the Compose test tags the user's screen carries, so a node the service serves but the UI never
 * renders fails here. What the service answers for the same node is `OpenRadioServiceBrowseTest`'s
 * subject and is not repeated.
 *
 * Nothing FAV and MINE show needs a network: they list the favorites and locals stores, which a
 * cleared profile holds empty. The other tabs fetch, which is why this journey stays on those two.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ColdLaunchJourneyTest {

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

    @After
    fun tearDown() {
        mProfile.finish()
    }

    /**
     * A first run opens on FAV with every tab the bound provider offers. Nothing is stored, so FAV
     * says it has no favourites rather than showing an empty list or an error; nothing on that
     * screen needs the network.
     */
    @Test
    fun aColdLaunchShowsTheTabsWithNothingStoredAndNoNetwork() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val view = MainScreenView(mCompose)

            view.awaitTabs(mProfile.cleanInstallTabs())
            mCompose.onNode(hasAnyAncestor(hasTestTag(MainScreenTags.TABS)) and hasText(MainScreenView.label(Tab.FAV)))
                .assertIsSelected()
            view.awaitEmptyLine(mContext.getString(R.string.main_empty_favorites))
        }
    }

    /**
     * An offline first run has nothing to show on FAV, and the screen has to arrive at saying so
     * with the spinner down: a spinner left up would tell a new user the application is stuck.
     *
     * Adding a station by hand is the only thing a user with no connection can actually do here,
     * and the add button belongs to MINE's root alone, so it is absent on FAV and offered on MINE.
     */
    @Test
    fun aColdLaunchSettlesAndOffersAddOnMineOnly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitEmptyLine(mContext.getString(R.string.main_empty_favorites))

            assertFalse(
                "The progress bar is still up after FAV settled. " + view.describe(),
                view.isShown(MainScreenTags.PROGRESS)
            )
            assertFalse(
                "FAV offers to add a station, which is MINE's. " + view.describe(),
                view.isShown(MainScreenTags.FAB_ADD)
            )

            try {
                navigation.selectTab(Tab.MINE)
                view.awaitEmptyLine(mContext.getString(R.string.main_empty_stations))
                assertTrue(
                    "MINE hid the only way to add a station without a connection. " + view.describe(),
                    view.isShown(MainScreenTags.FAB_ADD)
                )
            } finally {
                navigation.returnToRoots()
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
        val clearedIds = mProfile.browser.mediaIds(MediaId.MEDIA_ID_ROOT)
        assertFalse("The cleared favorite still has a node", clearedIds.contains(MediaId.MEDIA_ID_FAVORITES_LIST))
        assertFalse(
            "The cleared local station still has a node",
            clearedIds.contains(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitEmptyLine(mContext.getString(R.string.main_empty_favorites))
            try {
                navigation.selectTab(Tab.MINE)
                view.awaitEmptyLine(mContext.getString(R.string.main_empty_stations))
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    private companion object {

        const val FAVORITE_STATION_ID = "cold-launch-favorite"
    }
}
