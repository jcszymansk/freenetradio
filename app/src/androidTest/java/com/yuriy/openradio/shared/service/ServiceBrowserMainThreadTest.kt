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

package com.yuriy.openradio.shared.service

import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuriy.openradio.testing.onMainThread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every [ServiceBrowser] call runs on the main thread, and a call that throws there has to fail
 * the test that made it rather than kill the process. A browser that is not connected throws on
 * every call, which is the cheapest way to make each of its main-thread paths throw.
 *
 * A broken path does not fail these cases quietly: the throw escapes on the main looper and the
 * run reports this class as crashed, along with everything after it.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ServiceBrowserMainThreadTest {

    @Test
    fun aTransportCallWithoutAConnectionFailsTheCaller() {
        val browser = ServiceBrowser()

        assertThrows(IllegalStateException::class.java) { browser.play() }

        assertMainThreadAnswers()
    }

    @Test
    fun aReadWithoutAConnectionFailsTheCaller() {
        val browser = ServiceBrowser()

        assertThrows(IllegalStateException::class.java) { browser.mediaItemCount() }

        assertMainThreadAnswers()
    }

    @Test
    fun aBrowseWithoutAConnectionFailsTheCaller() {
        val browser = ServiceBrowser()

        assertThrows(IllegalStateException::class.java) { browser.libraryRoot() }

        assertMainThreadAnswers()
    }

    @Test
    fun theSessionCommandsWithoutAConnectionFailTheCaller() {
        val browser = ServiceBrowser()

        assertThrows(IllegalStateException::class.java) { browser.availableSessionCommands() }

        assertFalse("A refused call left the browser connected", browser.isConnected())
        assertMainThreadAnswers()
    }

    private fun assertMainThreadAnswers() {
        assertTrue("The main thread stopped answering after the call failed", onMainThread { true })
    }
}
