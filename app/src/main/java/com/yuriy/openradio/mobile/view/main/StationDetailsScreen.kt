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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleIconButton
import com.github.jcszymansk.console.design.ConsoleTopBar
import com.github.jcszymansk.console.design.KeyValueRow
import com.github.jcszymansk.console.design.KeyValueTable
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.presenter.main.DetailKind
import com.yuriy.openradio.shared.presenter.main.NowPlayingState

/**
 * Station details (REQ-012): a back arrow and the station's name, then COUNTRY, GENRE, STREAM,
 * URL and WEB, each only when known. WEB is a link that opens the home page.
 */
@Composable
internal fun StationDetailsScreen(state: NowPlayingState, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    val labels = DetailKind.entries.associateWith { stringResource(it.labelRes()) }
    Column(
        modifier
            .fillMaxSize()
            .background(Console.colors.background)
            .navigationBarsPadding()
            .testTag(NowPlayingTags.DETAILS)
    ) {
        ConsoleTopBar(
            title = state.name,
            navigation = {
                ConsoleIconButton(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    stringResource(R.string.main_back),
                    actions::onCloseStationDetails,
                    Modifier.testTag(NowPlayingTags.DETAILS_BACK),
                )
            },
        )
        KeyValueTable(
            rows = state.details.map { detail ->
                val link = detail.link
                KeyValueRow(
                    labels.getValue(detail.kind),
                    detail.value,
                    if (link == null) null else { { actions.onOpenHomePage(link) } },
                )
            },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag(NowPlayingTags.DETAILS_TABLE),
        )
    }
}

private fun DetailKind.labelRes(): Int = when (this) {
    DetailKind.COUNTRY -> R.string.details_country
    DetailKind.GENRE -> R.string.details_genre
    DetailKind.STREAM -> R.string.details_stream
    DetailKind.URL -> R.string.details_url
    DetailKind.WEB -> R.string.details_web
}
