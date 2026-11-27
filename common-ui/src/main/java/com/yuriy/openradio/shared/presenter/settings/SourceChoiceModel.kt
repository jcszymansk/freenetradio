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
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.presenter.screen.ChoiceKind
import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.shared.presenter.screen.ScreenStack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The source choice: every source, the active one, and whether "Restart to apply" shows. */
data class SourceChoiceState(val sources: List<Source>, val active: Source, val restartPending: Boolean)

/**
 * The Source choice in Settings.
 *
 * A choice is made active at once and applies on restart. Leaving the screen without restarting
 * puts back the source that was active when it opened. Only a pop off the [ScreenStack] counts as
 * a leave, so an activity recreation neither reverts nor loses a pending choice.
 *
 * Main thread only.
 */
@MainThread
class SourceChoiceModel(
    private val mSources: SourcesLayer,
    private val mRestart: AppRestart,
    private val mScreens: ScreenStack,
) : ScreenStack.LeaveListener {

    /** The source active when the sub-screen opened, or null while it is not open. */
    private var mOpenedWith: Source? = null

    private val mState = MutableStateFlow(read())

    /** The source choice as the screen shows it. */
    val state: StateFlow<SourceChoiceState> = mState.asStateFlow()

    init {
        mScreens.addLeaveListener(this)
    }

    /** Shows the source choice, remembering the source that is active now. */
    @MainThread
    fun open() {
        mOpenedWith = mSources.getActiveSource()
        mState.value = read()
        mScreens.push(DESTINATION)
    }

    /** Makes [source] the active source at once; it applies on restart. */
    @MainThread
    fun choose(source: Source) {
        mSources.setActiveSource(source)
        mState.value = read()
    }

    /** Restarts the application so the chosen source applies. */
    @MainThread
    fun restart() {
        mRestart.restart()
    }

    override fun onLeave(destination: Destination) {
        if (destination != DESTINATION) {
            return
        }
        val openedWith = mOpenedWith
        if (openedWith != null && openedWith != mSources.getActiveSource()) {
            mSources.setActiveSource(openedWith)
        }
        mOpenedWith = null
        mState.value = read()
    }

    private fun read(): SourceChoiceState {
        val active = mSources.getActiveSource()
        return SourceChoiceState(
            sources = mSources.getAllSources().toList(),
            active = active,
            restartPending = mOpenedWith != null && mOpenedWith != active,
        )
    }

    private companion object {
        val DESTINATION = Destination.SettingsChoice(ChoiceKind.SOURCE)
    }
}
