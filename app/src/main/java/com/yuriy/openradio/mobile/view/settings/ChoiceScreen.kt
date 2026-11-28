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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleDimens
import com.github.jcszymansk.console.design.bottomHairline
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.settings.SourceChoiceState
import com.yuriy.openradio.shared.service.location.Country

private const val FOOTER_KEY = "settings.choice.footer"

/**
 * One option of a choice sub-screen. [key] is stable across recompositions and names the row's
 * test tag.
 */
data class ChoiceRow(val key: String, val label: String, val detail: String?, val selected: Boolean)

internal fun startPlaybackRows(selected: StartPlayback, label: (StartPlayback) -> String): List<ChoiceRow> =
    StartPlayback.entries.map { value ->
        ChoiceRow(value.name, label(value), detail = null, selected = value == selected)
    }

internal fun sourceRows(state: SourceChoiceState): List<ChoiceRow> =
    state.sources.map { source ->
        ChoiceRow(source.name, source.srcName, detail = null, selected = source == state.active)
    }

internal fun countryRows(countries: List<Country>, selectedCode: String): List<ChoiceRow> =
    countries.map { country ->
        ChoiceRow(
            country.code,
            country.name,
            detail = country.code.takeUnless { it == country.name },
            selected = country.code == selectedCode,
        )
    }

internal fun firstShown(rows: List<ChoiceRow>): Int = rows.indexOfFirst { it.selected }.coerceAtLeast(0)

/**
 * The choice sub-screen (REQ-006): a back arrow and the setting's name, then one row per option
 * separated by hairlines with the selected label in accent. It opens at the selected row;
 * [footer] goes below the list (Source's "Restart to apply"). Build B reuses it for the
 * equaliser presets.
 */
@Composable
internal fun ChoiceScreen(
    title: String,
    rows: List<ChoiceRow>,
    onChoose: (Int) -> Unit,
    onBack: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstShown(rows))
    SettingsFrame(SettingsTags.CHOICE_SCREEN) {
        SubScreenBar(title, onBack)
        LazyColumn(
            Modifier.weight(1f).testTag(SettingsTags.CHOICE_LIST),
            state = listState,
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                ChoiceItem(row, onClick = { onChoose(index) })
            }
            if (footer != null) {
                item(key = FOOTER_KEY) { footer() }
            }
        }
    }
}

@Composable
private fun ChoiceItem(row: ChoiceRow, onClick: () -> Unit) {
    val c = Console.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ConsoleDimens.rowMinHeight)
            .bottomHairline(c.dividerRow)
            .selectable(selected = row.selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(SettingsTags.choice(row.key)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.label,
            style = Console.type.body,
            color = if (row.selected) c.accent else c.text,
            modifier = Modifier.weight(1f),
        )
        if (row.detail != null) {
            Text(row.detail, style = Console.type.dataValue, color = c.textSecondary)
        }
    }
}
