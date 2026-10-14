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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * How [BoundedRead] reads a response body, which is what keeps a url that names a playlist but
 * answers with a live stream from growing the process until it dies (TASK-080).
 */
class BoundedReadTest {

    /**
     * The case the limit exists for. Without it the read would never return, so the stream fails
     * the test itself once it has handed out far more than the limit, rather than leaving the
     * test to run out of memory.
     */
    @Test
    fun aStreamThatNeverEndsIsRefusedOnceItPassesTheLimit() {
        val endless = EndlessStream(LIMIT * TOO_FAR_FACTOR)

        val read = BoundedRead.of(GatedInputStream(endless) { true }, LIMIT)

        assertEquals(BoundedRead.TooLong, read)
        assertTrue(
            "${endless.handedOut} bytes were read past a limit of $LIMIT",
            endless.handedOut <= LIMIT + BoundedRead.BUFFER_SIZE
        )
    }

    @Test
    fun aStreamAsLongAsTheLimitIsReadWhole() {
        val content = bytes(LIMIT)

        assertArrayEquals(content, done(BoundedRead.of(gated(content), LIMIT)))
    }

    /**
     * A body over the limit is refused whole: half a playlist is not a shorter playlist.
     */
    @Test
    fun aStreamOneByteOverTheLimitIsRefused() {
        assertEquals(BoundedRead.TooLong, BoundedRead.of(gated(bytes(LIMIT + 1)), LIMIT))
    }

    @Test
    fun aStreamWithNoLimitIsReadWholeHoweverLong() {
        val content = bytes(BoundedRead.BUFFER_SIZE * 3 + 1)

        assertArrayEquals(content, done(BoundedRead.of(gated(content), DownloaderLayer.NO_LIMIT)))
    }

    @Test
    fun anEmptyStreamIsReadAsNoBytes() {
        assertArrayEquals(ByteArray(0), done(BoundedRead.of(gated(ByteArray(0)), LIMIT)))
    }

    /**
     * A stream that may no longer be read ends early, and what it held until then is not the
     * body, however far under the limit it is.
     */
    @Test
    fun aStreamCutShortIsNotAnsweredAsItsPartialContent() {
        val answers = ArrayDeque(listOf(true, false))
        val stream = GatedInputStream(EndlessStream(LIMIT * TOO_FAR_FACTOR)) { answers.removeFirst() }

        assertEquals(BoundedRead.CutShort, BoundedRead.of(stream, BoundedRead.BUFFER_SIZE * 2))
    }

    /**
     * With no limit, an early end is the only end a stream that never ends can have, and it is
     * still told apart from the end of the body.
     */
    @Test
    fun aStreamCutShortWithNoLimitIsStillNotAnsweredAsItsPartialContent() {
        val answers = ArrayDeque(listOf(true, true, false))
        val stream = GatedInputStream(EndlessStream(LIMIT * TOO_FAR_FACTOR)) { answers.removeFirst() }

        assertEquals(BoundedRead.CutShort, BoundedRead.of(stream, DownloaderLayer.NO_LIMIT))
    }

    private fun gated(content: ByteArray): GatedInputStream {
        return GatedInputStream(ByteArrayInputStream(content)) { true }
    }

    private fun done(read: BoundedRead): ByteArray {
        if (read is BoundedRead.Done) {
            return read.bytes
        }
        throw AssertionError("The read answered $read, not the body")
    }

    private fun bytes(size: Int): ByteArray {
        return ByteArray(size) { index -> (index % Byte.MAX_VALUE).toByte() }
    }

    /**
     * A stream with no end, the way a live station answers. It hands out at most [tooFar] bytes
     * and then fails, so that a read that does not stop is caught as a failure, not a hang.
     */
    private class EndlessStream(private val tooFar: Int) : InputStream() {

        var handedOut = 0
            private set

        override fun read(): Int {
            val buffer = ByteArray(1)
            read(buffer, 0, 1)
            return buffer[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (handedOut + length > tooFar) {
                throw AssertionError("The stream was read past $tooFar bytes")
            }
            buffer.fill(FILL, offset, offset + length)
            handedOut += length
            return length
        }
    }

    private companion object {

        /**
         * Not a multiple of [BoundedRead.BUFFER_SIZE], so the limit falls inside a buffer.
         */
        const val LIMIT = 10_000

        const val TOO_FAR_FACTOR = 100

        const val FILL: Byte = 'x'.code.toByte()
    }
}
