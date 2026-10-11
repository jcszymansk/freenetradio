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
}
