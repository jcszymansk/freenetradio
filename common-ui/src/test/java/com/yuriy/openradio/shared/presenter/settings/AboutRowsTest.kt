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

package com.yuriy.openradio.shared.presenter.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AboutRowsTest {

    private val mRows = aboutRows("1.4.0.27", "1.11.1")

    private fun row(key: AboutKey): AboutRow = mRows.single { it.key == key }

    @Test
    fun theVersionIsTheNameAndTheCodeJoinedByADot() {
        assertEquals("1.4.0.27", appVersion("1.4.0", 27L))
    }

    @Test
    fun theRowsComeInTheirScreenOrder() {
        assertEquals(
            listOf(
                AboutKey.VERSION,
                AboutKey.MEDIA3,
                AboutKey.PROJECT,
                AboutKey.ISSUES,
                AboutKey.RADIO_BROWSER,
                AboutKey.WEBRADIODB,
                AboutKey.PLAYLIST_PARSER,
                AboutKey.SWIPE,
                AboutKey.FONTS
            ),
            mRows.map { it.key }
        )
    }

    @Test
    fun theVersionRowIsNotALink() {
        val version = row(AboutKey.VERSION)
        assertEquals("1.4.0.27", version.value)
        assertNull(version.url)
    }

    @Test
    fun theMedia3RowShowsItsVersionAndKeepsTheExoPlayerLink() {
        assertEquals(
            AboutRow(AboutKey.MEDIA3, "1.11.1", "https://github.com/google/ExoPlayer"),
            row(AboutKey.MEDIA3)
        )
    }

    @Test
    fun everyAboutLinkIsKept() {
        assertEquals(
            mapOf(
                AboutKey.MEDIA3 to "https://github.com/google/ExoPlayer",
                AboutKey.PROJECT to "https://github.com/jcszymansk/freenetradio",
                AboutKey.ISSUES to "https://github.com/jcszymansk/freenetradio/issues",
                AboutKey.RADIO_BROWSER to "https://www.radio-browser.info",
                AboutKey.WEBRADIODB to "https://jcorporation.github.io/webradiodb",
                AboutKey.PLAYLIST_PARSER to "https://github.com/wseemann/JavaPlaylistParser",
                AboutKey.SWIPE to "https://github.com/xenione/swipe-maker",
                AboutKey.FONTS to "https://openfontlicense.org"
            ),
            mRows.mapNotNull { row -> row.url?.let { row.key to it } }.toMap()
        )
    }

    @Test
    fun aLinkRowShowsItsUrlWithoutTheScheme() {
        assertEquals("github.com/jcszymansk/freenetradio", row(AboutKey.PROJECT).value)
    }
}
