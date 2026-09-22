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

package com.yuriy.openradio.shared.model.storage

import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.item.station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * Covers the merge behind importing a data file: what the two sets contribute, which version of a
 * station survives a conflict, and the sort ids the result carries.
 *
 * The layer imports nothing from Android and the storages it drives are given the in memory
 * preferences of [preferencesContext], so the whole rule is checked here rather than on a device.
 * What Android's own preferences do with the same storages stays with the instrumented
 * [FavoritesStorage], [DeviceLocalsStorage] and [AbstractRadioStationsStorage] suites.
 */
class StorageManagerLayerImplTest {

    private val mContext = preferencesContext()

    private val mContextRef = WeakReference(mContext)

    private val mFavorites = FavoritesStorage(mContextRef)

    private val mLocals = DeviceLocalsStorage(mContextRef, mFavorites, LatestRadioStationStorage(mContextRef))

    private val mLayer = StorageManagerLayerImpl(mFavorites, mLocals)

    @Test
    fun mergingIntoAnEmptyStoreAddsEveryIncomingStation() {
        mLayer.mergeFavorites(exported(station("a", sortId = 1), station("b", sortId = 2)))

        assertEquals(listOf("a", "b"), mFavorites.getAll().map { it.id })
        assertTrue(mLocals.getAll().isEmpty())
    }

    /**
     * The two sets meet in one order: the stored stations have been renumbered from zero by the
     * read that produced them, and each incoming station lands wherever the sort id in the file
     * puts it among them.
     *
     * The assertion reads the stations back one at a time because [AbstractRadioStationsStorage.getAll]
     * renumbers on the way out as well, and would report contiguous sort ids even if the merge had
     * written none.
     */
    @Test
    fun disjointSetsInterleaveBySortIdAndComeOutNumberedFromZero() {
        mFavorites.addAll(setOf(station("a", sortId = 10), station("b", sortId = 20)))

        mLayer.mergeFavorites(exported(station("c", sortId = -3), station("d", sortId = 5)))

        assertEquals(listOf("c", "a", "b", "d"), mFavorites.getAll().map { it.id })
        assertEquals(listOf(0, 1, 2, 3), listOf("c", "a", "b", "d").map { mFavorites.get(it).sortId })
    }

    @Test
    fun aStationThatIsAlreadyStoredIsNotDuplicatedByTheMerge() {
        mFavorites.addAll(setOf(station("a", sortId = 1), station("b", sortId = 2)))

        mLayer.mergeFavorites(exported(station("a", sortId = 1), station("b", sortId = 2)))

        assertEquals(listOf("a", "b"), mFavorites.getAll().map { it.id })
    }

    /**
     * Two versions of one station share a storage key, so the one written last is the one that
     * stays, and the merge writes in sort id order.
     */
    @Test
    fun theConflictingVersionThatSortsLastIsTheOneThatSurvives() {
        mFavorites.addAll(setOf(station("a", name = "Stored", sortId = 1)))

        mLayer.mergeFavorites(exported(station("a", name = "Incoming", sortId = 9)))

        assertEquals(listOf("Incoming"), mFavorites.getAll().map { it.name })
    }

    @Test
    fun theStoredVersionSurvivesWhenTheIncomingOneSortsFirst() {
        mFavorites.addAll(setOf(station("a", name = "Stored", sortId = 1)))

        mLayer.mergeFavorites(exported(station("a", name = "Incoming", sortId = -5)))

        assertEquals(listOf("Stored"), mFavorites.getAll().map { it.name })
    }

    @Test
    fun mergingAnEmptyPayloadLeavesTheStoredStationsAlone() {
        mFavorites.addAll(setOf(station("a", sortId = 1)))
        mLocals.addAll(setOf(localStation("local-1", sortId = 1)))

        mLayer.mergeFavorites("")
        mLayer.mergeDeviceLocals("")

        assertEquals(listOf("a"), mFavorites.getAll().map { it.id })
        assertEquals(listOf("local-1"), mLocals.getAll().map { it.id })
    }

