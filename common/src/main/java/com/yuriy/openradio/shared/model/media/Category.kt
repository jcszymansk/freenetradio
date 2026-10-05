/*
 * Copyright 2017-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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
package com.yuriy.openradio.shared.model.media

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/15/14
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * [Category] holds a category of Radio Stations as a provider lists it.
 *
 * @property id What the provider is asked for when the category is browsed.
 * @property title What the user reads.
 * @property stationsCount How many stations the provider reports in the category.
 */
class Category(
    val id: String,
    val title: String,
    val stationsCount: Int
) {

    companion object {

        /**
         * The order the category list is browsed in: most stations first, ties broken by title
         * ignoring case, compared character by character rather than by locale collation so the order
         * does not move with the device language, and then by id. Two categories compare equal only
         * when count, id and the case-folded title all agree, so a sorted set built with this order
         * never merges categories with different ids.
         */
        val BROWSE_ORDER: Comparator<Category> =
            compareByDescending<Category> { it.stationsCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                .thenBy { it.id }
    }
}
