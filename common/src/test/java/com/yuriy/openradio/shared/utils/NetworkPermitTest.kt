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

package com.yuriy.openradio.shared.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NetworkPermitTest {

    @Test
    fun aPermitTheNetworkAllowsIsNotRevoked() {
        val permit = NetworkPermit { true }

        assertTrue(permit.mayContinue())
        assertTrue(permit.mayContinue())
        assertFalse(permit.isRevoked)
    }

    @Test
    fun aPermitStartsUnrevokedBeforeAnythingAsks() {
        var questions = 0
        val permit = NetworkPermit {
            questions++
            false
        }

        assertFalse(permit.isRevoked)
        assertEquals(0, questions)
    }

    @Test
    fun aRefusalRevokesThePermit() {
        val permit = NetworkPermit { false }

        assertFalse(permit.mayContinue())
        assertTrue(permit.isRevoked)
    }

    /**
     * The race the permit exists for: the network refuses one part of the read and is usable
     * again by the time the read looks back. The refusal still counts.
     */
    @Test
    fun aRevokedPermitStaysRevokedAndStopsAsking() {
        val answers = ArrayDeque(listOf(true, false, true, true))
        var questions = 0
        val permit = NetworkPermit {
            questions++
            answers.removeFirst()
        }

        assertTrue(permit.mayContinue())
        assertFalse(permit.mayContinue())
        assertFalse(permit.mayContinue())
        assertTrue(permit.isRevoked)
        assertEquals(2, questions)
    }

    /**
     * The downloader asks from its own threads, and the read looks from the one it runs on.
     */
    @Test
    fun aRefusalOnAnotherThreadIsSeenByTheReader() {
        val reader = Thread.currentThread()
        val permit = NetworkPermit { Thread.currentThread() == reader }
        val other = Executors.newSingleThreadExecutor()
        try {
            assertFalse(other.submit<Boolean> { permit.mayContinue() }.get(5, TimeUnit.SECONDS))
        } finally {
            other.shutdownNow()
        }

        assertTrue(permit.isRevoked)
        assertFalse(permit.mayContinue())
    }

    @Test
    fun revokingEndsThePermitWithoutAskingTheNetwork() {
        var questions = 0
        val permit = NetworkPermit {
            questions++
            true
        }

        permit.revoke()

        assertTrue(permit.isRevoked)
        assertFalse(permit.mayContinue())
        assertEquals(0, questions)
    }

    @Test
    fun aHeldReadRunsAndAnswersWhileThePermitStands() {
        val connection = RecordingConnection()

        val answer = NetworkPermit { true }.hold(connection) { 42 }

        assertEquals(42, answer)
        assertEquals(0, connection.disconnects)
    }

    @Test
    fun revokingDisconnectsAConnectionHeldOnAnotherThread() {
        val permit = NetworkPermit { true }
        val connection = RecordingConnection()
        val holding = CountDownLatch(1)
        val other = Executors.newSingleThreadExecutor()
        try {
            val read = other.submit<Int> {
                permit.hold(connection) {
                    holding.countDown()
                    connection.awaitDisconnect()
                    connection.disconnects
                }
            }
            assertTrue(holding.await(5, TimeUnit.SECONDS))

            permit.revoke()

            assertEquals(1, read.get(5, TimeUnit.SECONDS))
        } finally {
            other.shutdownNow()
        }
    }

    /**
     * A connection held after the revocation would otherwise be read in full, so it is
     * disconnected before its read starts.
     */
    @Test
    fun aConnectionHeldAfterTheRevocationIsDisconnectedBeforeItsRead() {
        val permit = NetworkPermit { true }
        val connection = RecordingConnection()
        permit.revoke()

        val disconnectsSeenByTheRead = permit.hold(connection) { connection.disconnects }

        assertEquals(1, disconnectsSeenByTheRead)
    }

    @Test
    fun aConnectionNoLongerHeldIsLeftAloneByALaterRevocation() {
        val permit = NetworkPermit { true }
        val connection = RecordingConnection()
        permit.hold(connection) { Unit }

        permit.revoke()

        assertEquals(0, connection.disconnects)
    }

    @Test
    fun aConnectionWhoseReadFailedIsNoLongerHeld() {
        val permit = NetworkPermit { true }
        val connection = RecordingConnection()
        val failure = runCatching { permit.hold(connection) { throw IllegalStateException("read failed") } }

        permit.revoke()

        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        assertEquals(0, connection.disconnects)
    }

    @Test
    fun revokingDisconnectsEveryConnectionHeldAtThatMoment() {
        val permit = NetworkPermit { true }
        val outer = RecordingConnection()
        val inner = RecordingConnection()

        permit.hold(outer) {
            permit.hold(inner) {
                permit.revoke()
            }
        }

        assertEquals(1, outer.disconnects)
        assertEquals(1, inner.disconnects)
    }

    /**
     * An [HttpURLConnection] that never touches the network and counts its disconnects.
     */
    private class RecordingConnection : HttpURLConnection(URL("http://permit.test/")) {

        private val mDisconnected = CountDownLatch(1)

        @Volatile
        var disconnects = 0
            private set

        fun awaitDisconnect() {
            assertTrue("Never disconnected", mDisconnected.await(5, TimeUnit.SECONDS))
        }

        override fun disconnect() {
            disconnects++
            mDisconnected.countDown()
        }

        override fun usingProxy() = false

        override fun connect() = Unit
    }
}
