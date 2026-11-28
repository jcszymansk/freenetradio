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
 * The top bar's overflow menu, in screen order. Settings and About open their screens;
 * the sleep timer and the equalizer still open their dialogs until build B moves them.
 */
enum class OverflowItem(@StringRes val labelRes: Int) {
    SETTINGS(R.string.main_menu_settings),
    SLEEP_TIMER(SharedR.string.main_menu_sleep_timer),
    EQUALIZER(R.string.eq_label),
    ABOUT(SharedR.string.main_menu_about),
}
