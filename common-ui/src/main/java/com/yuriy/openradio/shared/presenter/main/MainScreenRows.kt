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
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.storage.images.ImagesStore
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.MediaItemHelper
import java.util.Locale

/**
 * Turns the browse service's [MediaItem]s into the rows the main screen shows.
 */
object MainScreenRows {

    private const val META_SEPARATOR = " · "

    private val INITIALS_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")

    fun station(
        item: MediaItem,
        isFavorite: Boolean,
        isCurrent: Boolean,
        canLongPress: Boolean,
    ): RowState.Station {
        val name = item.mediaMetadata.title?.toString().orEmpty()
        return RowState.Station(
            id = item.mediaId,
            name = name,
            initials = initials(name),
            meta = meta(MediaItemHelper.getCountryCodeField(item), MediaItemHelper.getCodecField(item)),
            bitrate = bitrate(MediaItemHelper.getBitrateField(item)),
            isFavorite = isFavorite,
            isCurrent = isCurrent,
            canLongPress = canLongPress,
            artworkUri = artworkUri(item),
        )
    }

    fun category(item: MediaItem): RowState.Category {
        val drawableId = MediaItemHelper.getDrawableId(item.mediaMetadata.extras)
        val hasFlag = isCountry(item.mediaId) &&
            MediaItemHelper.isDrawableIdValid(drawableId) && drawableId != 0
        return RowState.Category(
            id = item.mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            iconResId = if (hasFlag) drawableId else null,
        )
    }

    fun initials(name: String): String {
        val words = name.split(INITIALS_SEPARATOR).filter { it.isNotEmpty() }
        val letters = when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}"
            words.size == 1 -> words[0].take(2)
            else -> AppUtils.EMPTY_STRING
        }
        return letters.uppercase(Locale.ROOT)
    }

    fun meta(countryCode: String, codec: String): String {
        return listOf(countryCode, codec)
            .filter { it.isNotBlank() }
            .joinToString(META_SEPARATOR) { it.trim().uppercase(Locale.ROOT) }
    }

    fun bitrate(kbps: Int): String? {
        return if (kbps > 0) "${kbps}k" else null
    }

    /**
     * @return the artwork URI as a string, or null when [item] has no URI or its image url is empty.
     */
    fun artworkUri(item: MediaItem): String? {
        val uri = item.mediaMetadata.artworkUri ?: return null
        if (ImagesStore.getImageUrl(uri).isEmpty()) {
            return null
        }
        return uri.toString()
    }

    private fun isCountry(mediaId: String): Boolean {
        return mediaId == MediaId.MEDIA_ID_COUNTRY_STATIONS ||
            (mediaId.startsWith(MediaId.MEDIA_ID_COUNTRIES_LIST) && mediaId != MediaId.MEDIA_ID_COUNTRIES_LIST)
    }
}
