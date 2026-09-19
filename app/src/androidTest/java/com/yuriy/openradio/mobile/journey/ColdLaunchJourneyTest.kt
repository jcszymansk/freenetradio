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
import android.net.ConnectivityManager
import android.view.View
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.isInvalid
import com.yuriy.openradio.shared.model.storage.makeStation
import com.yuriy.openradio.shared.service.AppDataReset
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.ServiceBrowser
import com.yuriy.openradio.shared.service.ServiceStorages
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.service.location.LocationService
import com.yuriy.openradio.shared.view.list.MediaItemsAdapter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private lateinit var mStorages: ServiceStorages

    private lateinit var mAppData: AppDataReset

    private lateinit var mBrowser: ServiceBrowser

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTheDeviceIsOffline()
        mStorages = ServiceStorages(mContext)
        mStorages.clear()
        mAppData = AppDataReset(mContext)
        mBrowser = ServiceBrowser()
        mBrowser.connect()
        // The service caches the root and outlives a single test, so whatever an earlier class
        // left in the tree has to go before this one launches an Activity against it.
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        assertRadioBrowserIsBound()
    }

    @After
    fun tearDown() {
        mStorages.clear()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.release()
    }

    /**
     * The root menu this journey expects is the Radio Browser one: New and Popular come from that
     * provider only. The selection is read once per process, at start up, so it can only be
     * asserted, not set. See `OpenRadioServiceBrowseTest` for the longer version of why.
     */
    private fun assertRadioBrowserIsBound() {
        val ids = mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT)
        assertTrue(
            "The service bound a provider whose root menu differs from the one asserted here. " +
                "Root offered $ids",
            ids.containsAll(
                listOf(MediaId.MEDIA_ID_NEW_STATIONS, MediaId.MEDIA_ID_POPULAR_STATIONS)
            )
        )
    }

    @Test
    fun aColdLaunchListsTheCatalogueWithNothingStoredAndNoNetwork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(cleanInstallRoot(), awaitRootRows(scenario))
        }
    }

    /**
     * An offline first run has no stations to show under any of the entries it lists, and the app
     * has one screen that says so. Showing it at the root would tell a new user the application is
     * broken, so the list has to arrive with both the spinner and that message down.
     */
    @Test
    fun aColdLaunchShowsNeitherTheSpinnerNorTheNoDataMessage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitRootRows(scenario)

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
            }
        }
    }

    /**
     * Android's clear-data scenario, from the user's side: an app that has been used, then cleared,
     * has to come back up as a fresh install rather than with the stores it used to have.
     */
    @Test
    fun clearingTheApplicationDataTakesTheStoredNodesOutOfTheColdLaunch() {
        val favorite = makeStation(FAVORITE_STATION_ID, name = "Favorite one")
        mStorages.favorites.add(favorite)
        mStorages.locals.add(
            makeStation(mStorages.locals.getId(), name = "Local one", isLocal = true)
        )
        mStorages.latest.add(favorite)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        val storedIds = mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT)
        assertTrue("The seeded favorite produced no node to clear", storedIds.contains(MediaId.MEDIA_ID_FAVORITES_LIST))
        assertTrue(
            "The seeded local station produced no node to clear",
            storedIds.contains(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )

        val preferenceFiles = mAppData.clearEveryPreferenceFile()
        assertTrue("The app owns no preference files, so nothing was cleared", preferenceFiles.isNotEmpty())
        // The last played station is the one piece of this that survives the file wipe: the
        // registry's storage keeps it in memory in front of the file, and only the clear command
        // drops that copy. Waiting on it therefore says the whole clear finished.
        mAppData.clearCaches(mBrowser) { mStorages.latest.get().isInvalid() }
        assertTrue("A preference file survived the wipe", mAppData.everyPreferenceFileIsEmpty(preferenceFiles))
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(cleanInstallRoot(), awaitRootRows(scenario))
        }
    }

    /**
     * What the root offers with both user stores empty. Favorites and locals are absent because
     * nothing is stored, and the country entry is the default one, since a cleared profile has no
     * chosen country and the location lookup stays shut for anything but "use location".
     */
    private fun cleanInstallRoot(): List<RootRow> {
        return listOf(
            RootRow(
                MediaId.MEDIA_ID_NEW_STATIONS,
                mContext.getString(com.yuriy.openradio.R.string.new_stations_title)
            ),
            RootRow(
                MediaId.MEDIA_ID_POPULAR_STATIONS,
                mContext.getString(com.yuriy.openradio.R.string.popular_stations_title)
            ),
            RootRow(
                MediaId.MEDIA_ID_ALL_CATEGORIES,
                mContext.getString(com.yuriy.openradio.R.string.all_categories_title)
            ),
            RootRow(
                MediaId.MEDIA_ID_COUNTRIES_LIST,
                mContext.getString(com.yuriy.openradio.R.string.countries_list_title)
            ),
            RootRow(
                MediaId.MEDIA_ID_COUNTRY_STATIONS,
                LocationService.COUNTRY_CODE_TO_NAME.getValue(Country.COUNTRY_CODE_DEFAULT)
            )
        )
    }

    /**
     * Waits for the browse list to be both loaded and laid out, then returns what it shows.
     *
     * The subscription that fills it crosses a process boundary and a layout pass, so there is no
     * event to wait on from here. A read that catches the list half way through either of those
     * yields nothing and is retried.
     */
    private fun awaitRootRows(scenario: ActivityScenario<MainActivity>): List<RootRow> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIST_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val rows = readRootRows(scenario)
            if (rows.isNotEmpty()) {
                return rows
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "The root list did not render within $LIST_TIMEOUT_SECONDS seconds. " +
                describeList(scenario)
        )
    }

    /**
     * Reads the rendered rows in adapter order, pairing the media id the adapter holds with the
     * name the row's own view displays.
     *
     * Every item has to be on screen, which is the one thing here that depends on the device: the
     * root menu is five entries and the emulator this suite targets shows all of them without
     * scrolling. A device that cannot is reported by [describeList] rather than left as a bare
     * timeout.
     *
     * @return the rows, or an empty list while the adapter and the laid out children disagree,
     *   which is every moment the list is still being filled or measured.
     */
    private fun readRootRows(scenario: ActivityScenario<MainActivity>): List<RootRow> {
        val result = AtomicReference(emptyList<RootRow>())
        scenario.onActivity { activity ->
            val listView = activity.findViewById<RecyclerView>(R.id.list_view)
            val adapter = listView.adapter as? MediaItemsAdapter ?: return@onActivity
            val displayed = HashMap<Int, String>()
            for (index in 0 until listView.childCount) {
                val child = listView.getChildAt(index)
                val position = listView.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) {
                    continue
                }
                displayed[position] = child.findViewById<TextView>(R.id.name_view).text.toString()
            }
            if (adapter.itemCount == 0 || displayed.size != adapter.itemCount) {
                return@onActivity
            }
            result.set(
                (0 until adapter.itemCount).map { position ->
                    RootRow(
                        adapter.getItem(position)?.mediaId.orEmpty(),
                        displayed.getValue(position)
                    )
                }
            )
        }
        return result.get()
    }

    /**
     * @return what the list held when it ran out of time, so a timeout says which half was
     *   missing: children the service never sent, or rows the screen was too short to lay out.
     */
    private fun describeList(scenario: ActivityScenario<MainActivity>): String {
        val result = AtomicReference("The Activity holds no browse list at all.")
        scenario.onActivity { activity ->
            val listView = activity.findViewById<RecyclerView>(R.id.list_view)
            val adapter = listView.adapter as? MediaItemsAdapter ?: return@onActivity
            result.set(
                "The adapter holds ${adapter.itemCount} items and ${listView.childCount} rows " +
                    "are laid out."
            )
        }
        return result.get()
    }

    /**
     * The journey only says anything about offline behavior if the device really is offline, and
     * that is a property of how the suite was started rather than of anything it can set. This is
     * the same question `NetworkLayerImpl` asks before it lets a fetch go ahead, so a null answer
     * here is the app's own definition of having no connection.
     */
    @Suppress("DEPRECATION")
    private fun assertTheDeviceIsOffline() {
        val manager = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = manager.activeNetworkInfo
        assertNull(
            "This journey has to run with networking disabled, and the device reports " +
                "$networkInfo. Run `adb shell svc wifi disable && adb shell svc data disable` " +
                "before the suite.",
            networkInfo
        )
    }

    /**
     * One row of the browse list: the item the adapter holds at a position, and the text the view
     * at that position puts on screen.
     */
    private data class RootRow(val mediaId: String, val title: String)

    private companion object {

        /**
         * Covers launching the Activity, connecting a MediaBrowser to the service, answering the
         * root request and laying the list out.
         */
        const val LIST_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L

        const val FAVORITE_STATION_ID = "cold-launch-favorite"
    }
}
