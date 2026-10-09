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
 * Only a station the network stopped while the user wanted it playing is resumed. The network
 * stops a station in two ways: the stream fails because the connection went away, or the
 * mobile-network policy pauses it because the connection that remains may not be used. Each event
 * returns the state that follows it, so the player keeps a single value and every transition is
 * visible here rather than spread over its callbacks.
 *
 * ```
 *                network lost while play is requested
 *         +-----------------------------------------------------+
 *         |                                                     v
 *       IDLE <------------------------------------------ AWAITING_NETWORK
 *       ^  |   playback ready, stop requested, or network      |
 *       |  |   lost while play is not requested                |
 *       |  |                                                   |
 *       |  | policy pause while               policy pause while
 *       |  | play is requested                play is requested
 *       |  v                                                   |
 *       |  PAUSED_BY_NETWORK_POLICY <--------------------------+
 *       |           |
 *       +-----------+
 *         play requested or stop requested
 * ```
 *
 * The policy pauses the player itself, so while it holds the station paused, play is not
 * requested because of the policy and not because of the user. That is why a ready stream or a
 * network failure leaves [PAUSED_BY_NETWORK_POLICY] alone: a paused player keeps loading, and
 * either can happen before a usable network returns.
 */
enum class NetworkRecovery {

    /**
     * Nothing to resume: playback is fine, or whoever stopped it was not the network.
     */
    IDLE,

    /**
     * The stream failed because the network went away while the user wanted the station playing,
     * and nothing has stopped it since.
     */
    AWAITING_NETWORK,

    /**
     * The mobile-network policy paused a station the user wanted playing, and nothing has played
     * or stopped it since.
     */
    PAUSED_BY_NETWORK_POLICY;

    /**
     * Whether a network-connected event should start playback again.
     */
    val resumesOnReconnect: Boolean
        get() = this != IDLE

    /**
     * The stream failed because the network went away.
     *
     * @param isPlayRequested Whether the player was asked to play when the stream failed. A stream
     * that fails while paused still loads, and the network taking it away must not turn the
     * user's pause into playback once it comes back. A pause the policy holds is the network's,
     * not the user's, so it is kept.
     */
    fun onNetworkLost(isPlayRequested: Boolean): NetworkRecovery {
        return when {
            this == PAUSED_BY_NETWORK_POLICY -> PAUSED_BY_NETWORK_POLICY
            isPlayRequested -> AWAITING_NETWORK
            else -> IDLE
        }
    }

    /**
     * The mobile-network policy paused playback because the current network may not be used.
     *
     * @param isPlayRequested Whether the player was asked to play when the policy paused it. A
     * station the user had paused stays as it was, and so does a station a policy pause has
     * already paused, which no longer requests play when the policy reports again.
     */
    fun onPolicyPause(isPlayRequested: Boolean): NetworkRecovery {
        return if (isPlayRequested) PAUSED_BY_NETWORK_POLICY else this
    }

    /**
     * The player was asked to play, which ends a pause the policy held: by a reconnect resuming
     * it, or by the user taking over. A stream that is still waiting for the network keeps
     * waiting until it is ready.
     */
    fun onPlayRequested(): NetworkRecovery {
        return if (this == PAUSED_BY_NETWORK_POLICY) IDLE else this
    }

    /**
     * The stream reached a ready state. A playing stream leaves nothing for a reconnect to
     * restore, but a stream the policy paused still loads and becomes ready while paused.
     */
    fun onPlaybackReady(): NetworkRecovery {
        return if (this == PAUSED_BY_NETWORK_POLICY) PAUSED_BY_NETWORK_POLICY else IDLE
    }

    /**
     * Something other than the network paused or stopped playback, which a reconnect must respect.
     */
    fun onStopRequested(): NetworkRecovery {
        return IDLE
    }
}
