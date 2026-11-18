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

import com.yuriy.openradio.shared.presenter.main.ScrollPosition
import com.yuriy.openradio.shared.presenter.main.Tab

/**
 * What the main screen's controls ask for. [com.yuriy.openradio.mobile.view.activity.MainActivity]
 * answers them; screenshot tests answer nothing.
 */
interface MainScreenActions {
    fun onSelectTab(tab: Tab)
    fun onBack()
    fun onSearch()
    fun onOverflow(item: OverflowItem)
    fun onRowClick(rowId: String)
    fun onRowLongClick(rowId: String)
    fun onFavoriteClick(rowId: String)
    fun onLoadMore()
    fun onRetry()
    fun onTogglePlayback()
    fun onOpenNowPlaying()
    fun onAddStation()
    fun onScroll(levelKey: String, position: ScrollPosition)
}
