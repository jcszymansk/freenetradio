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
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * A [Context] whose preference files keep what is written to them, for JVM tests of code that
 * reads an [AbstractStorage].
 *
 * Without one, `ContextWrapper(null).getSharedPreferences` answers `null` under
 * `unitTests.returnDefaultValues`, every [AbstractStorage] read falls back to its default, and a
 * stored value can only ever be the default. That is enough to assert a default and nothing else.
 *
 * This is a fake for a dependency, not a second implementation of it: what Android's own
 * preferences do with files, types, and processes is the subject of the instrumented storage
 * tests, and nothing here should be read as a claim about it.
 *
 * @param mFiles the preference files to start from, keyed by file name. A file that is not listed
 *               starts empty.
 */
internal fun preferencesContext(mFiles: Map<String, Map<String, Any>> = emptyMap()): Context {
    val stores = HashMap<String, InMemoryPreferences>()
    for ((name, values) in mFiles) {
        stores[name] = InMemoryPreferences(values)
    }
    return object : ContextWrapper(null) {

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return stores.getOrPut(name ?: "") { InMemoryPreferences(emptyMap()) }
        }
    }
}

/**
 * The store behind [preferencesContext]. Writes land immediately, so `apply` and `commit` are the
 * same thing here; production code only ever uses `apply`.
 */
internal class InMemoryPreferences(values: Map<String, Any>) : SharedPreferences {

    private val mValues = LinkedHashMap<String, Any>(values)

    override fun getAll(): MutableMap<String, *> {
        return LinkedHashMap(mValues)
    }

    override fun getString(key: String?, defValue: String?): String? {
        return mValues[key] as? String ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return mValues[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return mValues[key] as? Int ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return mValues[key] as? Long ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return mValues[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return mValues[key] as? Boolean ?: defValue
    }

    override fun contains(key: String?): Boolean {
        return mValues.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return Editor()
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        throw UnsupportedOperationException("No test observes preference changes")
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        throw UnsupportedOperationException("No test observes preference changes")
    }

    private inner class Editor : SharedPreferences.Editor {

        override fun putString(key: String?, value: String?) = put(key, value)

        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values)

        override fun putInt(key: String?, value: Int) = put(key, value)

        override fun putLong(key: String?, value: Long) = put(key, value)

        override fun putFloat(key: String?, value: Float) = put(key, value)

        override fun putBoolean(key: String?, value: Boolean) = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            mValues.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            mValues.clear()
            return this
        }

        override fun commit(): Boolean {
            return true
        }

        override fun apply() {
            /* Writes already landed. */
        }

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            if (key == null) {
                return this
            }
            if (value == null) {
                mValues.remove(key)
            } else {
                mValues[key] = value
            }
            return this
        }
    }
}
