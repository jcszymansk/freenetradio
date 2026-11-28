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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import com.yuriy.openradio.mobile.view.main.OverflowItem
import com.yuriy.openradio.mobile.view.settings.SettingsTags
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.SettingsModels
import com.yuriy.openradio.shared.dependencies.SettingsModelsDependency

/**
 * The settings screens a journey drives, by their Compose test tags, the way a user meets them:
 * from the overflow menu, a row at a time, and back out again.
 */
internal class SettingsView(private val mCompose: ComposeTestRule) {

    /** Opens Settings from the top bar's overflow menu. */
    fun openFromOverflow() {
        openOverflowItem(OverflowItem.SETTINGS)
        await(SettingsTags.SCREEN)
    }

    /** Opens About from the top bar's overflow menu. */
    fun openAboutFromOverflow() {
        openOverflowItem(OverflowItem.ABOUT)
        await(SettingsTags.ABOUT_SCREEN)
    }

    /** Opens the screen behind the row tagged [rowTag] and waits for [screenTag] to show. */
    fun open(rowTag: String, screenTag: String) {
        click(rowTag)
        await(screenTag)
    }

    /** Taps the node tagged [tag], scrolling it into view first when its screen scrolls. */
    fun click(tag: String) {
        scrollTo(tag)
        mCompose.onNodeWithTag(tag).performClick()
    }

    /**
     * Leaves the screen on top through its back arrow and waits for [nextTag].
     */
    fun back(nextTag: String) {
        mCompose.onNodeWithTag(SettingsTags.BACK).performClick()
        await(nextTag)
    }

    /** Leaves the screen on top through system back and waits for [nextTag]. */
    fun systemBack(nextTag: String) {
        Espresso.pressBack()
        await(nextTag)
    }

    /** @return whether the switch row tagged [tag] is on. */
    fun isOn(tag: String): Boolean {
        scrollTo(tag)
        return mCompose.onNodeWithTag(tag).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
    }

    /** Asserts that the row tagged [rowTag] shows [value] among its texts. */
    fun assertValue(rowTag: String, value: String) {
        scrollTo(rowTag)
        mCompose.onNodeWithTag(rowTag).assertTextContains(value)
    }

    /** Replaces what the text field tagged [tag] holds with [text], as typing it would. */
    fun type(tag: String, text: String) {
        scrollTo(tag)
        mCompose.onNodeWithTag(tag).performTextReplacement(text)
    }

    /** @return the text the text field tagged [tag] holds. */
    fun text(tag: String): String {
        scrollTo(tag)
        return mCompose.onNodeWithTag(tag).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText)?.text
            ?: throw AssertionError("$tag holds no editable text")
    }

    /** Chooses the option whose tag is `SettingsTags.choice(key)` on the choice screen. */
    fun choose(key: String) {
        scrollToChoice(key)
        mCompose.onNodeWithTag(SettingsTags.choice(key)).performClick()
    }

    /** @return whether the choice screen marks the option `SettingsTags.choice(key)` as chosen. */
    fun isChosen(key: String): Boolean {
        scrollToChoice(key)
        return mCompose.onNodeWithTag(SettingsTags.choice(key)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Selected) == true
    }

    /** @return whether a node tagged [tag] is composed. */
    fun isShown(tag: String): Boolean {
        return mCompose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    /** Waits for a node tagged [tag] to be composed. */
    fun await(tag: String) {
        mCompose.waitUntil(TIMEOUT_MILLIS) { isShown(tag) }
    }

    /** Waits for the node tagged [tag] to leave the screen. */
    fun awaitGone(tag: String) {
        mCompose.waitUntil(TIMEOUT_MILLIS) { !isShown(tag) }
    }

    /**
     * Scrolls the node tagged [tag] into view when the screen it sits on scrolls. A screen that
     * fits without scrolling has no scrollable ancestor, and `performScrollTo` would fail on it.
     */
    private fun scrollTo(tag: String) {
        val scrollable = mCompose
            .onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasScrollAction()))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
        if (scrollable) {
            mCompose.onNodeWithTag(tag).performScrollTo()
        }
    }

    /** Scrolls the choice list until the option `SettingsTags.choice(key)` is composed. */
    private fun scrollToChoice(key: String) {
        mCompose.onNodeWithTag(SettingsTags.CHOICE_LIST)
            .performScrollToNode(hasTestTag(SettingsTags.choice(key)))
    }

    private fun openOverflowItem(item: OverflowItem) {
        mCompose.onNodeWithTag(MainScreenTags.OVERFLOW).performClick()
        mCompose.onNodeWithTag(MainScreenTags.overflow(item)).performClick()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}

/**
 * The registry's own settings models, reached through the single-method hook.
 */
internal fun registrySettings(): SettingsModels {
    lateinit var found: SettingsModels
    DependencyRegistryCommonUi.injectSettingsModels(object : SettingsModelsDependency {
        override fun configureWith(models: SettingsModels) {
            found = models
        }
    })
    return found
}
