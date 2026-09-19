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

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.service.LoopbackHttpFixture
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What [NetUtils.extractUrlsFromPlaylist] answers, which is what
 * [com.yuriy.openradio.shared.service.OpenRadioService] acts on when a station url turns out to
 * be a playlist rather than a stream.
 *
 * The parsers themselves are covered on the JVM, in `AutoDetectParserTest`. What this adds is the
 * step above them: opening the url. That step is an `HttpURLConnection`, so it needs a server, and
 * [LoopbackHttpFixture] is one bound to the loopback address. The end-to-end effect of each answer
 * is in `OpenRadioServiceRecoveryTest`; this is where the answers themselves are pinned, because
 * the service acts on them from a background scope and a test that only watches the player cannot
 * tell which of them it acted on.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaylistResolutionTest {

    private lateinit var mContext: Context

    private lateinit var mServer: LoopbackHttpFixture

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mServer = LoopbackHttpFixture()
        mServer.start()
    }

    @After
    fun tearDown() {
        mServer.stop()
    }

    @Test
    fun aPlaylistResolvesToTheStreamItNames() {
        val stream = mServer.serve("/live.mp3", AUDIO_MPEG, "not really audio")
        val playlist = mServer.serve("/stream.pls", LoopbackHttpFixture.AUDIO_PLS, pls(stream))

        assertArrayEquals(arrayOf(stream), resolve(playlist))
    }

    @Test
    fun aPlaylistResolvesToEveryStreamItNames() {
        val first = mServer.serve("/first.mp3", AUDIO_MPEG, "not really audio")
        val second = mServer.serve("/second.mp3", AUDIO_MPEG, "not really audio")
        val playlist = mServer.serve(
            "/two.pls", LoopbackHttpFixture.AUDIO_PLS, pls(first, second)
        )

        assertArrayEquals(arrayOf(first, second), resolve(playlist))
    }

    @Test
    fun aPlaylistThatNamesNothingResolvesToNoUrls() {
        val playlist = mServer.serve(
            "/empty.pls", LoopbackHttpFixture.AUDIO_PLS, "[playlist]\nNumberOfEntries=0\nVersion=2\n"
        )

        assertEquals(0, resolve(playlist).size)
    }

    @Test
    fun aMalformedPlaylistResolvesToNoUrls() {
        val playlist = mServer.serve(
            "/broken.pls", LoopbackHttpFixture.AUDIO_PLS, "<html><body>not a playlist</body></html>"
        )

        assertEquals(0, resolve(playlist).size)
    }

    /**
     * A url that cannot be opened is answered with one empty string rather than with no urls, and
     * the two are indistinguishable to the caller. `handlePlayListUrlsExtracted` only asks whether
     * the answer is empty, so it puts this empty string on the station as its stream url, which is
     * TASK-043. This pins the answer as it stands so the fix has something to change.
     */
    @Test
    fun aPlaylistThatCannotBeOpenedResolvesToOneEmptyUrl() {
        assertArrayEquals(arrayOf(""), resolve("http://127.0.0.1:1/unreachable.pls"))
    }

    /**
     * A local file is the other way to reach that same answer:
     * [NetUtils.extractUrlsFromPlaylist] opens every url as an `HttpURLConnection`, so a url on
     * any other scheme fails before it is ever read.
     */
    @Test
    fun aPlaylistOnANonHttpSchemeResolvesToOneEmptyUrl() {
        assertArrayEquals(arrayOf(""), resolve("file:///does/not/matter.pls"))
    }

    private fun resolve(url: String): Array<String> {
        return NetUtils.extractUrlsFromPlaylist(mContext, url)
    }

    private fun pls(vararg urls: String): String {
        return buildString {
            append("[playlist]\n")
            append("NumberOfEntries=${urls.size}\n")
            for ((index, url) in urls.withIndex()) {
                append("File${index + 1}=$url\n")
                append("Title${index + 1}=Fixture ${index + 1}\n")
                append("Length${index + 1}=-1\n")
            }
            append("Version=2\n")
        }
    }

    private companion object {

        const val AUDIO_MPEG = "audio/mpeg"
    }
}
