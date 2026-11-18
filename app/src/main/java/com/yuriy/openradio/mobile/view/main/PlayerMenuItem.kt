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
 * Now playing's overflow menu, in screen order. Sleep timer and Equalizer open the dialogs the
 * main screen's overflow opens, under the same labels.
 *
 * @param needsStreamUrl offered only for a station with a stream URL.
 */
enum class PlayerMenuItem(@StringRes val labelRes: Int, val needsStreamUrl: Boolean) {
    STATION_DETAILS(R.string.now_playing_menu_details, false),
    COPY_STREAM_URL(R.string.now_playing_menu_copy_url, true),
    SHARE_STREAM_URL(R.string.now_playing_menu_share_url, true),
    SLEEP_TIMER(SharedR.string.main_menu_sleep_timer, false),
    EQUALIZER(R.string.eq_label, false);

    companion object {

        /** The items offered for a station whose listed stream URL is [streamUrl], in order. */
        fun offeredFor(streamUrl: String): List<PlayerMenuItem> {
            return entries.filter { !it.needsStreamUrl || streamUrl.isNotEmpty() }
        }
    }
}
