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

package android.os

/**
 * JVM replacement for the Android [Bundle]. The framework class ships as a stub whose accessors
 * silently return defaults, which makes every extras-carrying value object untestable off a device.
 */
class Bundle private constructor(private val mReadOnly: Boolean) {

    private val mValues = LinkedHashMap<String, Any?>()

    constructor() : this(false)

    fun putString(key: String, value: String?) {
        put(key, value)
    }

    fun putInt(key: String, value: Int) {
        put(key, value)
    }

    fun putBoolean(key: String, value: Boolean) {
        put(key, value)
    }

    private fun put(key: String, value: Any?) {
        if (mReadOnly) {
            throw UnsupportedOperationException("Bundle.EMPTY is read-only")
        }
        mValues[key] = value
    }

    fun getString(key: String): String? {
        return mValues[key] as? String
    }

    fun getString(key: String, defaultValue: String): String {
        return mValues[key] as? String ?: defaultValue
    }

    fun getInt(key: String): Int {
        return getInt(key, 0)
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return mValues[key] as? Int ?: defaultValue
    }

    fun getBoolean(key: String): Boolean {
        return getBoolean(key, false)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return mValues[key] as? Boolean ?: defaultValue
    }

    fun get(key: String): Any? {
        return mValues[key]
    }

    fun containsKey(key: String): Boolean {
        return mValues.containsKey(key)
    }

    fun keySet(): Set<String> {
        return mValues.keys
    }

    fun size(): Int {
        return mValues.size
    }

    fun isEmpty(): Boolean {
        return mValues.isEmpty()
    }

    override fun toString(): String {
        return "Bundle$mValues"
    }

    companion object {
        /**
         * The shared read-only empty bundle, as on Android. Media3's `PlaybackException`
         * constructor reads it, so JVM tests that build one need it to exist.
         */
        @JvmField
        val EMPTY: Bundle = Bundle(true)
    }
}
