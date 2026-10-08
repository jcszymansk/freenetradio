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

package com.yuriy.openradio.shared.model.storage.cache.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiCacheFreshnessTest {

    @Test
    fun theWindowIsOneDayOfMilliseconds() {
        assertEquals(24L * 60L * 60L * 1_000L, ApiCacheFreshness.WINDOW_MILLIS)
    }

    @Test
    fun aRecordReadAtTheMomentItWasWrittenIsFresh() {
        assertTrue(ApiCacheFreshness.isFresh(writtenAtMillis = NOW, nowMillis = NOW))
    }

    @Test
    fun aRecordWellPastTheOldEightySixSecondWindowIsFresh() {
        assertTrue(ApiCacheFreshness.isFresh(writtenAtMillis = NOW - 86_401L, nowMillis = NOW))
        assertTrue(ApiCacheFreshness.isFresh(writtenAtMillis = NOW - HOUR_MILLIS, nowMillis = NOW))
    }

    @Test
    fun aRecordExactlyOneDayOldIsStillFresh() {
        assertTrue(
            ApiCacheFreshness.isFresh(
                writtenAtMillis = NOW - ApiCacheFreshness.WINDOW_MILLIS, nowMillis = NOW
            )
        )
    }

    @Test
    fun aRecordOneMillisecondOlderThanADayIsStale() {
        assertFalse(
            ApiCacheFreshness.isFresh(
                writtenAtMillis = NOW - ApiCacheFreshness.WINDOW_MILLIS - 1L, nowMillis = NOW
            )
        )
    }

    @Test
    fun aRecordDatedAfterTheReadIsStale() {
        assertFalse(ApiCacheFreshness.isFresh(writtenAtMillis = NOW + 1L, nowMillis = NOW))
        assertFalse(ApiCacheFreshness.isFresh(writtenAtMillis = NOW + HOUR_MILLIS, nowMillis = NOW))
    }

    @Test
    fun aRecordWrittenAtTheEpochIsStaleToday() {
        assertFalse(ApiCacheFreshness.isFresh(writtenAtMillis = 0L, nowMillis = NOW))
    }

    @Test
    fun extremeTimestampsDoNotOverflowIntoFreshness() {
        assertFalse(ApiCacheFreshness.isFresh(writtenAtMillis = Long.MIN_VALUE, nowMillis = NOW))
        assertFalse(ApiCacheFreshness.isFresh(writtenAtMillis = Long.MAX_VALUE, nowMillis = NOW))
    }

    private companion object {

        /**
         * 2026-10-08T00:00:00Z, an arbitrary fixed instant so no case depends on the clock.
         */
        const val NOW = 1_791_417_600_000L

        const val HOUR_MILLIS = 3_600_000L
    }
}
