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
 * requested sort id and every other station takes a running counter that is incremented twice
 * when the station it is looking at currently holds the requested sort id, once otherwise.
 *
 * The double increment is meant to leave the requested slot free for the dragged station, and it
 * does that only when the station holding that slot comes after the counter has reached it, which
 * is to say only when the station is dragged towards the top of the list. The tests below pin both
 * outcomes, the working one and the two that leave two stations sharing one sort id; TASK-075
 * carries the fix.
 *
 * Nothing here reaches a device: the storages are the production ones driven by the in memory
 * preferences of [preferencesContext], as the storage suites do.
 */
class SortUtilsTest {

    private val mContext = preferencesContext()

    private val mContextRef = WeakReference(mContext)

    private val mFavorites = FavoritesStorage(mContextRef)

    private val mLocals = DeviceLocalsStorage(mContextRef, mFavorites, LatestRadioStationStorage(mContextRef))

    @Test
    fun movingAStationUpPutsItAtTheRequestedSortIdAndShiftsTheRestDown() {
        mFavorites.addAll(stations("a", "b", "c", "d"))

        SortUtils.updateSortIds("d", 1, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("a" to 0, "d" to 1, "b" to 2, "c" to 3), storedSortIds(mFavorites))
    }

    /**
     * Dragging towards the bottom of the list is where the rule breaks. The counter reaches the
     * requested sort id before the walk gets to the station that holds it, hands that value to
     * whichever station happens to be there, and only then makes the room the dragged station was
     * already given. Two stations come out of it sharing one sort id, and which of the two the
     * next read puts first is decided by the station name, not by where the user dropped the row.
     */
    @Test
    fun movingAStationDownLeavesItSharingASortIdWithTheStationThatHeldIt() {
        mFavorites.addAll(stations("a", "b", "c", "d"))

        SortUtils.updateSortIds("a", 2, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("b" to 0, "a" to 2, "c" to 2, "d" to 3), storedSortIds(mFavorites))
    }

    /**
     * Picking a row up and putting it back where it was is the same defect seen from its other
     * side: the station that holds the requested sort id is the dragged one, so no station in the
     * walk triggers the extra increment and the counter never skips the reserved slot.
     */
    @Test
    fun droppingAStationBackOnItsOwnPositionCollidesWithTheStationBelowIt() {
        mFavorites.addAll(stations("a", "b", "c", "d"))

        SortUtils.updateSortIds("b", 1, MediaId.MEDIA_ID_FAVORITES_LIST, mFavorites, mLocals)

        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 1, "d" to 2), storedSortIds(mFavorites))
    }

    /**
     * With no station holding the requested sort id there is no extra increment anywhere, which
     * leaves the bare counter rule visible: the stations that were not dragged are numbered from
     * zero in the order they were read.
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
