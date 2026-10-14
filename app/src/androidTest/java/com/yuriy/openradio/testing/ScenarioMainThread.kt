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
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs [block] against the scenario's Activity on the main thread and returns what it answered,
 * or throws on the calling thread whatever it threw.
 *
 * This is what every test uses instead of [ActivityScenario.onActivity]. Called off the main
 * thread, `onActivity` hands its block to `Instrumentation.runOnMainSync`, which does not catch
 * anything, so a throw inside the block escapes on the main looper: the application process dies
 * while the test thread is still waiting, the case is reported as a process crash rather than as
 * the failure it was, and every test after it in the run is lost. Catching inside the block and
 * throwing again here turns the same throw into an ordinary failure of the test that owns it.
 *
 * Assert on what this returns, not inside [block]. A throw that reaches here is still reported
 * properly, but the block is for reading and acting on views, which only the main thread may do.
 *
 * Only a throw from [block] is caught. Calling this after the Activity is destroyed still kills the
 * process, because `onActivity` then fails its own check on the main thread before the block runs.
 */
fun <A : Activity, T> ActivityScenario<A>.withActivity(block: (A) -> T): T {
    val outcome = AtomicReference<Result<T>?>(null)
    onActivity { activity -> outcome.set(runCatching { block(activity) }) }
    val result = outcome.get()
        ?: throw AssertionError("The scenario returned without running the block on its Activity")
    return result.getOrThrow()
}
