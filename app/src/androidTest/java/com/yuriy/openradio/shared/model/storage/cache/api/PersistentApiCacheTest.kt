package com.yuriy.openradio.shared.model.storage.cache.api

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the Room backed API response cache. [PersistentApiDb] hands out a single instance per
 * process regardless of the file name it is asked for, so these tests share the database the
 * application itself opened and wipe it around every case.
 */
@RunWith(AndroidJUnit4::class)
class PersistentApiCacheTest {

    private lateinit var mCache: PersistentApiCache
    private lateinit var mDao: PersistentApiCacheDao

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        mCache = PersistentApiCache(context, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME)
        mDao = PersistentApiDb.getInstance(context, PersistentApiDb.DATABASE_DEFAULT_FILE_NAME)
            .persistentApiCacheDao()
        mCache.clear()
    }

    @After
    fun tearDown() {
        mCache.clear()
    }

    @Test
    fun readingAnUnknownKeyYieldsAnEmptyValue() {
        assertEquals("", mCache[KEY])
    }

    @Test
    fun storedDataIsReadBack() {
        mCache.put(KEY, PAYLOAD)

        assertEquals(PAYLOAD, mCache[KEY])
        assertEquals(1, mDao.getCount())
    }

    @Test
    fun anEmptyPayloadIsIndistinguishableFromAMiss() {
        mCache.put(KEY, "")

        assertEquals("", mCache[KEY])
        assertEquals(1, mDao.getCount())
    }

    @Test
    fun removeDropsOnlyTheRequestedKey() {
        mCache.put(KEY, PAYLOAD)
        mCache.put(OTHER_KEY, OTHER_PAYLOAD)

        mCache.remove(KEY)

        assertEquals("", mCache[KEY])
        assertEquals(OTHER_PAYLOAD, mCache[OTHER_KEY])
    }

    @Test
    fun removingAnUnknownKeyChangesNothing() {
        mCache.put(KEY, PAYLOAD)

        mCache.remove(OTHER_KEY)

        assertEquals(PAYLOAD, mCache[KEY])
        assertEquals(1, mDao.getCount())
    }

    @Test
    fun clearDropsEveryRecord() {
        mCache.put(KEY, PAYLOAD)
        mCache.put(OTHER_KEY, OTHER_PAYLOAD)

        mCache.clear()

        assertEquals("", mCache[KEY])
        assertEquals("", mCache[OTHER_KEY])
        assertEquals(0, mDao.getCount())
    }

    @Test
    fun aRecordWrittenJustNowIsStillServed() {
        insertAged(KEY, PAYLOAD, ageMillis = 1_000L)

        assertEquals(PAYLOAD, mCache[KEY])
    }

    @Test
    fun aRecordOlderThanADayIsNotServed() {
        insertAged(KEY, PAYLOAD, ageMillis = DAY_MILLIS + 60_000L)

        assertEquals("", mCache[KEY])
    }

    @Test
    fun theFreshnessWindowIsCurrentlyMeasuredInMilliseconds() {
        // Pins the defect tracked as TASK-024: SEC_IN_DAY is 86400 and the comparison is against a
        // millisecond difference, so a record goes stale after 86.4 seconds rather than a day.
        // Update this test together with the fix.
        insertAged(KEY, PAYLOAD, ageMillis = 60_000L)
        insertAged(OTHER_KEY, OTHER_PAYLOAD, ageMillis = 120_000L)

        assertEquals(PAYLOAD, mCache[KEY])
        assertEquals("", mCache[OTHER_KEY])
    }

    @Test
    fun writingTheSameKeyTwiceCurrentlyAppendsARowInsteadOfReplacingIt() {
        // Pins the defect tracked as TASK-025: the table is keyed on an auto-generated id and the
        // name column is unconstrained, so a second write adds a row and the read, which takes the
        // first match, keeps serving the older payload. Update this test together with the fix.
        mCache.put(KEY, PAYLOAD)
        mCache.put(KEY, OTHER_PAYLOAD)

        assertEquals(2, mDao.getCount())
        assertEquals(PAYLOAD, mCache[KEY])
    }

    private fun insertAged(key: String, data: String, ageMillis: Long) {
        mDao.insert(
            PersistentApiEntry(
                name = key, data = data, timestamp = System.currentTimeMillis() - ageMillis
            )
        )
    }

    private companion object {

        const val KEY = "https://example.test/api/stations"

        const val OTHER_KEY = "https://example.test/api/countries"

        const val PAYLOAD = "[{\"name\":\"first\"}]"

        const val OTHER_PAYLOAD = "[{\"name\":\"second\"}]"

        const val DAY_MILLIS = 86_400_000L
    }
}
