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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * How [GatedInputStream] ends a transfer once it may no longer go on, which is what keeps a
 * playlist read from spending mobile data the user has barred playback from.
 */
class GatedInputStreamTest {

    @Test
    fun aStreamThatMayContinueIsReadWhole() {
        val stream = GatedInputStream(ByteArrayInputStream(CONTENT)) { true }

        assertArrayEquals(CONTENT, stream.readBytes())
        assertFalse(stream.isCutShort)
    }

    @Test
    fun aStreamThatMayNotStartReadsNothing() {
        val source = ByteArrayInputStream(CONTENT)
        val stream = GatedInputStream(source) { false }

        assertEquals(END_OF_STREAM, stream.read())
        assertTrue(stream.isCutShort)
        assertEquals(CONTENT.size, source.available())
    }

    @Test
    fun aStreamEndsAtTheFirstReadAfterItMayNoLongerContinue() {
        val source = ByteArrayInputStream(CONTENT)
        val answers = ArrayDeque(listOf(true, true, false))
        val stream = GatedInputStream(source) { answers.removeFirst() }
        val buffer = ByteArray(2)

        assertEquals(2, stream.read(buffer, 0, 2))
        assertEquals(CONTENT[2].toInt(), stream.read())
        assertEquals(END_OF_STREAM, stream.read(buffer, 0, 2))

        assertTrue(stream.isCutShort)
        assertEquals(CONTENT.size - 3, source.available())
    }

    /**
     * The question is asked once: a network that becomes usable again does not resume a transfer
     * the reader has already been told is over, because what follows would not join up with what
     * came before.
     */
    @Test
    fun aStreamCutShortStaysEndedAndStopsAsking() {
        var questions = 0
        var mayContinue = false
        val stream = GatedInputStream(ByteArrayInputStream(CONTENT)) {
            questions++
            mayContinue
        }
        stream.read()
        mayContinue = true

        assertEquals(END_OF_STREAM, stream.read())
        assertEquals(END_OF_STREAM, stream.read(ByteArray(4), 0, 4))
        assertEquals(0L, stream.skip(4))
        assertEquals(0, stream.available())
        assertEquals(1, questions)
    }

    @Test
    fun skipIsGatedLikeARead() {
        val source = ByteArrayInputStream(CONTENT)
        val answers = ArrayDeque(listOf(true, false))
        val stream = GatedInputStream(source) { answers.removeFirst() }

        assertEquals(2L, stream.skip(2))
        assertEquals(0L, stream.skip(2))
        assertTrue(stream.isCutShort)
        assertEquals(CONTENT.size - 2, source.available())
    }

    @Test
    fun availableAsksNothingWhileTheStreamIsOpen() {
        var questions = 0
        val stream = GatedInputStream(ByteArrayInputStream(CONTENT)) {
            questions++
            true
        }

        assertEquals(CONTENT.size, stream.available())
        assertEquals(0, questions)
    }

    @Test
    fun theWholeArrayReadIsGatedToo() {
        val stream = GatedInputStream(ByteArrayInputStream(CONTENT)) { false }

        assertEquals(END_OF_STREAM, stream.read(ByteArray(4)))
        assertTrue(stream.isCutShort)
    }

    @Test
    fun markIsNotOffered() {
        assertFalse(GatedInputStream(ByteArrayInputStream(CONTENT)) { true }.markSupported())
    }

    private companion object {

        val CONTENT = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        const val END_OF_STREAM = -1
    }
}
