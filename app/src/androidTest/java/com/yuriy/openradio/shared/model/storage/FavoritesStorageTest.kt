package com.yuriy.openradio.shared.model.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.media.isInvalid
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

@RunWith(AndroidJUnit4::class)
class FavoritesStorageTest {

    private lateinit var mContext: Context
    private lateinit var mStorage: FavoritesStorage

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mStorage = newStorage()
        mStorage.clear()
    }

    @After
    fun tearDown() {
        mStorage.clear()
    }

    @Test
    fun addedStationIsStoredAndReportedAsFavorite() {
        val station = makeStation("1")

        mStorage.add(station)

        assertTrue(mStorage.isFavorite(station))
        assertEquals(setOf("1"), mStorage.getAll().map { it.id }.toSet())
    }

    @Test
    fun addAssignsIncreasingSortIdWhenItIsUnknown() {
        val first = makeStation("1")
        val second = makeStation("2")
        assertEquals(DependencyRegistryCommon.UNKNOWN_ID, first.sortId)

        mStorage.add(first)
        mStorage.add(second)

        assertEquals(1, first.sortId)
        assertEquals(2, second.sortId)
    }

    @Test
    fun addKeepsSortIdThatWasAlreadyAssigned() {
        val station = makeStation("1", sortId = 7)

        mStorage.add(station)

        assertEquals(7, station.sortId)
        assertEquals(7, newStorage().get("1").sortId)
    }

    @Test
    fun addingTheSameStationTwiceKeepsOneEntry() {
        mStorage.add(makeStation("1", name = "First"))
        mStorage.add(makeStation("1", name = "Second"))

        val all = mStorage.getAll()
        assertEquals(1, all.size)
        assertEquals("Second", all.first().name)
    }

    @Test
    fun removeDropsTheStationAndClearsTheFavoriteFlag() {
        val station = makeStation("1")
        mStorage.add(station)

        mStorage.remove(station)

        assertFalse(mStorage.isFavorite(station))
        assertTrue(mStorage.getAll().isEmpty())
    }

    @Test
    fun removingAnUnknownStationLeavesTheStoredOnesAlone() {
        mStorage.add(makeStation("1"))

        mStorage.remove(makeStation("2"))

        assertEquals(setOf("1"), mStorage.getAll().map { it.id }.toSet())
    }

    @Test
    fun addStripsTheSearchPrefixFromTheMediaId() {
        val station = makeStation(MediaId.makeSearchId("42"))

        mStorage.add(station)

        assertEquals("42", station.id)
        assertEquals(setOf("42"), mStorage.getAll().map { it.id }.toSet())
        assertTrue(newStorage().isFavorite(makeStation("42")))
    }

    @Test
    fun isFavoriteFallsBackToTheStoredValuesWhenTheCacheIsCold() {
        mStorage.add(makeStation("1"))

        assertTrue(newStorage().isFavorite(makeStation("1")))
    }

    @Test
    fun isFavoriteIsFalseForAStationThatWasNeverAdded() {
        mStorage.add(makeStation("1"))

        assertFalse(mStorage.isFavorite(makeStation("2")))
        assertFalse(newStorage().isFavorite(makeStation("2")))
    }

    @Test
    fun aStoredStationIsLookedUpByItsMediaId() {
        mStorage.add(makeStation("1", name = "Looked up", url = "https://example.test/looked-up"))

        val loaded = newStorage().get("1")

        assertFalse(loaded.isInvalid())
        assertEquals("1", loaded.id)
        assertEquals("Looked up", loaded.name)
        assertEquals("https://example.test/looked-up", loaded.getStreamUrlFixed())
    }

    @Test
    fun lookupOfAMissingStationReturnsTheInvalidInstance() {
        assertTrue(mStorage.get("absent").isInvalid())
    }

    @Test
    fun getAllRenumbersSortIdsInSortOrder() {
        mStorage.add(makeStation("a", sortId = 5))
        mStorage.add(makeStation("b", sortId = 2))
        mStorage.add(makeStation("c", sortId = 9))

        val all = mStorage.getAll().toList()

        assertEquals(listOf("b", "a", "c"), all.map { it.id })
        assertEquals(listOf(0, 1, 2), all.map { it.sortId })
    }

    private fun newStorage(): FavoritesStorage {
        return FavoritesStorage(WeakReference(mContext))
    }
}
