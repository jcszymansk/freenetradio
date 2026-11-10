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

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import com.yuriy.openradio.mobile.view.main.labelRes
import com.yuriy.openradio.shared.presenter.main.Tab
import org.junit.Assert.assertEquals
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The main screen a journey is looking at, read through its Compose test tags the way a user
 * reads it: the rows in list order with the text each shows, the tabs, the empty and error lines
 * and the FAB. Only rows that are composed can be read, which is every row of the short lists
 * these journeys visit.
 */
internal class MainScreenView(private val mCompose: ComposeTestRule) {

    fun awaitTabs(expected: List<Tab>) {
        await("the tabs $expected") { tabLabels() == expected.map(::label) }
    }

    /**
     * Taps [tab]. The tabs scroll sideways when they do not fit, so the tab is scrolled into view
     * first, as a user would.
     */
    fun selectTab(tab: Tab) {
        mCompose.onNode(hasAnyAncestor(hasTestTag(MainScreenTags.TABS)) and hasText(label(tab)))
            .performScrollTo()
            .performClick()
        mCompose.waitForIdle()
    }

    fun awaitRows(expectation: String, matches: (List<BrowseRow>) -> Boolean): List<BrowseRow> {
        var last = emptyList<BrowseRow>()
        await(expectation) {
            last = rows()
            last.isNotEmpty() && matches(last)
        }
        return last
    }

    fun awaitEmptyLine(expected: String) {
        await("the empty line '$expected'") { texts(MainScreenTags.EMPTY) == listOf(expected) }
    }

    /**
     * Asserts the list still shows [expected] for [SETTLE_SECONDS]: a list about to change has not
     * changed yet, so a single read cannot tell "nothing happened" from "not yet".
     */
    fun assertRowsStay(expected: List<BrowseRow>, reason: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS)
        while (System.nanoTime() < deadline) {
            assertEquals(reason + ". " + describe(), expected, rows())
            Thread.sleep(POLL_MILLIS)
        }
    }

    fun tapRow(mediaId: String) {
        mCompose.onNodeWithTag(MainScreenTags.row(mediaId)).performClick()
    }

    fun longPressRow(mediaId: String) {
        mCompose.onNodeWithTag(MainScreenTags.row(mediaId)).performTouchInput { longClick() }
    }

    fun tapFavorite(mediaId: String) {
        mCompose.onNodeWithTag(MainScreenTags.favorite(mediaId)).performClick()
    }

    fun awaitRowFavorite(mediaId: String, expected: Boolean) {
        await("the heart of $mediaId to read $expected") { rowFavorite(mediaId) == expected }
    }

    /** @return whether the row's heart is filled, or null while no such row is composed. */
    fun rowFavorite(mediaId: String): Boolean? {
        val node = nodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, MainScreenTags.favorite(mediaId)))
            .singleOrNull() ?: return null
        return node.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
    }

    fun isShown(tag: String): Boolean = nodes(hasTestTag(tag)).isNotEmpty()

    fun tap(tag: String) {
        mCompose.onNodeWithTag(tag).performClick()
    }

    fun rows(): List<BrowseRow> {
        return nodes(IS_ROW).map { node ->
            BrowseRow(
                node.config[SemanticsProperties.TestTag].removePrefix(MainScreenTags.ROW_PREFIX),
                node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty()
            )
        }
    }

    fun describe(): String {
        return "The screen shows tabs ${tabLabels()}, rows ${rows()}, empty line ${texts(MainScreenTags.EMPTY)}, " +
            "error ${isShown(MainScreenTags.ERROR)}, progress ${isShown(MainScreenTags.PROGRESS)}, " +
            "FAB ${isShown(MainScreenTags.FAB_ADD)}."
    }

    private fun tabLabels(): List<String> {
        return nodes(
            hasAnyAncestor(hasTestTag(MainScreenTags.TABS)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
        ).mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text }
    }

    private fun texts(tag: String): List<String> {
        return nodes(hasTestTag(tag)).flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }
    }

    private fun nodes(matcher: SemanticsMatcher): List<SemanticsNode> {
        return mCompose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false)
    }

    private fun await(expectation: String, done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (done()) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("The main screen did not show $expectation within $TIMEOUT_SECONDS seconds. " + describe())
    }

    companion object {

        const val TIMEOUT_SECONDS = 20L
        const val SETTLE_SECONDS = 5L
        const val POLL_MILLIS = 50L

        private val IS_ROW = SemanticsMatcher("is a list row") { node ->
            val tag = node.config.getOrNull(SemanticsProperties.TestTag)
            tag != null && tag.startsWith(MainScreenTags.ROW_PREFIX) && !tag.endsWith(MainScreenTags.FAVORITE_SUFFIX)
        }

        /**
         * @return the label [tab] shows on screen. The tabs draw their labels upper-cased.
         */
        fun label(tab: Tab): String {
            return InstrumentationRegistry.getInstrumentation().targetContext.getString(tab.labelRes)
                .uppercase(Locale.ROOT)
        }
    }
}

/**
 * One row of the list: its media id and the first text it shows.
 */
internal data class BrowseRow(val mediaId: String, val title: String)
