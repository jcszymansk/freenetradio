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

/** The rows of the About table in screen order; the screen gives each its label. */
enum class AboutKey { VERSION, MEDIA3, PROJECT, ISSUES, RADIO_BROWSER, WEBRADIODB, PLAYLIST_PARSER, SWIPE, FONTS }

/** One About row, where a row with a [url] is a link that opens it. */
data class AboutRow(val key: AboutKey, val value: String, val url: String?)

/** The version as About and the Settings row show it: the name, a dot, the code. */
fun appVersion(versionName: String, versionCode: Long): String = "$versionName.$versionCode"

/** The About table built from the two [version]s, with the URLs the About dialog used. */
fun aboutRows(version: String, media3Version: String): List<AboutRow> = listOf(
    AboutRow(AboutKey.VERSION, version, null),
    AboutRow(AboutKey.MEDIA3, media3Version, EXO_PLAYER_URL)
) + LINK_ROWS.map { (key, url) -> AboutRow(key, url.removePrefix(URL_SCHEME), url) }

private const val URL_SCHEME = "https://"

private const val EXO_PLAYER_URL = "https://github.com/google/ExoPlayer"

private val LINK_ROWS: List<Pair<AboutKey, String>> = listOf(
    AboutKey.PROJECT to "https://github.com/jcszymansk/freenetradio",
    AboutKey.ISSUES to "https://github.com/jcszymansk/freenetradio/issues",
    AboutKey.RADIO_BROWSER to "https://www.radio-browser.info",
    AboutKey.WEBRADIODB to "https://jcorporation.github.io/webradiodb",
    AboutKey.PLAYLIST_PARSER to "https://github.com/wseemann/JavaPlaylistParser",
    AboutKey.SWIPE to "https://github.com/xenione/swipe-maker",
    AboutKey.FONTS to "https://openfontlicense.org"
)
