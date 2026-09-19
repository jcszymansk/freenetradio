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

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.permission.PermissionChecker
import com.yuriy.openradio.shared.service.LoopbackHttpFixture
import com.yuriy.openradio.shared.view.dialog.AddStationDialog
import com.yuriy.openradio.shared.view.dialog.BaseDialogFragment
import com.yuriy.openradio.shared.view.dialog.EditStationDialog
import com.yuriy.openradio.shared.view.dialog.RSSettingsDialog
import com.yuriy.openradio.shared.view.dialog.RemoveStationDialog
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.yuriy.openradio.shared.R as DialogR

/**
 * The second phone journey: a station the user typed in by hand, from the dialog that creates it
 * through editing and removing it, and across a relaunch.
 *
 * The local-station path is the only content the application can produce with no connection at
 * all, so it is the backbone of the offline suite. Everything below the UI is the real thing: the
 * real `RadioStationValidatorImpl`, the real `RadioStationManagerLayerImpl` and the real
 * `DeviceLocalsStorage`. The stream url points at [LoopbackHttpFixture] because the validator
 * probes it over HTTP before it will accept a station, and the loopback address is the one address
 * that answers with the device's networking disabled.
 *
 * Two things a user does are not driven here, and neither can be:
 *
 * The swipe that reveals a row's settings button is a drag on a [com.xenione.libs.swipemaker.SwipeLayout];
 * the button behind it is in the hierarchy either way, so nothing between it and the dialogs is
 * skipped by clicking it directly.
 *
 * Opening the locals list at all is refused while the device is offline.
 * `MediaPresenterImpl.handleItemSelected` gates every browse tap on connectivity and is the only
 * tap-driven way into a node, so there is no station row on screen to reach the settings button
 * on. `theLocalsRowDoesNotOpenWithoutANetwork` pins that down and TASK-049 holds the fix. Until
 * then this journey shows the settings dialog with the bundle `handleItemSettings` would have
 * built once the presenter was inside the locals node, and everything from that dialog onwards is
 * the application's own path: the locals-only edit and remove buttons, the media item they carry
 * as a tag, the `android:onClick` that lands in [MainActivity], and the real edit and remove
 * dialogs it opens.
 *
 * What the service answers for the locals node is `OpenRadioServiceBrowseTest`'s subject. What is
 * asserted here is the root list as the Activity renders it, and the store as a reader that has
 * cached nothing sees it.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class LocalStationLifecycleJourneyTest {

    private lateinit var mContext: Context

    private lateinit var mProfile: JourneyProfile

    private lateinit var mStreams: LoopbackHttpFixture

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        grantImageReadPermission()
        mProfile = JourneyProfile(mContext)
        mProfile.start()
        mStreams = LoopbackHttpFixture()
        mStreams.start()
    }

    @After
    fun tearDown() {
        mStreams.stop()
        mProfile.finish()
    }

    /**
     * The first half of the lifecycle, and the only half a user can drive offline: the add button
     * the cold launch leaves on screen, the dialog behind it, and the row the root grows.
     *
     * Nothing between the dialog closing and the row appearing is done by the test. The dialog
     * sends `CMD_UPDATE_TREE`, the service drops its cached root and notifies, and the Activity's
     * subscription rewrites the list. Asserting the row is absent first is what makes the wait for
     * it mean something.
     */
    @Test
    fun addingAStationThroughTheDialogPutsItOnTheRootList() {
        val url = serveStream(STATION_PATH)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitCleanInstallRoot(list)

            addStationThroughTheDialog(scenario, STATION_NAME, url)

            assertEquals(
                "Adding a station did not put the locals node on the root list",
                mProfile.cleanInstallRoot() + mProfile.localsRow(),
                list.awaitRows("the locals node") { it.contains(mProfile.localsRow()) }
            )
        }

        val station = storedStation()
        assertEquals("The stored station does not carry the typed name", STATION_NAME, station.name)
        assertEquals(
            "The stored station does not carry the typed stream url",
            url,
            station.getStreamUrlFixed()
        )
        assertTrue("A station added by hand was not marked as the user's own", station.isLocal)
        assertTrue(
            "The validator never probed the stream, so the station was accepted unchecked",
            mStreams.requestedPaths().contains(STATION_PATH)
        )
    }

    /**
     * Editing goes through the dialog the settings dialog opens, which loads the station out of
     * storage to fill its fields. Asserting what it loaded first is what separates an edit from a
     * blind overwrite: a dialog that showed the user empty fields would pass the rest of this test
     * while silently discarding everything the user did not retype.
     */
    @Test
    fun editingAStationThroughTheDialogRewritesWhatIsStored() {
        val url = serveStream(STATION_PATH)
        val editedUrl = serveStream(EDITED_PATH)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitCleanInstallRoot(list)
            addStationThroughTheDialog(scenario, STATION_NAME, url)
            list.awaitRows("the locals node") { it.contains(mProfile.localsRow()) }

            val settings = openStationSettings(scenario, storedMediaItem())
            val edit = openFromSettings(
                scenario, settings, DialogR.id.dialog_rs_settings_edit_btn, EditStationDialog.DIALOG_TAG
            )

            scenario.onActivity {
                assertEquals(
                    "The edit dialog did not load the station it was opened for",
                    STATION_NAME,
                    dialogView<EditText>(edit, DialogR.id.add_edit_station_name_edit).text.toString()
                )
                assertEquals(
                    "The edit dialog did not load the station's stream url",
                    url,
                    dialogView<EditText>(edit, DialogR.id.add_edit_station_stream_url_edit).text.toString()
                )
                dialogView<EditText>(edit, DialogR.id.add_edit_station_name_edit).setText(EDITED_NAME)
                dialogView<EditText>(edit, DialogR.id.add_edit_station_stream_url_edit).setText(editedUrl)
                dialogView<View>(edit, DialogR.id.add_edit_station_dialog_add_btn_view).performClick()
            }

            val edited = awaitStoredStation(EDITED_NAME)
            assertEquals("The edit did not rewrite the stream url", editedUrl, edited.getStreamUrlFixed())
            assertEquals(
                "The edit added a second station instead of changing the one it was given",
                1,
                mProfile.storages.freshLocals().getAll().size
            )
            assertEquals(
                "The service still serves the station under its old name",
                listOf(EDITED_NAME),
                servedLocalTitles()
            )
            assertEquals(
                "Editing a station changed the root list, which only gains and loses the node",
                mProfile.cleanInstallRoot() + mProfile.localsRow(),
                list.awaitRows()
            )
        }
    }

    /**
     * Removing the only station the user has takes the whole node away, which is the one part of
     * the edit and remove path that is visible from where an offline user is standing.
     */
    @Test
    fun removingTheLastStationTakesTheLocalsNodeOffTheRootList() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitCleanInstallRoot(list)
            addStationThroughTheDialog(scenario, STATION_NAME, serveStream(STATION_PATH))
            list.awaitRows("the locals node") { it.contains(mProfile.localsRow()) }

            val settings = openStationSettings(scenario, storedMediaItem())
            val remove = openFromSettings(
                scenario, settings, DialogR.id.dialog_rs_settings_remove_btn, RemoveStationDialog.DIALOG_TAG
            )

            scenario.onActivity {
                assertEquals(
                    "The confirmation does not say which station is about to go",
                    mContext.getString(DialogR.string.remove_station_dialog_main_text, STATION_NAME),
                    dialogView<TextView>(remove, DialogR.id.remove_station_text_view).text.toString()
                )
                dialogView<View>(remove, DialogR.id.remove_station_dialog_add_btn_view).performClick()
            }

            assertEquals(
                "Removing the last station left the locals node on the root list",
                mProfile.cleanInstallRoot(),
                list.awaitRows("the locals node gone") { !it.contains(mProfile.localsRow()) }
            )
        }

        assertTrue(
            "The station is off the root list but still in the store",
            mProfile.storages.freshLocals().getAll().isEmpty()
        )
    }

    /**
     * The station has to outlive the screen that created it.
     *
     * Three things a restart depends on are asserted, each defeating a different way the station
     * could appear to survive without having been stored: it reached a preference file on disk
     * rather than only the copy Android keeps in memory for this process; a second Activity
     * renders it after the service's cached root has been dropped, so the list it shows was built
     * from the store rather than replayed; and a
     * [com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage] this test constructed reads
     * it back, so nothing the writer cached can answer for it.
     *
     * What is not asserted is a real process restart, and it cannot be from here: the service
     * shares this process, so killing it takes the instrumentation with it. That is the whole
     * suite's limit, not this journey's, and TASK-031 carries the decision that would lift it
     * along with re-running this case across a genuinely new process.
     */
    @Test
    fun theStationOutlivesTheActivityThatAddedIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitCleanInstallRoot(list)
            addStationThroughTheDialog(scenario, STATION_NAME, serveStream(STATION_PATH))
            list.awaitRows("the locals node") { it.contains(mProfile.localsRow()) }
        }

        val preferenceFile = awaitWrittenToDisk(STATION_NAME)
        assertTrue(
            "The station was written to $preferenceFile, which is not the locals store",
            preferenceFile.contains(LOCALS_PREFERENCE_FILE)
        )

        // The root is the one node the service does cache, so a second Activity would otherwise be
        // handed the list the first one built and this would assert nothing about storage. Dropping
        // it makes the relaunch rebuild the root through MediaItemRoot, which reads the locals
        // store, so what the new Activity renders is what the store holds.
        mProfile.refreshTree()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(
                "A relaunched Activity does not offer the station the previous one added",
                mProfile.cleanInstallRoot() + mProfile.localsRow(),
                BrowseListView(scenario).awaitRows("the locals node") {
                    it.contains(mProfile.localsRow())
                }
            )
        }

        val restored = mProfile.storages.freshLocals().getAll()
        assertEquals("A store built from disk holds the wrong number of stations", 1, restored.size)
        assertEquals(
            "A store built from disk does not hold the added station",
            STATION_NAME,
            restored.first().name
        )
    }

    /**
     * The gate this journey ran into, asserted rather than described, so that TASK-049 lands with
     * a test that changes answer when it is fixed.
     *
     * `MediaPresenterImpl.handleItemSelected` returns before it does anything when
     * `NetworkLayer.checkConnectivityAndNotify` says there is no connection. The locals node needs
     * no connection: `MediaItemLocalsList` reads `DeviceLocalsStorage` and nothing else. So the
     * row is offered, tapping it raises the no-connection toast, and the user's own station stays
     * out of reach.
     */
    @Test
    fun theLocalsRowDoesNotOpenWithoutANetwork() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            awaitCleanInstallRoot(list)
            addStationThroughTheDialog(scenario, STATION_NAME, serveStream(STATION_PATH))
            val root = list.awaitRows("the locals node") { it.contains(mProfile.localsRow()) }

            list.tapRow(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)

            list.assertRowsStay(
                root,
                "Tapping the locals row offline opened it. TASK-049 asks for exactly that, so " +
                    "this test is what has to change, not the fix"
            )
            scenario.onActivity { activity ->
                assertEquals(
                    "The add-station button is hidden off the root, so the list did leave it",
                    View.VISIBLE,
                    activity.findViewById<View>(R.id.add_station_btn).visibility
                )
            }
        }
    }

    /**
     * Waits for the list to be the root of a cleared profile, which is where every case here
     * starts.
     *
     * This is a precondition rather than a restatement of the cold launch journey. The presenter
     * is one of the registry's singletons and its node stack outlives the Activity: `init` walks
     * back into whatever node the stack ends with, so a case that navigated leaves the next
     * Activity somewhere other than the root, in the same process. Nothing here navigates, which
     * this asserts rather than assumes, because Phase 6 asks for a suite with no order
     * assumptions in it.
     */
    private fun awaitCleanInstallRoot(list: BrowseListView) {
        assertEquals(
            "This case did not start at the root of a cleared profile. A case that walks into a " +
                "node leaves the presenter's stack there, and it is a registry singleton that " +
                "outlives the Activity, so the next case launches back into that node.",
            mProfile.cleanInstallRoot(),
            list.awaitRows("the root of a cleared profile") { it == mProfile.cleanInstallRoot() }
        )
    }

    /**
     * Fills in and submits the real add dialog, reached through the button the root list shows.
     *
     * Only the name and the url are typed. The home page is left empty on purpose: a home page the
     * validator cannot reach is reported to the user as a failed add even though the station was
     * stored, which is TASK-038, and this journey is not the place to pin that down.
     */
    private fun addStationThroughTheDialog(
        scenario: ActivityScenario<MainActivity>,
        name: String,
        url: String
    ) {
        scenario.onActivity { activity ->
            activity.findViewById<View>(R.id.add_station_btn).performClick()
        }
        val dialog = awaitDialog(scenario, AddStationDialog.DIALOG_TAG)
        scenario.onActivity {
            dialogView<EditText>(dialog, DialogR.id.add_edit_station_name_edit).setText(name)
            dialogView<EditText>(dialog, DialogR.id.add_edit_station_stream_url_edit).setText(url)
            dialogView<View>(dialog, DialogR.id.add_edit_station_dialog_add_btn_view).performClick()
        }
        awaitStoredStation(name)
    }

    /**
     * Shows the settings dialog for [item] with the arguments `MediaPresenterImpl.handleItemSettings`
     * builds, and asserts it came up offering the two buttons it shows for the locals node alone.
     */
    private fun openStationSettings(
        scenario: ActivityScenario<MainActivity>,
        item: MediaItem
    ): DialogFragment {
        scenario.onActivity { activity ->
            val arguments = Bundle()
            RSSettingsDialog.provideMediaItem(
                arguments, item, MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST, 1
            )
            BaseDialogFragment.newInstance(RSSettingsDialog::class.java.name, arguments)
                .show(activity.supportFragmentManager.beginTransaction(), RSSettingsDialog.DIALOG_TAG)
        }
        val settings = awaitDialog(scenario, RSSettingsDialog.DIALOG_TAG)
        scenario.onActivity {
            assertEquals(
                "The settings dialog for a station of the user's own does not offer edit and remove",
                View.VISIBLE,
                dialogView<View>(settings, DialogR.id.dialog_rs_settings_edit_remove).visibility
            )
        }
        return settings
    }

    /**
     * Clicks one of the settings dialog's two buttons and waits for the dialog it opens.
     *
     * The click goes through the button's `android:onClick`, which Android resolves against the
     * Activity hosting the dialog, so this exercises [MainActivity]'s own handler and the media
     * item the settings dialog left on the button as a tag.
     */
    private fun openFromSettings(
        scenario: ActivityScenario<MainActivity>,
        settings: DialogFragment,
        buttonId: Int,
        opened: String
    ): DialogFragment {
        scenario.onActivity { dialogView<View>(settings, buttonId).performClick() }
        return awaitDialog(scenario, opened)
    }

    /**
     * Waits for the dialog tagged [tag] to be up and to have a view to read.
     *
     * A dialog is shown through a fragment transaction, so it exists some time after the click
     * that asked for it. Which half holds the views depends on the dialog: the add and edit
     * dialogs build a fragment view, while the settings and remove dialogs build an
     * [android.app.AlertDialog] and have none, so both are waited for.
     */
    private fun awaitDialog(
        scenario: ActivityScenario<MainActivity>,
        tag: String
    ): DialogFragment {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DIALOG_TIMEOUT_SECONDS)
        val shown = AtomicReference<DialogFragment?>(null)
        while (System.nanoTime() < deadline) {
            scenario.onActivity { activity ->
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
    private fun <T : View> dialogView(fragment: DialogFragment, id: Int): T {
        val view = fragment.view?.findViewById<T>(id) ?: fragment.dialog?.findViewById<T>(id)
        return view ?: throw AssertionError(
            "The ${fragment.javaClass.simpleName} dialog holds no view for the requested id"
        )
    }

    /**
     * @return the one station the store holds, failing when the add left none or more than one.
     */
    private fun storedStation(): RadioStation {
        val stations = mProfile.storages.freshLocals().getAll()
        assertEquals("The locals store does not hold exactly one station", 1, stations.size)
        return stations.first()
    }

    /**
     * @return the station's media item as the service serves it, which is what a row in the locals
     *   list would be holding and what the settings dialog is given.
     */
    private fun storedMediaItem(): MediaItem {
        val items = mProfile.browser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        assertEquals("The service does not serve exactly one local station", 1, items.size)
        return items.first()
    }

    private fun servedLocalTitles(): List<String> {
        return mProfile.browser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            .map { it.mediaMetadata.title?.toString().orEmpty() }
    }

    /**
     * Waits for a station named [name] to reach the store.
     *
     * Adding and editing both answer through several hops off the main thread, and the dialog
     * closes before the store has been written, so there is nothing on the screen to wait for.
     */
    private fun awaitStoredStation(name: String): RadioStation {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STORE_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val station = mProfile.storages.freshLocals().getAll().firstOrNull { it.name == name }
            if (station != null) {
                return station
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "No station named '$name' reached the store within $STORE_TIMEOUT_SECONDS seconds. " +
                "The store holds ${mProfile.storages.freshLocals().getAll().map { it.name }} and " +
                "the stream server was asked for ${mStreams.requestedPaths()}, which is empty if " +
                "the validator rejected the input before probing it."
        )
    }

    /**
     * Waits for [needle] to appear in a preference file on disk.
     *
     * The stores write with [android.content.SharedPreferences.Editor.apply], so the value is
     * readable from this process before it has been written anywhere a restart could find it.
     * Reading the files rather than the preferences is the difference between the two.
     *
     * @return the name of the file it was found in.
     */
    private fun awaitWrittenToDisk(needle: String): String {
        val directory = File(mContext.applicationInfo.dataDir, PREFERENCE_DIRECTORY)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STORE_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val file = (directory.listFiles() ?: emptyArray())
                .filter { it.name.endsWith(PREFERENCE_FILE_SUFFIX) }
                .firstOrNull { it.readText().contains(needle) }
            if (file != null) {
                return file.name
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "'$needle' did not reach any file in $directory within $STORE_TIMEOUT_SECONDS " +
                "seconds, so nothing a restart reads holds it."
        )
    }

    private fun serveStream(path: String): String {
        return mStreams.serve(path, LoopbackHttpFixture.AUDIO_WAV, STREAM_BODY)
    }

    /**
     * Grants the permission the add and edit dialogs ask for when they resume, so that the request
     * does not put a system dialog over the Activity this journey is reading. Picking an image for
     * a station is not what is being tested, and the dialogs never reach the picker here.
     */
    private fun grantImageReadPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName, permission
        )
        assertTrue(
            "The image read permission could not be granted, so the dialogs will ask for it and " +
                "put a system prompt over the Activity this journey reads",
            PermissionChecker.isExternalStorageGranted(mContext)
        )
    }

    private companion object {

        const val STATION_NAME = "Journey local one"

        const val EDITED_NAME = "Journey local one renamed"

        const val STATION_PATH = "/journey-local-stream"

        const val EDITED_PATH = "/journey-local-stream-edited"

        /**
         * The validator asks for the stream and only looks at the status code, so the body only
         * has to be something the server can measure.
         */
        const val STREAM_BODY = "journey"

        const val LOCALS_PREFERENCE_FILE = "LocalRadioStationsPreferences"

        const val PREFERENCE_DIRECTORY = "shared_prefs"

        const val PREFERENCE_FILE_SUFFIX = ".xml"

        /**
         * Covers a fragment transaction and the dialog's own layout pass.
         */
        const val DIALOG_TIMEOUT_SECONDS = 10L

        /**
         * Covers the validator's probe of the stream, the write, and the answer back to the main
         * thread.
         */
        const val STORE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L
    }
}
