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
     * Arms the stored alarm, if it is enabled, without storing a date: an unset date reads as the
     * current time, and storing that would turn it into an alarm nobody set. An enabled alarm whose
     * time has passed is turned off instead, since nothing will ever count down to it.
     */
    override fun init() {
        if (!mTimer.handle(mSleepTimerStorage.loadDate().time, isEnabled())) {
            disableUnlessArmed()
        }
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
     * Arms the timer for [time] when [enabled], or cancels it otherwise, and stores [time] for the
     * settings dialog to offer back.
     *
     * A [time] the timer refuses is not stored, and the timer armed before it, if any, keeps
     * running. When none is, because the user switched the timer off and on again before picking
     * a time that has passed, the stored timer is turned off, so storage never says on while
     * nothing counts down.
     *
     * @return False when the timer refused [time], true otherwise.
     */
    override fun updateTimer(time: Long, enabled: Boolean): Boolean {
        if (!mTimer.handle(time, enabled)) {
            disableUnlessArmed()
            return false
        }
        mSleepTimerStorage.saveDate(time)
        return true
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
     * Brings the stored flag down after the timer refused a time, unless an earlier timer is still
     * counting down: then the flag and the stored date still describe that one.
     */
    private fun disableUnlessArmed() {
        if (!mTimer.isArmed()) {
            mSleepTimerStorage.saveEnabled(false)
        }
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
     * a listener ahead of it fails, with an [Error] as much as with an [Exception]. A failure is
     * logged rather than rethrown because the timer task running this logs and drops anything
     * thrown at it anyway, so rethrowing would only log it twice.
     */
    private inner class SleepTimerListenerImpl : SleepTimerListener {

        override fun onComplete() {
            mSleepTimerStorage.saveEnabled(false)
            for (listener in mTimerListenerExt) {
                try {
                    listener.onComplete()
                } catch (e: Throwable) {
                    AppLogger.e("Sleep timer listener $listener failed on completion", e)
                }
            }
        }
    }
}
