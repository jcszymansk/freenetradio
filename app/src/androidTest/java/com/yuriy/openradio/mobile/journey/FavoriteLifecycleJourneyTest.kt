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
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.storage.makeStation
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The third phone journey: marking a station as a favorite and taking the mark away again.
 *
 * A favorite is a check box on a station's row, a command to the service, a line in
 * `FavoritesPreferences`, and a node the root menu gains and loses. Those four have to agree, and
 * the one of them a user can see is the first. So what is asserted here is the rendered box, the
 * rendered lists it changes, and the store read back through an instance that has cached nothing -
 * `FavoritesStorage` answers `isFavorite` out of a map it fills as it goes, so the writer is the
 * one reader that cannot be trusted to say whether anything was written.
 *
 * The station the user marks is seeded into the locals store rather than typed into the add
 * dialog. Creating a station is TASK-006.02's subject, and with the device offline a station of
 * the user's own is the only kind there is: every other node needs a provider.
 *
 * Neither the swipe nor the tap that a user performs to get here can be performed. The check box
 * lives in the layer a left swipe reveals, which is a drag on a
 * [com.xenione.libs.swipemaker.SwipeLayout]; the drag is enabled in exactly the two nodes this
 * journey visits, and the box is a child of the row either way, so clicking it skips the drag and
 * nothing else. Opening a node by tapping its row is refused while the device is offline, which
 * [theFavoritesRowDoesNotOpenWithoutANetwork] pins down and TASK-049 lifts; [JourneyNavigation]
 * calls the step that tap performs once past the gate.
 *
 * What the service does with `CMD_FAVORITE_ON` and `CMD_FAVORITE_OFF` is
 * `OpenRadioServiceCommandTest`'s subject, and what it answers for the favorites node is
 * `OpenRadioServiceBrowseTest`'s. Neither of them renders anything.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class FavoriteLifecycleJourneyTest {

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
        // The root is cached and the profile has just browsed it to check which provider is bound,
        // so the seeded station would otherwise be missing from the first list an Activity shows.
        mProfile.refreshTree()
    }

    @After
    fun tearDown() {
        mProfile.finish()
    }

    /**
     * The first half of the lifecycle: the box on the station's own row, and the node the root
     * gains because of it.
     *
     * Nothing between the click and the row appearing is done by the test. The box sends
     * `CMD_FAVORITE_OFF`, naming the state it is leaving; the service writes the store, drops its
     * cached root and notifies; and the list the Activity shows on the way back out is built from
     * the store. Asserting the row is absent first is what makes waiting for it mean something.
     */
    @Test
    fun markingAStationFromItsRowPutsTheFavoritesNodeOnTheRoot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitSeededRoot(list)
            assertNull(
                "A browsable row offers a favorite box, so the box this journey clicks is not " +
                    "the station's own control",
                list.rowFavorite(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            )

            markTheStationFromTheLocalsList(scenario, list)

            assertEquals(
                "Marking a station did not put the favorites node on the root list",
                favoritesRoot(),
                list.awaitRows("the favorites node") { it.contains(mProfile.favoritesRow()) }
            )
        }

        assertEquals(
            "A favorites store built from scratch does not hold the marked station",
            listOf(mStation.id),
            mProfile.storages.freshFavorites().getAll().map { it.id }
        )
    }

    /**
     * The node the mark produced has to lead somewhere: the station, under the name it was marked
     * with, with its box showing what it is.
     */
    @Test
    fun theFavoritesNodeShowsTheStationTheUserMarked() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitSeededRoot(list)
            markTheStationFromTheLocalsList(scenario, list)
            list.awaitRows("the favorites node") { it.contains(mProfile.favoritesRow()) }

            val navigation = JourneyNavigation(scenario)
            try {
                navigation.open(MediaId.MEDIA_ID_FAVORITES_LIST)

                assertEquals(
                    "The favorites node does not show the station that was marked",
                    listOf(BrowseRow(mStation.id, STATION_NAME)),
                    list.awaitRows("the station's own row") { it.size == 1 }
                )
                list.awaitRowFavorite(mStation.id, true)
            } finally {
                navigation.returnToRoot()
            }
        }
    }

    /**
     * The second half of the lifecycle, driven where a user would drive it: inside the favorites
     * node, on the row that put it there.
     *
     * Removing the only favorite takes the whole node away, which is the part of this an offline
     * user can see. What they cannot see is the list they are standing in changing: the service
     * decides which node to notify from the one it last answered for, and with the store now empty
     * that decision lands on the root rather than on the list still on screen. TASK-050 holds that;
     * the two assertions on the stale row below are what will change when it is fixed.
     */
    @Test
    fun unmarkingAStationFromTheFavoritesNodeTakesItOffTheRoot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitSeededRoot(list)
            markTheStationFromTheLocalsList(scenario, list)
            list.awaitRows("the favorites node") { it.contains(mProfile.favoritesRow()) }

            val navigation = JourneyNavigation(scenario)
            try {
                navigation.open(MediaId.MEDIA_ID_FAVORITES_LIST)
                list.awaitRowFavorite(mStation.id, true)

                list.tapRowFavorite(mStation.id)

                awaitFavorites(emptyList())
                list.assertRowsStay(
                    listOf(BrowseRow(mStation.id, STATION_NAME)),
                    "Unmarking the last favorite refreshed the list it was removed from. " +
                        "TASK-050 asks for exactly that, so this assertion is what has to " +
                        "change, not the fix"
                )
                // A list that emptied reads as no rows at all, which is also how a list reads
                // while it is being laid out, so assertRowsStay cannot tell those apart and lets
                // the empty one through. This says the row is still there in its own right.
                list.awaitRowFavorite(mStation.id, false)
            } finally {
                navigation.returnToRoot()
            }

            assertEquals(
                "Unmarking the last favorite left the favorites node on the root list",
                mProfile.cleanInstallRoot() + mProfile.localsRow(),
                list.awaitRows("the favorites node gone") {
                    !it.contains(mProfile.favoritesRow())
                }
            )
        }

        assertEquals(
            "The node is off the root list but the station is still in the favorites store",
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
     * Activity offers the node and its station after the service's cached root has been dropped,
     * so what it renders was built from the store rather than replayed; and a
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
            val list = BrowseListView(scenario)
            awaitSeededRoot(list)
            markTheStationFromTheLocalsList(scenario, list)
            list.awaitRows("the favorites node") { it.contains(mProfile.favoritesRow()) }
        }

        mProfile.awaitStoredOnDisk(FAVORITES_PREFERENCE_FILE, STATION_NAME)

        // The root is the one node of the three involved here that the service caches, so a second
        // Activity would otherwise be handed the list the first one built. Dropping it makes the
        // relaunch rebuild the root through MediaItemRoot, which reads the favorites store.
        mProfile.refreshTree()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            assertEquals(
                "A relaunched Activity does not offer the node the previous one produced",
                favoritesRoot(),
                list.awaitRows("the favorites node") { it.contains(mProfile.favoritesRow()) }
            )

            val navigation = JourneyNavigation(scenario)
            try {
                navigation.open(MediaId.MEDIA_ID_FAVORITES_LIST)
                assertEquals(
                    "A relaunched Activity does not show the station inside the node",
                    listOf(BrowseRow(mStation.id, STATION_NAME)),
                    list.awaitRows("the station's own row") { it.size == 1 }
                )
            } finally {
                navigation.returnToRoot()
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
     * The gate this journey ran into, asserted rather than described, so that TASK-049 lands with
     * a test that changes answer when it is fixed.
     *
     * `MediaPresenterImpl.handleItemSelected` returns before it does anything when
     * `NetworkLayer.checkConnectivityAndNotify` says there is no connection. The favorites node
     * needs no connection: `MediaItemFavoritesList` reads `FavoritesStorage` and nothing else. So
     * the row is offered, tapping it raises the no-connection toast, and the stations the user
     * marked stay out of reach. TASK-049's second criterion is this node.
     */
    @Test
    fun theFavoritesRowDoesNotOpenWithoutANetwork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitSeededRoot(list)
            markTheStationFromTheLocalsList(scenario, list)
            val root = list.awaitRows("the favorites node") {
                it.contains(mProfile.favoritesRow())
            }

            list.tapRow(MediaId.MEDIA_ID_FAVORITES_LIST)

            list.assertRowsStay(
                root,
                "Tapping the favorites row offline opened it. TASK-049 asks for exactly that, so " +
                    "this test is what has to change, not the fix"
            )
        }
    }

    /**
     * Waits for the list to be the root of the profile every case here starts from: a cleared
     * install holding one station of the user's own and no favorites.
     *
     * This is a precondition rather than a restatement of the cold launch journey. The presenter
     * is one of the registry's singletons and its node stack outlives the Activity, so a case that
     * left the stack inside a node would send the next case's Activity straight back there. Every
     * case here returns to the root, which this asserts rather than assumes, because Phase 6 asks
     * for a suite with no order assumptions in it.
     */
    private fun awaitSeededRoot(list: BrowseListView) {
        assertEquals(
            "This case did not start at the root of the seeded profile. A case that walks into a " +
                "node leaves the presenter's stack there, and it is a registry singleton that " +
                "outlives the Activity, so the next case launches back into that node.",
            mProfile.cleanInstallRoot() + mProfile.localsRow(),
            list.awaitRows("the root of the seeded profile") {
                it == mProfile.cleanInstallRoot() + mProfile.localsRow()
            }
        )
    }

    /**
     * Marks the seeded station through the favorite box on its own row in the locals list, and
     * walks back out to the root.
     *
     * The box is read before it is clicked, so a station that arrived already marked, or a box
     * bound to some other station's state, fails here rather than passing the case it is setting
     * up.
     */
    private fun markTheStationFromTheLocalsList(
        scenario: ActivityScenario<MainActivity>,
        list: BrowseListView
    ) {
        val navigation = JourneyNavigation(scenario)
        try {
            navigation.open(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            assertEquals(
                "The locals list does not show the seeded station",
                listOf(BrowseRow(mStation.id, STATION_NAME)),
                list.awaitRows("the station's own row") { it.size == 1 }
            )
            list.awaitRowFavorite(mStation.id, false)

            list.tapRowFavorite(mStation.id)

            list.awaitRowFavorite(mStation.id, true)
            awaitFavorites(listOf(mStation.id))
        } finally {
            navigation.returnToRoot()
        }
    }

    /**
     * Waits for the favorites store to hold exactly [expected], read through an instance that has
     * cached no answers.
     *
     * The click hands the command to a coroutine and returns, so the store is written some time
     * after the box on screen has already flipped.
     */
    private fun awaitFavorites(expected: List<String>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STORE_TIMEOUT_SECONDS)
        var stored = emptyList<String>()
        while (System.nanoTime() < deadline) {
            stored = mProfile.storages.freshFavorites().getAll().map { it.id }
            if (stored == expected) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "The favorites store held $stored rather than $expected within " +
                "$STORE_TIMEOUT_SECONDS seconds."
        )
    }

    /**
     * @return the root list of this profile once the station is marked. `MediaItemRoot` puts the
     *   favorites node first and the locals node last, with the clean install catalogue between
     *   them.
     */
    private fun favoritesRoot(): List<BrowseRow> {
        return listOf(mProfile.favoritesRow()) + mProfile.cleanInstallRoot() + mProfile.localsRow()
    }

    private companion object {

        const val STATION_NAME = "Journey favorite one"

        const val FAVORITES_PREFERENCE_FILE = "FavoritesPreferences"

        /**
         * Covers the hop onto the main thread the command is sent from, the round trip to the
         * service, and the write.
         */
        const val STORE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L
    }
}
