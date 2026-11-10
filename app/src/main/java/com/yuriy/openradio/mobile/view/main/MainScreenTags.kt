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

package com.yuriy.openradio.mobile.view.main

import com.yuriy.openradio.shared.presenter.main.SectionKind

/**
 * Compose test tags of the main screen, shared by the screenshot tests and the instrumented
 * journeys. A row's tag is [ROW_PREFIX] followed by its media id.
 */
object MainScreenTags {
    const val TOP_BAR = "main.topBar"
    const val TABS = "main.tabs"
    const val LIST = "main.list"
    const val PROGRESS = "main.progress"
    const val PAGE_PROGRESS = "main.pageProgress"
    const val EMPTY = "main.empty"
    const val ERROR = "main.error"
    const val RETRY = "main.retry"
    const val FAB_ADD = "main.fab.add"
    const val BACK = "main.back"
    const val SEARCH = "main.search"
    const val OVERFLOW = "main.overflow"
    const val MINI_PLAYER = "main.miniPlayer"
    const val MINI_NAME = "main.miniPlayer.name"
    const val MINI_TRACK = "main.miniPlayer.track"
    const val MINI_TOGGLE = "main.miniPlayer.toggle"
    const val ROW_PREFIX = "main.row."
    const val FAVORITE_SUFFIX = ".favorite"

    fun row(mediaId: String): String = ROW_PREFIX + mediaId

    fun favorite(mediaId: String): String = row(mediaId) + FAVORITE_SUFFIX

    fun overflow(item: OverflowItem): String = "main.overflow.${item.name}"

    fun section(section: SectionKind): String = "main.section.${section.name}"
}
