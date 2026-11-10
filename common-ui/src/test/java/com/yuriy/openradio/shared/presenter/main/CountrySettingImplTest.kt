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

package com.yuriy.openradio.shared.presenter.main

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.location.Country
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * Covers what a country change does to the main screen: the code is stored, the service is told
 * to drop the root it built for the old country, and only once it has done so does the screen
 * read the root again.
 */
class CountrySettingImplTest {

    // Held here because the storage keeps its context only weakly.
    private val mContext: Context = StringPreferencesContext()
    private val mStorage = LocationStorage(WeakReference(mContext))

    @Test
    fun anUnsetCountryIsTheDefault() {
        val setting = CountrySettingImpl(mStorage, ModelUnderTest().model, CoroutineScope(Dispatchers.Unconfined))

        assertEquals(Country.COUNTRY_CODE_DEFAULT, setting.countryCode())
    }

    @Test
    fun aNewCountryIsStoredAndReadBack() {
        val m = ModelUnderTest().startAndConnect()
        val setting = CountrySettingImpl(mStorage, m.model, CoroutineScope(Dispatchers.Unconfined))

        setting.setCountryCode("PL")

        assertEquals("PL", setting.countryCode())
        assertEquals("PL", LocationStorage(WeakReference(mContext)).getCountryCode())
    }

    @Test
    fun aNewCountryRebuildsTheTreeAndThenReadsTheRootAgain() {
        val m = ModelUnderTest().startAndConnect()
        val setting = CountrySettingImpl(mStorage, m.model, CoroutineScope(Dispatchers.Unconfined))
        val fetchesBefore = m.client.fetches.size

        setting.setCountryCode("PL")

        assertEquals(OpenRadioService.CMD_UPDATE_TREE, m.client.commands.last().first)
        assertEquals(listOf(MediaId.MEDIA_ID_ROOT), m.client.fetches.drop(fetchesBefore))
    }

    @Test
    fun withNoServiceTheCountryIsStoredAndTheRootIsNotRead() {
        val m = ModelUnderTest().startAndConnect()
        m.model.stop()
        val setting = CountrySettingImpl(mStorage, m.model, CoroutineScope(Dispatchers.Unconfined))
        val commandsBefore = m.client.commands.size
        val fetchesBefore = m.client.fetches.size

        setting.setCountryCode("PL")

        assertEquals("PL", setting.countryCode())
        assertEquals("No service took the command", commandsBefore, m.client.commands.size)
        assertEquals("The root is not read without a service", fetchesBefore, m.client.fetches.size)
    }
}

/**
 * A context whose preference files keep the strings written to them. `:common` has a fuller fake,
 * but its test classes are internal to that module; [LocationStorage] stores one string, so this
 * holds strings only and fails on anything else.
 */
private class StringPreferencesContext : ContextWrapper(null) {

    private val mFiles = HashMap<String, StringPreferences>()

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        return mFiles.getOrPut(name ?: "") { StringPreferences() }
    }
}

private class StringPreferences : SharedPreferences {

    private val mValues = HashMap<String, String>()

    override fun getString(key: String?, defValue: String?): String? = mValues[key] ?: defValue

    override fun contains(key: String?): Boolean = mValues.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun getAll(): MutableMap<String, *> = unsupported()

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String> = unsupported()

    override fun getInt(key: String?, defValue: Int): Int = unsupported()

    override fun getLong(key: String?, defValue: Long): Long = unsupported()

    override fun getFloat(key: String?, defValue: Float): Float = unsupported()

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = unsupported()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = unsupported()

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = unsupported()

    private inner class Editor : SharedPreferences.Editor {

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            mValues[checkNotNull(key)] = checkNotNull(value)
            return this
        }

        override fun apply() = Unit

        override fun commit(): Boolean = true

        override fun putStringSet(key: String?, values: MutableSet<String>?) = unsupported()

        override fun putInt(key: String?, value: Int) = unsupported()

        override fun putLong(key: String?, value: Long) = unsupported()

        override fun putFloat(key: String?, value: Float) = unsupported()

        override fun putBoolean(key: String?, value: Boolean) = unsupported()

        override fun remove(key: String?) = unsupported()

        override fun clear() = unsupported()
    }
}

private fun unsupported(): Nothing {
    throw UnsupportedOperationException("CountrySettingImpl's storage holds one string")
}
