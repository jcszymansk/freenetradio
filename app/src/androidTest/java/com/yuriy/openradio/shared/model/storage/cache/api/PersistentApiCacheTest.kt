package com.yuriy.openradio.shared.model.storage.cache.api

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
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
    fun aRecordAnHourOldIsStillServed() {
        insertAged(KEY, PAYLOAD, ageMillis = HOUR_MILLIS)

        assertEquals(PAYLOAD, mCache[KEY])
    }

    @Test
    fun theFreshnessWindowClosesOneDayAfterTheWrite() {
        // The records sit a minute either side of the boundary, which keeps the fresh one inside
        // the window however long the read takes to schedule. The exact millisecond boundary is
        // pinned by ApiCacheFreshnessTest; this case proves the cache applies that rule to the
        // timestamp Room hands back.
        insertAged(KEY, PAYLOAD, ageMillis = DAY_MILLIS - BOUNDARY_MARGIN_MILLIS)
        insertAged(OTHER_KEY, OTHER_PAYLOAD, ageMillis = DAY_MILLIS + BOUNDARY_MARGIN_MILLIS)

        assertEquals(PAYLOAD, mCache[KEY])
        assertEquals("", mCache[OTHER_KEY])
    }

    @Test
    fun aRecordDatedInTheFutureIsNotServed() {
        insertAged(KEY, PAYLOAD, ageMillis = -HOUR_MILLIS)

        assertEquals("", mCache[KEY])
    }

    @Test
    fun writingTheSameKeyTwiceLeavesOneRow() {
        mCache.put(KEY, PAYLOAD)
        mCache.put(KEY, OTHER_PAYLOAD)

        assertEquals(1, mDao.getCount())
    }

    @Test
    fun aReadAfterASecondWriteReturnsTheValueWrittenLast() {
        mCache.put(KEY, PAYLOAD)
        mCache.put(KEY, OTHER_PAYLOAD)

        assertEquals(OTHER_PAYLOAD, mCache[KEY])
    }

    @Test
    fun aWriteReplacesAStaleRecordWithAFreshOne() {
        insertAged(KEY, PAYLOAD, ageMillis = DAY_MILLIS + 60_000L)

        mCache.put(KEY, OTHER_PAYLOAD)

        assertEquals(OTHER_PAYLOAD, mCache[KEY])
        assertEquals(1, mDao.getCount())
    }

    @Test
    fun rewritingOneKeyLeavesTheOthersAlone() {
        mCache.put(KEY, PAYLOAD)
        mCache.put(OTHER_KEY, OTHER_PAYLOAD)

        mCache.put(KEY, THIRD_PAYLOAD)

        assertEquals(THIRD_PAYLOAD, mCache[KEY])
        assertEquals(OTHER_PAYLOAD, mCache[OTHER_KEY])
        assertEquals(2, mDao.getCount())
    }

    private fun insertAged(key: String, data: String, ageMillis: Long) {
        mDao.insert(
            PersistentApiEntry(
                name = key, data = data, timestamp = System.currentTimeMillis() - ageMillis
            )
        )
    }

    private companion object {

        const val KEY = "$UNREACHABLE_ORIGIN/api/stations"

        const val OTHER_KEY = "$UNREACHABLE_ORIGIN/api/countries"

        const val PAYLOAD = "[{\"name\":\"first\"}]"

        const val OTHER_PAYLOAD = "[{\"name\":\"second\"}]"

        const val THIRD_PAYLOAD = "[{\"name\":\"third\"}]"

        const val DAY_MILLIS = 86_400_000L

        const val HOUR_MILLIS = 3_600_000L

        /**
         * Distance either side of the boundary each record is placed at.
         */
        const val BOUNDARY_MARGIN_MILLIS = 60_000L
    }
}
