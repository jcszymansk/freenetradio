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

package com.yuriy.openradio.shared.model.net

import com.yuriy.openradio.shared.utils.GatedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * How reading a response body whole ended.
 *
 * The read is the stream arithmetic of [HTTPDownloaderImpl], kept apart from its connection so
 * that what decides where it stops can be run on the JVM.
 */
sealed class BoundedRead {

    /**
     * The whole body, [bytes].
     */
    class Done(val bytes: ByteArray) : BoundedRead()

    /**
     * The body was longer than the caller allowed.
     */
    object TooLong : BoundedRead()

    /**
     * The network may no longer be used, and the body was abandoned.
     */
    object CutShort : BoundedRead()

    companion object {

        internal const val BUFFER_SIZE = 1024 * 4

        private const val END_OF_STREAM = -1

        /**
         * Reads [input] whole.
         *
         * @param maxBytes Most that may be read, or [DownloaderLayer.NO_LIMIT].
         * @return The content, [TooLong] when there is more of it than [maxBytes], or [CutShort]
         * when [input] was ended early. A response that long is not the document the caller
         * asked for, so it is refused rather than cut short, and a response ended early is not
         * the document either.
         */
        @Throws(IOException::class)
        fun of(input: GatedInputStream, maxBytes: Int): BoundedRead {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == END_OF_STREAM) {
                    return if (input.isCutShort) CutShort else Done(output.toByteArray())
                }
                if (maxBytes != DownloaderLayer.NO_LIMIT && output.size() + read > maxBytes) {
                    return TooLong
                }
                output.write(buffer, 0, read)
            }
        }
    }
}
