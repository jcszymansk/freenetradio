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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how [AppPreferencesManager] reads and migrates the start-playback setting and the
 * "was playing at exit" flag. Each test builds its own in-memory preference file, so no test
 * sees another test's values.
 */
class AppPreferencesManagerStartPlaybackTest {

    private val fileName = "OpenRadioPref"
    private val oldKey = "LAST_KNOWN_RADIO_STATION_ENABLED"
    private val newKey = "START_PLAYBACK"
    private val wasPlayingKey = "WAS_PLAYING_AT_EXIT"

    private fun contextWith(values: Map<String, Any>): Context {
        return preferencesContext(mapOf(fileName to values))
    }

    private fun storeOf(context: Context) = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    @Test
    fun noStoredSettingMigratesToIfPlaying() {
        val context = contextWith(emptyMap())

        assertEquals(StartPlayback.IF_PLAYING, AppPreferencesManager.startPlayback(context))
        assertEquals("IF_PLAYING", storeOf(context).getString(newKey, null))
    }

    @Test
    fun oldSettingFalseMigratesToNever() {
        val context = contextWith(mapOf(oldKey to false))

        assertEquals(StartPlayback.NEVER, AppPreferencesManager.startPlayback(context))
        assertEquals("NEVER", storeOf(context).getString(newKey, null))
        assertFalse(storeOf(context).contains(oldKey))
    }

    @Test
    fun oldSettingTrueMigratesToAlways() {
        val context = contextWith(mapOf(oldKey to true))

        assertEquals(StartPlayback.ALWAYS, AppPreferencesManager.startPlayback(context))
        assertEquals("ALWAYS", storeOf(context).getString(newKey, null))
        assertFalse(storeOf(context).contains(oldKey))
    }

    @Test
    fun migrationRunsOnlyOnce() {
        val context = contextWith(mapOf(oldKey to true))
        assertEquals(StartPlayback.ALWAYS, AppPreferencesManager.startPlayback(context))

        storeOf(context).edit().putBoolean(oldKey, false).apply()

        assertEquals(StartPlayback.ALWAYS, AppPreferencesManager.startPlayback(context))
        assertTrue(storeOf(context).contains(oldKey))
    }

    @Test
    fun storedSettingTakesPrecedenceOverOldSetting() {
        val context = contextWith(mapOf(newKey to "NEVER", oldKey to true))

        assertEquals(StartPlayback.NEVER, AppPreferencesManager.startPlayback(context))
        assertTrue(storeOf(context).contains(oldKey))
    }

    @Test
    fun eachSettingIsReadBackAsWritten() {
        val context = contextWith(emptyMap())

        for (value in StartPlayback.entries) {
            AppPreferencesManager.startPlayback(context, value)

            assertEquals(value, AppPreferencesManager.startPlayback(context))
            assertEquals(value.name, storeOf(context).getString(newKey, null))
        }
    }

    @Test
    fun unknownStoredSettingReadsAsIfPlaying() {
        val context = contextWith(mapOf(newKey to "SOMETIMES"))

        assertEquals(StartPlayback.IF_PLAYING, AppPreferencesManager.startPlayback(context))
        assertEquals("SOMETIMES", storeOf(context).getString(newKey, null))
    }

    @Test
    fun wasPlayingAtExitDefaultsToFalse() {
        val context = contextWith(emptyMap())

        assertFalse(AppPreferencesManager.wasPlayingAtExit(context))
    }

    @Test
    fun wasPlayingAtExitIsReadBackAsWritten() {
        val context = contextWith(emptyMap())

        AppPreferencesManager.wasPlayingAtExit(context, true)
        assertTrue(AppPreferencesManager.wasPlayingAtExit(context))
        assertTrue(storeOf(context).getBoolean(wasPlayingKey, false))

        AppPreferencesManager.wasPlayingAtExit(context, false)
        assertFalse(AppPreferencesManager.wasPlayingAtExit(context))
    }
}
