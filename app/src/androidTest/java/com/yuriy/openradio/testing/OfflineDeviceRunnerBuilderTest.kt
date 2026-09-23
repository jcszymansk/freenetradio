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

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.journey.ColdLaunchJourneyTest
import com.yuriy.openradio.shared.service.OpenRadioServiceBrowseTest
import com.yuriy.openradio.shared.service.ServiceBrowser
import junit.framework.TestCase
import junit.framework.TestSuite
import junit.framework.Test as JUnit3Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Suite

/**
 * The guard that keeps the instrumented suite offline, and the runner that installs it.
 *
 * The online cases cannot be reached by turning the device's network on, since this class would
 * then be refused along with every other, so they hand the builder a network instead.
 */
@RunWith(AndroidJUnit4::class)
open class OfflineDeviceRunnerBuilderTest {

    @Test
    fun anOnlineDeviceFailsATestClassWithoutRunningIt() {
        val runner = onlineBuilder().runnerForClass(OpenRadioServiceBrowseTest::class.java)
        assertNotNull("An online device let the class run", runner)

        val events = run(runner!!)

        val case = Description.createTestDescription(
            OpenRadioServiceBrowseTest::class.java, OfflineDeviceRunnerBuilder.CASE_NAME
        )
        assertEquals(listOf("started $case", "failed $case", "finished $case"), events.log)
        assertEquals(1, events.failures.size)
        val message = events.failures.single().message
        assertTrue("The failure does not name the network: $message", message.contains(NETWORK))
        assertTrue(
            "The failure does not tell the operator what to do: $message",
            message.contains("adb shell svc wifi disable && adb shell svc data disable")
        )
    }

    @Test
    fun anOnlineDeviceFailsAClassWhoseTestsAreOnlyInherited() {
        val runner = onlineBuilder().runnerForClass(InheritsTests::class.java)

        assertNotNull("A class whose tests come from its superclass ran online", runner)
    }

    @Test
    fun anOnlineDeviceFailsAJUnit3TestCase() {
        val runner = onlineBuilder().runnerForClass(JUnit3Case::class.java)

        assertNotNull("A JUnit 3 TestCase, whose test methods carry no annotation, ran online", runner)
    }

    @Test
    fun anOnlineDeviceFailsAClassBuiltFromASuiteMethod() {
        val runner = onlineBuilder().runnerForClass(SuiteMethodOnly::class.java)

        assertNotNull("A class offering a static suite() ran online", runner)
    }

    @Test
    fun anOnlineDeviceFailsAClassThatNamesItsOwnRunner() {
        val runner = onlineBuilder().runnerForClass(NamesItsRunner::class.java)

        assertNotNull("A class with a runner of its own and no @Test methods ran online", runner)
    }

    @Test
    fun anOnlineDeviceReportsOneFailurePerClass() {
        val builder = onlineBuilder()

        val classes = listOf(OpenRadioServiceBrowseTest::class.java, ColdLaunchJourneyTest::class.java)

        val reported = classes.map { builder.runnerForClass(it)?.description?.testClass }

        assertEquals(classes, reported)
    }

    @Test
    fun anOfflineDeviceLeavesEveryClassToTheDefaultBuilders() {
        val builder = OfflineDeviceRunnerBuilder { null }

        assertNull(builder.runnerForClass(OpenRadioServiceBrowseTest::class.java))
        assertNull(builder.runnerForClass(ColdLaunchJourneyTest::class.java))
    }

    @Test
    fun aClassWithoutTestsIsLeftAloneEvenOnline() {
        var asked = false
        val builder = OfflineDeviceRunnerBuilder {
            asked = true
            NETWORK
        }

        assertNull(builder.runnerForClass(ServiceBrowser::class.java))
        assertFalse("The network was consulted for a class that is not a test", asked)
    }

