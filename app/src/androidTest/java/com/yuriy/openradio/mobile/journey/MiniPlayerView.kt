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

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import java.util.concurrent.TimeUnit

/**
 * The mini player, read through its tags.
 */
internal class MiniPlayerView(private val mCompose: ComposeTestRule) {

    fun isVisible(): Boolean = hasNode(MainScreenTags.MINI_PLAYER)

    /** @return the station name the mini player shows, or null while it is down. */
    fun name(): String? = text(MainScreenTags.MINI_NAME)

    fun awaitName(expected: String) = await("the name '$expected'") { text(MainScreenTags.MINI_NAME) == expected }

    fun awaitTrack(expected: String) = await("the track '$expected'") { text(MainScreenTags.MINI_TRACK) == expected }

    fun awaitPlaying(expected: Boolean) = await("the button to read ${if (expected) "pause" else "play"}") {
        buttonDescription() == string(if (expected) R.string.main_pause else R.string.main_play)
    }

    fun tapButton() {
        mCompose.onNodeWithTag(MainScreenTags.MINI_TOGGLE).performClick()
    }

    /** Taps the station's name, which acts on nothing until a Now playing screen exists. */
    fun tapName() {
        mCompose.onNodeWithTag(MainScreenTags.MINI_NAME).performClick()
    }

    fun describe(): String {
        return "The mini player is ${if (isVisible()) "up" else "down"}, showing '${text(MainScreenTags.MINI_NAME)}' " +
            "and '${text(MainScreenTags.MINI_TRACK)}', button '${buttonDescription()}'."
    }

    /**
     * @return the toggle's content description. It sits on the icon inside the button, so it is
     *   read from the unmerged tree, the button's own node first.
     */
    private fun buttonDescription(): String? {
        return mCompose.onAllNodes(hasTestTag(MainScreenTags.MINI_TOGGLE), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .flatMap { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                    node.children.flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            }
            .firstOrNull()
    }

    private fun text(tag: String): String? {
        return mCompose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
    }

    private fun hasNode(tag: String): Boolean {
        return mCompose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun string(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun await(expectation: String, done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MainScreenView.TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (done()) {
                return
            }
            Thread.sleep(MainScreenView.POLL_MILLIS)
        }
        throw AssertionError("The mini player did not show $expectation. " + describe())
    }
}
