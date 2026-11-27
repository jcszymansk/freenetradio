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

package com.yuriy.openradio.shared.presenter.screen

import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.yuriy.openradio.shared.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The screens shown over the main screen and Now playing, top last.
 *
 * The activity renders [top] when there is one, and system back pops it before the main screen's
 * own back. The stack holds no View, Activity or Compose type.
 *
 * It is a registry singleton like the main screen's model, so open screens survive an activity
 * recreation but not the process, and a recreation pops nothing, so to a state holder it is not a
 * leave.
 *
 * Main thread only.
 */
@MainThread
class ScreenStack {

    /**
     * Told about each destination as it leaves, so its state holder can do what leaving that
     * screen does.
     */
    fun interface LeaveListener {
        /**
         * Called with [destination] as it leaves the stack, before the new top is published.
         */
        fun onLeave(destination: Destination)
    }

    private val mDestinations = ArrayList<Destination>()
    private val mLeaveListeners = ArrayList<LeaveListener>()
    private val mTop = MutableStateFlow<Destination?>(null)

    /** The destination on top of the stack, or null while the main screen shows. */
    val top: StateFlow<Destination?> = mTop.asStateFlow()

    /**
     * Adds [listener], told about every destination that leaves after it was added, in the order
     * that the listeners were added.
     */
    @MainThread
    fun addLeaveListener(listener: LeaveListener) {
        mLeaveListeners.add(listener)
    }

    /**
     * Shows [destination] on top of whatever was on top.
     *
     * Pushing the destination that is already on top changes nothing.
     */
    @MainThread
    fun push(destination: Destination) {
        if (destination == mDestinations.lastOrNull()) {
            AppLogger.d("$TAG '$destination' is already on top")
            return
        }
        mDestinations.add(destination)
        mTop.value = destination
    }

    /**
     * Removes the destination on top and shows the one below it, or the main screen when there
     * was no destination below.
     *
     * The leave listeners hear the removed destination before the new top is published.
     *
     * @return true when a destination was removed, false when the stack was empty.
     */
    @MainThread
    fun pop(): Boolean {
        val removed = mDestinations.removeLastOrNull() ?: return false
        for (listener in mLeaveListeners) {
            listener.onLeave(removed)
        }
        mTop.value = mDestinations.lastOrNull()
        return true
    }

    /**
     * Handles system back: pops the stack when anything is on it, and otherwise asks [next].
     *
     * @return true when the back was handled, false when [next] was asked and said no.
     */
    @MainThread
    fun backThen(next: () -> Boolean): Boolean {
        if (pop()) {
            return true
        }
        return next()
    }

    /**
     * Removes every destination, telling the leave listeners about each one top first, and shows
     * the main screen.
     */
    @MainThread
    fun clear() {
        while (pop()) {
            // Each pop tells the listeners about the destination it removed.
        }
    }

    /** The destinations on the stack, bottom first. */
    @VisibleForTesting
    fun snapshot(): List<Destination> = mDestinations.toList()

    private companion object {
        const val TAG = "ScreenStack"
    }
}
