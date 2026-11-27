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

import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.testing.onMainThread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins what [OpenRadioPlayer] asks of the audio path: offload is enabled, and gapless playback and
 * speed changes are not conditions for it, so a stream a device can offload but not play gaplessly
 * still gets the DSP decoder. Nothing is prepared, so no url is ever opened.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioPlayerOffloadTest {

    private lateinit var mPlayer: OpenRadioPlayer

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        mPlayer = onMainThread { newOpenRadioPlayer(context) }
    }

    @After
    fun tearDown() {
        onMainThread { mPlayer.release() }
    }

    @Test
    fun theTrackSelectionPrefersAudioOffload() {
        val offload = onMainThread { mPlayer.trackSelectionParameters.audioOffloadPreferences }

        assertEquals(
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
            offload.audioOffloadMode
        )
        assertFalse("Gapless support must not be a condition for offload", offload.isGaplessSupportRequired)
        assertFalse(
            "Speed change support must not be a condition for offload",
            offload.isSpeedChangeSupportRequired
        )
    }
}
