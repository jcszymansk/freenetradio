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

package android.webkit

/**
 * JVM replacement for the Android [MimeTypeMap]. Only the file extension lookup is modelled, and
 * it follows the platform code step by step: the url is cut at its last `#` and then at its last
 * `?`, the file name is what follows the last `/`, and a name holding any character outside
 * letters, digits, `_`, `.`, `-`, `(`, `)` and `%` has no extension at all. A path parameter such
 * as `;jsessionid=abc` therefore hides the extension on a device, and has to here too.
 */
class MimeTypeMap private constructor() {

    companion object {

        @JvmStatic
        fun getFileExtensionFromUrl(url: String?): String {
            if (url.isNullOrEmpty()) {
                return ""
            }
            val withoutFragment = cutAtLast(url, '#')
            val withoutQuery = cutAtLast(withoutFragment, '?')
            val fileName = withoutQuery.substringAfterLast('/')
            if (fileName.isEmpty() || !fileName.matches(FILE_NAME_PATTERN)) {
                return ""
            }
            val dotIndex = fileName.lastIndexOf('.')
            return if (dotIndex < 0) "" else fileName.substring(dotIndex + 1)
        }

        /**
         * The platform cuts only when the separator is past the first character.
         */
        private fun cutAtLast(url: String, separator: Char): String {
            val index = url.lastIndexOf(separator)
            return if (index > 0) url.substring(0, index) else url
        }

        private val FILE_NAME_PATTERN = Regex("[a-zA-Z_0-9.\\-()%]+")
    }
}
