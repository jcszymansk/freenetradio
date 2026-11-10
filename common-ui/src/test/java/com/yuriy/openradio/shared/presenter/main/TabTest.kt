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
import org.junit.Assert.assertEquals
import org.junit.Test

class TabTest {

    @Test
    fun radioBrowserShowsSixTabsInTheDecidedOrder() {
        assertEquals(
            listOf(Tab.FAV, Tab.MINE, Tab.POPULAR, Tab.NEW, Tab.COUNTRY, Tab.GENRE),
            Tab.visibleFor(Source.RADIO_BROWSER)
        )
    }

    @Test
    fun webRadioHasNoPopularOrNewTab() {
        assertEquals(listOf(Tab.FAV, Tab.MINE, Tab.COUNTRY, Tab.GENRE), Tab.visibleFor(Source.WEB_RADIO))
    }

    @Test
    fun eachTabOpensItsBrowseNode() {
        assertEquals(
            listOf(
                MediaId.MEDIA_ID_FAVORITES_LIST,
                MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST,
                MediaId.MEDIA_ID_POPULAR_STATIONS,
                MediaId.MEDIA_ID_NEW_STATIONS,
                MediaId.MEDIA_ID_COUNTRIES_LIST,
                MediaId.MEDIA_ID_ALL_CATEGORIES
            ),
            Tab.entries.map { it.rootId }
        )
    }
}
