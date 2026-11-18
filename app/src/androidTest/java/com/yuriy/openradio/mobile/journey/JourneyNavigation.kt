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
import com.yuriy.openradio.shared.dependencies.MainScreenModelDependency
import com.yuriy.openradio.shared.presenter.main.BrowseSnapshot
import com.yuriy.openradio.shared.presenter.main.MainScreenModel
import com.yuriy.openradio.shared.presenter.main.Tab
import com.yuriy.openradio.testing.withActivity

/**
 * Moving between the main screen's tabs from a journey, and getting back out afterwards.
 *
 * Tabs are switched by tapping them, as a user does. A search is the one way deeper into a tab
 * that no connectivity gate stands in front of, so it is pushed through the screen's
 * [MainScreenModel] directly, which is what the search field does once a query is entered.
 *
 * Getting back out is not optional. The model is one of the registry's singletons and its tab
 * stacks and selected tab outlive the Activity, so a case that leaves a search on a tab, or
 * another tab selected, sends the next case's Activity straight back there, in the same process.
 */
@UnstableApi
internal class JourneyNavigation(
    private val mScenario: ActivityScenario<MainActivity>,
    private val mView: MainScreenView,
) {

    /** Switches to [tab] by tapping it, as a user does; tabs are not gated on the network. */
    fun selectTab(tab: Tab) {
        mView.selectTab(tab)
    }

    /** Pushes a search for [query] onto the selected tab, the one drill-down that needs no network gate. */
    fun search(query: String) {
        val model = registryModel()
        mScenario.withActivity { model.search(query) }
    }

    fun snapshot(): BrowseSnapshot {
        val model = registryModel()
        return mScenario.withActivity { model.snapshot() }
    }

    /**
     * Puts every tab back at its root and selects FAV. Reselecting a tab pops it to its root, so
     * this needs nothing but the screen's own actions, and never presses back at a root, which
     * would leave the screen.
     *
     * This asserts nothing, because it is meant to be called from a `finally` and an assertion
     * there would replace whatever failure sent the case into it. A case that escaped without
     * getting back out is caught by [BrowseStacksRule].
     */
    fun returnToRoots() {
        val model = registryModel()
        mScenario.withActivity { resetToRoots(model) }
    }
}

/**
 * Closes Now playing, pops every tab of [model] to its root and selects FAV. Main thread only.
 */
internal fun resetToRoots(model: MainScreenModel) {
    model.closePlayer()
    for ((tab, stack) in model.snapshot().stacks) {
        if (stack.size > 1 && tab in model.state.value.tabs) {
            model.selectTab(tab)
            model.selectTab(tab)
        }
    }
    model.selectTab(Tab.FAV)
}

/**
 * @return the registry's own model, the one [MainActivity] renders, reached through the
 *   single-method hook that is the supported way to it.
 */
internal fun registryModel(): MainScreenModel {
    lateinit var result: MainScreenModel
    DependencyRegistryCommonUi.injectMainScreenModel(
        object : MainScreenModelDependency {

            override fun configureWith(model: MainScreenModel) {
                result = model
            }
        }
    )
    return result
}
