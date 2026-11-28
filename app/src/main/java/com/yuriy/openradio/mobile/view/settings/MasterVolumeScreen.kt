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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.GhostIconButton
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.presenter.settings.SettingsModel

private const val MIN_VOLUME = 0

private const val MAX_VOLUME = 100

/**
 * The master volume stepper: the value in mono between − and +, each a 5 % step. − is disabled at
 * 0 and + at 100; the model clamps anyway.
 */
@Composable
internal fun MasterVolumeScreen(volume: Int, actions: SettingsActions) {
    SettingsFrame(SettingsTags.VOLUME_SCREEN) {
        SubScreenBar(stringResource(R.string.settings_master_volume), actions::onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostIconButton(
                Icons.Outlined.Remove,
                stringResource(R.string.settings_volume_down),
                { actions.onStepMasterVolume(-SettingsModel.VOLUME_STEP) },
                Modifier.disabledLook(volume <= MIN_VOLUME).testTag(SettingsTags.VOLUME_DOWN),
            )
            Text(
                stringResource(R.string.settings_volume_value, volume),
                // console-design has no large mono style, so this is its data style at display size.
                style = Console.type.data.copy(fontSize = 32.sp, lineHeight = 40.sp),
                color = Console.colors.text,
                modifier = Modifier.testTag(SettingsTags.VOLUME_VALUE),
            )
            GhostIconButton(
                Icons.Outlined.Add,
                stringResource(R.string.settings_volume_up),
                { actions.onStepMasterVolume(SettingsModel.VOLUME_STEP) },
                Modifier.disabledLook(volume >= MAX_VOLUME).testTag(SettingsTags.VOLUME_UP),
            )
        }
    }
}
