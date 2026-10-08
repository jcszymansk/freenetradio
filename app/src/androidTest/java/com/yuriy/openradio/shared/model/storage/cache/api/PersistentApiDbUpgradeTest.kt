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

package com.yuriy.openradio.shared.model.storage.cache.api

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens a version 1 API cache file, written the way the version 1 schema stored it, through the
 * current [PersistentApiDb] configuration. The file lives under its own name so the database the
 * application holds open is never touched.
 */
@RunWith(AndroidJUnit4::class)
class PersistentApiDbUpgradeTest {

    private lateinit var mContext: Context

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mContext.deleteDatabase(DB_FILE_NAME)
    }

    @After
    fun tearDown() {
        mContext.deleteDatabase(DB_FILE_NAME)
    }

    @Test
    fun aVersionOneFileWithDuplicateRowsIsDiscardedOnOpen() {
        writeVersionOneFileWithDuplicates()

        val db = PersistentApiDb.buildDatabase(mContext, DB_FILE_NAME)
        try {
            val dao = db.persistentApiCacheDao()

            assertEquals(0, dao.getCount())
            assertNull(dao.getRecord(KEY))
        } finally {
            db.close()
        }
    }

    @Test
    fun theUpgradedFileReplacesARecordWrittenTwice() {
        writeVersionOneFileWithDuplicates()

        val db = PersistentApiDb.buildDatabase(mContext, DB_FILE_NAME)
        try {
            val dao = db.persistentApiCacheDao()
            dao.insert(PersistentApiEntry(name = KEY, data = OLD_PAYLOAD, timestamp = 1L))
            dao.insert(PersistentApiEntry(name = KEY, data = NEW_PAYLOAD, timestamp = 2L))

            assertEquals(1, dao.getCount())
            assertEquals(NEW_PAYLOAD, dao.getRecord(KEY)?.data)
        } finally {
            db.close()
        }
    }

    /**
     * Seeds [DB_FILE_NAME] with the version 1 table and identity row exactly as Room 2.6.1
     * generated them, holding two rows for [KEY] as the unkeyed table accumulated them.
     */
    private fun writeVersionOneFileWithDuplicates() {
        val legacy = mContext.openOrCreateDatabase(DB_FILE_NAME, Context.MODE_PRIVATE, null)
        try {
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `apicache` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`name` TEXT NOT NULL, `data` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
            )
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"
            )
            legacy.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                    "VALUES(42, '$VERSION_ONE_IDENTITY_HASH')"
            )
            listOf(OLD_PAYLOAD, NEW_PAYLOAD).forEachIndexed { index, payload ->
                val row = ContentValues()
                row.put("name", KEY)
                row.put("data", payload)
                row.put("timestamp", System.currentTimeMillis() + index)
                legacy.insertOrThrow("apicache", null, row)
            }
            legacy.version = 1
            assertEquals(2L, DatabaseUtils.queryNumEntries(legacy, "apicache"))
        } finally {
            legacy.close()
        }
    }

    private companion object {

        const val DB_FILE_NAME = "PersistentApiDbUpgradeTest"

        const val KEY = "$UNREACHABLE_ORIGIN/api/stations"

        const val OLD_PAYLOAD = "[{\"name\":\"old\"}]"

        const val NEW_PAYLOAD = "[{\"name\":\"new\"}]"

        /**
         * The identity Room computed for the version 1 schema, so the seeded file is one the old
         * build would have opened without complaint.
         */
        const val VERSION_ONE_IDENTITY_HASH = "50d268283fe3f815249c06a4c7b6ab3b"
    }
}
