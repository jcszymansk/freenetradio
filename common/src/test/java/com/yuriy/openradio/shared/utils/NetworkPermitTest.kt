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

        val held = NetworkPermit { true }.hold(connection) { 42 }

        assertEquals(NetworkPermit.Held.Answered(42), held)
        assertEquals(0, connection.disconnects)
    }

    /**
     * Revoking disconnects the connection a read is waiting on, and the read that comes back, by
     * failing or by answering, is not used: the connection is disconnected again in case the
     * first disconnect came before it had connected.
     */
    @Test
    fun revokingDisconnectsAConnectionHeldOnAnotherThreadAndDiscardsItsRead() {
        val permit = NetworkPermit { true }
        val connection = RecordingConnection()
        val holding = CountDownLatch(1)
        var disconnectsSeenByTheRead = 0
        val other = Executors.newSingleThreadExecutor()
        try {
            val held = other.submit<NetworkPermit.Held<Int>> {
                permit.hold(connection) {
                    holding.countDown()
                    connection.awaitDisconnect()
                    disconnectsSeenByTheRead = connection.disconnects
                    42
                }
            }
            assertTrue(holding.await(5, TimeUnit.SECONDS))

            permit.revoke()

            assertEquals(NetworkPermit.Held.Revoked, held.get(5, TimeUnit.SECONDS))
        } finally {
            other.shutdownNow()
        }
        assertEquals(1, disconnectsSeenByTheRead)
        assertEquals(2, connection.disconnects)
    }

    /**
     * A disconnect made before a connection connects is lost, so a read held after the
     * revocation must not run at all, or it would connect.
     */
    @Test
    fun aReadHeldAfterTheRevocationDoesNotRun() {
        val permit = NetworkPermit { true }
        val connection = RecordingConnection()
        var reads = 0
        permit.revoke()

        val held = permit.hold(connection) { reads++ }

        assertEquals(NetworkPermit.Held.Revoked, held)
        assertEquals(0, reads)
        assertEquals(1, connection.disconnects)
    }

    /**
     * The revocation that lands while a connection is connecting, when its disconnect is lost:
     * the read finishes, and the permit disconnects the connection once it can be.
     */
    @Test
    fun aRevocationDuringTheReadDisconnectsAfterIt() {
        val permit = NetworkPermit { true }
        val connection = RecordingConnection()

        val held = permit.hold(connection) {
            permit.revoke()
            connection.forgetDisconnects()
        }

        assertEquals(NetworkPermit.Held.Revoked, held)
        assertEquals(1, connection.disconnects)
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
        var disconnectsAtTheRevocation = Pair(0, 0)

        permit.hold(outer) {
            permit.hold(inner) {
                permit.revoke()
                disconnectsAtTheRevocation = Pair(outer.disconnects, inner.disconnects)
            }
        }

        assertEquals(Pair(1, 1), disconnectsAtTheRevocation)
    }

    @Test
    fun aConnectionStartsWhileThePermitStands() {
        var starts = 0

        val held = NetworkPermit { true }.connect(RecordingConnection()) { starts++ }

        assertEquals(NetworkPermit.Held.Answered(Unit), held)
        assertEquals(1, starts)
    }

    /**
     * A disconnect made before a connection connects is lost, so a connection whose permit is
     * already revoked must not start at all.
     */
    @Test
    fun aConnectionDoesNotStartOnceThePermitIsRevoked() {
        val permit = NetworkPermit { true }
        var starts = 0
        permit.revoke()

        val held = permit.connect(RecordingConnection()) { starts++ }

        assertEquals(NetworkPermit.Held.Revoked, held)
        assertEquals(0, starts)
    }

    /**
     * The network can turn after a request was allowed and before the service learns of it and
     * revokes, so starting the connection asks the network again.
     */
    @Test
    fun aConnectionDoesNotStartOnceTheNetworkRefusesIt() {
        val answers = ArrayDeque(listOf(true, false))
        val permit = NetworkPermit { answers.removeFirst() }
        var starts = 0
        assertTrue(permit.mayContinue())

        val held = permit.connect(RecordingConnection()) { starts++ }

        assertEquals(NetworkPermit.Held.Revoked, held)
        assertEquals(0, starts)
        assertTrue(permit.isRevoked)
    }

    /**
     * The gap a revocation must not fall into: after the permit allowed a connection and before
     * it has started. The revocation waits until it has, and the read of its response then finds
     * the permit revoked, does not run, and disconnects the connection that started.
     */
    @Test
    fun aRevocationWaitsForAConnectionThatIsStartingAndThenEndsIt() {
        val permit = NetworkPermit { true }
        val connection = RecordingConnection()
        val starting = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reader = Executors.newSingleThreadExecutor()
        val revoker = Executors.newSingleThreadExecutor()
        try {
            val started = reader.submit<NetworkPermit.Held<Unit>> {
                permit.connect(connection) {
                    starting.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(starting.await(5, TimeUnit.SECONDS))
            val revocation = revoker.submit { permit.revoke() }
            Thread.sleep(WAIT_MILLIS)

            assertFalse("The revocation did not wait for the connection to start", revocation.isDone)
            assertFalse(permit.isRevoked)

            release.countDown()
            revocation.get(5, TimeUnit.SECONDS)
            assertEquals(NetworkPermit.Held.Answered(Unit), started.get(5, TimeUnit.SECONDS))
        } finally {
            reader.shutdownNow()
            revoker.shutdownNow()
        }
        var reads = 0

        val held = permit.hold(connection) { reads++ }

        assertEquals(NetworkPermit.Held.Revoked, held)
        assertEquals(0, reads)
        assertEquals(1, connection.disconnects)
    }

    /**
     * A connection that takes long to start must not hold up the reads that only ask whether the
     * permit stands, such as every read of another transfer.
     */
    @Test
    fun askingDoesNotWaitForAConnectionThatIsStarting() {
        val permit = NetworkPermit { true }
        val starting = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reader = Executors.newSingleThreadExecutor()
        try {
            reader.submit<NetworkPermit.Held<Unit>> {
                permit.connect(RecordingConnection()) {
                    starting.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(starting.await(5, TimeUnit.SECONDS))

            assertFalse(permit.isRevoked)
            assertTrue(permit.mayContinue())
        } finally {
            release.countDown()
            reader.shutdownNow()
        }
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

        /**
         * Stands for a disconnect made before the connection connected, which does nothing.
         */
        fun forgetDisconnects() {
            disconnects = 0
        }

        override fun usingProxy() = false

        override fun connect() = Unit
    }

    private companion object {

        /**
         * Long enough for a revocation that does not wait to have finished.
         */
        const val WAIT_MILLIS = 300L
    }
}
