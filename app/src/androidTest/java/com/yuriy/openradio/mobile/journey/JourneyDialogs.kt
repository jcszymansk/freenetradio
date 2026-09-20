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
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import com.google.android.material.navigation.NavigationView
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
        mScenario.onActivity { activity ->
            val performed = activity.findViewById<NavigationView>(R.id.nav_view)
                .menu.performIdentifierAction(menuItemId, 0)
            if (!performed) {
                throw AssertionError("The navigation drawer holds no entry to open the $tag dialog")
            }
        }
        return awaitDialog(tag)
    }

    /**
     * Waits for the dialog tagged [tag] to be up and to have a view to read.
     */
    fun awaitDialog(tag: String): DialogFragment {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DIALOG_TIMEOUT_SECONDS)
        val shown = AtomicReference<DialogFragment?>(null)
        while (System.nanoTime() < deadline) {
            mScenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.executePendingTransactions()
                val fragment = manager.findFragmentByTag(tag) as? DialogFragment ?: return@onActivity
                if (fragment.view != null || fragment.dialog?.isShowing == true) {
                    shown.set(fragment)
                }
            }
            shown.get()?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("The $tag dialog did not come up within $DIALOG_TIMEOUT_SECONDS seconds")
    }

    /**
     * @return the view [id] inside [fragment], from whichever of its two halves holds it.
     */
    fun <T : View> field(fragment: DialogFragment, id: Int): T {
        val view = fragment.view?.findViewById<T>(id) ?: fragment.dialog?.findViewById<T>(id)
        return view ?: throw AssertionError(
            "The ${fragment.javaClass.simpleName} dialog holds no view for the requested id"
        )
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
            val gone = AtomicReference(false)
            mScenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.executePendingTransactions()
                val fragment = manager.findFragmentByTag(tag) as? DialogFragment
                if (fragment == null) {
                    gone.set(true)
                } else {
                    fragment.dismiss()
                    manager.executePendingTransactions()
                }
            }
            if (gone.get()) {
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
        val none = AtomicReference(false)
        mScenario.onActivity { activity ->
            val manager = activity.supportFragmentManager
            manager.executePendingTransactions()
            none.set(manager.fragments.none { it is DialogFragment })
        }
        return none.get()
    }

    private companion object {

        /**
         * Covers a fragment transaction and the dialog's own layout pass.
         */
        const val DIALOG_TIMEOUT_SECONDS = 10L

        const val POLL_MILLIS = 50L
    }
}
