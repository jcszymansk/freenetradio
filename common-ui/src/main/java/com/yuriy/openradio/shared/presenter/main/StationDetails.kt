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

package com.yuriy.openradio.shared.presenter.main

import androidx.media3.common.MediaItem
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getListedStreamUrl
import com.yuriy.openradio.shared.model.media.getStreamBitrate
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.MediaItemHelper
import java.util.Locale

/**
 * What Station details and the stream URL actions read about one station, whichever source it
 * came from: the item the player holds, or the station restored after a cold start.
 */
data class StationFacts(
    val country: String,
    val genre: String,
    val codec: String,
    val bitrate: Int,
    val streamUrl: String,
    val homePage: String,
) {
    companion object {
        val NONE = StationFacts(
            AppUtils.EMPTY_STRING, AppUtils.EMPTY_STRING, AppUtils.EMPTY_STRING,
            0, AppUtils.EMPTY_STRING, AppUtils.EMPTY_STRING
        )
    }
}

/**
 * Turns a station's facts into the rows of Station details (REQ-012).
 */
object StationDetails {

    private const val STREAM_SEPARATOR = " · "
    private const val SCHEME_SEPARATOR = "://"
    private const val DEFAULT_SCHEME = "http://"

    /**
     * The facts of a playable item as `MediaItemBuilder.buildPlayable` wrote them. The country is
     * read from its extra, because the player replaces the subtitle with the track text; the genre
     * is the description, which the player leaves alone.
     */
    fun of(item: MediaItem): StationFacts = StationFacts(
        country = MediaItemHelper.getCountryNameField(item),
        genre = item.mediaMetadata.description?.toString().orEmpty(),
        codec = MediaItemHelper.getCodecField(item),
        bitrate = MediaItemHelper.getBitrateField(item),
        streamUrl = MediaItemHelper.getStreamUrlField(item),
        homePage = MediaItemHelper.getHomePageField(item),
    )

    fun of(station: RadioStation): StationFacts = StationFacts(
        country = station.country,
        genre = station.genre,
        codec = station.codec,
        bitrate = station.getStreamBitrate(),
        streamUrl = station.getListedStreamUrl(),
        homePage = station.homePage,
    )

    /**
     * @return COUNTRY, GENRE, STREAM, URL and WEB, in that order, each only when [facts] has a
     *   non-blank value for it.
     */
    fun rows(facts: StationFacts): List<StationDetail> {
        val homePage = facts.homePage.trim()
        return listOfNotNull(
            detail(DetailKind.COUNTRY, facts.country),
            detail(DetailKind.GENRE, facts.genre),
            detail(DetailKind.STREAM, stream(facts.codec, facts.bitrate)),
            detail(DetailKind.URL, facts.streamUrl),
            if (homePage.isEmpty()) null else StationDetail(DetailKind.WEB, host(homePage), link(homePage)),
        )
    }

    /**
     * @return `MP3 · 192 kbps`, or the codec or the bitrate alone when only one is known, or an
     *   empty string when neither is.
     */
    fun stream(codec: String, kbps: Int): String {
        val name = codec.trim().uppercase(Locale.ROOT)
        return listOfNotNull(name.ifEmpty { null }, if (kbps > 0) "$kbps kbps" else null)
            .joinToString(STREAM_SEPARATOR)
    }

    /**
     * @return the host of [url]: what follows the scheme, without user info, port, path, query
     *   or fragment; [url] itself when that leaves nothing.
     */
    fun host(url: String): String {
        val authority = url.substringAfter(SCHEME_SEPARATOR)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        return authority.substringAfterLast('@').substringBefore(':').ifEmpty { url }
    }

    /**
     * @return [url] as the browser should open it: unchanged with a scheme, over http without one.
     */
    fun link(url: String): String {
        return if (url.contains(SCHEME_SEPARATOR)) url else DEFAULT_SCHEME + url
    }

    private fun detail(kind: DetailKind, value: String): StationDetail? {
        val trimmed = value.trim()
        return if (trimmed.isEmpty()) null else StationDetail(kind, trimmed)
    }
}
