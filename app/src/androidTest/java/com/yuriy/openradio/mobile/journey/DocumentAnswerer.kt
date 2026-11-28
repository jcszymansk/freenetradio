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
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Stands in for the document UI: every `ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT` the
 * application starts is recorded, kept from opening, and answered with [mDocument] as the chosen
 * document, the way the document UI answers. Every other start goes ahead.
 */
internal class DocumentAnswerer(private val mDocument: Uri) : Instrumentation.ActivityMonitor() {

    private val mStarts = LinkedBlockingQueue<Intent>()

    override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
        if (intent.action != Intent.ACTION_CREATE_DOCUMENT && intent.action != Intent.ACTION_OPEN_DOCUMENT) {
            return null
        }
        mStarts.add(intent)
        return Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(mDocument))
    }

    fun install() {
        InstrumentationRegistry.getInstrumentation().addMonitor(this)
    }

    fun remove() {
        InstrumentationRegistry.getInstrumentation().removeMonitor(this)
    }

    fun await(timeoutSeconds: Long = TIMEOUT_SECONDS): Intent {
        return mStarts.poll(timeoutSeconds, TimeUnit.SECONDS)
            ?: throw AssertionError("No document UI was started within $timeoutSeconds seconds")
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
