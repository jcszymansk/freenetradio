package com.yuriy.openradio.shared.model.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.media.isInvalid
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

@RunWith(AndroidJUnit4::class)
class DeviceLocalsStorageTest {

    private lateinit var mContext: Context
    private lateinit var mFavorites: FavoritesStorage
    private lateinit var mLatest: LatestRadioStationStorage
    private lateinit var mStorage: DeviceLocalsStorage

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mFavorites = FavoritesStorage(WeakReference(mContext))
        mLatest = LatestRadioStationStorage(WeakReference(mContext))
        mStorage = newStorage()
        clearAll()
    }

    @After
    fun tearDown() {
        clearAll()
    }

    @Test
    fun theFirstAllocatedIdIsTheInitialValueAndTheNextOnesIncrement() {
        val first = mStorage.getId().toInt()

        assertEquals(first + 1, mStorage.getId().toInt())
        assertEquals(first + 2, mStorage.getId().toInt())
    }

    @Test
    fun theIdCounterSurvivesANewStorageInstance() {
        val first = mStorage.getId().toInt()

        assertEquals(first + 1, newStorage().getId().toInt())
    }

    @Test
    fun theIdCounterIsNotReportedAsAStation() {
        mStorage.getId()
        mStorage.add(makeStation("local-1", isLocal = true))

        assertEquals(setOf("local-1"), mStorage.getAll().map { it.id }.toSet())
    }

    @Test
    fun addedStationIsReadBackByItsMediaId() {
        mStorage.add(makeStation("local-1", name = "Local one", isLocal = true))

        val loaded = mStorage["local-1"]

        assertEquals("Local one", loaded.name)
        assertTrue(loaded.isLocal)
    }

    @Test
    fun lookupOfAMissingStationReturnsTheInvalidInstance() {
        mStorage.add(makeStation("local-1", isLocal = true))

        assertTrue(mStorage["local-2"].isInvalid())
    }

    @Test
    fun removeDropsTheStation() {
        val station = makeStation("local-1", isLocal = true)
        mStorage.add(station)

        mStorage.remove(station)

        assertTrue(mStorage.getAll().isEmpty())
    }

    @Test
    fun updateRewritesEveryEditableField() {
        mStorage.add(makeStation("local-1", name = "Before", isLocal = true))

        val updated = mStorage.update(
            "local-1", "After", "$UNREACHABLE_ORIGIN/after",
            "$UNREACHABLE_ORIGIN/after.png", "$UNREACHABLE_ORIGIN/home", "Jazz", "PL", false
        )

        assertTrue(updated)
        val loaded = mStorage["local-1"]
        assertEquals("After", loaded.name)
        assertEquals("$UNREACHABLE_ORIGIN/after", loaded.getStreamUrlFixed())
        assertEquals("$UNREACHABLE_ORIGIN/after.png", loaded.imageUrl)
        assertEquals("$UNREACHABLE_ORIGIN/home", loaded.homePage)
        assertEquals("Jazz", loaded.genre)
        assertEquals("PL", loaded.country)
    }

    @Test
    fun updateReplacesNullOptionalsWithEmptyValues() {
        mStorage.add(makeStation("local-1", isLocal = true))

        mStorage.update("local-1", "Name", "$UNREACHABLE_ORIGIN/x", null, null, null, null, false)

        val loaded = mStorage["local-1"]
        assertEquals("", loaded.imageUrl)
        assertEquals("", loaded.homePage)
        assertEquals("", loaded.genre)
        assertEquals("", loaded.country)
    }

    @Test
    fun updateOfAnUnknownMediaIdChangesNothing() {
        mStorage.add(makeStation("local-1", name = "Before", isLocal = true))

        val updated = mStorage.update(
            "local-2", "After", "$UNREACHABLE_ORIGIN/after", null, null, null, null, false
        )

        assertFalse(updated)
        assertEquals("Before", mStorage["local-1"].name)
    }

    @Test
    fun updateAddsTheStationToFavoritesWhenRequested() {
        mStorage.add(makeStation("local-1", isLocal = true))

        mStorage.update(
            "local-1", "After", "$UNREACHABLE_ORIGIN/after", null, null, null, null, true
        )

        val favorites = mFavorites.getAll()
        assertEquals(setOf("local-1"), favorites.map { it.id }.toSet())
        assertEquals("After", favorites.first().name)
    }

    @Test
    fun updateDropsTheStationFromFavoritesWhenItIsNoLongerRequested() {
        val station = makeStation("local-1", isLocal = true)
        mStorage.add(station)
        mFavorites.add(makeStation("local-1", isLocal = true))

        mStorage.update(
            "local-1", "After", "$UNREACHABLE_ORIGIN/after", null, null, null, null, false
        )

        assertTrue(mFavorites.getAll().isEmpty())
        assertFalse(mFavorites.isFavorite(station))
    }

    @Test
    fun updatePropagatesToTheLatestStationWhenItIsTheSameOne() {
        mStorage.add(makeStation("local-1", name = "Before", isLocal = true))
        mLatest.add(makeStation("local-1", name = "Before", isLocal = true))

        mStorage.update(
            "local-1", "After", "$UNREACHABLE_ORIGIN/after", null, null, null, null, false
        )

        assertEquals("After", mLatest.get().name)
        assertEquals("After", LatestRadioStationStorage(WeakReference(mContext)).get().name)
    }

    @Test
    fun updateLeavesTheLatestStationAloneWhenItIsADifferentOne() {
        mStorage.add(makeStation("local-1", name = "Before", isLocal = true))
        mLatest.add(makeStation("other-2", name = "Other", isLocal = true))

        mStorage.update(
            "local-1", "After", "$UNREACHABLE_ORIGIN/after", null, null, null, null, false
        )

        assertEquals("Other", mLatest.get().name)
        assertEquals("other-2", mLatest.get().id)
    }

    private fun newStorage(): DeviceLocalsStorage {
        return DeviceLocalsStorage(WeakReference(mContext), mFavorites, mLatest)
    }

    private fun clearAll() {
        mStorage.clear()
        mFavorites.clear()
        mLatest.clear()
    }
}
