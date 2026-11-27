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

import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.screen.ChoiceKind
import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.shared.presenter.screen.ScreenStack
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelTest {

    private val mScope = CoroutineScope(Dispatchers.Unconfined)
    private val mGeneral = FakeGeneralSettings()
    private val mBuffers = FakeBufferStore()
    private val mUserAgent = FakeUserAgentStore()
    private val mCountry = FakeCountrySetting("CA")
    private val mSources = FakeSources(Source.RADIO_BROWSER)
    private val mCommander = RecordingCommander()
    private val mBluetooth = FakeBluetoothPermission()
    private val mScreens = ScreenStack()

    @After
    fun tearDown() {
        mScope.cancel()
    }

    private fun model(): SettingsModel = SettingsModel(
        mGeneral,
        mBuffers,
        mUserAgent,
        mCountry,
        COUNTRIES,
        mSources,
        mCommander,
        mBluetooth,
        mScreens,
        VERSION,
        mScope,
    )

    private fun clearWrites() {
        mGeneral.writes.clear()
        mBuffers.writes.clear()
        mUserAgent.writes.clear()
        mCountry.writes.clear()
        mSources.writes.clear()
    }

    @Test
    fun theStateShowsWhatIsStored() {
        mGeneral.startPlayback = StartPlayback.NEVER
        mGeneral.masterVolume = 73
        mGeneral.btAutoPlay = true
        mGeneral.useMobileData = false
        mBuffers.values = BufferSettings(30000, 60000, 2000, 3000)
        mUserAgent.isCustom = true
        mSources.setActiveSource(Source.WEB_RADIO)
        mCountry.setCountryCode("PL")

        val state = model().state.value
        clearWrites()

        assertEquals(
            SettingsState(
                startPlayback = StartPlayback.NEVER,
                masterVolume = 73,
                btAutoPlay = true,
                bufferingCustom = true,
                source = Source.WEB_RADIO,
                countryCode = "PL",
                useMobileData = false,
                userAgentCustom = true,
                version = VERSION,
                bluetoothRequest = false,
            ),
            state,
        )
    }

    @Test
    fun theDefaultsShowAsDefaults() {
        assertEquals(
            SettingsState(
                startPlayback = StartPlayback.IF_PLAYING,
                masterVolume = 100,
                btAutoPlay = false,
                bufferingCustom = false,
                source = Source.RADIO_BROWSER,
                countryCode = "CA",
                useMobileData = true,
                userAgentCustom = false,
                version = VERSION,
                bluetoothRequest = false,
            ),
            model().state.value,
        )
    }

    @Test
    fun bufferingIsCustomWhenAnyOfTheFourValuesDiffersFromItsDefault() {
        val defaults = BufferSettings.DEFAULTS
        val variants = listOf(
            defaults.copy(min = defaults.min + 1),
            defaults.copy(max = defaults.max + 1),
            defaults.copy(play = defaults.play + 1),
            defaults.copy(rebuffer = defaults.rebuffer + 1),
        )
        for (values in variants) {
            mBuffers.values = values
            val state = model().state.value
            assertTrue("$values must count as custom", state.bufferingCustom)
        }
    }

    @Test
    fun theUserAgentIsCustomFromTheFlagAlone() {
        mUserAgent.isCustom = true

        assertTrue(mUserAgent.storedCustom == null)
        assertTrue(model().state.value.userAgentCustom)
    }

    @Test
    fun openShowsSettings() {
        model().open()

        assertEquals(Destination.Settings, mScreens.top.value)
    }

    @Test
    fun aboutFromTheOverflowIsPushedAlone() {
        model().openAbout()

        assertEquals(listOf<Destination>(Destination.About), mScreens.snapshot())
    }

    @Test
    fun theValuesAreReadAgainWhenSettingsIsShownAgain() {
        val model = model()
        model.open()
        model.openStartPlayback()

        mGeneral.masterVolume = 40
        mSources.setActiveSource(Source.WEB_RADIO)
        mScreens.pop()

        assertEquals(40, model.state.value.masterVolume)
        assertEquals(Source.WEB_RADIO, model.state.value.source)
    }

    @Test
    fun choosingAStartPlaybackStoresItAndReturnsToSettings() {
        val model = model()
        model.open()
        model.openStartPlayback()
        assertEquals(Destination.SettingsChoice(ChoiceKind.START_PLAYBACK), mScreens.top.value)

        model.chooseStartPlayback(StartPlayback.ALWAYS)

        assertEquals(listOf("startPlayback=ALWAYS"), mGeneral.writes)
        assertEquals(Destination.Settings, mScreens.top.value)
        assertEquals(StartPlayback.ALWAYS, model.state.value.startPlayback)
    }

    @Test
    fun choosingACountryStoresItThroughTheCountrySettingAndReturnsToSettings() {
        val model = model()
        model.open()
        model.openCountry()
        assertEquals(Destination.SettingsChoice(ChoiceKind.DEFAULT_COUNTRY), mScreens.top.value)

        model.chooseCountry("PL")

        assertEquals(listOf("PL"), mCountry.writes)
        assertEquals(Destination.Settings, mScreens.top.value)
        assertEquals("PL", model.state.value.countryCode)
    }

    @Test
    fun theCountriesComeFromTheListInItsOrder() {
        assertEquals(
            listOf("Use Location", "CA", "PL"),
            model().countries().map { it.code },
        )
    }

    @Test
    fun openMasterVolumeShowsTheStepper() {
        val model = model()
        model.open()
        model.openMasterVolume()

        assertEquals(Destination.MasterVolume, mScreens.top.value)
    }

    @Test
    fun aVolumeStepStoresTheNewValueAndSendsIt() {
        val model = model()
        mGeneral.writes.clear()

        model.stepMasterVolume(-SettingsModel.VOLUME_STEP)

        assertEquals(listOf("masterVolume=95"), mGeneral.writes)
        assertEquals(95, model.state.value.masterVolume)
        assertEquals(listOf(OpenRadioService.CMD_MASTER_VOLUME_CHANGED), mCommander.commands())
        assertEquals(
            95,
            mCommander.sent.single().parameters.getInt(OpenRadioStore.EXTRA_KEY_MASTER_VOLUME),
        )
    }

    @Test
    fun aVolumeThatIsNotAMultipleOfFiveIsSteppedFromAsStored() {
        mGeneral.masterVolume = 73
        val model = model()
        mGeneral.writes.clear()

        model.stepMasterVolume(SettingsModel.VOLUME_STEP)
        assertEquals(78, model.state.value.masterVolume)
        model.stepMasterVolume(-SettingsModel.VOLUME_STEP)
        assertEquals(73, model.state.value.masterVolume)
        model.stepMasterVolume(-SettingsModel.VOLUME_STEP)

        assertEquals(
            listOf("masterVolume=78", "masterVolume=73", "masterVolume=68"),
            mGeneral.writes,
        )
    }

    @Test
    fun theVolumeStopsAtAHundred() {
        mGeneral.masterVolume = 98
        val model = model()
        mGeneral.writes.clear()

        model.stepMasterVolume(SettingsModel.VOLUME_STEP)
        model.stepMasterVolume(SettingsModel.VOLUME_STEP)
        model.stepMasterVolume(Int.MAX_VALUE)

        assertEquals(listOf("masterVolume=100"), mGeneral.writes)
        assertEquals(100, model.state.value.masterVolume)
        assertEquals(1, mCommander.sent.size)
    }

    @Test
    fun theVolumeStopsAtZero() {
        mGeneral.masterVolume = 3
        val model = model()
        mGeneral.writes.clear()

        model.stepMasterVolume(-SettingsModel.VOLUME_STEP)
        model.stepMasterVolume(-SettingsModel.VOLUME_STEP)
        model.stepMasterVolume(Int.MIN_VALUE)

        assertEquals(listOf("masterVolume=0"), mGeneral.writes)
        assertEquals(0, model.state.value.masterVolume)
        assertEquals(1, mCommander.sent.size)
    }

    @Test
    fun aVolumeStepIsStoredEvenWithNoServiceToTellIt() {
        mCommander.connected = false
        val model = model()
        mGeneral.writes.clear()

        model.stepMasterVolume(-SettingsModel.VOLUME_STEP)

        assertEquals(listOf("masterVolume=95"), mGeneral.writes)
    }

    @Test
    fun turningBluetoothAutoPlayOffStoresFalse() {
        mGeneral.btAutoPlay = true
        mBluetooth.required = true
        val model = model()
        mGeneral.writes.clear()

        model.setBluetoothAutoPlay(false)

        assertEquals(listOf("btAutoPlay=false"), mGeneral.writes)
        assertFalse(model.state.value.btAutoPlay)
    }

    @Test
    fun turningItOnWhenNoPermissionIsNeededStoresTrue() {
        val model = model()
        mGeneral.writes.clear()

        model.setBluetoothAutoPlay(true)

        assertEquals(listOf("btAutoPlay=true"), mGeneral.writes)
        assertTrue(model.state.value.btAutoPlay)
        assertFalse(model.state.value.bluetoothRequest)
    }

    @Test
    fun turningItOnWhenThePermissionIsNeededAsksAndStoresNothing() {
        mBluetooth.required = true
        val model = model()
        mGeneral.writes.clear()

        model.setBluetoothAutoPlay(true)

        assertEquals(emptyList<String>(), mGeneral.writes)
        assertFalse(model.state.value.btAutoPlay)
        assertTrue(model.state.value.bluetoothRequest)
    }

    @Test
    fun startingTheRequestTakesItOutOfTheState() {
        mBluetooth.required = true
        val model = model()
        model.setBluetoothAutoPlay(true)

        model.bluetoothRequestStarted()

        assertFalse(model.state.value.bluetoothRequest)
    }

    @Test
    fun aGrantedPermissionStoresTrue() {
        mBluetooth.required = true
        val model = model()
        model.setBluetoothAutoPlay(true)
        model.bluetoothRequestStarted()
        mGeneral.writes.clear()

        model.onBluetoothPermission(granted = true)

        assertEquals(listOf("btAutoPlay=true"), mGeneral.writes)
        assertTrue(model.state.value.btAutoPlay)
    }

    @Test
    fun aRefusedPermissionStoresNothingAndTheSwitchShowsOff() {
        mBluetooth.required = true
        val model = model()
        model.setBluetoothAutoPlay(true)
        model.bluetoothRequestStarted()
        mGeneral.writes.clear()

        model.onBluetoothPermission(granted = false)

        assertEquals(emptyList<String>(), mGeneral.writes)
        assertFalse(model.state.value.btAutoPlay)
    }

    @Test
    fun theMobileDataSwitchStoresAndTellsTheServiceEveryTime() {
        val model = model()
        mGeneral.writes.clear()

        model.setUseMobileData(false)
        model.setUseMobileData(true)

        assertEquals(listOf("useMobileData=false", "useMobileData=true"), mGeneral.writes)
        assertEquals(
            listOf(OpenRadioService.CMD_NET_CHANGED, OpenRadioService.CMD_NET_CHANGED),
            mCommander.commands(),
        )
        assertTrue(model.state.value.useMobileData)
    }

    @Test
    fun clearCacheTellsTheServiceAndEmptiesTheStack() {
        val model = model()
        model.open()

        model.clearCache()

        assertEquals(listOf(OpenRadioService.CMD_CLEAR_CACHE), mCommander.commands())
        assertEquals(null, mScreens.top.value)
    }

    private companion object {
        const val VERSION = "1.4.0.27"
    }
}
