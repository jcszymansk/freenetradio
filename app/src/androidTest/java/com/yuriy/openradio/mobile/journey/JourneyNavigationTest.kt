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
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.testing.onMainThread
import com.yuriy.openradio.testing.withActivity
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
 * The two helpers every journey leans on to leave the presenter where it found it:
 * [JourneyNavigation.returnToRoot], called from each navigating case's `finally`, and
 * [PresenterStackRule], which checks every journey case afterwards.
 *
 * The stacks [JourneyNavigation.returnToRoot] has to survive are set up through the presenter
 * itself. A single non-root node is the shape that matters most: pressing back on it is "leave the
 * application", which sends `CMD_STOP_SERVICE` and ends in `Process.killProcess`, so a helper that
 * gets this wrong reports this class as crashed along with everything after it in the run.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class JourneyNavigationTest {

    @get:Rule
    val mStack = PresenterStackRule()

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
    fun aStackSeveralNodesDeepIsWalkedBackToTheRoot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val navigation = awaitRoot(scenario)
            navigation.open(MediaId.MEDIA_ID_FAVORITES_LIST)
            navigation.open(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            assertEquals(
                listOf(
                    MediaId.MEDIA_ID_ROOT,
                    MediaId.MEDIA_ID_FAVORITES_LIST,
                    MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
                ),
                navigation.stack()
            )

            navigation.returnToRoot()

            assertBackAtTheRoot(scenario, navigation)
        }
    }

    /**
     * The hazard named in [JourneyNavigation.returnToRoot]'s own KDoc. Popping this node is what
     * the presenter does for the root, and it stops the service.
     */
    @Test
    fun aLoneNodeOnTheStackIsReplacedByTheRootRatherThanPopped() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val navigation = awaitRoot(scenario)
            val presenter = registryPresenter()
            scenario.withActivity {
                presenter.unsubscribeFromItem(MediaId.MEDIA_ID_ROOT)
                presenter.addMediaItemToStack(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            }
            assertEquals(listOf(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST), navigation.stack())

            navigation.returnToRoot()

            assertBackAtTheRoot(scenario, navigation)
        }
    }

    /**
     * The other end of the same hazard: once the stack is empty there is no node on it to read,
     * and asking the presenter for its current node throws.
     */
    @Test
    fun anEmptyStackIsGivenTheRoot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val navigation = awaitRoot(scenario)
            val presenter = registryPresenter()
            scenario.withActivity { presenter.unsubscribeFromItem(MediaId.MEDIA_ID_ROOT) }
            assertEquals(emptyList<String>(), navigation.stack())

            navigation.returnToRoot()

            assertBackAtTheRoot(scenario, navigation)
        }
    }

    @Test
    fun theRuleLetsACaseThatLeftTheStackAsItFoundItPass() {
        val found = stackOutsideAnActivity()

        PresenterStackRule().apply(
            statement {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    val navigation = awaitRoot(scenario)
                    navigation.open(MediaId.MEDIA_ID_FAVORITES_LIST)
                    navigation.returnToRoot()
                }
            },
            DESCRIPTION
        ).evaluate()

        assertEquals(found, stackOutsideAnActivity())
    }

    /**
     * The first Activity of a process finds the stack empty and puts the root on it, which is the
     * application's doing and not the case's. The rule takes it off again, so the next case finds
     * the stack exactly as this one did.
     */
    @Test
    fun theRuleTakesOffTheRootAnActivityPutOnAnEmptyStack() {
        clearStackOutsideAnActivity()

        PresenterStackRule().apply(
            statement {
                ActivityScenario.launch(MainActivity::class.java).use { scenario -> awaitRoot(scenario) }
            },
            DESCRIPTION
        ).evaluate()

        assertEquals(emptyList<String>(), stackOutsideAnActivity())
    }

    @Test
    fun theRuleFailsACaseThatLeftANodeOnTheStackAndTakesTheNodeOff() {
        val found = stackOutsideAnActivity()
        val rule = PresenterStackRule().apply(
            statement {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    awaitRoot(scenario).open(MediaId.MEDIA_ID_FAVORITES_LIST)
                }
            },
            DESCRIPTION
        )

        val failure = assertThrows(AssertionError::class.java) { rule.evaluate() }

        assertTrue(
            "The failure does not name the node the case left behind: ${failure.message}",
            failure.message.orEmpty().contains(MediaId.MEDIA_ID_FAVORITES_LIST)
        )
        assertEquals(found, stackOutsideAnActivity())
    }

    /**
     * A case that fails part way through a node is the case the rule exists for, and its own
     * failure is the one that says what broke, so the rule must add to it rather than replace it.
     */
    @Test
    fun theRuleReportsTheCasesOwnFailureAlongsideTheNodeItLeft() {
        val found = stackOutsideAnActivity()
        val caseFailure = AssertionError("the case's own failure")
        val rule = PresenterStackRule().apply(
            statement {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    awaitRoot(scenario).open(MediaId.MEDIA_ID_FAVORITES_LIST)
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
            failure.failures[1].message.orEmpty().contains(MediaId.MEDIA_ID_FAVORITES_LIST)
        )
        assertEquals(found, stackOutsideAnActivity())
    }

    private fun awaitRoot(scenario: ActivityScenario<MainActivity>): JourneyNavigation {
        BrowseListView(scenario).awaitRows("the root of a cleared profile") { it == mProfile.cleanInstallRoot() }
        return JourneyNavigation(scenario)
    }

    private fun assertBackAtTheRoot(scenario: ActivityScenario<MainActivity>, navigation: JourneyNavigation) {
        assertEquals(listOf(MediaId.MEDIA_ID_ROOT), navigation.stack())
        val list = BrowseListView(scenario)
        assertEquals(
            "The presenter is back at the root and the list does not show it",
            mProfile.cleanInstallRoot(),
            list.awaitRows("the root list") { it == mProfile.cleanInstallRoot() }
        )
        assertEquals(MediaId.MEDIA_ID_ROOT, list.renderedNode())
    }

    private fun stackOutsideAnActivity(): List<String> {
        val presenter = registryPresenter()
        return onMainThread { presenter.getMediaItemsStack() }
    }

    private fun clearStackOutsideAnActivity() {
        val presenter = registryPresenter()
        onMainThread { presenter.getMediaItemsStack().toSet().forEach(presenter::unsubscribeFromItem) }
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
    }
}
