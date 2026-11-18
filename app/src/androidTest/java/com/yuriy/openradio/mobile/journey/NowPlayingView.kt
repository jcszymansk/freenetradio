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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.main.NowPlayingTags
import com.yuriy.openradio.mobile.view.main.PlayerMenuItem
import java.util.concurrent.TimeUnit

/**
 * Now playing and Station details, read and driven through their tags.
 */
internal class NowPlayingView(private val mCompose: ComposeTestRule) {

    fun isShown(): Boolean = has(NowPlayingTags.SCREEN)

    fun isDetailsShown(): Boolean = has(NowPlayingTags.DETAILS)

    fun awaitShown() = await("Now playing") { isShown() }

    fun awaitGone() = await("no player screen") { !isShown() && !isDetailsShown() }

    fun awaitDetails() = await("Station details") { isDetailsShown() }

    fun awaitName(expected: String) = await("the name '$expected'") { text(NowPlayingTags.NAME) == expected }

    fun awaitPlaying(expected: Boolean) = await("the button to read ${if (expected) "Pause" else "Play"}") {
        text(NowPlayingTags.TOGGLE) == string(if (expected) R.string.main_pause else R.string.main_play)
    }

    fun awaitFavorite(expected: Boolean) = await("the heart ${if (expected) "filled" else "outlined"}") {
        description(NowPlayingTags.FAVORITE) ==
            string(if (expected) R.string.main_favorite_remove else R.string.main_favorite_add)
    }

    fun tapClose() = tap(NowPlayingTags.CLOSE)

    fun tapToggle() = tap(NowPlayingTags.TOGGLE)

    fun tapFavorite() = tap(NowPlayingTags.FAVORITE)

    fun tapDetailsBack() = tap(NowPlayingTags.DETAILS_BACK)

    /** Opens the overflow and picks [item]. */
    fun choose(item: PlayerMenuItem) {
        tap(NowPlayingTags.OVERFLOW)
        mCompose.waitForIdle()
        tap(NowPlayingTags.menu(item))
    }

    /** @return the Station details table's texts in screen order: each key, then its value. */
    fun detailTexts(): List<String> {
        return mCompose.onAllNodes(hasAnyAncestor(hasTestTag(NowPlayingTags.DETAILS_TABLE)), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }
    }

    /** Taps the link row whose value is [value]. */
    fun tapLink(value: String) {
        mCompose.onNode(hasAnyAncestor(hasTestTag(NowPlayingTags.DETAILS_TABLE)) and hasText(value) and hasClickAction())
            .performClick()
        mCompose.waitForIdle()
    }

    fun describe(): String {
        return "Now playing is ${if (isShown()) "open" else "shut"}, details ${if (isDetailsShown()) "open" else "shut"}, " +
            "showing '${text(NowPlayingTags.NAME)}', button '${text(NowPlayingTags.TOGGLE)}', " +
            "heart '${description(NowPlayingTags.FAVORITE)}'."
    }

    private fun tap(tag: String) {
        mCompose.onNodeWithTag(tag).performClick()
        mCompose.waitForIdle()
    }

    private fun has(tag: String): Boolean {
        return mCompose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    private fun text(tag: String): String? {
        return mCompose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
    }

    private fun description(tag: String): String? {
        return mCompose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
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
        throw AssertionError("The player screen did not show $expectation. " + describe())
    }
}
