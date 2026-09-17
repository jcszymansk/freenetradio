package com.yuriy.openradio.shared.model.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

/**
 * Covers the merge that backs importing a data file: duplicates, conflicting versions of the same
 * station and inputs that carry nothing usable.
 */
@RunWith(AndroidJUnit4::class)
class StorageManagerLayerTest {

    private lateinit var mContext: Context
    private lateinit var mFavorites: FavoritesStorage
    private lateinit var mLocals: DeviceLocalsStorage
    private lateinit var mLatest: LatestRadioStationStorage
    private lateinit var mLayer: StorageManagerLayer

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mFavorites = FavoritesStorage(WeakReference(mContext))
        mLatest = LatestRadioStationStorage(WeakReference(mContext))
        mLocals = DeviceLocalsStorage(WeakReference(mContext), mFavorites, mLatest)
        mLayer = StorageManagerLayerImpl(mFavorites, mLocals)
        clearAll()
    }

    @After
    fun tearDown() {
        clearAll()
    }

    @Test
    fun mergingIntoAnEmptyStoreAddsEveryIncomingStation() {
        mLayer.mergeFavorites(
            marshall(entry(makeStation("a", sortId = 1)), entry(makeStation("b", sortId = 2)))
        )

        assertEquals(setOf("a", "b"), mFavorites.getAll().map { it.id }.toSet())
        assertTrue(mLocals.getAll().isEmpty())
    }

    @Test
    fun mergingStationsThatAreAlreadyStoredDoesNotDuplicateThem() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1), makeStation("b", sortId = 2)))

        mLayer.mergeFavorites(
            marshall(entry(makeStation("a", sortId = 1)), entry(makeStation("b", sortId = 2)))
        )

        assertEquals(listOf("a", "b"), mFavorites.getAll().map { it.id })
    }

    @Test
    fun theConflictingVersionThatSortsLastIsTheOneThatSurvives() {
        mFavorites.addAll(setOf(makeStation("a", name = "Stored", sortId = 1)))

        mLayer.mergeFavorites(marshall(entry(makeStation("a", name = "Incoming", sortId = 9))))

        val all = mFavorites.getAll()
        assertEquals(1, all.size)
        assertEquals("Incoming", all.first().name)
    }

    @Test
    fun theStoredVersionSurvivesWhenTheIncomingOneSortsFirst() {
        mFavorites.addAll(setOf(makeStation("a", name = "Stored", sortId = 1)))

        mLayer.mergeFavorites(marshall(entry(makeStation("a", name = "Incoming", sortId = -5))))

        val all = mFavorites.getAll()
        assertEquals(1, all.size)
        assertEquals("Stored", all.first().name)
    }

    @Test
    fun mergingAnEmptyInputLeavesTheStoredStationsAlone() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))
        mLocals.addAll(setOf(makeStation("local-1", sortId = 1, isLocal = true)))

        mLayer.mergeFavorites("")
        mLayer.mergeDeviceLocals("")

        assertEquals(listOf("a"), mFavorites.getAll().map { it.id })
        assertEquals(listOf("local-1"), mLocals.getAll().map { it.id })
    }

    @Test
    fun mergingMalformedInputLeavesTheStoredStationsAlone() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))

        mLayer.mergeFavorites(marshall("no-delimiter", entry("broken", "{not json")))

        assertEquals(listOf("a"), mFavorites.getAll().map { it.id })
    }

    @Test
    fun mergingDeviceLocalsDoesNotTouchFavorites() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))

        mLayer.mergeDeviceLocals(marshall(entry(makeStation("local-1", sortId = 1, isLocal = true))))

        assertEquals(listOf("a"), mFavorites.getAll().map { it.id })
        assertEquals(listOf("local-1"), mLocals.getAll().map { it.id })
    }

    @Test
    fun exportedFavoritesCanBeImportedBackAfterTheStoreWasWiped() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1), makeStation("b", sortId = 2)))
        val exported = mLayer.getAllFavoritesAsString()

        mFavorites.clear()
        mLayer.mergeFavorites(exported)

        assertEquals(setOf("a", "b"), mFavorites.getAll().map { it.id }.toSet())
    }

    @Test
    fun exportedDeviceLocalsCarryTheIdCounterAndTheImportIgnoresIt() {
        mLocals.getId()
        mLocals.addAll(setOf(makeStation("local-1", sortId = 1, isLocal = true)))
        val exported = mLayer.getAllDeviceLocalsAsString()

        mLocals.clear()
        mLayer.mergeDeviceLocals(exported)

        assertEquals(listOf("local-1"), mLocals.getAll().map { it.id })
    }

    private fun clearAll() {
        mFavorites.clear()
        mLocals.clear()
        mLatest.clear()
    }
}
