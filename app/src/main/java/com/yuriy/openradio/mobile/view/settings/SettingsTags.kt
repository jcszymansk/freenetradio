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

import com.yuriy.openradio.shared.presenter.settings.BufferField

/**
 * The Compose test tags of the settings screens, shared by the screenshot tests and the
 * instrumented journeys. One tag serves the back arrow of every screen because only one
 * destination is composed at a time.
 */
object SettingsTags {

    const val BACK = "settings.back"
    const val SCREEN = "settings.screen"
    const val LIST = "settings.list"
    const val PROGRESS = "settings.progress"
    const val START_PLAYBACK = "settings.row.startPlayback"
    const val MASTER_VOLUME = "settings.row.masterVolume"
    const val BT_AUTO_PLAY = "settings.row.btAutoPlay"
    const val STREAM_BUFFERING = "settings.row.streamBuffering"
    const val SOURCE = "settings.row.source"
    const val DEFAULT_COUNTRY = "settings.row.defaultCountry"
    const val EXPORT = "settings.row.export"
    const val IMPORT = "settings.row.import"
    const val MOBILE_DATA = "settings.row.mobileData"
    const val USER_AGENT = "settings.row.userAgent"
    const val CLEAR_CACHE = "settings.row.clearCache"
    const val DIAGNOSTICS = "settings.row.diagnostics"
    const val ABOUT = "settings.row.about"
    const val CHOICE_SCREEN = "settings.choice"
    const val CHOICE_LIST = "settings.choice.list"
    const val RESTART = "settings.choice.restart"
    const val VOLUME_SCREEN = "settings.volume"
    const val VOLUME_VALUE = "settings.volume.value"
    const val VOLUME_DOWN = "settings.volume.down"
    const val VOLUME_UP = "settings.volume.up"
    const val BUFFERING_SCREEN = "settings.buffering"
    const val CLOSE = "settings.buffering.close"
    const val SAVE = "settings.buffering.save"
    const val USER_AGENT_SCREEN = "settings.userAgent"
    const val USER_AGENT_SWITCH = "settings.userAgent.custom"
    const val USER_AGENT_FIELD = "settings.userAgent.field"
    const val USER_AGENT_ERROR = "settings.userAgent.error"
    const val DIAGNOSTICS_SCREEN = "settings.diagnostics"
    const val DIAGNOSTICS_PROGRESS = "settings.diagnostics.progress"
    const val CREATE_REPORT = "settings.diagnostics.create"
    const val ABOUT_SCREEN = "settings.about"
    const val ABOUT_TABLE = "settings.about.table"

    /**
     * An option of the choice sub-screen; [key] is the option's stable key, an enum name or a
     * country code.
     */
    fun choice(key: String): String = "settings.choice.option.$key"

    fun bufferField(field: BufferField): String = "settings.buffering.field.${field.name}"

    fun bufferError(field: BufferField): String = "settings.buffering.error.${field.name}"
}
