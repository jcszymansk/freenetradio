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

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.NetworkSettingsStorage
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.settings.BufferSettings
import com.yuriy.openradio.shared.utils.AppUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

/**
 * Covers the preference-backed adapters of the settings ports: they read and write the keys the
 * dialogs used, with the same defaults, so a stored value keeps its meaning across the update.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAdaptersTest {

    private lateinit var mContext: Context
    private lateinit var mNetwork: NetworkSettingsStorage

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        clearPreferences()
        mNetwork = NetworkSettingsStorage(WeakReference(mContext))
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun theGeneralSettingsAreThePreferencesTheDialogsUsed() {
        val general = GeneralSettingsImpl(mContext, mNetwork)

        assertEquals(StartPlayback.IF_PLAYING, general.startPlayback)
        assertEquals(100, general.masterVolume)
        assertFalse(general.btAutoPlay)
        assertTrue(general.useMobileData)

        general.startPlayback = StartPlayback.NEVER
        general.masterVolume = 73
        general.btAutoPlay = true
        general.useMobileData = false

        assertEquals(StartPlayback.NEVER, AppPreferencesManager.startPlayback(mContext))
        assertEquals(73, AppPreferencesManager.getMasterVolume(mContext, -1))
        assertTrue(AppPreferencesManager.isBtAutoPlay(mContext))
        assertFalse(NetworkSettingsStorage(WeakReference(mContext)).getUseMobile())
    }

    @Test
    fun valuesStoredBeforeTheUpdateAreReadAsTheyWere() {
        AppPreferencesManager.startPlayback(mContext, StartPlayback.ALWAYS)
        AppPreferencesManager.setMasterVolume(mContext, 40)
        AppPreferencesManager.setMinBuffer(mContext, 30000)
        AppPreferencesManager.isCustomUserAgent(mContext, true)
        AppPreferencesManager.setCustomUserAgent(mContext, "Stored/1.0")

        val general = GeneralSettingsImpl(mContext, mNetwork)
        assertEquals(StartPlayback.ALWAYS, general.startPlayback)
        assertEquals(40, general.masterVolume)
        assertEquals(30000, BufferStoreImpl(mContext).values.min)
        val userAgent = UserAgentStoreImpl(mContext)
        assertTrue(userAgent.isCustom)
        assertEquals("Stored/1.0", userAgent.current)
    }

    @Test
    fun theBufferStoreIsTheFourBufferPreferences() {
        val store = BufferStoreImpl(mContext)
        assertEquals(BufferSettings.DEFAULTS, store.values)

        store.values = BufferSettings(min = 30000, max = 60000, play = 2000, rebuffer = 3000)

        assertEquals(30000, AppPreferencesManager.getMinBuffer(mContext))
        assertEquals(60000, AppPreferencesManager.getMaxBuffer(mContext))
        assertEquals(2000, AppPreferencesManager.getPlayBuffer(mContext))
        assertEquals(3000, AppPreferencesManager.getPlayBufferRebuffer(mContext))
    }

    @OptIn(UnstableApi::class)
    @Test
    fun theUserAgentStoreIsTheCustomUserAgentPreferences() {
        val store = UserAgentStoreImpl(mContext)
        assertFalse(store.isCustom)
        assertEquals(AppUtils.getUserAgent(mContext), store.current)

        store.isCustom = true
        store.storeCustom("Typed/1.0")

        assertTrue(AppPreferencesManager.isCustomUserAgent(mContext))
        assertEquals("Typed/1.0", AppPreferencesManager.getCustomUserAgent(mContext, ""))
        assertEquals("Typed/1.0", store.current)
    }

    private fun clearPreferences() {
        for (name in listOf(OPEN_RADIO_PREFERENCES, NETWORK_SETTINGS_PREFERENCES)) {
            mContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private companion object {
        private const val OPEN_RADIO_PREFERENCES = "OpenRadioPref"
        private const val NETWORK_SETTINGS_PREFERENCES = "NetworkSettingsStorage"
    }
}
