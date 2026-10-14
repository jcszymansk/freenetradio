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
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import com.google.android.material.navigation.NavigationView
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.testing.withActivity
import java.util.concurrent.TimeUnit

/**
 * The dialogs a journey drives, read the way a user meets them: asked for by a tap, waited for
 * until they are up, read through the views they put on the screen, and left again.
 *
 * Every dialog in this application is a [DialogFragment], so it exists some time after the tap
 * that asked for it, and which half of it holds the views depends on the dialog: the add and edit
 * dialogs build a fragment view, while the settings, remove and preference dialogs build an
 * [android.app.AlertDialog] and have no fragment view at all.
 */
@UnstableApi
internal class JourneyDialogs(private val mScenario: ActivityScenario<MainActivity>) {

    /**
     * Asks for the dialog behind the navigation drawer entry [menuItemId] and waits for it.
     *
     * The entry is chosen through the drawer's own menu, so `MainActivity`'s
     * [NavigationView.OnNavigationItemSelectedListener] decides which dialog appears, exactly as
     * it does for a tap. The drawer's slide is not part of it: it is an animation over the same
     * menu, and opening it first would only add a wait.
     */
    fun openFromDrawer(menuItemId: Int, tag: String): DialogFragment {
        val performed = mScenario.withActivity { activity ->
            activity.findViewById<NavigationView>(R.id.nav_view)
                .menu.performIdentifierAction(menuItemId, 0)
        }
        if (!performed) {
            throw AssertionError("The navigation drawer holds no entry to open the $tag dialog")
        }
        return awaitDialog(tag)
    }

    /**
     * Waits for the dialog tagged [tag] to be up and to have a view to read.
     */
    fun awaitDialog(tag: String): DialogFragment {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DIALOG_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val shown = mScenario.withActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.executePendingTransactions()
                val fragment = manager.findFragmentByTag(tag) as? DialogFragment
                fragment?.takeIf { it.view != null || it.dialog?.isShowing == true }
            }
            shown?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("The $tag dialog did not come up within $DIALOG_TIMEOUT_SECONDS seconds")
    }

    /**
     * Looks the view [id] up inside [fragment] on the main thread and returns what [reader] makes
     * of it there, failing on the calling thread when the dialog holds no such view.
     */
    fun <T : View, R> read(fragment: DialogFragment, id: Int, reader: (T) -> R): R {
        val found = mScenario.withActivity { _ -> find<T>(fragment, id)?.let { Found(reader(it)) } }
            ?: throw AssertionError(
                "The ${fragment.javaClass.simpleName} dialog holds no view for the requested id"
            )
        return found.value
    }

    /**
     * @return the text the [TextView] [id] inside [fragment] shows.
     */
    fun text(fragment: DialogFragment, id: Int): String {
        return read<TextView, String>(fragment, id) { it.text.toString() }
    }

    /**
     * @return whether the [CompoundButton] [id] inside [fragment] is checked.
     */
    fun isChecked(fragment: DialogFragment, id: Int): Boolean {
        return read<CompoundButton, Boolean>(fragment, id) { it.isChecked }
    }

    /**
     * Replaces what the [EditText] [id] inside [fragment] holds with [text], as typing it would.
     */
    fun type(fragment: DialogFragment, id: Int, text: String) {
        read<EditText, Unit>(fragment, id) { it.setText(text) }
    }

    /**
     * Clicks the view [id] inside [fragment], which flips a check box the way a finger does.
     */
    fun click(fragment: DialogFragment, id: Int) {
        read<View, Boolean>(fragment, id) { it.performClick() }
    }

    /**
     * Leaves the dialog tagged [tag] and waits until it is gone.
     *
     * Several of these dialogs write what the user typed in `onPause`, which the removal is what
     * runs, so a caller that reads a store straight afterwards needs the removal to have happened
     * rather than to have been queued.
     */
    fun dismiss(tag: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DIALOG_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val gone = mScenario.withActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.executePendingTransactions()
                val fragment = manager.findFragmentByTag(tag) as? DialogFragment
                if (fragment != null) {
                    fragment.dismiss()
                    manager.executePendingTransactions()
                }
                fragment == null
            }
            if (gone) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("The $tag dialog was still up $DIALOG_TIMEOUT_SECONDS seconds after it was left")
    }

    /**
     * @return true when no dialog is added to the Activity at all.
     */
    fun noneIsUp(): Boolean {
        return mScenario.withActivity { activity ->
            val manager = activity.supportFragmentManager
            manager.executePendingTransactions()
            manager.fragments.none { it is DialogFragment }
        }
    }

    /**
     * @return the view [id] inside [fragment], from whichever of its two halves holds it, or null
     *   when neither does. Main thread only.
     */
    private fun <T : View> find(fragment: DialogFragment, id: Int): T? {
        return fragment.view?.findViewById<T>(id) ?: fragment.dialog?.findViewById<T>(id)
    }

    /**
     * What a reader answered, kept apart from the null that stands for a missing view, so that a
     * reader answering null is not mistaken for one.
     */
    private class Found<R>(val value: R)

    private companion object {

        /**
         * Covers a fragment transaction and the dialog's own layout pass.
         */
        const val DIALOG_TIMEOUT_SECONDS = 10L

        const val POLL_MILLIS = 50L
    }
}
