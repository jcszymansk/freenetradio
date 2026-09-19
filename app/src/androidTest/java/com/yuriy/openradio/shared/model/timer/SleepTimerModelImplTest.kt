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
        restarted.init()
        restarted.updateTime(true)

        assertEquals(ALARM_TIMESTAMP, restarted.getTimestamp())
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

        mModel.updateTime(false)

        val drift = Math.abs(System.currentTimeMillis() - mModel.getTimestamp())
        assertTrue("A disabled timer started from $drift ms away from now", drift < NOW_TOLERANCE_MS)
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
        assertEquals(mModel.getTime().time, mModel.getTimestamp())
    }

    @Test
    fun onlyATimestampInTheFutureIsValid() {
        val now = System.currentTimeMillis()

        assertTrue(mModel.isTimestampNotValid(now))
        assertTrue(mModel.isTimestampNotValid(now - FUTURE_MS))
        assertFalse(mModel.isTimestampNotValid(now + FUTURE_MS))
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

        mModel.updateTimer(ALARM_TIMESTAMP, false)

        assertFalse(
            "A disabled timer notified its listeners", listener.awaitCompletionDuring(SILENCE_MS)
        )
        assertEquals(ALARM_TIMESTAMP, mStorage.loadDate().time)
    }

    private fun newModel(): SleepTimerModelImpl {
        return SleepTimerModelImpl(WeakReference(mContext))
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

    private companion object {

        const val ALARM_TIMESTAMP = 1_700_000_000_000L

        const val ALARM_YEAR = 2030
        val ALARM_MONTH = Calendar.MARCH
        const val ALARM_DAY = 7
        const val ALARM_HOUR = 22
        const val ALARM_MINUTE = 15

        const val DELAY_MS = 300L

        const val FUTURE_MS = 60_000L

        const val NOW_TOLERANCE_MS = 10_000L

        const val COMPLETION_TIMEOUT_MS = 5_000L

        const val SILENCE_MS = 1_500L
    }
}
