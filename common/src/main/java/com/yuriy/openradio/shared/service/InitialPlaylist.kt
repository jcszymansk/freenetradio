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

import androidx.media3.common.MediaItem
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import java.util.TreeSet

/**
 * The queue the service plays around one station when it has no queue of its own: the favourites,
 * or the provider's new stations when there are none, with the station put at the head when the
 * list does not hold it.
 *
 * Every item carries the favourite flag the presenter answers for its station, whichever list the
 * station came from, so the now playing screen can outline it.
 *
 * @param items The playable items, the last station included, in queue order.
 * @param stations The stations of [items], for the browse tree to resolve every one of them.
 * @param startIndex The index of the last station in [items].
 */
class InitialPlaylist(
    val items: List<MediaItem>,
    val stations: Set<RadioStation>,
    val startIndex: Int,
) {

    companion object {

        private const val TAG = "IPL"

        /**
         * Builds the queue around [rs] from [presenter]'s favourites or new stations. Each item's
         * favourite flag is asked from [presenter] for its own station.
         */
        fun build(rs: RadioStation, presenter: OpenRadioServicePresenter): InitialPlaylist {
            AppLogger.d("$TAG create init npl")
            val radioStations = TreeSet<RadioStation>()
            val favorites = presenter.getAllFavorites()
            if (favorites.isEmpty().not()) {
                AppLogger.d("$TAG create init npl fill with fav")
                radioStations.addAll(favorites)
            } else {
                AppLogger.d("$TAG create init npl fill with new stations")
                radioStations.addAll(presenter.getNewStations())
            }
            AppLogger.d("$TAG create init npl with ${radioStations.size}")
            val items = radioStations.map { buildItem(it, presenter) }.toMutableList()
            val stations = radioStations.toMutableSet()
            if (radioStations.none { it.id == rs.id }) {
                items.add(0, buildItem(rs, presenter))
                stations.add(rs)
            }
            val startIndex = items.indexOfFirst { it.mediaId == rs.id }
            return InitialPlaylist(items, stations, startIndex)
        }

        private fun buildItem(station: RadioStation, presenter: OpenRadioServicePresenter): MediaItem {
            return MediaItemBuilder.buildPlayable(station, presenter.isRadioStationFavorite(station))
        }
    }
}
