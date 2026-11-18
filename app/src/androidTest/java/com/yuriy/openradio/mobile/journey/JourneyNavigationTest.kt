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

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.presenter.main.BrowseSnapshot
import com.yuriy.openradio.shared.presenter.main.Tab
import com.yuriy.openradio.testing.onMainThread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * The two helpers every journey leans on to leave the main screen where it found it:
 * [JourneyNavigation.returnToRoots], called from each navigating case's `finally`, and
 * [BrowseStacksRule], which checks every journey case afterwards.
 *
 * What they have to undo is set up through the screen itself: tabs are tapped, and a search, the
 * one way deeper into a tab that needs no connection, is pushed through the model as the search
 * field pushes it. A tab's stack always keeps its root, so the hazard the presenter's single node
 * stack carried, a back press at the root that stops the service, has no counterpart here.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class JourneyNavigationTest {

    @get:Rule
    val mStack = BrowseStacksRule()

    @get:Rule
    val mCompose = createEmptyComposeRule()

    private lateinit var mContext: Context

    private lateinit var mProfile: JourneyProfile

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mProfile = JourneyProfile(mContext)
        mProfile.start()
    }

    @After
    fun tearDown() {
        mProfile.finish()
    }

    @Test
    fun searchesOnTwoTabsAreWalkedBackToTheRoots() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val navigation = awaitTabs(scenario)
            try {
                navigation.search(FIRST_QUERY)
                navigation.selectTab(Tab.GENRE)
                navigation.search(SECOND_QUERY)
                val walked = navigation.snapshot()
                assertEquals(Tab.GENRE, walked.selectedTab)
                assertEquals(listOf(Tab.FAV.rootId, MediaId.MEDIA_ID_SEARCH_FROM_APP), walked.stacks[Tab.FAV])
                assertEquals(listOf(Tab.GENRE.rootId, MediaId.MEDIA_ID_SEARCH_FROM_APP), walked.stacks[Tab.GENRE])
            } finally {
                navigation.returnToRoots()
            }

            assertBackAtTheRoots(navigation)
        }
    }

    @Test
    fun theRuleLetsACaseThatLeftTheStackAsItFoundItPass() {
        val found = snapshotOutsideAnActivity()

        BrowseStacksRule().apply(
            statement {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    val navigation = awaitTabs(scenario)
                    navigation.search(FIRST_QUERY)
                    navigation.returnToRoots()
                }
            },
            DESCRIPTION
        ).evaluate()

        assertEquals(found, snapshotOutsideAnActivity())
    }

    /**
     * The selected tab outlives the Activity along with the stacks, so a case that leaves the user
     * on another tab sends the next case's Activity there, which the rule has to catch and undo.
     */
    @Test
    fun theRuleFailsACaseThatLeftAnotherTabSelectedAndSelectsFavAgain() {
        val found = snapshotOutsideAnActivity()
        val rule = BrowseStacksRule().apply(
            statement {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    awaitTabs(scenario).selectTab(Tab.GENRE)
                }
            },
            DESCRIPTION
        )

        val failure = assertThrows(AssertionError::class.java) { rule.evaluate() }

        assertTrue(
            "The failure does not name the tab the case left selected: ${failure.message}",
            failure.message.orEmpty().contains("selectedTab=${Tab.GENRE}")
        )
        assertEquals(found, snapshotOutsideAnActivity())
        assertEquals(Tab.FAV, snapshotOutsideAnActivity().selectedTab)
    }

    @Test
    fun theRuleFailsACaseThatLeftANodeOnTheStackAndTakesTheNodeOff() {
        val found = snapshotOutsideAnActivity()
        val rule = BrowseStacksRule().apply(
            statement {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    awaitTabs(scenario).search(FIRST_QUERY)
                }
            },
            DESCRIPTION
        )

        val failure = assertThrows(AssertionError::class.java) { rule.evaluate() }

        assertTrue(
            "The failure does not name the node the case left behind: ${failure.message}",
            failure.message.orEmpty().contains(MediaId.MEDIA_ID_SEARCH_FROM_APP)
        )
        assertEquals(found, snapshotOutsideAnActivity())
    }

    /**
     * A case that fails part way through a search is the case the rule exists for, and its own
     * failure is the one that says what broke, so the rule must add to it rather than replace it.
     */
    @Test
    fun theRuleReportsTheCasesOwnFailureAlongsideTheNodeItLeft() {
        val found = snapshotOutsideAnActivity()
        val caseFailure = AssertionError("the case's own failure")
        val rule = BrowseStacksRule().apply(
            statement {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    awaitTabs(scenario).search(FIRST_QUERY)
                    throw caseFailure
                }
            },
            DESCRIPTION
        )

        val failure = assertThrows(MultipleFailureException::class.java) { rule.evaluate() }

        assertEquals(2, failure.failures.size)
        assertSame(caseFailure, failure.failures[0])
        assertTrue(
            "The second failure is not the stack the case left: ${failure.failures[1].message}",
            failure.failures[1].message.orEmpty().contains(MediaId.MEDIA_ID_SEARCH_FROM_APP)
        )
        assertEquals(found, snapshotOutsideAnActivity())
    }

    private fun awaitTabs(scenario: ActivityScenario<MainActivity>): JourneyNavigation {
        val view = MainScreenView(mCompose)
        view.awaitTabs(mProfile.cleanInstallTabs())
        return JourneyNavigation(scenario, view)
    }

    /**
     * Every tab holds its root alone, FAV is selected, and the screen shows FAV's root.
     */
    private fun assertBackAtTheRoots(navigation: JourneyNavigation) {
        val snapshot = navigation.snapshot()
        assertEquals(Tab.FAV, snapshot.selectedTab)
        assertEquals(Tab.entries.associateWith { listOf(it.rootId) }, snapshot.stacks)
        MainScreenView(mCompose).awaitEmptyLine(mContext.getString(R.string.main_empty_favorites))
    }

    private fun snapshotOutsideAnActivity(): BrowseSnapshot {
        val model = registryModel()
        return onMainThread { model.snapshot() }
    }

    private fun statement(body: () -> Unit): Statement {
        return object : Statement() {

            override fun evaluate() {
                body()
            }
        }
    }

    private companion object {

        val DESCRIPTION: Description =
            Description.createTestDescription(JourneyNavigationTest::class.java, "aCaseTheRuleWraps")

        const val FIRST_QUERY = "rock"

        const val SECOND_QUERY = "jazz"
    }
}
