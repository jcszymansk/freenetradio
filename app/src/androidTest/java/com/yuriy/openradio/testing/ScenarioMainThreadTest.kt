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

package com.yuriy.openradio.testing

import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.permission.grantImageReadPermission
import com.yuriy.openradio.shared.service.ServiceBrowser
import com.yuriy.openradio.shared.service.ServiceStorages
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [withActivity] is what keeps a failing check inside an Activity from taking the whole run down,
 * so its contract is asserted here directly: the block runs on the main thread, what it answers
 * reaches the caller, and what it throws reaches the caller as the same throwable while the
 * application keeps running. [onMainThread] is held to the same contract with no Activity up.
 *
 * A broken helper does not fail these cases quietly. A throw that escaped on the main looper would
 * kill the process, and the run would report this class as crashed along with everything after it.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ScenarioMainThreadTest {

    /**
     * The service's own storages: the latest station is cleared so that the service the Activity
     * starts adopts none, as in `AddStationDialogTest`.
     */
    private lateinit var mStorages: ServiceStorages

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        grantImageReadPermission(context)
        mStorages = ServiceStorages(context)
        clearState()
        ServiceBrowser.assertABrowseCannotStartPlayback()
    }

    @After
    fun tearDown() {
        clearState()
    }

    private fun clearState() {
        mStorages.sleepTimer.clear()
        mStorages.latest.clear()
    }

    @Test
    fun theBlockRunsOnTheMainThreadAndItsAnswerReachesTheCaller() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val mainLooper = Looper.getMainLooper()
            assertNotSame("The test itself runs on the main thread", mainLooper, Looper.myLooper())

            val blockLooper = scenario.withActivity { Looper.myLooper() }

            assertSame("The block did not run on the main thread", mainLooper, blockLooper)
        }
    }

    @Test
    fun anAssertionThatFailsInTheBlockFailsTheCallerAndLeavesTheApplicationRunning() {
        val failure = AssertionError("raised inside the block")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val thrown = assertThrows(AssertionError::class.java) {
                scenario.withActivity<MainActivity, Unit> { throw failure }
            }

            assertSame(
                "The caller was handed a different failure than the block raised", failure, thrown
            )
            assertTrue(
                "The main thread stopped answering after the block failed",
                scenario.withActivity { !it.isFinishing }
            )
        }
    }

    /**
     * A test reads views on the main thread and can trip over the application there, so anything
     * the block throws has to come back, not assertions alone.
     */
    @Test
    fun anExceptionThrownInTheBlockReachesTheCallerUnchanged() {
        val failure = IllegalStateException("thrown inside the block")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val thrown = assertThrows(IllegalStateException::class.java) {
                scenario.withActivity<MainActivity, Unit> { throw failure }
            }

            assertSame(
                "The caller was handed a different exception than the block threw", failure, thrown
            )
        }
    }

    @Test
    fun aBlockRunOnTheMainThreadWithNoActivityAnswersTheCaller() {
        val mainLooper = Looper.getMainLooper()

        val blockLooper = onMainThread { Looper.myLooper() }

        assertSame("The block did not run on the main thread", mainLooper, blockLooper)
    }

    @Test
    fun aThrowOnTheMainThreadWithNoActivityReachesTheCallerAndLeavesTheApplicationRunning() {
        val failure = AssertionError("raised on the main thread")

        val thrown = assertThrows(AssertionError::class.java) { onMainThread<Unit> { throw failure } }

        assertSame("The caller was handed a different failure than the block raised", failure, thrown)
        assertTrue("The main thread stopped answering after the block failed", onMainThread { true })
    }
}
