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
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.storage.makeStation
import com.yuriy.openradio.shared.presenter.main.Tab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The third phone journey: marking a station as a favorite and taking the mark away again.
 *
 * A favorite is a heart on a station's row, a command to the service, a line in
 * `FavoritesPreferences`, and the FAV tab's list. Those four have to agree, and the one of them a
 * user can see is the first. So what is asserted here is the rendered heart, the FAV list it
 * changes, and the store read back through an instance that has cached nothing -
 * `FavoritesStorage` answers `isFavorite` out of a map it fills as it goes, so the writer is the
 * one reader that cannot be trusted to say whether anything was written.
 *
 * The station the user marks is seeded into the locals store rather than typed into the add
 * dialog. Creating a station is TASK-006.02's subject, and with the device offline a station of
 * the user's own is the only kind there is: every other tab needs a provider.
 *
 * Every gesture here is the user's own: the heart is a toggle on the row, and FAV and MINE are
 * tabs, which open without a network. Only a row tap is gated on the connection
 * (`MainScreenModel.open`), and no case here taps a row.
 *
 * What the service does with `CMD_MARK_FAVORITE` and `CMD_UNMARK_FAVORITE` is
 * `OpenRadioServiceCommandTest`'s subject, and what it answers for the favorites node is
 * `OpenRadioServiceBrowseTest`'s. Neither of them renders anything.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class FavoriteLifecycleJourneyTest {

    @get:Rule
    val mStack = BrowseStacksRule()

    @get:Rule
    val mCompose = createEmptyComposeRule()

    private lateinit var mContext: Context

    private lateinit var mProfile: JourneyProfile

    private lateinit var mStation: RadioStation

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mProfile = JourneyProfile(mContext)
        mProfile.start()
        mStation = makeStation(
            mProfile.storages.locals.getId(), name = STATION_NAME, isLocal = true
        )
        mProfile.storages.locals.add(mStation)
        // The locals list is cached and the profile has just browsed the root to check which
        // provider is bound, so the seeded station would otherwise be missing from MINE.
        mProfile.refreshTree()
    }

    @After
    fun tearDown() {
        mProfile.finish()
    }

    /**
     * The first half of the lifecycle: the heart on the station's own row on MINE, and the FAV
     * list it fills.
     *
     * Nothing between the tap and the row appearing on FAV is done by the test. The heart sends
     * `CMD_MARK_FAVORITE`, the service writes the store, and FAV reads the store when it is shown
     * again. FAV is asserted empty first, which is what makes waiting for the row mean something.
     */
    @Test
    fun markingAStationFromItsRowPutsItOnFav() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                navigation.selectTab(Tab.FAV)
                view.awaitEmptyLine(mContext.getString(R.string.main_empty_favorites))

                markTheStationOnMine(view)

                navigation.selectTab(Tab.FAV)
                awaitTheStationOnFav(view, "Marking a station did not put it on FAV")
            } finally {
                navigation.returnToRoots()
            }
        }

        assertEquals(
            "A favorites store built from scratch does not hold the marked station",
            listOf(mStation.id),
            mProfile.storages.freshFavorites().getAll().map { it.id }
        )
    }

    /**
     * FAV has to show the station as what it is: under the name it was marked with, and with its
     * heart filled, which is bound from the item FAV read rather than from the row on MINE.
     */
    @Test
    fun favShowsTheStationTheUserMarkedWithAFilledHeart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                markTheStationOnMine(view)

                navigation.selectTab(Tab.FAV)

                awaitTheStationOnFav(view, "FAV does not show the station that was marked")
                view.awaitRowFavorite(mStation.id, true)
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    /**
     * The second half of the lifecycle, driven where a user would drive it: on FAV, on the row the
     * mark put there.
     *
     * Removing the only favorite empties the list the user is standing in. That is the half that
     * went wrong once: the service used to pick the node to notify from the one it last answered a
     * browse for, and named the favorites list only while the store still held something, so the
     * removed station stayed on screen (TASK-050).
     */
    @Test
    fun unmarkingOnFavEmptiesFav() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                markTheStationOnMine(view)
                navigation.selectTab(Tab.FAV)
                awaitTheStationOnFav(view, "FAV does not show the station that was marked")
                view.awaitRowFavorite(mStation.id, true)

                view.tapFavorite(mStation.id)

                mProfile.awaitFavorites(emptyList())
                view.awaitEmptyLine(mContext.getString(R.string.main_empty_favorites))
                assertEquals(
                    "The list that emptied is not FAV, where the station was unmarked",
                    Tab.FAV,
                    navigation.snapshot().selectedTab
                )
            } finally {
                navigation.returnToRoots()
            }
        }

        assertEquals(
            "FAV is empty but the station is still in the favorites store",
            emptyList<String>(),
            mProfile.storages.freshFavorites().getAll().map { it.id }
        )
    }

    /**
     * The mark has to outlive the screen that made it.
     *
     * Three things a restart depends on are asserted, each defeating a different way the mark
     * could appear to survive without having been stored: it reached the favorites preference file
     * on disk rather than only the copy Android keeps in memory for this process; a second
     * Activity shows it on FAV after the service's cached tree has been dropped, so what it
     * renders was built from the store rather than replayed; and a
     * [com.yuriy.openradio.shared.model.storage.FavoritesStorage] this test constructed reads it
     * back, so the answer cache the writer filled cannot be what is answering.
     *
     * What is not asserted is a real process restart, and it cannot be from here: the service
     * shares this process, so killing it takes the instrumentation with it. That is the whole
     * suite's limit rather than this journey's, and TASK-031 carries the decision that would lift
     * it.
     */
    @Test
    fun theMarkOutlivesTheActivityThatMadeIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                markTheStationOnMine(view)
            } finally {
                navigation.returnToRoots()
            }
        }

        mProfile.awaitStoredOnDisk(FAVORITES_PREFERENCE_FILE, STATION_NAME)

        // Dropping the service's cached tree makes the relaunch build FAV through
        // MediaItemFavoritesList, which reads the favorites store.
        mProfile.refreshTree()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                navigation.selectTab(Tab.FAV)
                awaitTheStationOnFav(view, "A relaunched Activity does not show the marked station on FAV")
                view.awaitRowFavorite(mStation.id, true)
            } finally {
                navigation.returnToRoots()
            }
        }

        val restored = mProfile.storages.freshFavorites().getAll()
        assertEquals("A store built from disk holds the wrong number of stations", 1, restored.size)
        assertEquals(
            "A store built from disk does not hold the marked station",
            STATION_NAME,
            restored.first().name
        )
    }

    /**
     * The stations a user marked are within reach without a network.
     *
     * `MediaItemFavoritesList` reads `FavoritesStorage` and nothing else, so FAV needs no
     * connection, and it is a tab, which the connectivity gate does not stand in front of: only a
     * row tap goes through `MainScreenModel.open`. The old screen offered FAV as a row on the root
     * list and refused to open it offline, which is what TASK-049 was filed for. The tab is
     * asserted to stand at its root, so FAV's own list is what answered rather than a node the
     * tap pushed.
     */
    @Test
    fun favOpensWithoutANetwork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                markTheStationOnMine(view)

                navigation.selectTab(Tab.FAV)

                awaitTheStationOnFav(view, "FAV did not open without a network")
                val snapshot = navigation.snapshot()
                assertEquals("Opening FAV left another tab selected", Tab.FAV, snapshot.selectedTab)
                assertEquals(
                    "Opening FAV pushed a node onto it",
                    listOf(Tab.FAV.rootId),
                    snapshot.stacks.getValue(Tab.FAV)
                )
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    /**
     * Marks the seeded station through the heart on its own row on MINE, and leaves MINE
     * selected.
     *
     * The heart is read before it is tapped, so a station that arrived already marked, or a heart
     * bound to some other station's state, fails here rather than passing the case it is setting
     * up.
     */
    private fun markTheStationOnMine(view: MainScreenView) {
        assertEquals(
            "MINE does not show the seeded station",
            listOf(BrowseRow(mStation.id, STATION_NAME)),
            mProfile.awaitSeededMine(view, mStation)
        )
        view.awaitRowFavorite(mStation.id, false)

        view.tapFavorite(mStation.id)

        view.awaitRowFavorite(mStation.id, true)
        mProfile.awaitFavorites(listOf(mStation.id))
    }

    private fun awaitTheStationOnFav(view: MainScreenView, reason: String) {
        assertEquals(
            reason,
            listOf(BrowseRow(mStation.id, STATION_NAME)),
            view.awaitRows("the marked station on FAV") { rows -> rows.any { it.mediaId == mStation.id } }
        )
    }

    private companion object {

        const val STATION_NAME = "Journey favorite one"

        const val FAVORITES_PREFERENCE_FILE = "FavoritesPreferences"
    }
}
