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

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.eq.EqualizerState
import com.yuriy.openradio.shared.model.media.BrowseTree

/**
 * Builds an [OpenRadioPlayer] the way the service does, minus the parts no player test is about:
 * its equalizer is never attached, and unless a test passes its own, its listener is ignored and
 * the network policy never blocks playback. Call it on the main thread.
 *
 * @param retrySchedule When the player tries again a stream the network stopped. A test that
 * spends the retries passes a short one instead of waiting out the minutes the service's takes.
 * @param listener What the service would hear from the player.
 * @param isPlaybackBlocked What the service's mobile-network policy answers.
 */
@UnstableApi
internal fun newOpenRadioPlayer(
    context: Context,
    retrySchedule: StreamRetrySchedule = StreamRetrySchedule(),
    listener: OpenRadioPlayer.Listener = IgnoringListener(),
    isPlaybackBlocked: () -> Boolean = { false }
): OpenRadioPlayer {
    return OpenRadioPlayer(
        context, listener, InertEqualizerLayer(), BrowseTree(), isPlaybackBlocked, retrySchedule
    )
}

/**
 * A listener that ignores every event. A test that listens for some extends it.
 */
internal open class IgnoringListener : OpenRadioPlayer.Listener {

    override fun onHandledError(error: PlaybackException) = Unit

    override fun onPlaybackStateChanged(mediaItem: MediaItem) = Unit

    override fun onStreamRetryBlocked() = Unit
}

/**
 * An equalizer that is never attached: the audio effect is not what is under test, and the real
 * one changes the device's stored equalizer state.
 */
private class InertEqualizerLayer : EqualizerLayer {

    override fun init(audioSessionId: Int) = Unit

    override fun deinit() = Unit

    override fun isInit(): Boolean = false

    override fun saveState(state: EqualizerState) = Unit

    override fun loadState(): EqualizerState = EqualizerState()

    override fun applyState(state: EqualizerState, onSuccess: (state: EqualizerState) -> Unit) = Unit
}
