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

import android.os.Bundle
import androidx.annotation.MainThread
import com.yuriy.openradio.shared.model.ServiceCommander
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.main.CountrySetting
import com.yuriy.openradio.shared.presenter.screen.ChoiceKind
import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.shared.presenter.screen.ScreenStack
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioStore
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The values the Settings screen shows, one row each.
 *
 * @param bufferingCustom true unless all four stored buffering values are their defaults.
 * @param userAgentCustom the custom user agent flag, whatever text is stored.
 * @param version the version as `name.code`, which appVersion formats.
 * @param bluetoothRequest true when the Bluetooth auto play switch asked for
 * BLUETOOTH_CONNECT: the platform asks, then calls
 * [SettingsModel.bluetoothRequestStarted].
 */
data class SettingsState(
    val startPlayback: StartPlayback,
    val masterVolume: Int,
    val btAutoPlay: Boolean,
    val bufferingCustom: Boolean,
    val source: Source,
    val countryCode: String,
    val useMobileData: Boolean,
    val userAgentCustom: Boolean,
    val version: String,
    val bluetoothRequest: Boolean = false,
)

/**
 * Settings' state holder.
 *
 * Every row is read and written through a port, so each keeps the key and the meaning it has
 * today, and the rows are read again whenever Settings becomes the top destination. It holds no
 * View, Activity or Compose type.
 *
 * Main thread only.
 */
@MainThread
class SettingsModel(
    private val mGeneral: GeneralSettings,
    private val mBuffers: BufferStore,
    private val mUserAgent: UserAgentStore,
    private val mCountry: CountrySetting,
    private val mCountries: CountryList,
    private val mSources: SourcesLayer,
    private val mCommander: ServiceCommander,
    private val mBluetooth: BluetoothPermission,
    private val mScreens: ScreenStack,
    private val mVersion: String,
    private val mScope: CoroutineScope,
) {

    private val mState = MutableStateFlow(readState(bluetoothRequest = false))

    /** The values Settings shows. */
    val state: StateFlow<SettingsState> = mState.asStateFlow()

    init {
        mScope.launch {
            mScreens.top.collect { destination ->
                if (destination == Destination.Settings) {
                    refresh()
                }
            }
        }
    }

    /** Shows the Settings screen. */
    @MainThread
    fun open() {
        mScreens.push(Destination.Settings)
    }

    /** Shows the version and the project links. */
    @MainThread
    fun openAbout() {
        mScreens.push(Destination.About)
    }

    /** Shows the start playback choice. */
    @MainThread
    fun openStartPlayback() {
        mScreens.push(Destination.SettingsChoice(ChoiceKind.START_PLAYBACK))
    }

    /** Shows the default country choice. */
    @MainThread
    fun openCountry() {
        mScreens.push(Destination.SettingsChoice(ChoiceKind.DEFAULT_COUNTRY))
    }

    /** Shows the master volume stepper. */
    @MainThread
    fun openMasterVolume() {
        mScreens.push(Destination.MasterVolume)
    }

    /** The countries the default country can be, in screen order. */
    fun countries(): List<Country> = mCountries.countries()

    /** Stores [value] as the start playback mode and returns to Settings. */
    @MainThread
    fun chooseStartPlayback(value: StartPlayback) {
        mGeneral.startPlayback = value
        mScreens.pop()
    }

    /** Stores [code] as the default country, as the old spinner did, and returns to Settings. */
    @MainThread
    fun chooseCountry(code: String) {
        mCountry.setCountryCode(code)
        mScreens.pop()
    }

    /**
     * Moves the master volume by [delta] from the stored value, clamped to the volume range, and
     * tells the service. At the bound it stores and sends nothing.
     */
    @MainThread
    fun stepMasterVolume(delta: Int) {
        val stored = mGeneral.masterVolume
        val next = (stored.toLong() + delta.toLong())
            .coerceIn(MIN_VOLUME.toLong(), MAX_VOLUME.toLong())
            .toInt()
        if (next == stored) {
            return
        }
        mGeneral.masterVolume = next
        refresh()
        send(OpenRadioService.CMD_MASTER_VOLUME_CHANGED, OpenRadioStore.makeMasterVolumeChangedBundle(next))
    }

    /**
     * Stores the Bluetooth auto play switch, asking for BLUETOOTH_CONNECT first when turning it
     * on needs the permission.
     */
    @MainThread
    fun setBluetoothAutoPlay(on: Boolean) {
        if (on && mBluetooth.isRequired()) {
            mState.value = mState.value.copy(bluetoothRequest = true)
            return
        }
        mGeneral.btAutoPlay = on
        refresh()
    }

    /** Notes that the platform is asking for BLUETOOTH_CONNECT. */
    @MainThread
    fun bluetoothRequestStarted() {
        mState.value = mState.value.copy(bluetoothRequest = false)
    }

    /** Stores Bluetooth auto play as on when [granted], and reads the rows again either way. */
    @MainThread
    fun onBluetoothPermission(granted: Boolean) {
        if (granted) {
            mGeneral.btAutoPlay = true
        }
        refresh()
    }

    /** Stores the mobile data switch and tells the service every time. */
    @MainThread
    fun setUseMobileData(on: Boolean) {
        mGeneral.useMobileData = on
        refresh()
        send(OpenRadioService.CMD_NET_CHANGED)
    }

    /**
     * Clears the cache and empties the screen stack.
     *
     * The service clears the app data, emptying its browse tree; the stack empties so the user is
     * back on the main screen while the lists reload. The service shows its own "Cache is
     * cleared" message, so the model adds none.
     */
    @MainThread
    fun clearCache() {
        send(OpenRadioService.CMD_CLEAR_CACHE)
        mScreens.clear()
    }

    private fun readState(bluetoothRequest: Boolean): SettingsState {
        return SettingsState(
            startPlayback = mGeneral.startPlayback,
            masterVolume = mGeneral.masterVolume,
            btAutoPlay = mGeneral.btAutoPlay,
            bufferingCustom = mBuffers.values != BufferSettings.DEFAULTS,
            source = mSources.getActiveSource(),
            countryCode = mCountry.countryCode(),
            useMobileData = mGeneral.useMobileData,
            userAgentCustom = mUserAgent.isCustom,
            version = mVersion,
            bluetoothRequest = bluetoothRequest,
        )
    }

    private fun refresh() {
        mState.value = readState(mState.value.bluetoothRequest)
    }

    private fun send(command: String, parameters: Bundle = Bundle()) {
        mScope.launch {
            if (!mCommander.sendCommand(command, parameters)) {
                AppLogger.w("$TAG no service took $command")
            }
        }
    }

    companion object {

        /** The step the master volume stepper moves by. */
        const val VOLUME_STEP = 5

        private const val MIN_VOLUME = 0
        private const val MAX_VOLUME = 100
        private const val TAG = "SettingsModel"
    }
}
