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

package com.yuriy.openradio.shared.model.storage

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * The fake behind [preferencesContext] decides what every JVM test built on it can observe, so the
 * properties those tests lean on are pinned here: a write is readable, a file is one store, two
 * files are two stores, and a missing key answers with the caller's default.
 */
class InMemoryPreferencesTest {

    @Test
    fun aWrittenValueIsReadBack() {
        val storage = TestStorage(preferencesContext())

        storage.putBooleanValue(KEY, true)
        storage.putIntValue("count", 7)
        storage.putStringValue("name", "station")
        storage.putLongValue("at", 42L)

        assertTrue(storage.getBooleanValue(KEY, false))
        assertEquals(7, storage.getIntValue("count", 0))
        assertEquals("station", storage.getStringValue("name", ""))
        assertEquals(42L, storage.getLongValue("at", 0L))
    }

    @Test
    fun anUnwrittenKeyAnswersWithTheDefault() {
        val storage = TestStorage(preferencesContext())

        assertTrue(storage.getBooleanValue(KEY, true))
        assertFalse(storage.getBooleanValue(KEY, false))
        assertEquals(-1, storage.getIntValue(KEY, -1))
        assertEquals("fallback", storage.getStringValue(KEY, "fallback"))
    }

    @Test
    fun aSeededFileIsReadableWithoutWritingToIt() {
        val context = preferencesContext(mapOf(FILE_NAME to mapOf(KEY to false)))

        assertFalse(TestStorage(context).getBooleanValue(KEY, true))
    }

    @Test
    fun theSameFileIsTheSameStoreAndDifferentFilesAreNot() {
        val context = preferencesContext()

        assertSame(
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        )
        assertNotSame(
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
            context.getSharedPreferences("Other", Context.MODE_PRIVATE)
        )
    }

    @Test
    fun clearingOneFileLeavesTheOtherAlone() {
        val context = preferencesContext()
        val storage = TestStorage(context)
        val other = TestStorage(context, "Other")
        storage.putBooleanValue(KEY, true)
        other.putBooleanValue(KEY, true)

        storage.clear()

        assertFalse(storage.getBooleanValue(KEY, false))
        assertTrue(other.getBooleanValue(KEY, false))
    }

    @Test
    fun aValueStoredUnderAnotherTypeFallsBackToTheDefault() {
        val storage = TestStorage(preferencesContext())

        storage.putStringValue(KEY, "not a number")

        assertEquals(-1, storage.getIntValue(KEY, -1))
    }

    private class TestStorage(context: Context, name: String = FILE_NAME) :
        AbstractStorage(WeakReference(context), name)

    private companion object {

        const val FILE_NAME = "InMemoryPreferencesTest"

        const val KEY = "key"
    }
}
