/*
 * Copyright 2021-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage

object SortUtils {

    /**
     * Updates Radio Stations of a particular category with the Sort Id by the given Media Id.
     *
     * @param mediaId Media Id of the Radio Station.
     * @param sortId  Sort Id to update to.
     * @param categoryMediaId Category.
     */
    fun updateSortIds(
        mediaId: String, sortId: Int, categoryMediaId: String,
        favoritesStorage: FavoritesStorage, deviceLocalsStorage: DeviceLocalsStorage
    ) {
        when (categoryMediaId) {
            MediaId.MEDIA_ID_FAVORITES_LIST -> {
                val all = favoritesStorage.getAll()
                resortIds(all, sortId, mediaId)
                favoritesStorage.addAll(all)
            }

            MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST -> {
                val all = deviceLocalsStorage.getAll()
                resortIds(all, sortId, mediaId)
                deviceLocalsStorage.addAll(all)
            }
        }
    }

    /**
     * Gives the station with [mediaId] the [sortId] and numbers every other station in [all] from
     * zero, in iteration order, stepping over [sortId] so no other station shares it.
     *
     * The skip is keyed on the counter rather than on the sort id a station held before the drag:
     * that station is met after the counter has passed [sortId] whenever the drag goes towards the
     * bottom of the list, and is the dragged station itself when a row is dropped where it was.
     */
    private fun resortIds(all: Set<RadioStation>, sortId: Int, mediaId: String) {
        var counter = 0
        for (item in all) {
            item.sortId = if (mediaId == item.id) {
                sortId
            } else {
                if (counter == sortId) {
                    counter++
                }
                counter++
            }
        }
    }
}
