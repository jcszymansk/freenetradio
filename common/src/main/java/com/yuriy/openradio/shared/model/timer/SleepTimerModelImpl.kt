/*
 * Copyright 2021, 2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import com.yuriy.openradio.shared.model.storage.SleepTimerStorage
import com.yuriy.openradio.shared.utils.AppLogger
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * [SleepTimerModelImpl] is a class that is responsible for preparing and managing the data for an Activity or a Fragment.
 * It also handles the communication of the Activity / Fragment with the rest of the application
 * (e.g. calling the business logic classes).
 *
 * @param contextRef Weak reference to the application context, for the stored timer state.
 * @param mClock Wall clock milliseconds. The timer measures its delay from the same clock, so the
 * model and the timer agree about which timestamps are in the future.
 */
class SleepTimerModelImpl(
    contextRef: WeakReference<Context>,
    private val mClock: () -> Long = System::currentTimeMillis
) : SleepTimerModel {

    private val mSleepTimerStorage = SleepTimerStorage(contextRef)
    private val mTimerListenerExt = ConcurrentLinkedQueue<SleepTimerListener>()
    private val mTimerListener = SleepTimerListenerImpl()
    private val mTimer = SleepTimerImpl(mTimerListener, mClock)

    /**
     * The time the settings dialog is editing. [Calendar] is not thread safe and the model is
     * shared across threads, so every access holds the calendar's monitor.
     */
    private val mCalendar = Calendar.getInstance()

    /**
     * Arms the stored alarm, if it is enabled, without writing anything back: an unset date reads
     * as the current time, and storing that would turn it into an alarm nobody set.
     */
    override fun init() {
        mTimer.handle(mSleepTimerStorage.loadDate().time, isEnabled())
    }

    /**
     * Returns whether or not timer is enabled by the user.
     *
     * @return True if enabled, false otherwise.
     */
    override fun isEnabled(): Boolean {
        return mSleepTimerStorage.loadEnabled()
    }

    /**
     * Sets whether or not timer is enabled. Disabling also cancels a timer that is running, so it
     * cannot close the service once the timer is off. Enabling arms nothing: the stored date may be
     * unset, which reads as the current time, and [updateTimer] arms the time the user picked.
     *
     * @param value True if enabled, false otherwise.
     */
    override fun setEnabled(value: Boolean) {
        mSleepTimerStorage.saveEnabled(value)
        if (!value) {
            mTimer.cancel()
        }
    }

    /**
     * Updates calendar with a timestamp either set by user or the system current.
     *
     * @param enabled Whether or not timer is enabled by the user.
     */
    override fun updateTime(enabled: Boolean) {
        val time = if (enabled) mSleepTimerStorage.loadDate() else Date(mClock())
        synchronized(mCalendar) {
            mCalendar.time = time
        }
    }

    /**
     * Updates timer logic itself. Communicates with the service and provides value of the timestamp to be triggered
     * at if enabled, or disable the service otherwise.
     */
    override fun updateTimer(time: Long, enabled: Boolean) {
        mSleepTimerStorage.saveDate(time)
        mTimer.handle(time, enabled)
    }

    /**
     * Sets the date of the calendar.
     *
     * @param year Year to set.
     * @param month Month to set. The first month of the year in the Gregorian and Julian calendars is JANUARY
     *              which is 0; the last depends on the number of months in a year.
     * @param day Day of the month. This is a synonym for DATE. The first day of the month has value 1.
     */
    override fun setDate(year: Int, month: Int, day: Int) {
        synchronized(mCalendar) {
            mCalendar.set(Calendar.YEAR, year)
            mCalendar.set(Calendar.MONTH, month)
            mCalendar.set(Calendar.DAY_OF_MONTH, day)
        }
    }

    /**
     * Sets time of the calendar.
     *
     * @param hourOfDay Hour of the day. It is used for the 24-hour clock.
     *                  E.g., at 10:04:15.250 PM the HOUR_OF_DAY is 22.
     * @param minute Minute within the hour. E.g., at 10:04:15.250 PM the MINUTE is 4.
     */
    override fun setTime(hourOfDay: Int, minute: Int) {
        synchronized(mCalendar) {
            mCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            mCalendar.set(Calendar.MINUTE, minute)
            mCalendar.set(Calendar.SECOND, 0)
            mCalendar.set(Calendar.MILLISECOND, 0)
        }
    }

    /**
     * Returns a [Date] object representing calendar's time value.
     *
     * @return Time representation.
     */
    override fun getTime(): Date {
        synchronized(mCalendar) {
            return mCalendar.time
        }
    }

    /**
     * returns current timestamp of the calendar based on the current state of the calendar.
     *
     * @return Returns the number of milliseconds since January 1, 1970, 00:00:00 GMT represented by this Date object.
     */
    override fun getTimestamp(): Long {
        return getTime().time
    }

    /**
     * Helper function to validate whether or not timestamp is not valid for processing. The timer
     * owns the rule, so a timestamp rejected here is one the timer would refuse to arm.
     *
     * @return True if not valid, false otherwise.
     */
    override fun isTimestampNotValid(value: Long): Boolean {
        return !mTimer.accepts(value)
    }

    override fun addSleepTimerListener(listener: SleepTimerListener) {
        mTimerListenerExt.add(listener)
    }

    override fun removeSleepTimerListener(listener: SleepTimerListener) {
        mTimerListenerExt.remove(listener)
    }

    /**
     * Fans a completion out to every registered listener. Each one is shielded from the others: the
     * service's listener, which is the one that stops playback, must hear the completion even when
     * a listener ahead of it fails.
     */
    private inner class SleepTimerListenerImpl : SleepTimerListener {

        override fun onComplete() {
            mSleepTimerStorage.saveEnabled(false)
            for (listener in mTimerListenerExt) {
                try {
                    listener.onComplete()
                } catch (e: Exception) {
                    AppLogger.e("Sleep timer listener $listener failed on completion", e)
                }
            }
        }
    }
}
