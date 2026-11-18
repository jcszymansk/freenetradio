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

import androidx.annotation.StringRes
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.R as SharedR

/**
 * The top bar's overflow menu, in screen order. Each opens its existing dialog.
 */
enum class OverflowItem(@StringRes val labelRes: Int) {
    GENERAL(SharedR.string.main_menu_general),
    SOURCE(SharedR.string.main_menu_source),
    NETWORK(SharedR.string.main_menu_network),
    BUFFERING(SharedR.string.main_menu_buffering),
    SLEEP_TIMER(SharedR.string.main_menu_sleep_timer),
    EQUALIZER(R.string.eq_label),
    FILE_STORAGE(SharedR.string.main_menu_file_storage),
    ABOUT(SharedR.string.main_menu_about),
}
