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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.FieldLabel
import com.github.jcszymansk.console.design.SectionHeader
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.R as SharedR
import com.yuriy.openradio.shared.presenter.settings.BufferBounds
import com.yuriy.openradio.shared.presenter.settings.BufferField
import com.yuriy.openradio.shared.presenter.settings.BufferRule
import com.yuriy.openradio.shared.presenter.settings.StreamBufferingState

private const val SCREEN_PADDING = 16

private const val FIELD_SPACING = 16

/**
 * The stream buffering edit screen (REQ-015, REQ-016): ✕, the title and Save in the top bar, the
 * four numeric fields of a section with their units, "Restore defaults" as that section's text
 * action, the allowed range under the fields, and each error under its own field.
 */
@Composable
internal fun StreamBufferingScreen(state: StreamBufferingState, bounds: BufferBounds, actions: SettingsActions) {
    SettingsFrame(SettingsTags.BUFFERING_SCREEN) {
        EditScreenBar(
            stringResource(R.string.settings_stream_buffering),
            actions::onCloseBuffering,
            actions::onSaveBuffering,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(
                stringResource(R.string.settings_buffering_values),
                actionLabel = stringResource(R.string.settings_buffering_restore),
                onAction = actions::onRestoreBufferDefaults,
            )
            Column(
                Modifier.padding(horizontal = SCREEN_PADDING.dp),
                verticalArrangement = Arrangement.spacedBy(FIELD_SPACING.dp),
            ) {
                BufferField.entries.forEach { field ->
                    Column {
                        FieldLabel(stringResource(field.labelRes()))
                        NumberField(
                            state.texts[field],
                            { text -> actions.onEditBuffer(field, text) },
                            Modifier.testTag(SettingsTags.bufferField(field)),
                        )
                        val rule = state.errors[field]
                        if (rule != null) {
                            FieldError(
                                errorMessage(rule, state.texts[field]),
                                Modifier.testTag(SettingsTags.bufferError(field)),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_buffering_range, bounds.min, bounds.max),
                    style = Console.type.supporting,
                    color = Console.colors.textSecondary,
                )
            }
        }
    }
}

/**
 * The message shown under a field that broke [rule]: for a value the user can read, the old
 * dialog's toast repeated it after the colon, which is what [INVALID_VALUE] does here.
 */
@Composable
private fun errorMessage(rule: BufferRule, text: String): String = when (rule) {
    BufferRule.INVALID_VALUE -> stringResource(SharedR.string.invalid_buffer_desc) + text.trim { it <= ' ' }
    BufferRule.MAX_BELOW_MIN -> stringResource(SharedR.string.min_buffer_greater_max_buffer)
    BufferRule.PLAY_ABOVE_MIN -> stringResource(SharedR.string.play_buffer_greater_min_buffer)
    BufferRule.REBUFFER_ABOVE_MIN -> stringResource(SharedR.string.play_re_buffer_greater_min_buffer)
}

private fun BufferField.labelRes(): Int = when (this) {
    BufferField.MIN -> R.string.settings_buffer_min
    BufferField.MAX -> R.string.settings_buffer_max
    BufferField.PLAY -> R.string.settings_buffer_play
    BufferField.REBUFFER -> R.string.settings_buffer_rebuffer
}
