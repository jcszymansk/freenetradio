package com.yuriy.openradio.shared.model.storage

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.service.location.Country
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

/**
 * Covers the settings that live in Shared Preferences: their defaults, their write and reload
 * behaviour, and what they report for values that cannot be honoured.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class SettingsStorageTest {

    private lateinit var mContext: Context
    private lateinit var mNetworkSettings: NetworkSettingsStorage
    private lateinit var mSleepTimer: SleepTimerStorage
    private lateinit var mLocation: LocationStorage
    private lateinit var mEqualizer: EqualizerStorage

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mNetworkSettings = NetworkSettingsStorage(WeakReference(mContext))
        mSleepTimer = SleepTimerStorage(WeakReference(mContext))
        mLocation = LocationStorage(WeakReference(mContext))
        mEqualizer = EqualizerStorage(WeakReference(mContext))
        clearAll()
    }

    @After
    fun tearDown() {
        clearAll()
    }

    @Test
    fun applicationPreferencesReportTheirDefaults() {
        assertTrue(AppPreferencesManager.lastKnownRadioStationEnabled(mContext))
        assertFalse(AppPreferencesManager.isCustomUserAgent(mContext))
        assertEquals("fallback", AppPreferencesManager.getCustomUserAgent(mContext, "fallback"))
        assertEquals(37, AppPreferencesManager.getMasterVolume(mContext, 37))
        assertFalse(AppPreferencesManager.isBtAutoPlay(mContext))
        assertEquals(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, AppPreferencesManager.getMinBuffer(mContext)
        )
        assertEquals(
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, AppPreferencesManager.getMaxBuffer(mContext)
        )
        assertEquals(
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            AppPreferencesManager.getPlayBuffer(mContext)
        )
        assertEquals(
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            AppPreferencesManager.getPlayBufferRebuffer(mContext)
        )
    }

    @Test
    fun applicationPreferencesKeepWhatWasWritten() {
        AppPreferencesManager.lastKnownRadioStationEnabled(mContext, false)
        AppPreferencesManager.isCustomUserAgent(mContext, true)
        AppPreferencesManager.setCustomUserAgent(mContext, "FreeNetRadio test agent")
        AppPreferencesManager.setMasterVolume(mContext, 42)
        AppPreferencesManager.setBtAutoPlay(mContext, true)
        AppPreferencesManager.setMinBuffer(mContext, 1_111)
        AppPreferencesManager.setMaxBuffer(mContext, 2_222)
        AppPreferencesManager.setPlayBuffer(mContext, 333)
        AppPreferencesManager.setPlayBufferRebuffer(mContext, 444)

        assertFalse(AppPreferencesManager.lastKnownRadioStationEnabled(mContext))
        assertTrue(AppPreferencesManager.isCustomUserAgent(mContext))
        assertEquals(
            "FreeNetRadio test agent", AppPreferencesManager.getCustomUserAgent(mContext, "fallback")
        )
        assertEquals(42, AppPreferencesManager.getMasterVolume(mContext, 37))
        assertTrue(AppPreferencesManager.isBtAutoPlay(mContext))
        assertEquals(1_111, AppPreferencesManager.getMinBuffer(mContext))
        assertEquals(2_222, AppPreferencesManager.getMaxBuffer(mContext))
        assertEquals(333, AppPreferencesManager.getPlayBuffer(mContext))
        assertEquals(444, AppPreferencesManager.getPlayBufferRebuffer(mContext))
    }

    @Test
    fun anEmptyCustomUserAgentFallsBackToTheDefault() {
        AppPreferencesManager.setCustomUserAgent(mContext, "")

        assertEquals("fallback", AppPreferencesManager.getCustomUserAgent(mContext, "fallback"))
    }

    @Test
    fun mobileNetworkUsageDefaultsToAllowedAndSurvivesAReload() {
        assertTrue(mNetworkSettings.getUseMobile())

        mNetworkSettings.setUseMobile(false)

        assertFalse(mNetworkSettings.getUseMobile())
        assertFalse(NetworkSettingsStorage(WeakReference(mContext)).getUseMobile())
    }

    @Test
    fun theSleepTimerDefaultsToDisabledAndKeepsTheSavedDate() {
        assertFalse(mSleepTimer.loadEnabled())
        val fallback = mSleepTimer.loadDate().time
        assertTrue(
            "An unset sleep timer date should default to the current time, was $fallback",
            Math.abs(System.currentTimeMillis() - fallback) < DATE_DEFAULT_TOLERANCE_MS
        )

        mSleepTimer.saveEnabled(true)
        mSleepTimer.saveDate(SLEEP_TIMER_DATE)

        assertTrue(mSleepTimer.loadEnabled())
        assertEquals(SLEEP_TIMER_DATE, mSleepTimer.loadDate().time)
        val reloaded = SleepTimerStorage(WeakReference(mContext))
        assertTrue(reloaded.loadEnabled())
        assertEquals(SLEEP_TIMER_DATE, reloaded.loadDate().time)
    }

    @Test
    fun theCountryCodeDefaultsAndSurvivesAReload() {
        assertEquals(Country.COUNTRY_CODE_DEFAULT, mLocation.getCountryCode())

        mLocation.setCountryCode("PL")

        assertEquals("PL", mLocation.getCountryCode())
        assertEquals("PL", LocationStorage(WeakReference(mContext)).getCountryCode())
    }

    @Test
    fun theEqualizerStateStartsEmptyAndRoundTrips() {
        assertTrue(mEqualizer.isEmpty())
        assertEquals("", mEqualizer.loadEqualizerState())

        mEqualizer.saveEqualizerState(EQUALIZER_STATE)

        assertFalse(mEqualizer.isEmpty())
        assertEquals(EQUALIZER_STATE, mEqualizer.loadEqualizerState())
        assertEquals(
            EQUALIZER_STATE, EqualizerStorage(WeakReference(mContext)).loadEqualizerState()
        )
    }

    @Test
    fun anEmptyEqualizerStateIsReportedAsNoStateAtAll() {
        mEqualizer.saveEqualizerState(EQUALIZER_STATE)

        mEqualizer.saveEqualizerState("")

        assertTrue(mEqualizer.isEmpty())
    }

    private fun clearAll() {
        mNetworkSettings.clear()
        mSleepTimer.clear()
        mLocation.clear()
        mEqualizer.clear()
        // AppPreferencesManager offers no clear() of its own and keeps its file name private,
        // so the file it writes to is wiped here by name.
        mContext.getSharedPreferences(APP_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private companion object {

        /**
         * Mirrors the private file name of [AppPreferencesManager].
         */
        const val APP_PREFERENCES_FILE_NAME = "OpenRadioPref"

        const val SLEEP_TIMER_DATE = 1_700_000_000_000L

        const val EQUALIZER_STATE = "{\"CurrentPreset\":2}"

        const val DATE_DEFAULT_TOLERANCE_MS = 10_000L
    }
}
