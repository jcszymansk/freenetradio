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

package com.yuriy.openradio.shared.utils

import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.item.station
import com.yuriy.openradio.shared.model.media.item.stations
import com.yuriy.openradio.shared.model.storage.AbstractRadioStationsStorage
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.preferencesContext
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * Covers the renumbering behind dragging a station to a new place in Favorites or in the stations
 * added on the device: which collection the drag rewrites, and the sort id every station in it
 * ends up with.
 *
 * The rule runs in two halves, and the second only makes sense given the first.
 * [AbstractRadioStationsStorage.getAll] sorts the stored stations by sort id and renumbers them
 * `0..n-1` before handing them over, so the set the rule walks is always contiguous, ascending,
 * and numbered from zero whatever the file held. Walking it, the dragged station takes the
 * requested sort id and every other station takes a running counter that steps over the requested
 * sort id, so the dragged station is the only one left holding it. The skip has to follow the
 * counter, not the sort id a station held before the drag: the station that held the requested
 * sort id comes after the counter has passed it whenever the drag goes towards the bottom of the
 * list, and is the dragged station itself when a row is dropped back where it was picked up, and
 * keying the skip on it left two stations sharing one sort id in both cases (TASK-075).
 *
 * Nothing here reaches a device: the storages are the production ones driven by the in memory
 * preferences of [preferencesContext], as the storage suites do.
 */
class SortUtilsTest {

    private val mContext = preferencesContext()

    private val mContextRef = WeakReference(mContext)

    private val mFavorites = FavoritesStorage(mContextRef)

    private val mLocals = DeviceLocalsStorage(mContextRef, mFavorites, LatestRadioStationStorage(mContextRef))

    /**
     * The walk behind the expected order, since counting it out by hand is easy to get wrong:
     * `a` takes the counter at 0, leaving it at 1. The counter has reached the requested 1, so it
     * steps over it before `b` reads it, `b` takes 2, and the counter ends at 3. `c` takes 3. `d`
     * is the dragged station and takes 1, the number the counter stepped over.
     */
    @Test
    fun movingAStationUpPutsItAtTheRequestedSortIdAndShiftsTheRestDown() {
        mFavorites.addAll(stations("a", "b", "c", "d"))

        SortUtils.updateSortIds("d", 1, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("a" to 0, "d" to 1, "b" to 2, "c" to 3), storedSortIds(mFavorites))
    }

    /**
     * Dragging towards the bottom of the list: the counter reaches the requested sort id before
     * the walk gets to `c`, which held it, and the slot has to be kept for `a` all the same. A
     * collision here is invisible in the next read, which renumbers apart by station name, so the
     * test reads the stored sort ids; the user would only see the row land one place off.
     */
    @Test
    fun movingAStationDownPutsItAtTheRequestedSortIdAndShiftsTheRestUp() {
        mFavorites.addAll(stations("a", "b", "c", "d"))

        SortUtils.updateSortIds("a", 2, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("b" to 0, "c" to 1, "a" to 2, "d" to 3), storedSortIds(mFavorites))
    }

