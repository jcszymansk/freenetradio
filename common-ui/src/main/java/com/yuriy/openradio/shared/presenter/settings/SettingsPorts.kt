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

package com.yuriy.openradio.shared.presenter.settings

import android.net.Uri
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.service.location.Country

/**
 * The general settings as the Settings screen reads and writes them: the start playback mode,
 * the master volume, Bluetooth auto play and the mobile data switch.
 *
 * The platform adapter stores them under the keys that AppPreferencesManager and
 * NetworkSettingsStorage use today, with the same meanings and defaults.
 */
interface GeneralSettings {

    /** Whether the app resumes the last played station when it starts. */
    var startPlayback: StartPlayback

    /** The master volume, from 0 to 100. */
    var masterVolume: Int

    /** Whether playback starts when a Bluetooth device connects. */
    var btAutoPlay: Boolean

    /** Whether streams may play over mobile data. Defaults to true. */
    var useMobileData: Boolean
}

/** The four stream buffering values, stored under the keys AppPreferencesManager uses today. */
interface BufferStore {

    /** The four values, in milliseconds. */
    var values: BufferSettings
}

/**
 * The custom user agent.
 *
 * The platform adapter stores the flag and the text under the keys AppPreferencesManager uses
 * today.
 */
interface UserAgentStore {

    /** Whether requests go out with the custom user agent. */
    var isCustom: Boolean

    /** The user agent requests go out with now, as AppUtils.getUserAgent answers it. */
    val current: String

    /**
     * Stores [value] as the custom user agent; the caller has trimmed it and checked that it is
     * not empty.
     */
    fun storeCustom(value: String)
}

/** The countries the default country can be, in screen order. */
fun interface CountryList {
    fun countries(): List<Country>
}

/** Whether turning Bluetooth auto play on has to ask for BLUETOOTH_CONNECT first. */
fun interface BluetoothPermission {

    /** True on Android 12 and later while the permission is not granted. */
    fun isRequired(): Boolean
}

/** Restarts the application, as the source dialog's restart button did. */
fun interface AppRestart {
    fun restart()
}

/** The stations data file, written to and merged from a document the user chose. */
interface StationFiles {

    /** Writes the stations file to [document], returning true when the whole file was written. */
    suspend fun exportTo(document: Uri): Boolean

    /**
     * Merges the stations file from [document], returning true when the document was read; a
     * file holding nothing usable also counts.
     */
    suspend fun importFrom(document: Uri): Boolean
}

/** The short messages the settings screens show. */
enum class Notice { SUCCESS, FAILURE }

/** Shows a [Notice] the way the app shows its other short messages. */
fun interface SettingsNotices {
    fun show(notice: Notice)
}
