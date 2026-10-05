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

package com.yuriy.openradio.shared.model.media

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [PlaybackState.isPlaying]: a player counts as playing only while it is buffering or ready
 * and was asked to play when ready. Every Media3 player state is crossed with both values of
 * playWhenReady, and the constructor defaults describe an idle player that is not playing.
 */
class PlaybackStateTest {

    @Test
    fun aBufferingPlayerAskedToPlayIsPlaying() {
        assertTrue(PlaybackState(Player.STATE_BUFFERING, true).isPlaying)
    }

    @Test
    fun aReadyPlayerAskedToPlayIsPlaying() {
        assertTrue(PlaybackState(Player.STATE_READY, true).isPlaying)
    }

    @Test
    fun aBufferingPlayerThatIsPausedIsNotPlaying() {
        assertFalse(PlaybackState(Player.STATE_BUFFERING, false).isPlaying)
    }

    @Test
    fun aReadyPlayerThatIsPausedIsNotPlaying() {
        assertFalse(PlaybackState(Player.STATE_READY, false).isPlaying)
    }

    @Test
    fun anIdlePlayerIsNotPlayingWhateverPlayWhenReadySays() {
        assertFalse(PlaybackState(Player.STATE_IDLE, true).isPlaying)
        assertFalse(PlaybackState(Player.STATE_IDLE, false).isPlaying)
    }

    @Test
    fun anEndedPlayerIsNotPlayingWhateverPlayWhenReadySays() {
        assertFalse(PlaybackState(Player.STATE_ENDED, true).isPlaying)
        assertFalse(PlaybackState(Player.STATE_ENDED, false).isPlaying)
    }

    @Test
    fun aStateOutsideTheMedia3SetIsNotPlaying() {
        assertFalse(PlaybackState(UNKNOWN_STATE, true).isPlaying)
    }

    @Test
    fun theDefaultsDescribeAnIdlePlayerThatIsNotPlaying() {
        assertFalse(PlaybackState().isPlaying)
    }

    @Test
    fun playWhenReadyDefaultsToFalseSoAReadyPlayerAloneIsNotPlaying() {
        assertFalse(PlaybackState(Player.STATE_READY).isPlaying)
    }

    companion object {
        private const val UNKNOWN_STATE = 99
    }
}
