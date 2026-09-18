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

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import com.yuriy.openradio.shared.utils.AppLogger
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicInteger

/**
 * Turns a playback error into the decision the player acts on.
 *
 * Errors with no specific cause are tolerated while the error budget holds, because ExoPlayer
 * recovers from most of them by itself. The budget counts errors since the last time the stream
 * was ready, so [reset] belongs on every transition into a ready state.
 */
class PlaybackErrorClassifier {

    /**
     * Number of errors raised since the stream was last ready.
     */
    private val mErrorCount = AtomicInteger(0)

    fun classify(exception: PlaybackException): PlaybackErrorDecision {
        val decision = decide(exception)
        AppLogger.d("$TAG classified ${exception.errorCodeName} as $decision [${mErrorCount.get()}]")
        return decision
    }

    /**
     * Starts the error budget over. The stream reached a ready state, so whatever failed before it
     * did no longer says anything about the stream.
     */
    fun reset() {
        mErrorCount.set(0)
    }

    private fun decide(exception: PlaybackException): PlaybackErrorDecision {
        if (exception.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
            return PlaybackErrorDecision.NetworkLost
        }
        val cause = exception.cause
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            return PlaybackErrorDecision.StreamRejected(toRejectionReason(cause.responseCode))
        }
        if (mErrorCount.getAndIncrement() > MAX_ERROR_COUNT) {
            return PlaybackErrorDecision.Unrecoverable
        }
        return if (cause is UnrecognizedInputFormatException) {
            PlaybackErrorDecision.UnsupportedFormat
        } else {
            PlaybackErrorDecision.WithinErrorBudget
        }
    }

    private fun toRejectionReason(responseCode: Int): StreamRejectionReason {
        return when (responseCode) {
            HttpURLConnection.HTTP_FORBIDDEN -> StreamRejectionReason.FORBIDDEN
            HttpURLConnection.HTTP_NOT_FOUND -> StreamRejectionReason.NOT_FOUND
            else -> StreamRejectionReason.OTHER
        }
    }

    companion object {
        /**
         * String tag to use in logs.
         */
        private const val TAG = "PEC"

        /**
         * Number of errors tolerated before the stream is treated as broken.
         */
        private const val MAX_ERROR_COUNT = 5
    }
}
