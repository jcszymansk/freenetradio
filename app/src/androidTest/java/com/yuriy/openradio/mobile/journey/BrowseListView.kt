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
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.view.list.MediaItemsAdapter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * The browse list an [ActivityScenario] is showing, read the way a user reads it.
 *
 * Rows come back in adapter order, each pairing the media id the adapter holds at a position with
 * the text the view at that position actually displays. An entry the service serves but the UI
 * never renders is therefore absent here, which is the whole reason a journey reads the list
 * instead of asking the service what it would answer.
 *
 * Every item has to be on screen, which is the one thing here that depends on the device. The
 * nodes these journeys visit are short lists and the emulator they target lays all of them out
 * without scrolling. A device that cannot is reported by [describe] rather than left as a bare
 * timeout.
 */
@UnstableApi
internal class BrowseListView(private val mScenario: ActivityScenario<MainActivity>) {

    /**
     * Waits for the list to be both filled and laid out, then returns what it shows.
     *
     * The subscription that fills it crosses a process boundary and a layout pass, so there is no
     * event to wait on from here. A read that catches the list part way through either of those
     * yields nothing and is retried.
     */
    fun awaitRows(): List<BrowseRow> {
        return awaitRows("any rendered row") { true }
    }

    /**
     * Waits until the rendered list satisfies [matches].
     *
     * Used for the pushes a journey triggers but does not perform: adding a station makes the
     * service notify the root, which arrives on the subscription and rewrites the adapter some
     * time after the dialog that caused it has closed.
     *
     * @param expectation what the list was being waited for, so a timeout names it.
     */
    fun awaitRows(expectation: String, matches: (List<BrowseRow>) -> Boolean): List<BrowseRow> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIST_TIMEOUT_SECONDS)
        var last = emptyList<BrowseRow>()
        while (System.nanoTime() < deadline) {
            val rows = rows()
            if (rows.isNotEmpty()) {
                last = rows
                if (matches(rows)) {
                    return rows
                }
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "The browse list did not show $expectation within $LIST_TIMEOUT_SECONDS seconds. " +
                "It last showed $last. " + describe()
        )
    }

    /**
     * Asserts the list still shows [expected] and goes on doing so.
     *
     * A list that is about to change has not changed yet, so a single read cannot tell "nothing
     * happened" from "it has not happened yet". This keeps reading for as long as the pushes this
     * suite does trigger take to arrive, and fails on the first row that differs.
     */
    fun assertRowsStay(expected: List<BrowseRow>, reason: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS)
        while (System.nanoTime() < deadline) {
            val rows = rows()
            if (rows.isNotEmpty()) {
                assertEquals(reason, expected, rows)
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    /**
     * Taps the row for [mediaId], on the view that carries the click listener a finger lands on.
     */
    fun tapRow(mediaId: String) {
        clickInRow(mediaId, R.id.foreground_view)
    }

    /**
     * Clicks the settings button of the row for [mediaId].
     *
     * On a station row this is the button a swipe reveals. The swipe itself is a drag on a
     * [com.xenione.libs.swipemaker.SwipeLayout] and is not performed, but the button is a child of
     * the row either way, so its listener and everything it reaches are the real ones.
     */
    fun tapRowSettings(mediaId: String) {
        clickInRow(mediaId, R.id.settings_btn_view)
    }

    /**
     * Clicks the favorite check box of the row for [mediaId].
     *
     * This is the whole favorite gesture on the phone: the box is a child of every playable row,
     * `performClick` flips it the way a finger does, and the listener the adapter put on it sends
     * the command. Nothing about the toggle is assembled here.
     */
    fun tapRowFavorite(mediaId: String) {
        clickInRow(mediaId, R.id.favorite_btn_view)
    }

    /**
     * Waits for the favorite box of the row for [mediaId] to read [expected], and fails naming what
     * it read instead.
     *
     * The box is bound when the adapter binds the row, so the state a journey is asking about
     * arrives with the list rather than with the click that caused it: a node reopened after a
     * toggle is a fresh set of children and a fresh bind.
     */
    fun awaitRowFavorite(mediaId: String, expected: Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIST_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (rowFavorite(mediaId) == expected) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "The favorite box of the row $mediaId reads ${rowFavorite(mediaId)} rather than " +
                "$expected. " + describe()
        )
    }

    /**
     * @return whether the favorite box of the row for [mediaId] is checked, or null while no
     *   rendered row carries that media id or the box is hidden, which is what a row that is not
     *   playable shows.
     */
    fun rowFavorite(mediaId: String): Boolean? {
        return inRow(mediaId) { row, _ ->
            val box = row.findViewById<CheckBox>(R.id.favorite_btn_view)
            if (box.visibility == View.VISIBLE) box.isChecked else null
        }
    }

    /**
     * @return the item the adapter bound the row for [mediaId] from, which is what a tap on that
     *   row hands the presenter, or null while no rendered row carries that media id.
     */
    fun rowItem(mediaId: String): MediaItem? {
        return inRow(mediaId) { _, item -> item }
    }

    private fun clickInRow(mediaId: String, viewId: Int) {
        val clicked = inRow(mediaId) { row, _ -> row.findViewById<View>(viewId).performClick() }
        assertNotNull("No rendered row carries the media id $mediaId. " + describe(), clicked)
    }

    /**
     * Reads the rendered row whose adapter position holds [mediaId], handing [read] both the row
     * on screen and the item the adapter bound it from.
     *
     * @return what [read] answered, or null when no such row was on screen at all, which is the
     *   difference between a control that did not react and a row the list never laid out.
     */
    private fun <T> inRow(mediaId: String, read: (View, MediaItem) -> T): T? {
        val result = AtomicReference<T?>(null)
        mScenario.onActivity { activity ->
            val listView = activity.findViewById<RecyclerView>(R.id.list_view)
            val adapter = listView.adapter as? MediaItemsAdapter ?: return@onActivity
            for (index in 0 until listView.childCount) {
                val child = listView.getChildAt(index)
                val position = listView.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) {
                    continue
                }
                val item = adapter.getItem(position) ?: continue
                if (item.mediaId != mediaId) {
                    continue
                }
                result.set(read(child, item))
            }
        }
        return result.get()
    }

    /**
     * @return the rendered rows, or an empty list while the adapter and the laid out children
     *   disagree, which is every moment the list is still being filled or measured.
     */
    fun rows(): List<BrowseRow> {
        val result = AtomicReference(emptyList<BrowseRow>())
        mScenario.onActivity { activity ->
            val listView = activity.findViewById<RecyclerView>(R.id.list_view)
            val adapter = listView.adapter as? MediaItemsAdapter ?: return@onActivity
            val displayed = HashMap<Int, String>()
            for (index in 0 until listView.childCount) {
                val child = listView.getChildAt(index)
                val position = listView.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION) {
                    continue
                }
                displayed[position] = child.findViewById<TextView>(R.id.name_view).text.toString()
            }
            if (adapter.itemCount == 0 || displayed.size != adapter.itemCount) {
                return@onActivity
            }
            result.set(
                (0 until adapter.itemCount).map { position ->
                    BrowseRow(
                        adapter.getItem(position)?.mediaId.orEmpty(),
                        displayed.getValue(position)
                    )
                }
            )
        }
        return result.get()
    }

    /**
     * @return what the list held when it ran out of time, so a timeout says which half was
     *   missing: children the service never sent, or rows the screen was too short to lay out.
     */
    fun describe(): String {
        val result = AtomicReference("The Activity holds no browse list at all.")
        mScenario.onActivity { activity ->
            val listView = activity.findViewById<RecyclerView>(R.id.list_view)
            val adapter = listView.adapter as? MediaItemsAdapter ?: return@onActivity
            result.set(
                "The adapter holds ${adapter.itemCount} items and ${listView.childCount} rows " +
                    "are laid out."
            )
        }
        return result.get()
    }

    private companion object {

        /**
         * Covers launching the Activity, connecting a MediaBrowser to the service, answering the
         * request and laying the list out.
         */
        const val LIST_TIMEOUT_SECONDS = 20L

        /**
         * How long a list is watched before "it did not change" is believed. Long enough to cover
         * the pushes this suite does trigger, which arrive well inside a second.
         */
        const val SETTLE_SECONDS = 5L

        const val POLL_MILLIS = 50L
    }
}

/**
 * One row of the browse list: the item the adapter holds at a position, and the text the view at
 * that position puts on screen.
 */
internal data class BrowseRow(val mediaId: String, val title: String)
