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

package com.yuriy.openradio.mobile.journey

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.testing.onMainThread
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [JourneyPlayback] selects on the main thread, and a selection that throws there has to fail the
 * journey that made it rather than kill the process and every journey after it.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class JourneyPlaybackTest {

    @Test
    fun aSelectionWithoutAConnectionFailsTheCaller() {
        val playback = JourneyPlayback(InstrumentationRegistry.getInstrumentation().targetContext)
        val item = MediaItem.Builder().setMediaId("unconnected").build()

        assertThrows(IllegalStateException::class.java) { playback.select(item, "parent") }

        assertTrue("The main thread stopped answering after the selection failed", onMainThread { true })
    }
}
