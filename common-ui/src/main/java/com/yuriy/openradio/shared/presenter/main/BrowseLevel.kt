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

import android.os.Bundle
import androidx.media3.common.MediaItem

/**
 * One level of a tab's stack: the node, what the service last answered for it, and where the
 * list stood.
 *
 * @param serial unique per level ever pushed, so two visits of one node keep separate positions.
 * @param title shown in the top bar, or null for a tab's root.
 * @param bundle the search query for a search level, empty otherwise.
 * @param items the rows answered so far, or null before the first answer.
 * @param inFlight a first page was asked for and not answered yet.
 * @param failed the last first-page request failed.
 * @param page the last page asked for.
 * @param pageInFlight a further page was asked for and not answered yet.
 * @param endReached the last page came back empty.
 */
internal data class BrowseLevel(
    val serial: Int,
    val mediaId: String,
    val title: String?,
    val bundle: Bundle,
    val items: List<MediaItem>? = null,
    val inFlight: Boolean = false,
    val failed: Boolean = false,
    val page: Int = 0,
    val pageInFlight: Boolean = false,
    val endReached: Boolean = false,
    val scroll: ScrollPosition = ScrollPosition(),
) {
    val key: String
        get() = "level-$serial"
}
