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

package com.yuriy.openradio.shared.utils

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Reads [source] for as long as [mayContinue] allows, and ends it early once it no longer does.
 *
 * A transfer over the network keeps spending data until whoever reads it stops, so the question
 * is asked before every read rather than once before the transfer starts. An early end looks like
 * the end of the stream to the reader, which is why [isCutShort] has to be consulted before what
 * was read is trusted. Closing the connection behind [source] stays the caller's job.
 *
 * @param mayContinue Asked on the thread that reads, before every read and skip.
 */
class GatedInputStream(
    source: InputStream,
    private val mayContinue: () -> Boolean
) : FilterInputStream(source) {

    /**
     * Whether the stream was ended because [mayContinue] answered no. Once set it stays set, and
     * every later read answers the end of the stream without asking again.
     */
    var isCutShort = false
        private set

    override fun read(): Int {
        if (isOpen().not()) {
            return END_OF_STREAM
        }
        return super.read()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return super.read(buffer, offset, length)
        }
        if (isOpen().not()) {
            return END_OF_STREAM
        }
        return super.read(buffer, offset, length)
    }

    override fun skip(count: Long): Long {
        if (isOpen().not()) {
            return 0
        }
        return super.skip(count)
    }

    override fun available(): Int {
        if (isCutShort) {
            return 0
        }
        return super.available()
    }

    /**
     * A stream that can end early cannot honour a reset to a mark it read past.
     */
    override fun markSupported(): Boolean {
        return false
    }

    private fun isOpen(): Boolean {
        if (isCutShort) {
            return false
        }
        if (mayContinue()) {
            return true
        }
        AppLogger.i("$CLASS_NAME stream cut short")
        isCutShort = true
        return false
    }

    private companion object {

        const val CLASS_NAME = "GatedInputStream"

        const val END_OF_STREAM = -1
    }
}
