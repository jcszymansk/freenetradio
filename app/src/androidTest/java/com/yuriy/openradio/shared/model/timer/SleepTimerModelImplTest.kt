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

package com.yuriy.openradio.shared.model.timer

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.storage.SleepTimerStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Covers the half of the sleep timer that [SleepTimerImpl] does not own: what reaches Shared
 * Preferences, what a restarted model reads back from them, what the calendar of the settings
 * dialog holds, and who hears about a completion. The timing rules themselves are covered by
 * `SleepTimerImplTest` in the JVM test source set.
 *
 * Every model here is built by the test and never taken from the dependency registry. The one the
 * registry hands out is the one [com.yuriy.openradio.shared.service.OpenRadioService] listens to,
 * and its listener ends in `Process.killProcess`, which would take the instrumentation run down
 * with the application process the moment a timer completed.
 */
@RunWith(AndroidJUnit4::class)
class SleepTimerModelImplTest {

    private lateinit var mContext: Context

    private lateinit var mStorage: SleepTimerStorage

    private lateinit var mModel: SleepTimerModelImpl

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mStorage = SleepTimerStorage(WeakReference(mContext))
        mStorage.clear()
        mModel = newModel()
    }

    @After
    fun tearDown() {
        mStorage.clear()
    }

    @Test
    fun theTimerIsDisabledUntilItIsEnabled() {
        assertFalse(mModel.isEnabled())

        mModel.setEnabled(true)

        assertTrue(mModel.isEnabled())
        assertTrue(mStorage.loadEnabled())
        assertTrue(newModel().isEnabled())

        mModel.setEnabled(false)

        assertFalse(mModel.isEnabled())
        assertFalse(newModel().isEnabled())
    }

    @Test
    fun updateTimerPersistsTheTimestampForTheNextProcess() {
        mModel.updateTimer(ALARM_TIMESTAMP, false)

        assertEquals(ALARM_TIMESTAMP, mStorage.loadDate().time)

        val restarted = newModel()
        restarted.updateTime(true)

        assertEquals(ALARM_TIMESTAMP, restarted.getTimestamp())
    }

    /**
     * What a process start has to do with an alarm that was set before it: the stored pair is read
     * back and armed without anyone touching the dialog.
     */
    @Test
    fun initArmsTheStoredAlarm() {
        mStorage.saveDate(System.currentTimeMillis() + DELAY_MS)
        mStorage.saveEnabled(true)
        val listener = RecordingListener()
        val restarted = newModel()
        restarted.addSleepTimerListener(listener)

        restarted.init()

        assertTrue("The stored alarm was not armed", listener.awaitCompletion())
    }

    /**
     * A fresh install has no alarm, and starting the application must not invent one. The storage
     * answers an unset date with the current time, so its own API cannot tell a stored alarm from
     * none; the file it writes to is read by name instead.
     */
    @Test
    fun initDoesNotStoreAnAlarmNobodySet() {
        newModel().init()

        val stored = mContext.getSharedPreferences(SLEEP_TIMER_FILE_NAME, Context.MODE_PRIVATE).all
        assertEquals("init wrote to the sleep timer storage", emptyMap<String, Any?>(), stored)
    }

    @Test
    fun initArmsNothingWhenTheStoredAlarmIsOff() {
        mStorage.saveDate(System.currentTimeMillis() + DELAY_MS)
        mStorage.saveEnabled(false)
        val listener = RecordingListener()
        val restarted = newModel()
        restarted.addSleepTimerListener(listener)

        restarted.init()

        assertFalse(
            "A disabled stored alarm was armed", listener.awaitCompletionDuring(SILENCE_MS)
        )
    }

    @Test
    fun updateTimeWithTheTimerEnabledLoadsTheStoredAlarm() {
        mStorage.saveDate(ALARM_TIMESTAMP)

        mModel.updateTime(true)

        assertEquals(ALARM_TIMESTAMP, mModel.getTimestamp())
        assertEquals(Date(ALARM_TIMESTAMP), mModel.getTime())
    }

    @Test
    fun updateTimeWithTheTimerDisabledStartsFromNow() {
        mStorage.saveDate(ALARM_TIMESTAMP)
        val model = newModel { CLOCK_NOW }

        model.updateTime(false)

        assertEquals(CLOCK_NOW, model.getTimestamp())
    }

    @Test
    fun theDateAndTimeSettersBuildTheTimestampTogether() {
        mModel.setDate(ALARM_YEAR, ALARM_MONTH, ALARM_DAY)
        mModel.setTime(ALARM_HOUR, ALARM_MINUTE)

        val expected = Calendar.getInstance()
        expected.set(Calendar.YEAR, ALARM_YEAR)
        expected.set(Calendar.MONTH, ALARM_MONTH)
        expected.set(Calendar.DAY_OF_MONTH, ALARM_DAY)
        expected.set(Calendar.HOUR_OF_DAY, ALARM_HOUR)
        expected.set(Calendar.MINUTE, ALARM_MINUTE)
        expected.set(Calendar.SECOND, 0)
        expected.set(Calendar.MILLISECOND, 0)

        assertEquals(expected.timeInMillis, mModel.getTimestamp())
        assertEquals(expected.time, mModel.getTime())
    }

    /**
     * The dialog sets the calendar on the main thread while other threads may read it, and
     * [Calendar] is not thread safe: a read that lands between the fields of one [setTime] call
     * sees an hour from one call and a minute from the other. Every read must see one whole call.
     */
    @Test
    fun aReadDuringSetTimeSeesOneWholeCall() {
        mModel.setTime(ALARM_HOUR, ALARM_MINUTE)
        val deadline = System.currentTimeMillis() + RACE_MS
        val failure = AtomicReference<Throwable?>(null)
        val writer = thread {
            try {
                while (System.currentTimeMillis() < deadline && failure.get() == null) {
                    mModel.setTime(ALARM_HOUR, ALARM_MINUTE)
                    mModel.setTime(OTHER_HOUR, OTHER_MINUTE)
                }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
        val reader = thread {
            try {
                val seen = Calendar.getInstance()
                while (System.currentTimeMillis() < deadline && failure.get() == null) {
                    seen.time = mModel.getTime()
                    val read = listOf(
                        seen.get(Calendar.HOUR_OF_DAY),
                        seen.get(Calendar.MINUTE),
                        seen.get(Calendar.SECOND),
                        seen.get(Calendar.MILLISECOND)
                    )
                    if (read != listOf(ALARM_HOUR, ALARM_MINUTE, 0, 0) &&
                        read != listOf(OTHER_HOUR, OTHER_MINUTE, 0, 0)
                    ) {
                        failure.compareAndSet(
                            null, AssertionError("Read $read, which no single setTime call wrote")
                        )
                    }
                }
            } catch (e: Throwable) {
                failure.compareAndSet(null, e)
            }
        }
        writer.join()
        reader.join()

        failure.get()?.let { throw AssertionError("The calendar was read mid-write", it) }
    }

    /**
     * The settings dialog reports a failure when [SleepTimerModelImpl.updateTimer] says no, so the
     * answer has to match what the timer did, and the instant where the two could part is the
     * current millisecond.
     */
    @Test
    fun updateTimerRefusesTheCurrentMillisecond() {
        val listener = RecordingListener()
        val model = newModel { CLOCK_NOW }
        model.addSleepTimerListener(listener)

        assertFalse("A timestamp equal to now was taken", model.updateTimer(CLOCK_NOW, true))

        assertFalse(
            "The timer fired for a timestamp it refused",
            listener.awaitCompletionDuring(SILENCE_MS)
        )
    }

    /**
     * The other half of the agreement: the model's clock is the one the timer measures its delay
     * from, so a timestamp after it is armed rather than refused as past.
     */
    @Test
    fun updateTimerArmsATimestampAfterTheClock() {
        val listener = RecordingListener()
        val model = newModel { CLOCK_NOW }
        model.addSleepTimerListener(listener)

        assertTrue("A future timestamp was refused", model.updateTimer(CLOCK_NOW + DELAY_MS, true))

        assertTrue("The timer never completed", listener.awaitCompletion())
    }

    /**
     * An alarm stored on, for a time that passed while no process was running (it was killed, or
     * the phone was off), cannot be armed any more. Left on in storage, it would open the settings
     * dialog switched on with a past date, and fail on every close.
     */
    @Test
    fun initTurnsOffAStoredAlarmThatHasPassed() {
        mStorage.saveDate(System.currentTimeMillis() - DELAY_MS)
        mStorage.saveEnabled(true)
        val listener = RecordingListener()
        val restarted = newModel()
        restarted.addSleepTimerListener(listener)

        restarted.init()

        assertFalse("A passed alarm is still stored on", mStorage.loadEnabled())
        assertFalse("A passed alarm fired", listener.awaitCompletionDuring(SILENCE_MS))
    }

    /**
     * What the settings dialog does when the user switches an armed timer off and on again and
     * then picks a time that has passed: switching off cancelled the armed timer, so the refused
     * time leaves nothing counting down, and storage must not say otherwise.
     */
    @Test
    fun aPastTimeAfterTurningTheTimerOffAndOnTurnsItOff() {
        val listener = RecordingListener()
        mModel.addSleepTimerListener(listener)
        mModel.setEnabled(true)
        assertTrue(mModel.updateTimer(System.currentTimeMillis() + ARMED_DELAY_MS, true))
        mModel.setEnabled(false)
        mModel.setEnabled(true)

        assertFalse(
            "A past time was taken", mModel.updateTimer(System.currentTimeMillis() - DELAY_MS, true)
        )

        assertFalse("Storage says on with no timer armed", mStorage.loadEnabled())
        assertFalse("A cancelled timer completed", listener.awaitCompletionDuring(SILENCE_MS))
    }

    /**
     * A past time picked without touching the switch is refused and changes nothing: the alarm
     * set before keeps counting down, and storage keeps describing it.
     */
    @Test
    fun aPastTimeKeepsTheAlarmThatIsArmed() {
        val listener = RecordingListener()
        mModel.addSleepTimerListener(listener)
        mModel.setEnabled(true)
        val alarm = System.currentTimeMillis() + ARMED_DELAY_MS
        assertTrue(mModel.updateTimer(alarm, true))

        assertFalse(
            "A past time was taken", mModel.updateTimer(System.currentTimeMillis() - DELAY_MS, true)
        )

        assertTrue("The armed alarm is no longer stored on", mStorage.loadEnabled())
        assertEquals("The stored alarm time changed", alarm, mStorage.loadDate().time)
        assertTrue("The armed alarm did not survive", listener.awaitCompletion())
    }

    @Test
    fun aRegisteredListenerHearsTheTimerElapse() {
        val listener = RecordingListener()
        mModel.addSleepTimerListener(listener)

        mModel.updateTimer(System.currentTimeMillis() + DELAY_MS, true)

        assertTrue("The listener was never notified", listener.awaitCompletion())
    }

    @Test
    fun everyRegisteredListenerHearsTheTimerElapse() {
        val first = RecordingListener()
        val second = RecordingListener()
        mModel.addSleepTimerListener(first)
        mModel.addSleepTimerListener(second)

        mModel.updateTimer(System.currentTimeMillis() + DELAY_MS, true)

        assertTrue("The first listener was never notified", first.awaitCompletion())
        assertTrue("The second listener was never notified", second.awaitCompletion())
    }

    /**
     * The service's listener, the one that stops playback, is registered like any other, so a
     * listener ahead of it that fails must not keep the completion from reaching it.
     */
    @Test
    fun aFailingListenerDoesNotSilenceTheListenersAfterIt() {
        val failing = ThrowingListener(IllegalStateException("A listener that fails"))
        val after = RecordingListener()
        mModel.addSleepTimerListener(failing)
        mModel.addSleepTimerListener(after)

        mModel.updateTimer(System.currentTimeMillis() + DELAY_MS, true)

        assertTrue("The failing listener was never called", failing.awaitCall())
        assertTrue("The listener after a failing one was never notified", after.awaitCompletion())
    }

    /**
     * An [Error] is not an [Exception], and a listener that throws one, as `TODO()` does, must not
     * keep the completion from the listener that stops playback either.
     */
    @Test
    fun aListenerThrowingAnErrorDoesNotSilenceTheListenersAfterIt() {
        val failing = ThrowingListener(NotImplementedError("A listener that is not written yet"))
        val after = RecordingListener()
        mModel.addSleepTimerListener(failing)
        mModel.addSleepTimerListener(after)

        mModel.updateTimer(System.currentTimeMillis() + DELAY_MS, true)

        assertTrue("The failing listener was never called", failing.awaitCall())
        assertTrue("The listener after an Error was never notified", after.awaitCompletion())
    }

    @Test
    fun aRemovedListenerHearsNothing() {
        val kept = RecordingListener()
        val removed = RecordingListener()
        mModel.addSleepTimerListener(kept)
        mModel.addSleepTimerListener(removed)
        mModel.removeSleepTimerListener(removed)

        mModel.updateTimer(System.currentTimeMillis() + DELAY_MS, true)

        assertTrue("The kept listener was never notified", kept.awaitCompletion())
        assertFalse("A removed listener was notified", removed.awaitCompletionDuring(SILENCE_MS))
    }

    /**
     * Turning the timer off has to stop the one that is counting down, not only the stored flag:
     * otherwise it would still close the service while the settings say it is off.
     */
    @Test
    fun disablingCancelsATimerThatIsRunning() {
        val listener = RecordingListener()
        mModel.addSleepTimerListener(listener)
        mModel.setEnabled(true)
        mModel.updateTimer(System.currentTimeMillis() + DELAY_MS, true)

        mModel.setEnabled(false)

        assertFalse("A disabled timer completed", listener.awaitCompletionDuring(SILENCE_MS))
        assertFalse(mStorage.loadEnabled())
    }

    /**
     * The application arms the stored alarm on a background thread at start while the user may be
     * switching the timer off on the main thread. A disable that lands after [SleepTimerModelImpl.init]
     * has read the stored flag as on, but before it arms, must still win: otherwise the timer is
     * armed with storage saying off, and closes the service when it fires.
     *
     * The stored flag is read inside that window, so its read is where the arming thread parks: it
     * lets the disabling thread go and waits for it to finish. A model whose read and arm are not
     * atomic lets the disable run to the end in the window, and the arm that follows starts a timer
     * the disable was meant to remove. An atomic one holds the disable back until init is done, so
     * the wait runs out, init arms, and the disable then cancels what it armed.
     */
    @Test
    fun aDisableIssuedWhileInitIsArmingWins() {
        mStorage.saveDate(System.currentTimeMillis() + ARMED_DELAY_MS)
        mStorage.saveEnabled(true)
        val readingEnabled = CountDownLatch(1)
        val disableDone = CountDownLatch(1)
        val listener = RecordingListener()
        val model = SleepTimerModelImpl(
            WeakReference(
                EnabledReadParkingContext(mContext) {
                    readingEnabled.countDown()
                    disableDone.await(WINDOW_MS, TimeUnit.MILLISECONDS)
                }
            )
        )
        model.addSleepTimerListener(listener)

        val arming = thread { model.init() }
        assertTrue(
            "init never read the stored flag",
            readingEnabled.await(COMPLETION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
        model.setEnabled(false)
        disableDone.countDown()
        arming.join()

        assertFalse(mStorage.loadEnabled())
        assertFalse(
            "A timer was left armed after the disable", listener.awaitCompletionDuring(SILENCE_MS)
        )
    }

    /**
     * Enabling only records the choice. The stored date may be unset, which reads as the current
     * time, so arming it here could fire at once; the dialog arms the time the user picked.
     */
    @Test
    fun enablingArmsNothingByItself() {
        val listener = RecordingListener()
        mModel.addSleepTimerListener(listener)
        mStorage.saveDate(System.currentTimeMillis() + DELAY_MS)

        mModel.setEnabled(true)

        assertFalse("Enabling armed the timer", listener.awaitCompletionDuring(SILENCE_MS))
        assertTrue(mStorage.loadEnabled())
    }

    /**
     * The alarm is a one shot, so the stored flag has to go down with it. A flag left up would arm
     * the timer again at the next start for a timestamp already in the past.
     */
    @Test
    fun aCompletedTimerDisablesItselfInStorage() {
        val listener = RecordingListener()
        mModel.addSleepTimerListener(listener)
        mModel.setEnabled(true)

        mModel.updateTimer(System.currentTimeMillis() + DELAY_MS, true)

        assertTrue("The timer never completed", listener.awaitCompletion())
        assertFalse(mModel.isEnabled())
        assertFalse(mStorage.loadEnabled())
    }

    /**
     * The timestamp is stored whether or not the alarm is armed, which is what lets the settings
     * dialog offer back the time the user picked while the timer was off.
     */
    @Test
    fun aDisabledUpdateStoresTheTimestampAndArmsNothing() {
        val listener = RecordingListener()
        mModel.addSleepTimerListener(listener)
        val alarm = System.currentTimeMillis() + DELAY_MS

        mModel.updateTimer(alarm, false)

        assertFalse(
            "A disabled timer notified its listeners", listener.awaitCompletionDuring(SILENCE_MS)
        )
        assertEquals(alarm, mStorage.loadDate().time)
    }

    private fun newModel(clock: () -> Long = System::currentTimeMillis): SleepTimerModelImpl {
        return SleepTimerModelImpl(WeakReference(mContext), clock)
    }

    /**
     * Hands out the real sleep timer preferences, except that the first read of the enabled flag
     * runs [mPark] after reading the value and before returning it, which is the moment a thread
     * that has read the flag can be preempted before it acts on it.
     */
    private class EnabledReadParkingContext(
        base: Context,
        private val mPark: () -> Unit
    ) : ContextWrapper(base) {

        private val mParkPending = AtomicBoolean(true)

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val preferences = super.getSharedPreferences(name, mode)
            if (name != SLEEP_TIMER_FILE_NAME) {
                return preferences
            }
            return object : SharedPreferences by preferences {
                override fun getBoolean(key: String, defValue: Boolean): Boolean {
                    val value = preferences.getBoolean(key, defValue)
                    if (key == SLEEP_TIMER_ENABLED_KEY && mParkPending.compareAndSet(true, false)) {
                        mPark()
                    }
                    return value
                }
            }
        }
    }

    /**
     * Lets a test wait for a completion, or wait out a window in which none may arrive. Both waits
     * are bounded by the caller.
     */
    private class RecordingListener : SleepTimerListener {

        private val mCompleted = CountDownLatch(1)

        override fun onComplete() {
            mCompleted.countDown()
        }

        fun awaitCompletion(): Boolean {
            return awaitCompletionDuring(COMPLETION_TIMEOUT_MS)
        }

        fun awaitCompletionDuring(millis: Long): Boolean {
            return mCompleted.await(millis, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Fails every completion it hears with [mFailure], after recording that it heard one.
     */
    private class ThrowingListener(private val mFailure: Throwable) : SleepTimerListener {

        private val mCalled = CountDownLatch(1)

        override fun onComplete() {
            mCalled.countDown()
            throw mFailure
        }

        fun awaitCall(): Boolean {
            return mCalled.await(COMPLETION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {

        const val ALARM_TIMESTAMP = 1_700_000_000_000L

        const val ALARM_YEAR = 2030
        val ALARM_MONTH = Calendar.MARCH
        const val ALARM_DAY = 7
        const val ALARM_HOUR = 22
        const val ALARM_MINUTE = 15

        /**
         * A second time of day that shares neither its hour nor its minute with the alarm, so a
         * read mixing the two is told apart from both.
         */
        const val OTHER_HOUR = 7
        const val OTHER_MINUTE = 40

        /**
         * How long the calendar is written and read at once.
         */
        const val RACE_MS = 3_000L

        /**
         * Mirrors the private file name of [SleepTimerStorage].
         */
        const val SLEEP_TIMER_FILE_NAME = "SleepTimerStorage"

        /**
         * Mirrors the private key of the enabled flag in [SleepTimerStorage].
         */
        const val SLEEP_TIMER_ENABLED_KEY = "ENABLED"

        const val DELAY_MS = 300L

        /**
         * For a timer that has to stay armed while a test makes several calls and reads storage,
         * yet still completes, or is shown not to, within [SILENCE_MS].
         */
        const val ARMED_DELAY_MS = 1_000L

        /**
         * The instant a fixed clock reports, years before the real one, so a timer that measured
         * its delay from the system clock instead would refuse everything as past.
         */
        const val CLOCK_NOW = 1_500_000_000_000L

        const val COMPLETION_TIMEOUT_MS = 5_000L

        const val SILENCE_MS = 1_500L

        /**
         * How long a thread parked inside a race window waits for the other thread to finish. The
         * other thread finishes at once when nothing holds it back, so this only has to outlast a
         * slow device, and stays well under [ARMED_DELAY_MS] so the timer is still in the future
         * once the wait runs out.
         */
        const val WINDOW_MS = 300L
    }
}
