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

package com.yuriy.openradio.shared.service.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.yuriy.openradio.shared.utils.AppLogger

/**
 * Logs every audio offload state change of the player at info level, so that a logcat taken on a
 * phone shows whether offload engaged and when the player slept for it. Given to
 * [ExoPlayer.addAudioOffloadListener].
 *
 * @param mLog Where to write the lines. Tests pass a recorder.
 */
@UnstableApi
internal class AudioOffloadLog(
    private val mLog: (String) -> Unit = AppLogger::i
) : ExoPlayer.AudioOffloadListener {

    override fun onOffloadedPlayback(isOffloadedPlayback: Boolean) {
        if (isOffloadedPlayback) {
            mLog("AudioOffload offloaded playback started")
        } else {
            mLog("AudioOffload offloaded playback stopped")
        }
    }

    override fun onSleepingForOffloadChanged(isSleepingForOffload: Boolean) {
        if (isSleepingForOffload) {
            mLog("AudioOffload player sleeps for offload")
        } else {
            mLog("AudioOffload player woke from offload sleep")
        }
    }
}