    /**
     * Picking the wrong file is the realistic way an import carries nothing, and it has to leave
     * the collection as it was rather than empty it.
     */
    @Test
    fun mergingInputThatCarriesNoStationLeavesTheStoredOnesAlone() {
        mFavorites.addAll(setOf(station("a", sortId = 1)))
        mLocals.addAll(setOf(localStation("local-1", sortId = 1)))

        mLayer.mergeFavorites("this is not an export")
        mLayer.mergeDeviceLocals("this is not an export")

        assertEquals(listOf("a"), mFavorites.getAll().map { it.id })
        assertEquals(listOf("local-1"), mLocals.getAll().map { it.id })
    }

    @Test
    fun disjointLocalStationsInterleaveBySortIdAndKeepTheLocalFlag() {
        mLocals.addAll(setOf(localStation("local-1", sortId = 10)))

        mLayer.mergeDeviceLocals(
            exported(localStation("local-2", sortId = -3), localStation("local-3", sortId = 5))
        )

        assertEquals(listOf("local-2", "local-1", "local-3"), mLocals.getAll().map { it.id })
        assertTrue(mLocals.getAll().all { it.isLocal })
    }

    @Test
    fun theConflictingLocalVersionThatSortsLastIsTheOneThatSurvives() {
        mLocals.addAll(setOf(localStation("local-1", name = "Stored", sortId = 1)))

        mLayer.mergeDeviceLocals(exported(localStation("local-1", name = "Incoming", sortId = 9)))

        assertEquals(listOf("Incoming"), mLocals.getAll().map { it.name })
    }

    @Test
    fun theStoredLocalVersionSurvivesWhenTheIncomingOneSortsFirst() {
        mLocals.addAll(setOf(localStation("local-1", name = "Stored", sortId = 1)))

        mLayer.mergeDeviceLocals(exported(localStation("local-1", name = "Incoming", sortId = -5)))

        assertEquals(listOf("Stored"), mLocals.getAll().map { it.name })
    }

    /**
     * Favorites and local stations are separate collections in the data file and have to stay
     * separate on the way back in, whichever of the two the user imports.
     */
    @Test
    fun eachMergeTouchesOnlyItsOwnCollection() {
        mFavorites.addAll(setOf(station("a", sortId = 1)))
        mLocals.addAll(setOf(localStation("local-1", sortId = 1)))

        mLayer.mergeDeviceLocals(exported(localStation("local-2", sortId = 2)))
        mLayer.mergeFavorites(exported(station("b", sortId = 2)))

        assertEquals(listOf("a", "b"), mFavorites.getAll().map { it.id })
        assertEquals(listOf("local-1", "local-2"), mLocals.getAll().map { it.id })
    }

    @Test
    fun exportedFavoritesCanBeImportedBackAfterTheStoreWasWiped() {
        mFavorites.addAll(setOf(station("a", sortId = 1), station("b", sortId = 2)))
        val exported = mLayer.getAllFavoritesAsString()

        mFavorites.clear()
        mLayer.mergeFavorites(exported)

        assertEquals(listOf("a", "b"), mFavorites.getAll().map { it.id })
    }

    /**
     * The local stations file keeps the counter that names the next locally added station next to
     * the stations themselves, so the export carries it and the import has to read past it.
     */
    @Test
    fun exportedDeviceLocalsCarryTheIdCounterAndTheImportIgnoresIt() {
        mLocals.getId()
        mLocals.addAll(setOf(localStation("local-1", sortId = 1)))
        val exported = mLayer.getAllDeviceLocalsAsString()

        mLocals.clear()
        mLayer.mergeDeviceLocals(exported)

        assertEquals(listOf("local-1"), mLocals.getAll().map { it.id })
    }

    /**
     * What the layer merges is one collection's export read back from a data file, so the test
     * builds its input the same way, out of a storage of its own. Marshalling lives on
     * [AbstractRadioStationsStorage], so which subclass does the exporting cannot change the
     * string, and restating its delimiters here would only let the two drift apart.
     */
    private fun exported(vararg stations: RadioStation): String {
        val context = preferencesContext()
        val donor = FavoritesStorage(WeakReference(context))
        donor.addAll(stations.toSet())
        return donor.getAllAsString()
    }

    private fun localStation(id: String, name: String = "Station $id", sortId: Int): RadioStation {
        return station(id, name = name, sortId = sortId).apply { isLocal = true }
    }
}
