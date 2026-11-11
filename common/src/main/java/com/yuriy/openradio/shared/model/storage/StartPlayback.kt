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

package com.yuriy.openradio.shared.model.storage

/**
 * Whether the app resumes the last played station when it starts.
 */
enum class StartPlayback {

    /**
     * Starts the last played station on every app start.
     */
    ALWAYS,

    /**
     * Starts the last played station only if playback was running when the app last exited.
     */
    IF_PLAYING,

    /**
     * Never starts playback on app start.
     */
    NEVER;

    /**
     * @param wasPlaying True if playback was running when the app last exited.
     * @return True if the app starts playback now.
     */
    fun playsOnStart(wasPlaying: Boolean): Boolean {
        return when (this) {
            ALWAYS -> true
            IF_PLAYING -> wasPlaying
            NEVER -> false
        }
    }
}
