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

package com.yuriy.openradio.shared.dependencies

import com.yuriy.openradio.shared.presenter.screen.ScreenStack
import com.yuriy.openradio.shared.presenter.settings.AboutRow
import com.yuriy.openradio.shared.presenter.settings.DiagnosticsModel
import com.yuriy.openradio.shared.presenter.settings.SettingsModel
import com.yuriy.openradio.shared.presenter.settings.SourceChoiceModel
import com.yuriy.openradio.shared.presenter.settings.StationTransferModel
import com.yuriy.openradio.shared.presenter.settings.StreamBufferingModel
import com.yuriy.openradio.shared.presenter.settings.UserAgentModel

/**
 * The screen stack and the state holders of every settings screen, built once by the registry so
 * they outlive the activity as the main screen's model does.
 */
class SettingsModels(
    val screens: ScreenStack,
    val settings: SettingsModel,
    val source: SourceChoiceModel,
    val buffering: StreamBufferingModel,
    val userAgent: UserAgentModel,
    val diagnostics: DiagnosticsModel,
    val transfers: StationTransferModel,
    val about: List<AboutRow>,
)

/**
 * Lets a client receive the [SettingsModels] it needs without depending on the registry.
 */
interface SettingsModelsDependency {

    /**
     * Callback to provide [SettingsModels] to a client.
     */
    fun configureWith(models: SettingsModels)
}
