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

package com.yuriy.openradio.testing

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs [block] against the scenario's Activity on the main thread and returns what it answered,
 * or throws on the calling thread whatever it threw.
 *
 * This is what every test uses instead of [ActivityScenario.onActivity], whose block can only hand
 * a value back through a variable the test declares outside it. Returning the value is what lets
 * the block stay on the main thread for reading and acting on views and the test assert on its own
 * thread.
 *
 * A throw inside the block reaches the caller rather than the main looper, which is what makes it
 * fail the test that owns it instead of killing the application process and every test after it.
 * The runner's `MonitoringInstrumentation.runOnMainSync`, which `onActivity` goes through from the
 * test thread, already rethrows a [RuntimeException] or an [Error] on the caller. The block is
 * caught here as well, so the contract does not depend on that override and a checked exception
 * keeps its own type.
 */
fun <A : Activity, T> ActivityScenario<A>.withActivity(block: (A) -> T): T {
    val outcome = AtomicReference<Result<T>?>(null)
    onActivity { activity -> outcome.set(runCatching { block(activity) }) }
    val result = outcome.get()
        ?: throw AssertionError("The scenario returned without running the block on its Activity")
    return result.getOrThrow()
}

/**
 * Runs [block] on the main thread and returns what it answered, or throws on the calling thread
 * whatever it threw: [withActivity] with no Activity, for state that has to be read or changed
 * where the application owns it, such as the registry's singletons between two cases or a Media3
 * controller, which only accepts calls on the looper it was built on.
 *
 * Every test helper that needs the main thread goes through this rather than calling
 * `Instrumentation.runOnMainSync` itself, for the reasons [withActivity] gives.
 */
fun <T> onMainThread(block: () -> T): T {
    val outcome = AtomicReference<Result<T>?>(null)
    InstrumentationRegistry.getInstrumentation().runOnMainSync { outcome.set(runCatching(block)) }
    val result = outcome.get()
        ?: throw AssertionError("The main thread returned without running the block")
    return result.getOrThrow()
}
