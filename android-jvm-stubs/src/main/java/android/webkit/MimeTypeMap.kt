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
 * JVM replacement for the Android [MimeTypeMap]. Only the file extension lookup is modelled,
 * following the platform rule: the extension is what follows the last dot of the last path segment.
 */
class MimeTypeMap private constructor() {

    companion object {

        @JvmStatic
        fun getFileExtensionFromUrl(url: String?): String {
            if (url.isNullOrEmpty()) {
                return ""
            }
            val withoutFragment = url.substringBefore('#')
            val withoutQuery = withoutFragment.substringBefore('?')
            val lastSegment = withoutQuery.substringAfterLast('/')
            val dotIndex = lastSegment.lastIndexOf('.')
            if (dotIndex < 0) {
                return ""
            }
            val extension = lastSegment.substring(dotIndex + 1)
            return if (extension.matches(EXTENSION_PATTERN)) {
                extension
            } else {
                ""
            }
        }

        private val EXTENSION_PATTERN = Regex("[a-zA-Z_0-9.\\-]+")
    }
}
