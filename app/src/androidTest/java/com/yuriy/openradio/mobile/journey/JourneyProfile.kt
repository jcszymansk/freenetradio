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
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.isInvalid
import com.yuriy.openradio.shared.model.storage.makeStation
import com.yuriy.openradio.shared.service.AppDataReset
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.ServiceBrowser
import com.yuriy.openradio.shared.service.ServiceStorages
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.service.location.LocationService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * The device state every phone journey starts from: offline, with nothing an earlier test stored
 * still in place, and with the browse tree rebuilt so that what an Activity is about to render is
 * the profile this test set up rather than the one before it.
 *
 * The journeys share this because a journey that starts from a different profile proves something
 * about the previous test rather than about the application.
 */
@UnstableApi
internal class JourneyProfile(private val mContext: Context) {

    val storages = ServiceStorages(mContext)

    val browser = ServiceBrowser()

    private val mAppData = AppDataReset(mContext)

    /**
     * Brings the device to a fresh install, connected to the service. That the device is offline
     * is not this profile's to check: `OfflineDeviceRunnerBuilder` refuses to run any class on a
     * networked device.
     */
    fun start() {
        storages.clear()
        browser.connect()
        clearApplicationData()
        // The service caches the root and outlives a single test, so whatever an earlier class
        // left in the tree has to go before this one launches an Activity against it.
        refreshTree()
        assertRadioBrowserIsBound()
    }

    /**
     * Undoes as much of [start] as it got through.
     *
     * The tree is only worth dropping if this profile ever reached it. [start] can fail before it
     * connects, and refreshing regardless would bury that failure under "Browser is not
     * connected".
     */
    fun finish() {
        storages.clear()
        if (browser.isConnected()) {
            refreshTree()
        }
        browser.release()
    }

    /**
     * Drops the service's cached root and locals children, the way every mutation in the
     * application does once it has written.
     */
    fun refreshTree() {
        browser.command(OpenRadioService.CMD_UPDATE_TREE)
    }

    /**
     * Android's clear-data scenario, minus the process kill. Every journey starts from it, because
     * a first run is only a first run if nothing an earlier test stored is still there. The chosen
     * country is the plainest example: it decides the last row of the root menu, and
     * `ServiceStorages.clear` does not own that file.
     *
     * The command clears on a coroutine and answers before it has finished, so the wait needs
     * something it is known to take away. The last played station is the step it finishes with,
     * and the registry's storage keeps a copy in memory that outlives the file wipe, so a station
     * seeded through that instance cannot make the wait pass before the command has run.
     *
     * @return the preference files that were emptied.
     */
    fun clearApplicationData(): List<String> {
        val preferenceFiles = mAppData.clearEveryPreferenceFile()
        assertTrue("The app owns no preference files, so nothing was cleared", preferenceFiles.isNotEmpty())
        storages.latest.add(makeStation(CLEAR_PROBE_STATION_ID))
        assertFalse(
            "The clear probe was not stored, so waiting for it to go would prove nothing",
            storages.latest.get().isInvalid()
        )
        mAppData.clearCaches(browser) { storages.latest.get().isInvalid() }
        return preferenceFiles
    }

    /**
     * Waits until the store [preferenceFile] holds [needle] in its file on disk, which is what a
     * restart would read, rather than in the copy Android keeps in memory for this process.
     */
    fun awaitStoredOnDisk(preferenceFile: String, needle: String) {
        mAppData.awaitPreferenceFileContaining(preferenceFile, needle)
    }

    fun assertEveryPreferenceFileIsEmpty(preferenceFiles: List<String>) {
        assertTrue("A preference file survived the wipe", mAppData.everyPreferenceFileIsEmpty(preferenceFiles))
    }

    /**
     * What the root offers with both user stores empty. Favorites and locals are absent because
     * nothing is stored, and the country entry is the default one, since a cleared profile has no
     * chosen country and the location lookup stays shut for anything but "use location".
     */
    fun cleanInstallRoot(): List<BrowseRow> {
        return listOf(
            BrowseRow(
                MediaId.MEDIA_ID_NEW_STATIONS,
                mContext.getString(com.yuriy.openradio.R.string.new_stations_title)
            ),
            BrowseRow(
                MediaId.MEDIA_ID_POPULAR_STATIONS,
                mContext.getString(com.yuriy.openradio.R.string.popular_stations_title)
            ),
            BrowseRow(
                MediaId.MEDIA_ID_ALL_CATEGORIES,
                mContext.getString(com.yuriy.openradio.R.string.all_categories_title)
            ),
            BrowseRow(
                MediaId.MEDIA_ID_COUNTRIES_LIST,
                mContext.getString(com.yuriy.openradio.R.string.countries_list_title)
            ),
            BrowseRow(
                MediaId.MEDIA_ID_COUNTRY_STATIONS,
                LocationService.COUNTRY_CODE_TO_NAME.getValue(Country.COUNTRY_CODE_DEFAULT)
            )
        )
    }

    /**
     * @return the row the root grows once a station has been marked, which `MediaItemRoot` adds
     *   first, ahead of everything in [cleanInstallRoot].
     */
    fun favoritesRow(): BrowseRow {
        return BrowseRow(
            MediaId.MEDIA_ID_FAVORITES_LIST,
            mContext.getString(com.yuriy.openradio.R.string.favorites_list_title)
        )
    }

    /**
     * @return the row the root grows once the device holds a station of the user's own, which
     *   `MediaItemRoot` adds last.
     */
    fun localsRow(): BrowseRow {
        return BrowseRow(
            MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST,
            mContext.getString(com.yuriy.openradio.R.string.local_radio_stations_list_title)
        )
    }

    /**
     * The root menu these journeys expect is the Radio Browser one: New and Popular come from that
     * provider only.
     *
     * The provider is the one piece of the profile [clearApplicationData] cannot reach, and no
     * test hook can reach it either: `ImagesProvider.onCreate` calls `DependencyRegistryCommon.init`,
     * and Android creates content providers before the Application and before the instrumentation,
     * so the URL layer, the parser and the root command are bound before any test code has run at
     * all. An install left on WebRadio stays on WebRadio for the whole run, so all a test can do
     * is assert what was bound. `OpenRadioServiceBrowseTest` and `OpenRadioServiceSearchTest`
     * carry the same guard.
     *
     * Clearing the app's data before the run is what avoids it, which is why `AGENTS.md` asks for
     * that; a run that trips this has emptied the stored selection on the way past, so the next
     * one binds Radio Browser regardless. Getting a fresh process per test is TASK-031.
     */
    private fun assertRadioBrowserIsBound() {
        val ids = browser.mediaIds(MediaId.MEDIA_ID_ROOT)
        assertTrue(
            "This process bound a provider other than Radio Browser. It read that from " +
                "SourcePreferences before any test ran, and nothing here can change it, so the " +
                "device was carrying a stored selection into the run. Clear the app's data first, " +
                "as AGENTS.md describes. The stored selection has now been reset either way, so " +
                "running this again binds Radio Browser. Root offered $ids",
            ids.containsAll(
                listOf(MediaId.MEDIA_ID_NEW_STATIONS, MediaId.MEDIA_ID_POPULAR_STATIONS)
            )
        )
    }

    private companion object {

        /**
         * Stored only so that the wait on the asynchronous clear has something to watch go away.
         */
        const val CLEAR_PROBE_STATION_ID = "journey-clear-probe"
    }
}
