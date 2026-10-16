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

package com.yuriy.openradio.shared.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.TimeUnit

/**
 * Destroys the running [OpenRadioService], so that the next connection creates a new one.
 *
 * The service otherwise outlives every test class, and a new instance is the nearest an
 * instrumented test gets to the service a killed process starts again with: it runs `onCreate`
 * and holds a new player and browse tree, so nothing is remembered in memory, while the
 * registry's singletons and the stores carry over as they would on disk. What a new process
 * would also drop, the registry's caches, is not dropped here.
 *
 * A service is only destroyed once it is stopped and nothing is bound to it, and every
 * [ServiceBrowser] is a binding. The caller releases each of its own before asking, and a binding
 * from anywhere else fails the wait rather than leaving the old instance in place unnoticed.
 */
@UnstableApi
internal class ServiceRecreation(private val mContext: Context) {

    fun destroyTheService() {
        mContext.stopService(Intent(mContext, OpenRadioService::class.java))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (isRunning()) {
            if (System.nanoTime() >= deadline) {
                throw AssertionError(
                    "OpenRadioService was still running ${TIMEOUT_SECONDS}s after it was stopped, " +
                        "so something is still bound to it"
                )
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    /**
     * Android stopped reporting the services of other applications in API 26, and still reports
     * the caller's own, which is all this asks about.
     */
    @Suppress("DEPRECATION")
    private fun isRunning(): Boolean {
        val manager = mContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.packageName == mContext.packageName &&
                it.service.className == OpenRadioService::class.java.name
        }
    }

    private companion object {

        const val TIMEOUT_SECONDS = 15L

        const val POLL_MILLIS = 50L
    }
}
