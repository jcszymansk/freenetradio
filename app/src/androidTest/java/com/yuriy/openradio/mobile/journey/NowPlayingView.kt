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

import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue

/**
 * The now-playing bar an [ActivityScenario] is showing above the browse list.
 *
 * It is the whole of the phone's playback screen: the station's name, the description the player
 * reports about the stream, a favorite box, and the bar itself, whose click listener is the only
 * transport control the phone UI has. There is no play button anywhere in the application; the
 * bar sends `CMD_TOGGLE_LAST_PLAYED_ITEM` and the service decides from the player's state whether
 * that means pause, play or seek.
 *
 * The bar starts out `GONE` and the application is what puts it up, on the first metadata the
 * session pushes, so everything read here arrives without the test doing anything to the views.
 */
@UnstableApi
internal class NowPlayingView(private val mScenario: ActivityScenario<MainActivity>) {

    /**
     * Waits for the bar to be up and showing [expected] as the station's name.
     *
     * Metadata crosses a process boundary and lands on the main thread, so there is no event to
     * wait on from here: the bar is still down, then it is up with the previous station's name,
     * then with this one's.
     */
    fun awaitTitle(expected: String) {
        await("the title '$expected'") { isVisible() && title() == expected }
    }

    /**
     * Waits for the description line to read [expected].
     *
     * The player writes this line rather than the station: it says "buffering" while the stream
     * is opening, the default stream description once it is playing, and the reason it failed
     * when it did.
     */
    fun awaitDescription(expected: String) {
        await("the description '$expected'") { isVisible() && description() == expected }
    }

    fun awaitFavorite(expected: Boolean) {
        await("the favorite box to read $expected") { isVisible() && favorite() == expected }
    }

    /**
     * Asserts the bar is down and stays down, which is what a phone that is playing nothing
     * shows. It is watched rather than read once, because a bar that is about to come up has not
     * come up yet.
     */
    fun assertStaysDown(reason: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS)
        while (System.nanoTime() < deadline) {
            assertTrue("$reason " + describe(), !isVisible())
            Thread.sleep(POLL_MILLIS)
        }
    }

    /**
     * Clicks the bar, which is how a user pauses and resumes what they are listening to.
     */
    fun tap() {
        assertTrue("The now-playing bar is down, so there is nothing to tap. " + describe(), isVisible())
        mScenario.onActivity { activity ->
            activity.findViewById<View>(R.id.current_radio_station_view).performClick()
        }
    }

    /**
     * Clicks the favorite box on the bar, which marks the station that is playing.
     */
    fun tapFavorite() {
        assertTrue("The now-playing bar is down, so its favorite box is not on screen. " + describe(), isVisible())
        mScenario.onActivity { activity ->
            activity.findViewById<CheckBox>(R.id.crs_favorite_check_view).performClick()
        }
    }

    fun isVisible(): Boolean {
        return read { activity ->
            activity.findViewById<View>(R.id.current_radio_station_view).visibility == View.VISIBLE
        }
    }

    fun title(): String {
        return text(R.id.crs_name_view)
    }

    fun description(): String {
        return text(R.id.crs_description_view)
    }

    fun favorite(): Boolean {
        return read { activity -> activity.findViewById<CheckBox>(R.id.crs_favorite_check_view).isChecked }
    }

    /**
     * @return what the bar held when a wait ran out of time, so a timeout says whether the bar
     *   never came up or came up saying something else.
     */
    fun describe(): String {
        return "The now-playing bar is " + (if (isVisible()) "up" else "down") +
            ", showing '${title()}' and '${description()}'."
    }

    private fun await(expectation: String, matches: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BAR_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (matches()) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "The now-playing bar did not show $expectation within $BAR_TIMEOUT_SECONDS seconds. " +
                describe()
        )
    }

    private fun text(viewId: Int): String {
        return read { activity -> activity.findViewById<TextView>(viewId).text.toString() }
    }

    private fun <T> read(reader: (MainActivity) -> T): T {
        val result = AtomicReference<T>()
        mScenario.onActivity { activity -> result.set(reader(activity)) }
        return result.get()
    }

    private companion object {

        /**
         * Covers launching the Activity, connecting to the session, opening the stream and the
         * metadata push that puts the bar up.
         */
        const val BAR_TIMEOUT_SECONDS = 20L

        /**
         * How long the bar is watched before "it stayed down" is believed.
         */
        const val SETTLE_SECONDS = 5L

        const val POLL_MILLIS = 50L
    }
}
