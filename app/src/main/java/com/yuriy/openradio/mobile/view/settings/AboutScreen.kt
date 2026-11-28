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

package com.yuriy.openradio.mobile.view.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.KeyValueRow
import com.github.jcszymansk.console.design.KeyValueTable
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.presenter.settings.AboutKey
import com.yuriy.openradio.shared.presenter.settings.AboutRow
import com.yuriy.openradio.shared.R as SharedR

/**
 * The version, the Media3 version and the project's links. A link row opens its URL.
 */
@Composable
internal fun AboutScreen(rows: List<AboutRow>, actions: SettingsActions) {
    SettingsFrame(SettingsTags.ABOUT_SCREEN) {
        SubScreenBar(stringResource(SharedR.string.main_menu_about), actions::onBack)
        KeyValueTable(
            rows.map { row ->
                KeyValueRow(
                    aboutLabel(row.key),
                    row.value,
                    row.url?.let { url -> { actions.onOpenLink(url) } },
                )
            },
            Modifier.padding(horizontal = 16.dp).testTag(SettingsTags.ABOUT_TABLE),
        )
    }
}

@Composable
private fun aboutLabel(key: AboutKey): String = stringResource(
    when (key) {
        AboutKey.VERSION -> R.string.about_key_version
        AboutKey.MEDIA3 -> R.string.about_key_media3
        AboutKey.PROJECT -> R.string.about_key_project
        AboutKey.ISSUES -> R.string.about_key_issues
        AboutKey.RADIO_BROWSER -> R.string.about_key_radio_browser
        AboutKey.WEBRADIODB -> R.string.about_key_webradiodb
        AboutKey.PLAYLIST_PARSER -> R.string.about_key_playlist_parser
        AboutKey.SWIPE -> R.string.about_key_swipe
        AboutKey.FONTS -> R.string.about_key_fonts
    }
)
