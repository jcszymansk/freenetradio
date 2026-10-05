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

/**
 * Whether a regained network connection should start playback again.
 *
 * Only a station the network stopped while the user wanted it playing is resumed. Each event
 * returns the state that follows it, so the player keeps a single value and every transition is
 * visible here rather than spread over its callbacks.
 *
 * ```
 *          network lost while play is requested
 *   IDLE ----------------------------------------> AWAITING_NETWORK
 *    ^                                                    |
 *    +----------------------------------------------------+
 *      playback ready, stop requested, or network lost
 *      while play is not requested
 * ```
 */
enum class NetworkRecovery {

    /**
     * Nothing to resume: playback is fine, or whoever stopped it was not the network.
     */
    IDLE,

    /**
     * The network stopped a station the user wanted playing, and nothing has stopped it since.
     */
    AWAITING_NETWORK;

    /**
     * Whether a network-connected event should start playback again.
     */
    val resumesOnReconnect: Boolean
        get() = this == AWAITING_NETWORK

    /**
     * The stream failed because the network went away.
     *
     * @param isPlayRequested Whether the player was asked to play when the stream failed. A stream
     * that fails while paused still loads, and the network taking it away must not turn the
     * user's pause into playback once it comes back.
     */
    fun onNetworkLost(isPlayRequested: Boolean): NetworkRecovery {
        return if (isPlayRequested) AWAITING_NETWORK else IDLE
    }

    /**
     * The stream reached a ready state, so there is nothing left for a reconnect to restore.
     */
    fun onPlaybackReady(): NetworkRecovery {
        return IDLE
    }

    /**
     * Something other than the network paused or stopped playback, which a reconnect must respect.
     */
    fun onStopRequested(): NetworkRecovery {
        return IDLE
    }
}