    /**
     * Picking a row up and putting it back where it was: the station holding the requested sort
     * id is the dragged one, so the slot has to be kept even though no other station held it.
     */
    @Test
    fun droppingAStationBackOnItsOwnPositionChangesNoSortId() {
        mFavorites.addAll(stations("a", "b", "c", "d"))

        SortUtils.updateSortIds("b", 1, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2, "d" to 3), storedSortIds(mFavorites))
    }

    /**
     * The same downward drag among the stations added on the device, which go through their own
     * storage and their own branch of the rule.
     */
    @Test
    fun movingAStationAddedOnTheDeviceDownPutsItAtTheRequestedSortId() {
        mLocals.addAll((0 until 3).map { localStation("local-$it", sortId = it) }.toSet())

        SortUtils.updateSortIds("local-0", 1, MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST, mFavorites, mLocals)

        assertEquals(mapOf("local-1" to 0, "local-0" to 1, "local-2" to 2), storedSortIds(mLocals))
    }

    /**
     * A requested sort id the counter never reaches leaves nothing to step over, which leaves the
     * bare counter rule visible: the stations that were not dragged are numbered from zero in the
     * order they were read.
     */
    @Test
    fun aRequestedSortIdPastTheEndOfTheListRenumbersTheRestFromZero() {
        mFavorites.addAll(stations("a", "b", "c", "d"))

        SortUtils.updateSortIds("d", 10, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2, "d" to 10), storedSortIds(mFavorites))
    }

    @Test
    fun reorderingFavoritesLeavesTheStationsAddedOnTheDeviceAlone() {
        mFavorites.addAll(stations("a", "b", "c"))
        mLocals.addAll(setOf(localStation("local-1", sortId = 7), localStation("local-2", sortId = 3)))

        SortUtils.updateSortIds("c", 0, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("c" to 0, "a" to 1, "b" to 2), storedSortIds(mFavorites))
        assertEquals(mapOf("local-1" to 7, "local-2" to 3), storedSortIds(mLocals))
    }

    @Test
    fun reorderingTheStationsAddedOnTheDeviceLeavesFavoritesAlone() {
        mFavorites.addAll(setOf(station("a", sortId = 4), station("b", sortId = 9)))
        mLocals.addAll(setOf(localStation("local-1", sortId = 0), localStation("local-2", sortId = 1)))

        SortUtils.updateSortIds("local-2", 0, MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST, mFavorites, mLocals)

        assertEquals(mapOf("local-2" to 0, "local-1" to 1), storedSortIds(mLocals))
        assertEquals(mapOf("a" to 4, "b" to 9), storedSortIds(mFavorites))
    }

    /**
     * Only the two collections the user can arrange by hand are sortable, and the rule has no
     * branch for anything else, so a drag reported against any other category has to leave both
     * files exactly as they were. The stations are seeded with sort ids that no read would
     * produce, so a renumbering of either collection shows up as a change.
     */
    @Test
    fun aCategoryThatIsNeitherFavoritesNorLocalsChangesNothing() {
        mFavorites.addAll(setOf(station("a", sortId = 4), station("b", sortId = 9)))
        mLocals.addAll(setOf(localStation("local-1", sortId = 7)))

        SortUtils.updateSortIds("a", 0, MediaId.MEDIA_ID_ALL_CATEGORIES, mFavorites, mLocals)

        assertEquals(mapOf("a" to 4, "b" to 9), storedSortIds(mFavorites))
        assertEquals(mapOf("local-1" to 7), storedSortIds(mLocals))
    }

    @Test
    fun reorderingAnEmptyCategoryChangesNothing() {
        mLocals.addAll(setOf(localStation("local-1", sortId = 7)))

        SortUtils.updateSortIds("a", 0, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(emptyMap<String, Int>(), storedSortIds(mFavorites))
        assertEquals(mapOf("local-1" to 7), storedSortIds(mLocals))
    }

    /**
     * A media id that names no station in the category leaves the requested slot free and hands it
     * to nobody, so every station is still rewritten and the collection comes out with a gap in it
     * rather than untouched.
     */
    @Test
    fun aMediaIdThatMatchesNoStationStillRenumbersAndLeavesAGap() {
        mFavorites.addAll(stations("a", "b", "c"))

        SortUtils.updateSortIds("missing", 1, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("a" to 0, "b" to 2, "c" to 3), storedSortIds(mFavorites))
    }

    /**
     * The sort ids the rule works from are the ones the read produced, not the ones the file held,
     * so a drag also compacts sort ids that a merge or an import left far apart.
     */
    @Test
    fun sortIdsLeftFarApartAreCompactedByTheDrag() {
        mFavorites.addAll(setOf(station("a", sortId = 5), station("b", sortId = 10), station("c", sortId = 20)))

        SortUtils.updateSortIds("c", 0, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("c" to 0, "a" to 1, "b" to 2), storedSortIds(mFavorites))
    }

    /**
     * Reports the sort ids that were written, which [AbstractRadioStationsStorage.getAll] cannot:
     * that read renumbers the whole collection from zero on its way out, and would report a tidy
     * `0..n-1` even where the rule wrote a gap or handed the same sort id to two stations.
     * [AbstractRadioStationsStorage.getAllFromString] over the stored string is the one read that
     * hands back what is in the file.
     */
    private fun storedSortIds(storage: AbstractRadioStationsStorage): Map<String, Int> {
        return storage.getAllFromString(storage.getAllAsString()).associate { it.id to it.sortId }
    }

    private fun localStation(id: String, sortId: Int): RadioStation {
        return station(id, sortId = sortId).apply { isLocal = true }
    }
}
