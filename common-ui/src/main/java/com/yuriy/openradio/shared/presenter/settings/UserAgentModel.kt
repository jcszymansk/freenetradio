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

/** The user agent sub-screen: whether the custom user agent is on, and the field's text as typed. */
data class UserAgentState(val custom: Boolean, val text: String) {

    /**
     * Whether the screen shows the empty-field error: the switch is on and the field holds
     * nothing but blanks. Kept out of the constructor so it never disagrees with the text.
     */
    val emptyWarning: Boolean
        get() = custom && text.trimUserAgent().isEmpty()
}

/**
 * The User agent sub-screen of Settings.
 *
 * The switch is stored as soon as it is toggled. The typed text is a draft held here, so an
 * activity recreation keeps it; it is stored, trimmed, when the screen is popped off the
 * [ScreenStack], unless nothing is left after trimming, in which case the stored user agent stays.
 * Leaving is never blocked.
 *
 * Main thread only.
 */
@MainThread
class UserAgentModel(
    private val mStore: UserAgentStore,
    private val mScreens: ScreenStack,
) : ScreenStack.LeaveListener {

    private val mState = MutableStateFlow(read())

    /** The user agent sub-screen as the screen shows it. */
    val state: StateFlow<UserAgentState> = mState.asStateFlow()

    init {
        mScreens.addLeaveListener(this)
    }

    /** Shows the sub-screen with the flag and the user agent in use now. */
    @MainThread
    fun open() {
        mState.value = read()
        mScreens.push(Destination.UserAgent)
    }

    /** Turns the custom user agent [on] or off, storing the flag at once. */
    @MainThread
    fun setCustom(on: Boolean) {
        mStore.isCustom = on
        mState.value = mState.value.copy(custom = on)
    }

    /** Keeps [text] as typed; it is stored only when the screen is left. */
    @MainThread
    fun edit(text: String) {
        mState.value = mState.value.copy(text = text)
    }

    /**
     * Stores the trimmed text when the user agent screen leaves, unless it is empty. The text is
     * stored whatever the switch says: with the switch off it is unused, because the flag is read
     * first.
     */
    override fun onLeave(destination: Destination) {
        if (destination != Destination.UserAgent) {
            return
        }
        val userAgent = mState.value.text.trimUserAgent()
        if (userAgent.isNotEmpty()) {
            mStore.storeCustom(userAgent)
        }
    }

    private fun read(): UserAgentState = UserAgentState(mStore.isCustom, mStore.current)
}

/** Trims as the old settings dialog did, control characters included. */
private fun String.trimUserAgent(): String = trim { it <= ' ' }
