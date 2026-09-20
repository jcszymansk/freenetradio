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

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue

/**
 * Whether this process is holding an Activity, which is what makes a service first start one.
 *
 * Android Auto binds the media service with no Activity anywhere, so a test that means to prove
 * that ordering has to say what the process was actually holding rather than assume that having
 * launched none is the same thing. Every test class shares one process here and an Activity is a
 * registry-wide fact, so the answer belongs to the process, not to the class asking.
 *
 * [Stage.DESTROYED] is excluded on purpose: an Activity an earlier test already tore down is gone
 * as far as the service is concerned, and counting it would make every check depend on which
 * classes ran before.
 */
internal class ActivityPresence {

    private val mInstrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * @return one entry per Activity that is alive, naming it and the stage it is in, so a
     *   failure says what is holding the process rather than only that something is.
     */
    fun alive(): List<String> {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val result = AtomicReference(emptyList<String>())
        // The lifecycle monitor is main thread state, so it is read there.
        mInstrumentation.runOnMainSync {
            result.set(
                Stage.values()
                    .filter { it != Stage.DESTROYED }
                    .flatMap { stage ->
                        monitor.getActivitiesInStage(stage)
                            .map { "${it.javaClass.simpleName} in $stage" }
                    }
            )
        }
        return result.get()
    }

    fun assertNone(reason: String) {
        val alive = alive()
        assertTrue("$reason The process is holding $alive.", alive.isEmpty())
    }

    /**
     * Waits for the process to be rid of every Activity.
     *
     * Used where one is expected to be going away rather than expected never to have been there:
     * an [androidx.test.core.app.ActivityScenario] hands its Activity to the framework to destroy,
     * and a case that starts by asserting it is gone would otherwise race the previous one's
     * teardown.
     */
    fun awaitNone(reason: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (alive().isEmpty()) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        assertNone(reason)
    }

    private companion object {

        const val TIMEOUT_SECONDS = 10L

        const val POLL_MILLIS = 50L
    }
}
