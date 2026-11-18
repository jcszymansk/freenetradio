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

import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.source.Source

/**
 * A tab of the main screen and the browse node at the bottom of its stack. The order of the
 * entries is the order on screen.
 */
enum class Tab(val rootId: String) {
    FAV(MediaId.MEDIA_ID_FAVORITES_LIST),
    MINE(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST),
    POPULAR(MediaId.MEDIA_ID_POPULAR_STATIONS),
    NEW(MediaId.MEDIA_ID_NEW_STATIONS),
    COUNTRY(MediaId.MEDIA_ID_COUNTRIES_LIST),
    GENRE(MediaId.MEDIA_ID_ALL_CATEGORIES);

    companion object {

        /**
         * The tabs [source] can fill. WebRadioDB has no popular or new node, so those tabs would
         * only ever be empty. FAV and MINE are always there, although the root node lists them
         * only when they hold something.
         */
        fun visibleFor(source: Source): List<Tab> {
            return if (source == Source.WEB_RADIO) listOf(FAV, MINE, COUNTRY, GENRE) else entries.toList()
        }
    }
}
