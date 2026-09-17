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

package android.net

import java.nio.charset.StandardCharsets

/**
 * JVM replacement for the Android [Uri]. The framework class ships as a stub whose builder hands
 * back null, so anything composing a content Uri - station artwork, and therefore every playable
 * media item - cannot be built off a device. Only the hierarchical form the project uses is
 * modelled: scheme, authority, path, query and fragment.
 */
class Uri private constructor(
    private val mScheme: String?,
    private val mAuthority: String?,
    private val mPath: String,
    private val mQuery: String?,
    private val mFragment: String?
) : Comparable<Uri> {

    fun getScheme(): String? = mScheme

    fun getAuthority(): String? = mAuthority

    fun getPath(): String = mPath

    fun getQuery(): String? = mQuery

    fun getFragment(): String? = mFragment

    fun getLastPathSegment(): String? {
        return mPath.split(PATH_SEPARATOR).lastOrNull { it.isNotEmpty() }?.let { decode(it) }
    }

    fun getQueryParameter(key: String): String? {
        val query = mQuery ?: return null
        for (pair in query.split(QUERY_SEPARATOR)) {
            if (pair.isEmpty()) {
                continue
            }
            val separator = pair.indexOf(VALUE_SEPARATOR)
            val name = if (separator < 0) pair else pair.substring(0, separator)
            if (decode(name) != key) {
                continue
            }
            return if (separator < 0) "" else decode(pair.substring(separator + 1))
        }
        return null
    }

    fun buildUpon(): Builder {
        return Builder(mScheme, mAuthority, mPath, mQuery, mFragment)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        if (mScheme != null) {
            builder.append(mScheme).append(SCHEME_SEPARATOR)
        }
        if (mAuthority != null) {
            builder.append(AUTHORITY_PREFIX).append(mAuthority)
        }
        builder.append(mPath)
        if (mQuery != null) {
            builder.append(QUERY_PREFIX).append(mQuery)
        }
        if (mFragment != null) {
            builder.append(FRAGMENT_PREFIX).append(mFragment)
        }
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        return other is Uri && toString() == other.toString()
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun compareTo(other: Uri): Int {
        return toString().compareTo(other.toString())
    }

    class Builder internal constructor(
        private var mScheme: String?,
        private var mAuthority: String?,
        private var mPath: String,
        private var mQuery: String?,
        private var mFragment: String?
    ) {

        constructor() : this(null, null, "", null, null)

        fun scheme(scheme: String?): Builder {
            mScheme = scheme
            return this
        }

        fun authority(authority: String?): Builder {
            mAuthority = authority
            return this
        }

        fun appendPath(segment: String): Builder {
            if (!mPath.startsWith(PATH_SEPARATOR)) {
                mPath = PATH_SEPARATOR + mPath
            }
            if (!mPath.endsWith(PATH_SEPARATOR)) {
                mPath += PATH_SEPARATOR
            }
            mPath += encode(segment)
            return this
        }

        fun appendQueryParameter(key: String, value: String?): Builder {
            val pair = encode(key) + VALUE_SEPARATOR + encode(value ?: "")
            mQuery = if (mQuery.isNullOrEmpty()) pair else mQuery + QUERY_SEPARATOR + pair
            return this
        }

        fun build(): Uri {
            return Uri(mScheme, mAuthority, mPath, mQuery, mFragment)
        }

        override fun toString(): String {
            return build().toString()
        }
    }

    companion object {

        private const val SCHEME_SEPARATOR = ":"
        private const val AUTHORITY_PREFIX = "//"
        private const val QUERY_PREFIX = "?"
        private const val FRAGMENT_PREFIX = "#"
        private const val PATH_SEPARATOR = "/"
        private const val QUERY_SEPARATOR = "&"
        private const val VALUE_SEPARATOR = "="
        private const val SCHEME_SYMBOLS = "+-."
        private const val UNRESERVED_SYMBOLS = "_-!.~'()*"
        private const val ESCAPE = '%'
        private const val HEX_RADIX = 16
        private const val HEX_LENGTH = 2

        @JvmStatic
        fun parse(uriString: String): Uri {
            var rest = uriString
            var fragment: String? = null
            val fragmentIndex = rest.indexOf(FRAGMENT_PREFIX)
            if (fragmentIndex >= 0) {
                fragment = rest.substring(fragmentIndex + 1)
                rest = rest.substring(0, fragmentIndex)
            }
            var query: String? = null
            val queryIndex = rest.indexOf(QUERY_PREFIX)
            if (queryIndex >= 0) {
                query = rest.substring(queryIndex + 1)
                rest = rest.substring(0, queryIndex)
            }
            var scheme: String? = null
            val schemeIndex = rest.indexOf(SCHEME_SEPARATOR)
            if (schemeIndex > 0 && isScheme(rest.substring(0, schemeIndex))) {
                scheme = rest.substring(0, schemeIndex)
                rest = rest.substring(schemeIndex + 1)
            }
            var authority: String? = null
            if (rest.startsWith(AUTHORITY_PREFIX)) {
                rest = rest.substring(AUTHORITY_PREFIX.length)
                val pathIndex = rest.indexOf(PATH_SEPARATOR)
                if (pathIndex >= 0) {
                    authority = rest.substring(0, pathIndex)
                    rest = rest.substring(pathIndex)
                } else {
                    authority = rest
                    rest = ""
                }
            }
            return Uri(scheme, authority, rest, query, fragment)
        }

        @JvmStatic
        fun encode(value: String): String {
            val builder = StringBuilder()
            for (symbol in value) {
                if (isAsciiLetterOrDigit(symbol) || UNRESERVED_SYMBOLS.indexOf(symbol) >= 0) {
                    builder.append(symbol)
                    continue
                }
                for (element in symbol.toString().toByteArray(StandardCharsets.UTF_8)) {
                    builder.append(ESCAPE).append(String.format("%02X", element))
                }
            }
            return builder.toString()
        }

        @JvmStatic
        fun decode(value: String?): String? {
            if (value == null) {
                return null
            }
            val bytes = ArrayList<Byte>(value.length)
            var index = 0
            while (index < value.length) {
                val symbol = value[index]
                val escaped = if (symbol == ESCAPE && index + 1 + HEX_LENGTH <= value.length) {
                    value.substring(index + 1, index + 1 + HEX_LENGTH).toIntOrNull(HEX_RADIX)
                } else {
                    null
                }
                if (escaped != null) {
                    bytes.add(escaped.toByte())
                    index += 1 + HEX_LENGTH
                    continue
                }
                for (element in symbol.toString().toByteArray(StandardCharsets.UTF_8)) {
                    bytes.add(element)
                }
                index++
            }
            return String(bytes.toByteArray(), StandardCharsets.UTF_8)
        }

        private fun isScheme(value: String): Boolean {
            return value.isNotEmpty() && value.all { isAsciiLetterOrDigit(it) || SCHEME_SYMBOLS.indexOf(it) >= 0 }
        }

        private fun isAsciiLetterOrDigit(symbol: Char): Boolean {
            return symbol in 'a'..'z' || symbol in 'A'..'Z' || symbol in '0'..'9'
        }
    }
}
