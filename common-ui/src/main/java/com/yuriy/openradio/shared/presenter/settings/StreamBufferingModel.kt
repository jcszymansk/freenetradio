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

package com.yuriy.openradio.shared.presenter.settings

import androidx.annotation.MainThread
import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.shared.presenter.screen.ScreenStack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The buffering edit screen: what the fields hold and the error shown under each field. */
data class StreamBufferingState(val texts: BufferTexts, val errors: Map<BufferField, BufferRule>)

/**
 * The Stream buffering edit screen.
 *
 * Unlike the old dialog, which stored on leave, this stores only on Save and only when
 * [validateBuffering] accepts all four values. The ✕ button and system back leave without
 * storing, so whatever was typed is dropped.
 *
 * The draft lives here, not in the composition, so an activity recreation shows what was typed
 * and the errors that were shown.
 *
 * Main thread only.
 *
 * @param bounds the range a value must fall in, which the screen also quotes under the fields.
 */
@MainThread
class StreamBufferingModel(
    private val mBuffers: BufferStore,
    val bounds: BufferBounds,
    private val mScreens: ScreenStack,
) {

    private val mState = MutableStateFlow(read())

    /** The buffering screen as the screen shows it. */
    val state: StateFlow<StreamBufferingState> = mState.asStateFlow()

    /** Shows the screen filled from the stored values, with no errors. */
    @MainThread
    fun open() {
        mState.value = read()
        mScreens.push(DESTINATION)
    }

    /** Replaces the text of [field]; the errors shown under the fields stay until the next save. */
    @MainThread
    fun edit(field: BufferField, text: String) {
        mState.value = mState.value.copy(texts = mState.value.texts.with(field, text))
    }

    /** Fills the fields with the shipped defaults; nothing is stored until Save. */
    @MainThread
    fun restoreDefaults() {
        mState.value = mState.value.copy(texts = BufferTexts.of(BufferSettings.DEFAULTS))
    }

    /** Stores all four values and returns to Settings when they are valid; otherwise shows why. */
    @MainThread
    fun save() {
        when (val validation = validateBuffering(mState.value.texts, bounds)) {
            is BufferValidation.Valid -> {
                mBuffers.values = validation.values
                mScreens.pop()
            }
            is BufferValidation.Invalid -> {
                mState.value = mState.value.copy(
                    errors = validation.errors.associate { it.field to it.rule },
                )
            }
        }
    }

    /** Leaves without storing anything. */
    @MainThread
    fun close() {
        mScreens.pop()
    }

    private fun read(): StreamBufferingState = StreamBufferingState(
        texts = BufferTexts.of(mBuffers.values),
        errors = emptyMap(),
    )

    private companion object {
        val DESTINATION = Destination.StreamBuffering
    }
}
