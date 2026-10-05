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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision a network-connected event acts on: whether the station that stopped is one the
 * network, rather than the user, stopped. Every case is a sequence of player events; none of them
 * needs a network or a stream.
 */
class NetworkRecoveryTest {

    @Test
    fun freshPlayerDoesNotResumeOnReconnect() {
        assertFalse(NetworkRecovery.IDLE.resumesOnReconnect)
    }

    @Test
    fun stationStoppedByTheNetworkResumesOnReconnect() {
        val recovery = NetworkRecovery.IDLE.onNetworkLost(isPlayRequested = true)

        assertEquals(NetworkRecovery.AWAITING_NETWORK, recovery)
        assertTrue(recovery.resumesOnReconnect)
    }

    @Test
    fun repeatedNetworkFailureKeepsWaitingForTheNetwork() {
        val recovery = NetworkRecovery.IDLE
            .onNetworkLost(isPlayRequested = true)
            .onNetworkLost(isPlayRequested = true)

        assertTrue(recovery.resumesOnReconnect)
    }

    @Test
    fun networkFailureWhilePausedDoesNotResumeOnReconnect() {
        val recovery = NetworkRecovery.IDLE.onNetworkLost(isPlayRequested = false)

        assertFalse(recovery.resumesOnReconnect)
    }

    @Test
    fun networkFailureAfterUserPausedForgetsAnEarlierNetworkStop() {
        val recovery = NetworkRecovery.IDLE
            .onNetworkLost(isPlayRequested = true)
            .onNetworkLost(isPlayRequested = false)

        assertFalse(recovery.resumesOnReconnect)
    }

    @Test
    fun recoveredPlaybackDoesNotResumeOnALaterReconnect() {
        val recovery = NetworkRecovery.IDLE
            .onNetworkLost(isPlayRequested = true)
            .onPlaybackReady()

        assertEquals(NetworkRecovery.IDLE, recovery)
        assertFalse(recovery.resumesOnReconnect)
    }

    @Test
    fun userStopAfterNetworkLossDoesNotResumeOnReconnect() {
        val recovery = NetworkRecovery.IDLE
            .onNetworkLost(isPlayRequested = true)
            .onStopRequested()

        assertFalse(recovery.resumesOnReconnect)
    }

    @Test
    fun userStopOfAHealthyStationDoesNotResumeOnReconnect() {
        val recovery = NetworkRecovery.IDLE
            .onPlaybackReady()
            .onStopRequested()

        assertFalse(recovery.resumesOnReconnect)
    }

    @Test
    fun secondOutageAfterRecoveryResumesAgain() {
        val recovery = NetworkRecovery.IDLE
            .onNetworkLost(isPlayRequested = true)
            .onPlaybackReady()
            .onNetworkLost(isPlayRequested = true)

        assertTrue(recovery.resumesOnReconnect)
    }

    @Test
    fun readyStateWithoutAnOutageStaysIdle() {
        assertEquals(NetworkRecovery.IDLE, NetworkRecovery.IDLE.onPlaybackReady())
    }

    @Test
    fun stopRequestedWithoutAnOutageStaysIdle() {
        assertEquals(NetworkRecovery.IDLE, NetworkRecovery.IDLE.onStopRequested())
    }
}
