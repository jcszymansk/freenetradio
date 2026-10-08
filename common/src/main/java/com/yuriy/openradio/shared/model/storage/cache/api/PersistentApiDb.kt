/*
 * Copyright 2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Room database behind [PersistentApiCache].
 *
 * Version 2 keys the table on the request instead of an auto-generated id. Version 1 files are
 * discarded rather than migrated: they hold at most a day of responses, and the duplicate rows
 * they accumulated would have to be resolved by guessing which one was meant.
 */
@Database(
    entities = [PersistentApiEntry::class],
    version = 2,
    exportSchema = false
)
abstract class PersistentApiDb : RoomDatabase() {

    abstract fun persistentApiCacheDao(): PersistentApiCacheDao

    companion object {

        /**
         * Default file name for the database.
         */
        const val DATABASE_DEFAULT_FILE_NAME = "PersistentApiCache"

        @Volatile
        private var INSTANCE: PersistentApiDb? = null

        /**
         * Makes and returns default instance of the database. Optionally, database name can be provided as formal
         * parameter. Default one uses otherwise.
         *
         * @param context Context of the callee.
         * @param dbFileName File name of the database. Optional parameter.
         * @return [PersistentApiDb].
         */
        fun getInstance(
            context: Context,
            dbFileName: String = DATABASE_DEFAULT_FILE_NAME
        ): PersistentApiDb =
            INSTANCE ?: synchronized(this) {
                /**
                 * Return instance if not null else build and assign.
                 */
                INSTANCE ?: buildDatabase(context, dbFileName).also { INSTANCE = it }
            }

        /**
         * Builds a database over [dbFileName] that is not shared with [getInstance], configured
         * exactly as the shared one. The caller owns it and must close it.
         *
         * @param context Context of the callee.
         * @param dbFileName File name of the database.
         * @return [PersistentApiDb].
         */
        fun buildDatabase(context: Context, dbFileName: String): PersistentApiDb {
            return Room.databaseBuilder(
                context,
                PersistentApiDb::class.java, dbFileName
            )
                .fallbackToDestructiveMigration()
                .build()
        }

    }
}
