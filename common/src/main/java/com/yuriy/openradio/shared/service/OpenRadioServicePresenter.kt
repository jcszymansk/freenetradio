/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.item.MediaItemCommand
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.model.translation.MediaIdBuilder
import com.yuriy.openradio.shared.service.location.Country

interface OpenRadioServicePresenter {

    fun getMediaItemCommand(commandId: String): MediaItemCommand?

    /**
     * The command that answers a search from the service. It is the instance
     * [getMediaItemCommand] returns for [MediaId.MEDIA_ID_SEARCH_FROM_SERVICE], so a search and a
     * browse of that node run the same command. Every presenter holds one, so the caller has no
     * missing case to handle.
     */
    fun getSearchCommand(): MediaItemCommand

    fun startNetworkMonitor(context: Context, listener: NetworkMonitorListener)

    fun stopNetworkMonitor(context: Context)

    /**
     * Whether the device is on a mobile network the user has told the application not to stream
     * over. Playback must not start, and must not resume, while this holds.
     */
    fun isPlaybackBlockedByMobileNetwork(): Boolean

    fun getStationsInCategory(categoryId: String, pageNumber: Int): Set<RadioStation>

    fun getStationsByCountry(countryCode: String, pageNumber: Int): Set<RadioStation>

    fun getNewStations(): Set<RadioStation>

    fun getPopularStations(): Set<RadioStation>

    /**
     * Stations matching [query], with ids made by [mediaIdBuilder]. There is no default builder:
     * a caller that searches has to choose how its results are tagged, so none gets plain
     * catalogue ids by omission.
     */
    fun getSearchStations(query: String, mediaIdBuilder: MediaIdBuilder): Set<RadioStation>

    fun getAllCategories(): Set<Category>

    fun getAllCountries(): Set<Country>

    fun getAllFavorites(): Set<RadioStation>

    fun getAllDeviceLocal(): Set<RadioStation>

    fun getLastRadioStation(): RadioStation

    fun getEqualizerLayer(): EqualizerLayer

    fun setLastRadioStation(radioStation: RadioStation)

    fun getCountryCode(): String

    fun isRadioStationFavorite(radioStation: RadioStation): Boolean

    fun updateRadioStationFavorite(radioStation: RadioStation)

    /**
     * Marks [radioStation] as a favorite or unmarks it, as [isFavorite] says.
     *
     * @return whether the favorites store changed: false when the station already was in the
     *   requested state, in which case nothing is written.
     */
    fun updateRadioStationFavorite(radioStation: RadioStation, isFavorite: Boolean): Boolean

    fun updateSortIds(mediaId: String, sortId: Int, categoryMediaId: String)

    fun getSleepTimerModel(): SleepTimerModel

    /**
     * Clear resources related to service provider, such as persistent or in memory storage, etc ...
     */
    fun clear()

    /**
     * Close resources related to service provider, such as connections, streams, etc ...
     */
    fun close()
}
