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

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Classification of playback errors, which decides what the listener hears about a broken stream.
 * Every case is built from a real [PlaybackException]; none of them reaches a network.
 */
class PlaybackErrorClassifierTest {

    @Test
    fun networkFailureIsReportedAsLostNetwork() {
        val classifier = PlaybackErrorClassifier()

        val decision = classifier.classify(networkFailure())

        assertEquals(PlaybackErrorDecision.NetworkLost, decision)
    }

    @Test
    fun forbiddenStreamIsReportedAsRejected() {
        val classifier = PlaybackErrorClassifier()

        val decision = classifier.classify(httpFailure(HttpURLConnection.HTTP_FORBIDDEN))

        assertEquals(PlaybackErrorDecision.StreamRejected(StreamRejectionReason.FORBIDDEN), decision)
    }

    @Test
    fun missingStreamIsReportedAsRejected() {
        val classifier = PlaybackErrorClassifier()

        val decision = classifier.classify(httpFailure(HttpURLConnection.HTTP_NOT_FOUND))

        assertEquals(PlaybackErrorDecision.StreamRejected(StreamRejectionReason.NOT_FOUND), decision)
    }

    @Test
    fun anyOtherHttpStatusIsReportedWithoutItsOwnReason() {
        val classifier = PlaybackErrorClassifier()

        val decision = classifier.classify(httpFailure(HttpURLConnection.HTTP_INTERNAL_ERROR))

        assertEquals(PlaybackErrorDecision.StreamRejected(StreamRejectionReason.OTHER), decision)
    }

    @Test
    fun unreadableStreamIsReportedAsUnsupportedFormat() {
        val classifier = PlaybackErrorClassifier()

        val decision = classifier.classify(unrecognizedFormat())

        assertEquals(PlaybackErrorDecision.UnsupportedFormat, decision)
    }

    @Test
    fun errorWithNoSpecificCauseIsToleratedWhileTheBudgetHolds() {
        val classifier = PlaybackErrorClassifier()

        val decision = classifier.classify(genericFailure())

        assertEquals(PlaybackErrorDecision.WithinErrorBudget, decision)
    }

    @Test
    fun theErrorBudgetEndsInAnUnrecoverableStream() {
        val classifier = PlaybackErrorClassifier()

        val decisions = (1..TOLERATED_ERRORS + 1).map { classifier.classify(genericFailure()) }

        assertEquals(
            List(TOLERATED_ERRORS) { PlaybackErrorDecision.WithinErrorBudget } +
                PlaybackErrorDecision.Unrecoverable,
            decisions
        )
    }

    @Test
    fun anUnreadableStreamStopsBeingRetriedOnceTheBudgetIsSpent() {
        val classifier = PlaybackErrorClassifier()
        repeat(TOLERATED_ERRORS) { classifier.classify(unrecognizedFormat()) }

        val decision = classifier.classify(unrecognizedFormat())

        assertEquals(PlaybackErrorDecision.Unrecoverable, decision)
    }

    @Test
    fun aReadyStreamStartsTheErrorBudgetOver() {
        val classifier = PlaybackErrorClassifier()
        repeat(TOLERATED_ERRORS) { classifier.classify(genericFailure()) }

        classifier.reset()

        assertEquals(PlaybackErrorDecision.WithinErrorBudget, classifier.classify(genericFailure()))
    }

    @Test
    fun networkAndHttpFailuresDoNotSpendTheErrorBudget() {
        val classifier = PlaybackErrorClassifier()

        repeat(TOLERATED_ERRORS * 2) {
            classifier.classify(networkFailure())
            classifier.classify(httpFailure(HttpURLConnection.HTTP_NOT_FOUND))
        }

        assertEquals(PlaybackErrorDecision.WithinErrorBudget, classifier.classify(genericFailure()))
    }

    private fun networkFailure(): PlaybackException {
        return PlaybackException(
            "Unable to connect", IOException("Unable to connect"),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )
    }

    private fun httpFailure(responseCode: Int): PlaybackException {
        val cause = HttpDataSource.InvalidResponseCodeException(
            responseCode, "Response code: $responseCode", IOException("Bad status"), emptyMap(),
            DataSpec(Uri.parse(STREAM_URL)), ByteArray(0)
        )
        return PlaybackException(
            cause.message, cause, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
    }

    private fun unrecognizedFormat(): PlaybackException {
        val cause = UnrecognizedInputFormatException(
            "None of the available extractors could read the stream.", Uri.parse(STREAM_URL)
        )
        return PlaybackException(
            cause.message, cause, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        )
    }

    private fun genericFailure(): PlaybackException {
        return PlaybackException(
            "Decoder failed", IllegalStateException("Decoder failed"),
            PlaybackException.ERROR_CODE_DECODING_FAILED
        )
    }

    private companion object {

        /**
         * Number of errors the classifier tolerates before it gives the stream up. Kept here so a
         * change to the production budget fails these tests instead of passing quietly.
         */
        const val TOLERATED_ERRORS = 6

        const val STREAM_URL = "http://localhost/stream.mp3"
    }
}
