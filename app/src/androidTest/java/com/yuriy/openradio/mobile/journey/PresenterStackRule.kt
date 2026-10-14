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
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.testing.onMainThread
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * Fails a journey case that leaves the presenter's node stack other than it found it, and takes
 * the nodes it left off again so that the failure stays with that case.
 *
 * The presenter is one of the registry's singletons and its stack outlives the Activity, so a case
 * that leaves a node on it sends the next Activity in the process straight into that node. Every
 * navigating case walks back out in a `finally`; this is what notices when one does not, whichever
 * case and whichever class it was. An empty stack and one holding only the root count as the same
 * stack: an Activity puts the root on an empty stack as it starts, and from either one the next
 * Activity starts at the root.
 *
 * The case's own failure, if it has one, comes first. A case that fails inside a node is the likely
 * way to leave one behind, and what it failed on is what says what broke.
 *
 * The check runs after the case's `@After`, by which time its Activity is gone. Without an Activity
 * the presenter can drop a node but not show one, so a stack the case emptied below what it found
 * is reported and left as it is.
 */
@UnstableApi
class PresenterStackRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {

            override fun evaluate() {
                val found = stack()
                val failures = mutableListOf<Throwable>()
                try {
                    base.evaluate()
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                val left = stack()
                if (normalised(left) != normalised(found)) {
                    failures.add(
                        AssertionError(
                            "${description.displayName} left the presenter's node stack at $left and " +
                                "found it at $found. The stack is a registry singleton that outlives " +
                                "the Activity, so the next case would have launched into the node this " +
                                "one left. The nodes it added have been taken off again."
                        )
                    )
                }
                restore(found, left)
                MultipleFailureException.assertEmpty(failures)
            }
        }
    }

    private fun stack(): List<String> {
        val presenter = registryPresenter()
        return onMainThread { presenter.getMediaItemsStack() }
    }

    /**
     * Drops every node [found] did not hold, the root included when [found] was empty, which puts
     * back any stack a case only added to.
     */
    private fun restore(found: List<String>, left: List<String>) {
        val added = left.toSet() - found.toSet()
        if (added.isEmpty()) {
            return
        }
        val presenter = registryPresenter()
        onMainThread { added.forEach(presenter::unsubscribeFromItem) }
    }

    private fun normalised(stack: List<String>): List<String> {
        return stack.ifEmpty { listOf(MediaId.MEDIA_ID_ROOT) }
    }
}
