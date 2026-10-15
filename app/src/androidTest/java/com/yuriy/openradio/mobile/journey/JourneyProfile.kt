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
import com.yuriy.openradio.shared.service.assertRadioBrowserIsBound
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.service.location.LocationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.TimeUnit

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
        // The provider is the one piece of the profile clearApplicationData cannot reach. A run
        // that trips this has emptied the stored selection on the way past, though, so the next
        // one binds Radio Browser regardless.
        assertRadioBrowserIsBound(browser.mediaIds(MediaId.MEDIA_ID_ROOT))
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
     * @return the root of a profile holding one station of the user's own: the clean install
     *   catalogue with the locals node appended, which is what `MediaItemRoot` builds from a locals
     *   store holding one entry.
     */
    fun seededRoot(): List<BrowseRow> {
        return cleanInstallRoot() + localsRow()
    }

    /**
     * Waits for [list] to be the [seededRoot] a seeded journey starts every case from.
     *
     * This is a precondition rather than a restatement of the cold launch journey. The presenter
     * is one of the registry's singletons and its node stack outlives the Activity, so a case that
     * left the stack inside a node would send the next case's Activity straight back there. Phase
     * 6 asks for a suite with no order assumptions in it, so every case asserts where it starts.
     *
     * @return the rendered rows, for a case that has something else to say about them.
     */
    fun awaitSeededRoot(list: BrowseListView): List<BrowseRow> {
        val rows = list.awaitRows("the root of the seeded profile") { it == seededRoot() }
        assertEquals(
            "This case did not start at the root of the seeded profile. A case that walks into a " +
                "node leaves the presenter's stack there, and it is a registry singleton that " +
                "outlives the Activity, so the next case launches back into that node.",
            seededRoot(),
            rows
        )
        return rows
    }

    /**
     * Waits for the favorites store to hold exactly [expected], read through an instance that has
     * cached no answers, because the writer answers out of a map it fills as it goes.
     *
     * A favorite is written from a coroutine the gesture hands it to, so the store is written some
     * time after whatever the gesture changed on screen.
     */
    fun awaitFavorites(expected: List<String>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STORE_TIMEOUT_SECONDS)
        var stored = emptyList<String>()
        while (System.nanoTime() < deadline) {
            stored = storages.freshFavorites().getAll().map { it.id }
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

    private companion object {

        /**
         * Stored only so that the wait on the asynchronous clear has something to watch go away.
         */
        const val CLEAR_PROBE_STATION_ID = "journey-clear-probe"

        /**
         * Covers the hop onto the main thread a command is sent from, the round trip to the
         * service, and the write.
         */
        const val STORE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L
    }
}
