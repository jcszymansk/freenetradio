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

package com.yuriy.openradio.mobile.view.main

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins Now playing's overflow (REQ-008): five items in order, and no copy or share without a
 * stream URL to copy or share.
 */
class PlayerMenuItemTest {

    @Test
    fun aStationWithAStreamUrlIsOfferedEveryItemInOrder() {
        assertEquals(
            listOf(
                PlayerMenuItem.STATION_DETAILS,
                PlayerMenuItem.COPY_STREAM_URL,
                PlayerMenuItem.SHARE_STREAM_URL,
                PlayerMenuItem.SLEEP_TIMER,
                PlayerMenuItem.EQUALIZER,
            ),
            PlayerMenuItem.offeredFor("http://stream.arrow.nl/arrow.pls")
        )
    }

    @Test
    fun aStationWithoutAStreamUrlIsNotOfferedCopyOrShare() {
        assertEquals(
            listOf(PlayerMenuItem.STATION_DETAILS, PlayerMenuItem.SLEEP_TIMER, PlayerMenuItem.EQUALIZER),
            PlayerMenuItem.offeredFor("")
        )
    }
}
