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
 * What the player does about one playback error.
 */
sealed class PlaybackErrorDecision {

    /**
     * The stream stopped because the network went away. Playback is resumable once connectivity
     * returns, so the player remembers that the network, not the stream, ended it.
     */
    object NetworkLost : PlaybackErrorDecision()

    /**
     * The server answered the stream request with an error status.
     */
    data class StreamRejected(val reason: StreamRejectionReason) : PlaybackErrorDecision()

    /**
     * The player cannot read what the stream url returned. It is usually a playlist, so the
     * listener gets a chance to resolve it into an actual stream url.
     */
    object UnsupportedFormat : PlaybackErrorDecision()

    /**
     * An error with no specific cause, raised while the error budget still holds. The stream is
     * left alone: ExoPlayer recovers from most of these on its own.
     */
    object WithinErrorBudget : PlaybackErrorDecision()

    /**
     * The error budget is spent without the stream ever reaching a ready state.
     */
    object Unrecoverable : PlaybackErrorDecision()
}

/**
 * Why the server refused to serve the stream.
 */
enum class StreamRejectionReason {

    FORBIDDEN,

    NOT_FOUND,

    OTHER
}
