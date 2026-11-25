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
import org.junit.Test

/**
 * Pins the logcat lines the owner reads to see whether audio offload engaged. Every offload state
 * change must leave exactly one line, worded so that it says which state the player entered.
 */
class AudioOffloadLogTest {

    private val mLogged = arrayListOf<String>()

    private val mLog = AudioOffloadLog { mLogged.add(it) }

    @Test
    fun offloadedPlaybackStartingIsLogged() {
        mLog.onOffloadedPlayback(true)

        assertEquals(
            listOf("AudioOffload offloaded playback started"),
            mLogged
        )
    }

    @Test
    fun offloadedPlaybackStoppingIsLogged() {
        mLog.onOffloadedPlayback(false)

        assertEquals(
            listOf("AudioOffload offloaded playback stopped"),
            mLogged
        )
    }

    @Test
    fun sleepingForOffloadIsLogged() {
        mLog.onSleepingForOffloadChanged(true)

        assertEquals(
            listOf("AudioOffload player sleeps for offload"),
            mLogged
        )
    }

    @Test
    fun wakingFromOffloadSleepIsLogged() {
        mLog.onSleepingForOffloadChanged(false)

        assertEquals(
            listOf("AudioOffload player woke from offload sleep"),
            mLogged
        )
    }
}
