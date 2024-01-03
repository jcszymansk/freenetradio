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

package com.yuriy.openradio.shared.model.filter

import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.utils.AppUtils

/**
 * Default implementation of the [Filter] interface.
 */
class FilterImpl : Filter {

    private val mData = arrayOf(
        Item("stream.radiojar.com/z4qyckhr9druv", homePage = "radiosindia.com"),
        Item(name = setOf("rainbow", "vijayawada")),
        Item(name = setOf("mirchi", "Telugu"))
    )

    override fun filter(radioStation: RadioStation): Boolean {
        return mData.any { item ->
            val variant = radioStation.mediaStream.getVariant(0)
            val url = variant.url.lowercase()
            val homePage = radioStation.homePage.lowercase()
            val name = radioStation.name.lowercase()

            val nameContains = item.name.any { (name.isNotEmpty() && name.contains(it)) }
            (url.isNotEmpty() && url.contains(item.streamUrl))
                    || (homePage.isNotEmpty() && homePage.contains(item.homePage))
                    || nameContains
        }
    }

    private data class Item(
        val streamUrl: String = AppUtils.EMPTY_STRING,
        val name: Set<String> = setOf(AppUtils.EMPTY_STRING),
        val homePage: String = AppUtils.EMPTY_STRING
    )
}