    @Test
    fun theRunnerInstallsTheGuardAheadOfEveryOtherBuilder() {
        val arguments = Bundle().apply {
            putString(OfflineTestRunner.RUNNER_BUILDER_ARGUMENT, "com.example.First, com.example.Second")
            putString("class", "com.example.SomeTest")
        }

        val guarded = OfflineTestRunner.withOfflineGuard(arguments)

        assertEquals(
            "${OfflineDeviceRunnerBuilder::class.java.name},com.example.First,com.example.Second",
            guarded.getString(OfflineTestRunner.RUNNER_BUILDER_ARGUMENT)
        )
        assertEquals("com.example.SomeTest", guarded.getString("class"))
        assertEquals(
            "The launcher's arguments were changed in place",
            "com.example.First, com.example.Second",
            arguments.getString(OfflineTestRunner.RUNNER_BUILDER_ARGUMENT)
        )
    }

    @Test
    fun theRunnerInstallsTheGuardWhenNoBuilderWasPassed() {
        val guarded = OfflineTestRunner.withOfflineGuard(Bundle())

        assertEquals(
            OfflineDeviceRunnerBuilder::class.java.name,
            guarded.getString(OfflineTestRunner.RUNNER_BUILDER_ARGUMENT)
        )
    }

    @Test
    fun theRunnerDoesNotInstallTheGuardTwice() {
        val once = OfflineTestRunner.withOfflineGuard(Bundle())

        val twice = OfflineTestRunner.withOfflineGuard(once)

        assertEquals(
            OfflineDeviceRunnerBuilder::class.java.name,
            twice.getString(OfflineTestRunner.RUNNER_BUILDER_ARGUMENT)
        )
    }

    /**
     * What makes the guard structural: this very run went through [OfflineTestRunner], and the
     * arguments it ran with name the builder.
     */
    @Test
    fun thisRunIsGuarded() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        assertTrue(
            "The suite ran under ${instrumentation.javaClass.name}, not the offline runner",
            instrumentation is OfflineTestRunner
        )
        val builders = InstrumentationRegistry.getArguments()
            .getString(OfflineTestRunner.RUNNER_BUILDER_ARGUMENT).orEmpty().split(',')
        assertEquals(OfflineDeviceRunnerBuilder::class.java.name, builders.first())
    }

    private fun onlineBuilder() = OfflineDeviceRunnerBuilder { NETWORK }

    private fun run(runner: Runner): RecordedEvents {
        val events = RecordedEvents()
        val notifier = RunNotifier()
        notifier.addListener(events)
        runner.run(notifier)
        return events
    }

    private class RecordedEvents : RunListener() {

        val log = mutableListOf<String>()

        val failures = mutableListOf<Failure>()

        override fun testStarted(description: Description) {
            log.add("started $description")
        }

        override fun testFailure(failure: Failure) {
            log.add("failed ${failure.description}")
            failures.add(failure)
        }

        override fun testFinished(description: Description) {
            log.add("finished $description")
        }
    }

    /*
     * The fixtures below are abstract because the test loader skips abstract classes. The builder
     * does not, so each one reaches it as a class of its format would, without ever being run.
     */

    /**
     * Carries a JUnit 4 test and no runner annotation, so only the @Test method marks it.
     */
    abstract class TestsWithoutRunner {

        @Test
        fun inheritedCase() {
            fail("A fixture case ran")
        }
    }

    /**
     * Declares no test of its own and names no runner.
     */
    abstract class InheritsTests : TestsWithoutRunner()

    /**
     * A JUnit 3 case: its test method is found by name, not by annotation.
     */
    abstract class JUnit3Case : TestCase() {

        fun testCase() {
            fail("A fixture case ran")
        }
    }

    /**
     * A JUnit 3 suite method on a class that is neither a TestCase nor annotated.
     */
    abstract class SuiteMethodOnly {

        companion object {

            @JvmStatic
            fun suite(): JUnit3Test = TestSuite()
        }
    }

    /**
     * A runner of its own and no @Test method.
     */
    @RunWith(Suite::class)
    @Suite.SuiteClasses()
    abstract class NamesItsRunner

    private companion object {

        const val NETWORK = "[type: WIFI[], state: CONNECTED/CONNECTED]"
    }
}
