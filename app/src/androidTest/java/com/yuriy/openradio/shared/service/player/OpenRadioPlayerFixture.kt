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
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.eq.EqualizerState
import com.yuriy.openradio.shared.model.media.BrowseTree

/**
 * Builds an [OpenRadioPlayer] the way the service does, minus the parts no player test is about:
 * its listener is ignored and its equalizer is never attached. Call it on the main thread.
 */
@UnstableApi
internal fun newOpenRadioPlayer(context: Context): OpenRadioPlayer {
    return OpenRadioPlayer(context, IgnoringListener(), InertEqualizerLayer(), BrowseTree())
}

/**
 * Runs [block] on the main thread and returns its result. ExoPlayer is bound to the looper it was
 * built on, and the service builds it on the main one.
 */
internal fun <T> onMain(block: () -> T): T {
    var result: Result<T>? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        result = runCatching(block)
    }
    return result?.getOrThrow() ?: throw IllegalStateException("The main thread ran nothing")
}

private class IgnoringListener : OpenRadioPlayer.Listener {

    override fun onHandledError(error: PlaybackException) = Unit

    override fun onPlaybackStateChanged(mediaItem: MediaItem) = Unit
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
