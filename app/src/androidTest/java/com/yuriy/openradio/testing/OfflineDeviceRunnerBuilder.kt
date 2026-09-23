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

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.RunnerBuilder

/**
 * Refuses to run any test class while the device has a network.
 *
 * Nothing in this suite may reach the internet, and most of it cannot know whether it would: the
 * service fetches from Radio Browser on its own schedule, and the validator and the artwork loader
 * go out without asking the connectivity gate. So the promise is made once, here, rather than by
 * every class that might remember to ask. `AndroidJUnitRunner` consults custom builders before its
 * own, so on a networked device every test class is replaced by a runner that fails with the
 * instruction to disable networking, and no test body runs at all. Offline, this builder answers
 * null and the default builders take over as if it did not exist.
 *
 * [OfflineTestRunner] installs it, so it applies however the run was started.
 *
 * @param mActiveNetwork answers what [activeNetworkOf] answers; tests replace it.
 */
class OfflineDeviceRunnerBuilder internal constructor(
    private val mActiveNetwork: () -> String?
) : RunnerBuilder() {

    /**
     * The constructor `AndroidJUnitRunner` instantiates reflectively.
     */
    constructor() : this({ activeNetworkOf(InstrumentationRegistry.getInstrumentation().targetContext) })

    /**
     * @return a runner that fails [testClass] when the device is online, or null to leave the class
     *   to the default builders. Classes without tests are always left alone, because the test
     *   loader offers every class in the APK and a helper is not a test to fail.
     */
    override fun runnerForClass(testClass: Class<*>): Runner? {
        if (!declaresTests(testClass)) {
            return null
        }
        val network = mActiveNetwork() ?: return null
        return DeviceOnlineRunner(testClass, onlineMessage(network))
    }

    private fun declaresTests(testClass: Class<*>): Boolean {
        return generateSequence(testClass) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .any { it.isAnnotationPresent(Test::class.java) }
    }

    /**
     * Reports its class as a single failed case instead of running it.
     */
    private class DeviceOnlineRunner(testClass: Class<*>, private val mMessage: String) : Runner() {

        private val mDescription = Description.createTestDescription(testClass, CASE_NAME)

        override fun getDescription(): Description = mDescription

        override fun run(notifier: RunNotifier) {
            notifier.fireTestStarted(mDescription)
            notifier.fireTestFailure(Failure(mDescription, AssertionError(mMessage)))
            notifier.fireTestFinished(mDescription)
        }
    }

    internal companion object {

        /**
         * The name the failure is reported under, standing in for every case of the class.
         */
        const val CASE_NAME = "deviceIsOffline"

        fun onlineMessage(network: String): String {
            return "The instrumented suite has to run with networking disabled, and the device " +
                "reports $network. No test in this class ran. Run " +
                "`adb shell svc wifi disable && adb shell svc data disable` before the suite."
        }
    }
}
