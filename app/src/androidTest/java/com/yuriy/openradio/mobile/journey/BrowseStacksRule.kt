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

import androidx.media3.common.util.UnstableApi
import com.yuriy.openradio.shared.presenter.main.BrowseSnapshot
import com.yuriy.openradio.testing.onMainThread
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * Fails a journey case that leaves the main screen's tabs other than it found them, and puts
 * every tab back at its root so that the failure stays with that case.
 *
 * The [com.yuriy.openradio.shared.presenter.main.MainScreenModel] is one of the registry's
 * singletons and its tab stacks and selected tab outlive the Activity, so a case that leaves a
 * search on a tab, or another tab selected, sends the next Activity in the process straight there.
 * Every navigating case walks back out in a `finally`; this is what notices when one does not,
 * whichever case and whichever class it was.
 *
 * The case's own failure, if it has one, comes first. A case that fails part way through is the
 * likely way to leave a tab behind, and what it failed on is what says what broke.
 *
 * It does the same for the settings screen stack, which is a registry singleton too.
 */
@UnstableApi
class BrowseStacksRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {

            override fun evaluate() {
                val found = snapshot()
                val failures = mutableListOf<Throwable>()
                try {
                    base.evaluate()
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                val left = snapshot()
                if (left != found) {
                    failures.add(
                        AssertionError(
                            "${description.displayName} left the main screen at $left and found it at $found. " +
                                "The model is a registry singleton that outlives the Activity, so the next case " +
                                "would have launched into what this one left. Every tab has been put back at its root."
                        )
                    )
                    val model = registryModel()
                    onMainThread { resetToRoots(model) }
                }
                val screens = onMainThread { registrySettings().screens.snapshot() }
                if (screens.isNotEmpty()) {
                    failures.add(
                        AssertionError(
                            "${description.displayName} left the settings screens $screens open. The stack is a " +
                                "registry singleton that outlives the Activity, so the next case would have " +
                                "launched into them. They have been closed."
                        )
                    )
                    onMainThread { registrySettings().screens.clear() }
                }
                MultipleFailureException.assertEmpty(failures)
            }
        }
    }

    private fun snapshot(): BrowseSnapshot {
        val model = registryModel()
        return onMainThread { model.snapshot() }
    }
}
