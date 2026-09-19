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

package com.yuriy.openradio.shared.service

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The audio the playback tests play, generated into the application's cache directory and reached
 * over `file://`.
 *
 * Generated rather than checked in so that nothing about the tests depends on a binary nobody can
 * read in a diff, and so a test can ask for the length it needs. The samples are silence: playback
 * has to be observable with no audio output attached, and a tone would only add noise to an
 * emulator that may or may not have one.
 *
 * Streams in production are HTTP, not files, and that difference is deliberate here. `file://`
 * reaches ExoPlayer through the same [androidx.media3.datasource.DefaultDataSource] the
 * application builds for everything else, with no server and no socket in the way, so a failure
 * in these tests is a failure in playback rather than in a fixture's networking. The paths where
 * HTTP itself is the subject have their own fixture.
 */
internal class LocalAudioFixture(private val mContext: Context) {

    private val mFiles = mutableListOf<File>()

    /**
     * Writes a mono 16-bit PCM WAV file of [seconds] of silence and returns its `file://` url.
     *
     * @param name file name, which also decides the extension the player infers the format from.
     */
    fun wav(name: String, seconds: Int = DEFAULT_SECONDS): String {
        return write(name, silence(seconds))
    }

    /**
     * The same audio as [wav], for the cases that serve it rather than write it.
     */
    fun wavBytes(seconds: Int = DEFAULT_SECONDS): ByteArray {
        return silence(seconds)
    }

    /**
     * Writes [content] verbatim and returns its `file://` url, for the cases where what the url
     * points at is not playable audio.
     */
    fun file(name: String, content: String): String {
        return write(name, content.toByteArray(Charsets.UTF_8))
    }

    /**
     * Removes every file this fixture wrote. A url handed out before this is no longer readable.
     */
    fun delete() {
        for (file in mFiles) {
            file.delete()
        }
        mFiles.clear()
    }

    private fun write(name: String, bytes: ByteArray): String {
        val file = File(directory(), name)
        file.writeBytes(bytes)
        mFiles.add(file)
        return "file://${file.absolutePath}"
    }

    private fun directory(): File {
        val directory = File(mContext.cacheDir, DIRECTORY_NAME)
        directory.mkdirs()
        return directory
    }

    /**
     * A canonical 44-byte RIFF/WAVE header followed by zeroed samples.
     *
     * ```text
     * 0      4      8      12     16                 36     44
     * +------+------+------+------+------------------+------+-------------------+
     * |"RIFF"| size |"WAVE"|"fmt "| 16, PCM, ch, rate|"data"| size | samples ... |
     * +------+------+------+------+------------------+------+-------------------+
     * ```
     */
    private fun silence(seconds: Int): ByteArray {
        val dataSize = seconds * SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE
        val buffer = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(HEADER_SIZE - 8 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(FORMAT_PCM)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE)
        buffer.putShort((CHANNELS * BYTES_PER_SAMPLE).toShort())
        buffer.putShort((BYTES_PER_SAMPLE * 8).toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        return buffer.array()
    }

    companion object {

        /**
         * Long enough that a test can pause, resume, and step through a playlist before the
         * stream ends on its own.
         */
        const val DEFAULT_SECONDS = 30

        private const val DIRECTORY_NAME = "playback-fixtures"

        private const val SAMPLE_RATE = 8_000

        private const val CHANNELS = 1

        private const val BYTES_PER_SAMPLE = 2

        private const val HEADER_SIZE = 44

        private const val FORMAT_PCM: Short = 1
    }
}
