/*
 * Copyright 2017 The "Open Radio" Project. Author: Chernyshov Yuriy
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
package com.yuriy.openradio.shared.model.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import com.yuriy.openradio.shared.utils.AppLogger

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 5/3/15
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * [AppPreferencesManager] is a class that provides access and manage of the
 * Application's Shared Preferences.
 */
object AppPreferencesManager {

    /**
     * Name of the Preferences.
     */
    private const val FILE_NAME = "OpenRadioPref"
    private const val PREFS_KEY_LAST_KNOWN_RADIO_STATION_ENABLED = "LAST_KNOWN_RADIO_STATION_ENABLED"
    private const val PREFS_KEY_IS_CUSTOM_USER_AGENT = "IS_CUSTOM_USER_AGENT"
    private const val PREFS_KEY_CUSTOM_USER_AGENT = "CUSTOM_USER_AGENT"
    private const val PREFS_KEY_MASTER_VOLUME = "MASTER_VOLUME"
    private const val PREFS_KEY_MIN_BUFFER = "PREFS_KEY_MIN_BUFFER"
    private const val PREFS_KEY_MAX_BUFFER = "PREFS_KEY_MAX_BUFFER"
    private const val PREFS_KEY_BUFFER_FOR_PLAYBACK = "PREFS_KEY_BUFFER_FOR_PLAYBACK"
    private const val PREFS_KEY_BUFFER_FOR_REBUFFER_PLAYBACK = "PREFS_KEY_BUFFER_FOR_REBUFFER_PLAYBACK"
    private const val PREFS_KEY_BT_AUTO_PLAY = "PREFS_KEY_BT_AUTO_PLAY"
    private const val PREFS_KEY_START_PLAYBACK = "START_PLAYBACK"
    private const val PREFS_KEY_WAS_PLAYING_AT_EXIT = "WAS_PLAYING_AT_EXIT"

    /**
     * @return [StartPlayback] to apply on app start. A setting that was never stored is migrated from
     * [PREFS_KEY_LAST_KNOWN_RADIO_STATION_ENABLED] once: true becomes [StartPlayback.ALWAYS], false
     * becomes [StartPlayback.NEVER], and no old setting becomes [StartPlayback.IF_PLAYING].
     */
    fun startPlayback(context: Context): StartPlayback {
        val preferences = getSharedPreferences(context)
        if (preferences.contains(PREFS_KEY_START_PLAYBACK)) {
            val stored = preferences.getString(PREFS_KEY_START_PLAYBACK, null)
            val match = StartPlayback.entries.firstOrNull { it.name == stored }
            if (match != null) {
                return match
            }
            AppLogger.w("Unknown start playback setting '$stored', using ${StartPlayback.IF_PLAYING}")
            return StartPlayback.IF_PLAYING
        }

        val migrated = if (!preferences.contains(PREFS_KEY_LAST_KNOWN_RADIO_STATION_ENABLED)) {
            StartPlayback.IF_PLAYING
        } else if (preferences.getBoolean(PREFS_KEY_LAST_KNOWN_RADIO_STATION_ENABLED, true)) {
            StartPlayback.ALWAYS
        } else {
            StartPlayback.NEVER
        }
        val editor = preferences.edit()
        editor.putString(PREFS_KEY_START_PLAYBACK, migrated.name)
        editor.remove(PREFS_KEY_LAST_KNOWN_RADIO_STATION_ENABLED)
        editor.apply()
        AppLogger.i("Migrated start playback setting to $migrated")
        return migrated
    }

    /**
     * Set the [StartPlayback] to apply on app start.
     *
     * @param value [StartPlayback] value.
     */
    fun startPlayback(context: Context, value: StartPlayback) {
        val editor = getEditor(context)
        editor.putString(PREFS_KEY_START_PLAYBACK, value.name)
        editor.apply()
    }

    /**
     * @return True if playback was running when the app last exited. False - otherwise.
     */
    fun wasPlayingAtExit(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(PREFS_KEY_WAS_PLAYING_AT_EXIT, false)
    }

    /**
     * Record whether playback is running when the app exits.
     *
     * @param value Boolean value.
     */
    fun wasPlayingAtExit(context: Context, value: Boolean) {
        val editor = getEditor(context)
        editor.putBoolean(PREFS_KEY_WAS_PLAYING_AT_EXIT, value)
        editor.apply()
    }

