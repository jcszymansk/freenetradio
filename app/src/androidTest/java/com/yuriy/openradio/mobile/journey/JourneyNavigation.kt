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
import androidx.test.core.app.ActivityScenario
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.MediaPresenterDependency
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.presenter.MediaPresenter

/**
 * Moving between browse nodes from a journey, and getting back out afterwards.
 *
 * Offline, no journey can open a node by tapping its row: `MediaPresenterImpl.handleItemSelected`
 * gates every browse tap on connectivity and is the only tap-driven way in, so the nodes that need
 * no connection at all are shut along with the ones that do. TASK-049 lifts that, and each journey
 * pins the refusal down where it meets it. Until then [open] calls the one step that tap performs
 * once past the gate, so everything after it - the children the service answers with, the rows the
 * adapter renders, the controls on them and the node the presenter reports standing in - is the
 * application's own.
 *
 * Getting back out is not optional. The presenter is one of the registry's singletons and its node
 * stack outlives the Activity, so a case that walks into a node and leaves it there sends the next
 * case's Activity straight back into it, in the same process.
 */
@UnstableApi
internal class JourneyNavigation(private val mScenario: ActivityScenario<MainActivity>) {

    /**
     * Opens the node [mediaId], the way a tap on its row would once there is a connection.
     */
    fun open(mediaId: String) {
        val presenter = presenter()
        mScenario.onActivity { presenter.addMediaItemToStack(mediaId) }
    }

    /**
     * Walks the presenter back out of whatever node a case opened.
     *
     * Popping stops at the root: `handleBackPressed` treats the root as "leave the application"
     * and sends `CMD_STOP_SERVICE`, which ends in `Process.killProcess` and would take the whole
     * run with it.
     *
     * This asserts nothing, because it is meant to be called from a `finally` and an assertion
     * there would replace whatever failure sent the case into it. A failure to get back out is
     * caught by the case's own root-list assertion, and one that escaped the case entirely by the
     * next case asserting the node it starts in.
     */
    fun returnToRoot() {
        val presenter = presenter()
        mScenario.onActivity {
            var remaining = MAX_BROWSE_DEPTH
            while (presenter.getCurrentCategory() != MediaId.MEDIA_ID_ROOT && remaining-- > 0) {
                presenter.handleBackPressed()
            }
        }
    }

    /**
     * @return the registry's own presenter, the one [MainActivity] is driving, reached through the
     *   single-method hook that is the supported way to it.
     */
    private fun presenter(): MediaPresenter {
        lateinit var result: MediaPresenter
        DependencyRegistryCommonUi.inject(
            object : MediaPresenterDependency {

                override fun configureWith(mediaPresenter: MediaPresenter) {
                    result = mediaPresenter
                }
            }
        )
        return result
    }

    private companion object {

        /**
         * How far a case is allowed to have walked into the browse tree, so that popping back out
         * cannot loop for ever if the stack ever stops shrinking.
         */
        const val MAX_BROWSE_DEPTH = 8
    }
}
