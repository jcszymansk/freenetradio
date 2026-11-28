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

package com.yuriy.openradio.shared.presenter.screen

/**
 * A screen shown over the main screen and Now playing and held by [ScreenStack].
 *
 * This build has the settings screens; later builds add the sleep timer, the equalizer, the
 * station form and search.
 */
sealed interface Destination {

    /** The Settings screen. */
    data object Settings : Destination

    /** A setting that is one pick from a list. */
    data class SettingsChoice(val kind: ChoiceKind) : Destination

    /** The master volume stepper. */
    data object MasterVolume : Destination

    /** The stream buffering edit screen. */
    data object StreamBuffering : Destination

    /** The custom user agent. */
    data object UserAgent : Destination

    /** The warning shown before a diagnostic report is created. */
    data object Diagnostics : Destination

    /** The version and the project links. */
    data object About : Destination
}

/** The settings that [Destination.SettingsChoice] offers as a pick from a list. */
enum class ChoiceKind { START_PLAYBACK, SOURCE, DEFAULT_COUNTRY }
