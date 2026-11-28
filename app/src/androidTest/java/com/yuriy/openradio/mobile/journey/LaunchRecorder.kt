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

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Records the starts of [mActions] the application asks for and keeps them from opening, while
 * every other start goes ahead. A chooser and a browser belong to other applications, and the
 * device is offline, so a journey checks the intent and nothing past it.
 */
internal class LaunchRecorder(private vararg val mActions: String) : Instrumentation.ActivityMonitor() {

    private val mStarts = LinkedBlockingQueue<Intent>()

    override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
        if (intent.action !in mActions) {
            return null
        }
        mStarts.add(intent)
        return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
    }

    fun install() {
        InstrumentationRegistry.getInstrumentation().addMonitor(this)
    }

    fun remove() {
        InstrumentationRegistry.getInstrumentation().removeMonitor(this)
    }

    /**
     * Waits for the first start of one of the actions and returns its intent.
     *
     * [pump] runs between polls for a caller that has to keep something moving before the start
     * can happen, such as the Compose frames of a start the application makes only after an
     * asynchronous result.
     */
    fun await(timeoutSeconds: Long = TIMEOUT_SECONDS, pump: () -> Unit = {}): Intent {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            mStarts.poll(POLL_MILLIS, TimeUnit.MILLISECONDS)?.let { return it }
            pump()
        }
        throw AssertionError("No start of ${mActions.toList()} within $timeoutSeconds seconds")
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L

        const val POLL_MILLIS = 50L
    }
}
