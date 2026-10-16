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

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [PlayerUtils.playerStateToString].
 */
class PlayerUtilsTest {

    @Test
    fun eachMedia3PlayerStateHasItsName() {
        assertEquals("STATE_IDLE", PlayerUtils.playerStateToString(Player.STATE_IDLE))
        assertEquals("STATE_BUFFERING", PlayerUtils.playerStateToString(Player.STATE_BUFFERING))
        assertEquals("STATE_READY", PlayerUtils.playerStateToString(Player.STATE_READY))
        assertEquals("STATE_ENDED", PlayerUtils.playerStateToString(Player.STATE_ENDED))
    }

    @Test
    fun anUnknownStateIsNamedUndefinedWithItsValue() {
        assertEquals("UNDEFINED{99}", PlayerUtils.playerStateToString(99))
        assertEquals("UNDEFINED{-1}", PlayerUtils.playerStateToString(-1))
    }
}
