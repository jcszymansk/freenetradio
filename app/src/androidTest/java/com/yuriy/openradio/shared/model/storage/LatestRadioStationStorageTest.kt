package com.yuriy.openradio.shared.model.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.media.isInvalid
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

@RunWith(AndroidJUnit4::class)
class LatestRadioStationStorageTest {

    private lateinit var mContext: Context
    private lateinit var mStorage: LatestRadioStationStorage

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
    fun anEmptyStorageReportsTheInvalidInstance() {
        assertTrue(newStorage().get().isInvalid())
    }

    @Test
    fun theSavedStationIsReturnedBack() {
        mStorage.add(makeStation("1", name = "Latest"))

        val loaded = mStorage.get()

        assertEquals("1", loaded.id)
        assertEquals("Latest", loaded.name)
    }

    @Test
    fun theSavedStationIsReloadedByANewInstance() {
        mStorage.add(makeStation("1", name = "Latest", url = "https://example.test/latest"))

        val loaded = newStorage().get()

        assertEquals("1", loaded.id)
        assertEquals("Latest", loaded.name)
        assertEquals("https://example.test/latest", loaded.getStreamUrlFixed())
    }

    @Test
    fun onlyTheMostRecentStationIsKept() {
        mStorage.add(makeStation("1", name = "First"))
        mStorage.add(makeStation("2", name = "Second"))

        assertEquals("2", newStorage().get().id)
        assertEquals(1, newStorage().getAll().size)
    }

    @Test
    fun clearRemovesThePersistedStation() {
        mStorage.add(makeStation("1"))

        mStorage.clear()

        assertTrue(newStorage().get().isInvalid())
    }

    @Test
    fun clearDoesNotResetTheInMemoryCacheOfTheSameInstance() {
        // Pins current behaviour: the cached value outlives clear() on the instance that holds it,
        // so callers that need a cleared value have to read through a fresh storage.
        mStorage.add(makeStation("1"))

        mStorage.clear()

        assertEquals("1", mStorage.get().id)
    }

    private fun newStorage(): LatestRadioStationStorage {
        return LatestRadioStationStorage(WeakReference(mContext))
    }
}
