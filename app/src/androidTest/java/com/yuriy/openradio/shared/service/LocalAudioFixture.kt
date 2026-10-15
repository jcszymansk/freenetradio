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
import com.yuriy.openradio.R
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
     * The same audio as [wavBytes], with a format chunk naming an encoding ExoPlayer cannot read.
     * ExoPlayer recognizes the file as WAV and then fails to read it, which is a playback error
     * with no specific cause: not the network, not the server, and not an unrecognized format.
     */
    fun unreadableWavBytes(seconds: Int = DEFAULT_SECONDS): ByteArray {
        val bytes = silence(seconds)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(FORMAT_OFFSET, FORMAT_UNKNOWN)
        return bytes
    }

    /**
     * The header of a WAV stream that never ends, for a server to follow with samples for as long
     * as a client keeps reading. It declares the largest data size the format can, which is how a
     * live source that cannot know its length is served as WAV.
     */
    fun endlessWavHeader(): ByteArray {
        return header(ENDLESS_DATA_SIZE).array()
    }

    /**
     * One frame of MPEG-1 Layer III silence, for a server to repeat at [MP3_BYTES_PER_SECOND] for
     * as long as a client keeps reading. Unlike [endlessWavHeader], a run of these frames declares
     * neither a length nor a duration, which is how a radio station's MP3 stream reaches the
     * player, and the player treats such a stream as live: one it cannot resume where it left
     * off, only load again from wherever the station is now.
     *
     * The header names 128 kbit/s at 48 kHz, mono, with no CRC, which makes every frame exactly
     * 384 bytes long with no padding. Everything after the header is zero, and zeroed side
     * information names no main data, which decodes as silence.
     */
    fun silentMp3Frame(): ByteArray {
        val frame = ByteArray(MP3_FRAME_SIZE)
        MP3_FRAME_HEADER.copyInto(frame)
        return frame
    }

    /**
     * @return what the player writes on the description line of a stream that is open and has
     *   told it nothing else about itself, which is every moment of the audio generated here.
     */
    fun liveStreamDescription(): String {
        return mContext.getString(R.string.media_description_default)
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
        val dataSize = seconds * BYTES_PER_SECOND
        val buffer = header(dataSize, HEADER_SIZE + dataSize)
        return buffer.array()
    }

    private fun header(dataSize: Int, capacity: Int = HEADER_SIZE): ByteBuffer {
        val buffer = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(HEADER_SIZE - 8 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(FORMAT_PCM)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(BYTES_PER_SECOND)
        buffer.putShort((CHANNELS * BYTES_PER_SAMPLE).toShort())
        buffer.putShort((BYTES_PER_SAMPLE * 8).toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        return buffer
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

        /**
         * How many bytes of samples one second of the fixture's audio takes.
         */
        const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE

        /**
         * How many bytes one second of [silentMp3Frame] takes: 128 kbit/s.
         */
        const val MP3_BYTES_PER_SECOND = 16_000

        /**
         * 144 times the bit rate over the sample rate, the length of a Layer III frame without
         * padding.
         */
        private const val MP3_FRAME_SIZE = 384

        /**
         * Frame sync, MPEG-1, Layer III, no CRC; 128 kbit/s, 48 kHz, no padding; mono.
         */
        private val MP3_FRAME_HEADER = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x94.toByte(), 0xC0.toByte())

        private const val HEADER_SIZE = 44

        /**
         * The largest data size whose RIFF size, eight bytes of header smaller than the file,
         * still fits a signed 32-bit field.
         */
        private const val ENDLESS_DATA_SIZE = Int.MAX_VALUE - HEADER_SIZE

        private const val FORMAT_PCM: Short = 1

        /**
         * Where the format chunk's encoding field sits in the header.
         */
        private const val FORMAT_OFFSET = 20

        /**
         * An encoding ExoPlayer's WAV extractor does not read: it takes PCM, float, A-law, mu-law
         * and IMA ADPCM, and refuses every other as an unsupported container feature.
         */
        private const val FORMAT_UNKNOWN: Short = 0x7777
    }
}