    /**
     * @return `true` if custom user agent is enabled, `false` otherwise.
     */
    fun isCustomUserAgent(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(PREFS_KEY_IS_CUSTOM_USER_AGENT, false)
    }

    /**
     * Sets `true` if custom user agent is enabled, `false` otherwise.
     *
     * @param value Boolean value.
     */
    fun isCustomUserAgent(context: Context, value: Boolean) {
        val editor = getEditor(context)
        editor.putBoolean(PREFS_KEY_IS_CUSTOM_USER_AGENT, value)
        editor.apply()
    }

    /**
     * @return Value of the custom user agent, or default one in case of errors.
     */
    fun getCustomUserAgent(context: Context, defaultValue: String): String {
        var value = getSharedPreferences(context).getString(PREFS_KEY_CUSTOM_USER_AGENT, defaultValue)
        if (value.isNullOrEmpty()) {
            value = defaultValue
        }
        return value
    }

    /**
     * Sets the value of custom user agent.
     *
     * @param value Custom user agent. Non null and not empty value.
     */
    fun setCustomUserAgent(context: Context, value: String) {
        val editor = getEditor(context)
        editor.putString(PREFS_KEY_CUSTOM_USER_AGENT, value)
        editor.apply()
    }

    /**
     * @return Value of the master volume, or default one in case of errors.
     */
    fun getMasterVolume(context: Context, defaultValue: Int): Int {
        return getSharedPreferences(context).getInt(PREFS_KEY_MASTER_VOLUME, defaultValue)
    }

    /**
     * Sets the value of master volume.
     *
     * @param value Master volume.
     */
    fun setMasterVolume(context: Context, value: Int) {
        val editor = getEditor(context)
        editor.putInt(PREFS_KEY_MASTER_VOLUME, value)
        editor.apply()
    }

    @UnstableApi
    fun getMinBuffer(context: Context): Int {
        return getSharedPreferences(context).getInt(PREFS_KEY_MIN_BUFFER, DefaultLoadControl.DEFAULT_MIN_BUFFER_MS)
    }

    fun setMinBuffer(context: Context, value: Int) {
        val editor = getEditor(context)
        editor.putInt(PREFS_KEY_MIN_BUFFER, value)
        editor.apply()
    }

    @UnstableApi
    fun getMaxBuffer(context: Context): Int {
        return getSharedPreferences(context).getInt(PREFS_KEY_MAX_BUFFER, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS)
    }

    fun setMaxBuffer(context: Context, value: Int) {
        val editor = getEditor(context)
        editor.putInt(PREFS_KEY_MAX_BUFFER, value)
        editor.apply()
    }

    fun getPlayBuffer(context: Context): Int {
        return getSharedPreferences(context).getInt(
                PREFS_KEY_BUFFER_FOR_PLAYBACK, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
        )
    }

    fun setPlayBuffer(context: Context, value: Int) {
        val editor = getEditor(context)
        editor.putInt(PREFS_KEY_BUFFER_FOR_PLAYBACK, value)
        editor.apply()
    }

    fun getPlayBufferRebuffer(context: Context): Int {
        return getSharedPreferences(context).getInt(
                PREFS_KEY_BUFFER_FOR_REBUFFER_PLAYBACK, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
    }

    fun setPlayBufferRebuffer(context: Context, value: Int) {
        val editor = getEditor(context)
        editor.putInt(PREFS_KEY_BUFFER_FOR_REBUFFER_PLAYBACK, value)
        editor.apply()
    }

    fun isBtAutoPlay(context: Context): Boolean {
        return getSharedPreferences(context).getBoolean(PREFS_KEY_BT_AUTO_PLAY, false)
    }

    fun setBtAutoPlay(context: Context, value: Boolean) {
        val editor = getEditor(context)
        editor.putBoolean(PREFS_KEY_BT_AUTO_PLAY, value)
        editor.apply()
    }

    /**
     * @return [SharedPreferences.Editor]
     */
    private fun getEditor(context: Context): SharedPreferences.Editor {
        return getSharedPreferences(context).edit()
    }

    /**
     * @return [SharedPreferences] of the Application
     */
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }
}
