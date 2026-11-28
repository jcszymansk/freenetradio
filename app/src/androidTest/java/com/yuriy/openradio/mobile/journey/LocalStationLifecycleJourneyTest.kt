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

import android.content.Context
import android.view.View
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.fragment.app.DialogFragment
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.storage.makeStation
import com.yuriy.openradio.shared.permission.grantImageReadPermission
import com.yuriy.openradio.shared.presenter.main.Tab
import com.yuriy.openradio.shared.service.LoopbackHttpFixture
import com.yuriy.openradio.shared.view.dialog.AddStationDialog
import com.yuriy.openradio.shared.view.dialog.EditStationDialog
import com.yuriy.openradio.shared.view.dialog.RSSettingsDialog
import com.yuriy.openradio.shared.view.dialog.RemoveStationDialog
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
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
 * Every gesture is the user's own. MINE is a tab, which opens without a network; the add dialog
 * is behind MINE's FAB; and a long-press on a station's row on MINE opens the settings dialog,
 * whose locals-only edit and remove buttons open the real edit and remove dialogs. The long-press
 * hands the dialog the item and the node from `MainScreenModel.longPressTarget`, which answers
 * only on FAV and MINE; that it answers nothing elsewhere is `MainScreenModelTest`'s
 * `longPressActsOnlyOnFavAndMine`, because no other tab has rows offline.
 *
 * What the service answers for the locals node is `OpenRadioServiceBrowseTest`'s subject. What is
 * asserted here is what the Activity renders, and the store as a reader that has cached nothing
 * sees it.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class LocalStationLifecycleJourneyTest {

    @get:Rule
    val mStack = BrowseStacksRule()

    @get:Rule
    val mCompose = createEmptyComposeRule()

    private lateinit var mContext: Context

    private lateinit var mProfile: JourneyProfile

    private lateinit var mStreams: LoopbackHttpFixture

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        grantImageReadPermission(mContext)
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
     * The first half of the lifecycle: the FAB on an empty MINE, the dialog behind it, and the row
     * MINE grows.
     *
     * Nothing between the dialog closing and the row appearing is done by the test. The dialog
     * sends `CMD_UPDATE_TREE`, the service drops its cached locals list and notifies, and the
     * screen's subscription reads MINE again. Asserting MINE empty first is what makes the wait
     * for the row mean something.
     */
    @Test
    fun addingAStationThroughTheDialogPutsItOnMine() {
        val url = serveStream(STATION_PATH)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            try {
                awaitEmptyMine(view, navigation)

                addStationThroughTheDialog(scenario, view, STATION_NAME, url)

                awaitTheStationOnMine(view, STATION_NAME, "Adding a station did not put it on MINE")
            } finally {
                navigation.returnToRoots()
            }
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
     *
     * MINE has to show the new name, which is the refresh the dialog asked for arriving.
     */
    @Test
    fun editingAStationThroughTheDialogRewritesWhatIsStored() {
        val url = serveStream(STATION_PATH)
        val editedUrl = serveStream(EDITED_PATH)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val dialogs = JourneyDialogs(scenario)
            val navigation = JourneyNavigation(scenario, view)
            try {
                awaitEmptyMine(view, navigation)
                addStationThroughTheDialog(scenario, view, STATION_NAME, url)
                val station = awaitTheStationOnMine(view, STATION_NAME, "Adding a station did not put it on MINE")

                val settings = openStationSettings(scenario, view, station)
                val edit = openFromSettings(
                    scenario, settings, DialogR.id.dialog_rs_settings_edit_btn, EditStationDialog.DIALOG_TAG
                )

                assertEquals(
                    "The edit dialog did not load the station it was opened for",
                    STATION_NAME,
                    dialogs.text(edit, DialogR.id.add_edit_station_name_edit)
                )
                assertEquals(
                    "The edit dialog did not load the station's stream url",
                    url,
                    dialogs.text(edit, DialogR.id.add_edit_station_stream_url_edit)
                )
                dialogs.type(edit, DialogR.id.add_edit_station_name_edit, EDITED_NAME)
                dialogs.type(edit, DialogR.id.add_edit_station_stream_url_edit, editedUrl)
                dialogs.click(edit, DialogR.id.add_edit_station_dialog_add_btn_view)
                view.awaitRows("the station's row under its new name") {
                    it == listOf(BrowseRow(station.id, EDITED_NAME))
                }
            } finally {
                navigation.returnToRoots()
            }
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
    }

    /**
     * Removing the only station the user has empties MINE, and the empty MINE offers the FAB
     * again, which is the way back to adding one.
     */
    @Test
    fun removingTheLastStationEmptiesMine() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val dialogs = JourneyDialogs(scenario)
            val navigation = JourneyNavigation(scenario, view)
            try {
                awaitEmptyMine(view, navigation)
                addStationThroughTheDialog(scenario, view, STATION_NAME, serveStream(STATION_PATH))
                val station = awaitTheStationOnMine(view, STATION_NAME, "Adding a station did not put it on MINE")

                val settings = openStationSettings(scenario, view, station)
                val remove = openFromSettings(
                    scenario, settings, DialogR.id.dialog_rs_settings_remove_btn, RemoveStationDialog.DIALOG_TAG
                )

                assertEquals(
                    "The confirmation does not say which station is about to go",
                    mContext.getString(
                        DialogR.string.remove_station_dialog_main_text, STATION_NAME
                    ),
                    dialogs.text(remove, DialogR.id.remove_station_text_view)
                )
                dialogs.click(remove, DialogR.id.remove_station_dialog_add_btn_view)

                view.awaitEmptyLine(mContext.getString(R.string.main_empty_stations))
                assertTrue("The emptied MINE offers no FAB. " + view.describe(), view.isShown(MainScreenTags.FAB_ADD))
            } finally {
                navigation.returnToRoots()
            }
        }

        assertTrue(
            "MINE is empty but the station is still in the store",
            mProfile.storages.freshLocals().getAll().isEmpty()
        )
    }

    /**
     * Switching tabs while a refresh of MINE is still in flight has to leave the user on the tab
     * they switched to, showing that tab's content.
     *
     * A refresh makes the service push a change for the locals node, and the screen answers it by
     * fetching MINE's children again. The case switches to FAV as soon as the service has
     * acknowledged the refresh, so that fetch is still running when FAV is shown. The store holds
     * a page of stations so that the locals answer, which carries every one of them, is slow
     * enough to arrive after the switch. An answer for MINE rendered on FAV would put the locals
     * rows under the FAV tab; FAV holds no favorites, so any row on it is that answer.
     */
    @Test
    fun switchingTabsWhileMineRefreshesKeepsTheOtherTab() {
        val locals = mProfile.storages.locals
        repeat(LOCALS_PAGE) { index ->
            locals.add(makeStation(locals.getId(), name = "$BULK_NAME $index", isLocal = true))
        }
        mProfile.refreshTree()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                navigation.selectTab(Tab.MINE)
                view.awaitRows("the bulk stations on MINE") { rows -> rows.all { it.title.startsWith(BULK_NAME) } }
                locals.add(makeStation(locals.getId(), name = "$BULK_NAME extra", isLocal = true))
                mProfile.refreshTree()

                navigation.selectTab(Tab.FAV)

                view.awaitEmptyLine(mContext.getString(R.string.main_empty_favorites))
                view.assertRowsStay(emptyList(), "A MINE refresh that was in flight during the switch filled FAV")
                view.awaitEmptyLine(mContext.getString(R.string.main_empty_favorites))
                assertEquals(
                    "A MINE refresh that was in flight during the switch moved the user off FAV",
                    Tab.FAV,
                    navigation.snapshot().selectedTab
                )
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    /**
     * The station has to outlive the screen that created it.
     *
     * Three things a restart depends on are asserted, each defeating a different way the station
     * could appear to survive without having been stored: it reached a preference file on disk
     * rather than only the copy Android keeps in memory for this process; a second Activity
     * renders it on MINE after the service's cached locals list has been dropped, so the list it
     * shows was built from the store rather than replayed; and a
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
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            try {
                awaitEmptyMine(view, navigation)
                addStationThroughTheDialog(scenario, view, STATION_NAME, serveStream(STATION_PATH))
                awaitTheStationOnMine(view, STATION_NAME, "Adding a station did not put it on MINE")
            } finally {
                navigation.returnToRoots()
            }
        }

        mProfile.awaitStoredOnDisk(LOCALS_PREFERENCE_FILE, STATION_NAME)

        // The service caches the locals list, so a second Activity would otherwise be handed the
        // list the first one built and this would assert nothing about storage. Dropping it makes
        // the relaunch rebuild MINE through MediaItemLocalsList, which reads the locals store.
        mProfile.refreshTree()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                navigation.selectTab(Tab.MINE)
                awaitTheStationOnMine(
                    view, STATION_NAME, "A relaunched Activity does not show the station the previous one added"
                )
            } finally {
                navigation.returnToRoots()
            }
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
     * The user's own stations are within reach without a network.
     *
     * `MediaItemLocalsList` reads `DeviceLocalsStorage` and nothing else, so MINE needs no
     * connection, and it is a tab, which the connectivity gate does not stand in front of: only a
     * row tap goes through `MainScreenModel.open`. The old screen offered the locals as a row on
     * the root list and refused to open it offline, which is what TASK-049 was filed for. The tab
     * is asserted to stand at its root, so MINE's own list is what answered.
     */
    @Test
    fun mineOpensWithoutANetwork() {
        val station = seedStation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                assertEquals(
                    "MINE did not open without a network",
                    listOf(BrowseRow(station.id, STATION_NAME)),
                    mProfile.awaitSeededMine(view, station)
                )
                assertEquals(
                    "Opening MINE pushed a node onto it",
                    listOf(Tab.MINE.rootId),
                    navigation.snapshot().stacks.getValue(Tab.MINE)
                )
                assertTrue("MINE offers no FAB. " + view.describe(), view.isShown(MainScreenTags.FAB_ADD))
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    /**
     * A long-press acts on FAV as well as on MINE (REQ-007), and on FAV it offers what a favorite
     * can be given and nothing more: the settings dialog comes up without the edit and remove
     * buttons, which only a station of the user's own has, wherever it is listed.
     *
     * The station is marked through its heart rather than written into the store, so FAV is read
     * from what the service was told rather than from a list it cached before.
     */
    @Test
    fun longPressOnAFavRowOpensTheStationSettings() {
        val station = seedStation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val view = MainScreenView(mCompose)
            val dialogs = JourneyDialogs(scenario)
            val navigation = JourneyNavigation(scenario, view)
            view.awaitTabs(mProfile.cleanInstallTabs())
            try {
                mProfile.awaitSeededMine(view, station)
                view.tapFavorite(station.id)
                mProfile.awaitFavorites(listOf(station.id))
                navigation.selectTab(Tab.FAV)
                view.awaitRows("the marked station on FAV") { it == listOf(BrowseRow(station.id, STATION_NAME)) }

                view.longPressRow(station.id)

                val settings = dialogs.awaitDialog(RSSettingsDialog.DIALOG_TAG)
                assertEquals(
                    "The settings dialog for a favorite offers edit and remove, which only MINE may",
                    View.GONE,
                    dialogs.read<View, Int>(settings, DialogR.id.dialog_rs_settings_edit_remove) { it.visibility }
                )
                dialogs.dismiss(RSSettingsDialog.DIALOG_TAG)
            } finally {
                navigation.returnToRoots()
            }
        }
    }

    /**
     * Selects MINE and waits for it to say it holds nothing, which is where every case that adds
     * a station starts. The FAB is MINE's own control, so it has to be up as well.
     */
    private fun awaitEmptyMine(view: MainScreenView, navigation: JourneyNavigation) {
        view.awaitTabs(mProfile.cleanInstallTabs())
        navigation.selectTab(Tab.MINE)
        view.awaitEmptyLine(mContext.getString(R.string.main_empty_stations))
        assertTrue("An empty MINE offers no FAB. " + view.describe(), view.isShown(MainScreenTags.FAB_ADD))
    }

    /**
     * Fills in and submits the real add dialog, reached through MINE's FAB.
     *
     * Only the name and the url are typed. The home page is left empty on purpose: a home page the
     * validator cannot reach is reported to the user as a failed add even though the station was
     * stored, which is TASK-038, and this journey is not the place to pin that down.
     */
    private fun addStationThroughTheDialog(
        scenario: ActivityScenario<MainActivity>,
        view: MainScreenView,
        name: String,
        url: String
    ) {
        val dialogs = JourneyDialogs(scenario)
        view.tap(MainScreenTags.FAB_ADD)
        val dialog = dialogs.awaitDialog(AddStationDialog.DIALOG_TAG)
        dialogs.type(dialog, DialogR.id.add_edit_station_name_edit, name)
        dialogs.type(dialog, DialogR.id.add_edit_station_stream_url_edit, url)
        dialogs.click(dialog, DialogR.id.add_edit_station_dialog_add_btn_view)
        awaitStoredStation(name)
    }

    /**
     * Waits for MINE to list exactly the one station named [name], and checks it is the stored
     * one.
     *
     * @return the stored station.
     */
    private fun awaitTheStationOnMine(view: MainScreenView, name: String, reason: String): RadioStation {
        val rows = view.awaitRows("'$name' on MINE") { rows -> rows.any { it.title == name } }
        val station = storedStation()
        assertEquals(reason, listOf(BrowseRow(station.id, name)), rows)
        return station
    }

    /**
     * Long-presses [station]'s row on MINE, which is how a user reaches editing and removing, and
     * waits for the settings dialog to offer both.
     */
    private fun openStationSettings(
        scenario: ActivityScenario<MainActivity>,
        view: MainScreenView,
        station: RadioStation
    ): DialogFragment {
        view.longPressRow(station.id)
        val dialogs = JourneyDialogs(scenario)
        val settings = dialogs.awaitDialog(RSSettingsDialog.DIALOG_TAG)
        assertEquals(
            "The settings dialog for a station of the user's own does not offer edit and remove",
            View.VISIBLE,
            dialogs.read<View, Int>(settings, DialogR.id.dialog_rs_settings_edit_remove) {
                it.visibility
            }
        )
        return settings
    }

    /**
     * Clicks one of the settings dialog's two buttons and waits for the dialog it opens.
     *
     * The click goes through the listener the settings dialog put on the button, with the media
     * item the long-press handed the dialog.
     */
    private fun openFromSettings(
        scenario: ActivityScenario<MainActivity>,
        settings: DialogFragment,
        buttonId: Int,
        opened: String
    ): DialogFragment {
        val dialogs = JourneyDialogs(scenario)
        dialogs.click(settings, buttonId)
        return dialogs.awaitDialog(opened)
    }

    /**
     * Writes one station of the user's own into the store and drops the service's cached locals
     * list, for a case whose subject is not adding it.
     */
    private fun seedStation(): RadioStation {
        val locals = mProfile.storages.locals
        val station = makeStation(locals.getId(), name = STATION_NAME, isLocal = true)
        locals.add(station)
        mProfile.refreshTree()
        return station
    }

    /**
     * @return the one station the store holds, failing when the add left none or more than one.
     */
    private fun storedStation(): RadioStation {
        val stations = mProfile.storages.freshLocals().getAll()
        assertEquals("The locals store does not hold exactly one station", 1, stations.size)
        return stations.first()
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

    private fun serveStream(path: String): String {
        return mStreams.serve(path, LoopbackHttpFixture.AUDIO_WAV, STREAM_BODY)
    }

    private companion object {

        const val STATION_NAME = "Journey local one"

        const val EDITED_NAME = "Journey local one renamed"

        const val BULK_NAME = "Bulk local"

        const val STATION_PATH = "/journey-local-stream"

        const val EDITED_PATH = "/journey-local-stream-edited"

        /**
         * The validator asks for the stream and only looks at the status code, so the body only
         * has to be something the server can measure.
         */
        const val STREAM_BODY = "journey"

        const val LOCALS_PREFERENCE_FILE = "LocalRadioStationsPreferences"

        /**
         * Covers the validator's probe of the stream, the write, and the answer back to the main
         * thread.
         */
        const val STORE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L

        /**
         * One full page of the locals list, the most a single answer carries. The more rows the
         * locals answer carries, the longer it takes to arrive after the switch.
         */
        const val LOCALS_PAGE = DependencyRegistryCommon.PAGE_SIZE
    }
}
