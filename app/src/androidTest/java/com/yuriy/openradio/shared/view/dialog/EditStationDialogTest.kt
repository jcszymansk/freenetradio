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

package com.yuriy.openradio.shared.view.dialog

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.R
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.makeStation
import com.yuriy.openradio.shared.permission.grantImageReadPermission
import com.yuriy.openradio.shared.service.location.LocationService
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

/**
 * What the edit dialog puts on the screen for the station it was opened for.
 *
 * This is the edit half of the regression for 13cc159: the dialog collected a home page and then
 * never showed the stored one back, so every edit of a station silently blanked the field the user
 * had filled in. The `LocalStationLifecycleJourneyTest` drives the same dialog through the real
 * taps that reach it, but the station it creates has no home page - the add dialog there cannot
 * type one without running into TASK-038 - so nothing in that journey can see this.
 *
 * The dialog is shown directly rather than reached through the settings dialog because what is
 * under test is the load, and the real `EditStationPresenterImpl` and the real
 * [DeviceLocalsStorage] behind it are the only collaborators that load anything. The bundle is the
 * one `MediaPresenterImpl` builds.
 */
@RunWith(AndroidJUnit4::class)
class EditStationDialogTest {

    private lateinit var mContext: Context

    private lateinit var mFavorites: FavoritesStorage

    private lateinit var mLocals: DeviceLocalsStorage

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        grantImageReadPermission(mContext)
        val contextRef = WeakReference(mContext)
        mFavorites = FavoritesStorage(contextRef)
        mLocals = DeviceLocalsStorage(contextRef, mFavorites, LatestRadioStationStorage(contextRef))
        mLocals.clear()
        mFavorites.clear()
    }

    @After
    fun tearDown() {
        mLocals.clear()
        mFavorites.clear()
    }

    /**
     * Every field the dialog loads is asserted, not the home page alone: a dialog that showed
     * nothing at all would otherwise read as a home page that came through.
     */
    @Test
    fun theDialogShowsTheStoredStation() {
        mLocals.add(
            makeStation(
                STATION_ID,
                name = STATION_NAME,
                url = STREAM_URL,
                homePage = HOME_PAGE,
                genre = genre(),
                country = country(),
                isLocal = true
            )
        )

        openDialogFor(STATION_ID) { dialog ->
            assertEquals(
                "The edit dialog did not show the stored home page",
                HOME_PAGE,
                text(dialog, R.id.add_edit_station_home_page_edit)
            )
            assertEquals(
                "The edit dialog did not show the stored name",
                STATION_NAME,
                text(dialog, R.id.add_edit_station_name_edit)
            )
            assertEquals(
                "The edit dialog did not show the stored stream url",
                STREAM_URL,
                text(dialog, R.id.add_edit_station_stream_url_edit)
            )
            assertEquals(
                "The edit dialog did not select the stored genre",
                genre(),
                selection(dialog, R.id.add_station_genre_spin)
            )
            assertEquals(
                "The edit dialog did not select the stored country",
                country(),
                selection(dialog, R.id.add_edit_station_country_spin)
            )
            assertTrue(
                "The edit dialog cannot submit the station it loaded",
                field<View>(dialog, R.id.add_edit_station_dialog_add_btn_view).isEnabled
            )
        }
    }

    /**
     * A station that is no longer in the store, which is what a stale media id looks like from
     * here. The dialog has nothing to edit, so it must not offer to submit: doing so would write a
     * station built entirely out of the empty fields it is showing.
     */
    @Test
    fun theDialogRefusesAStationItCannotFind() {
        openDialogFor(STATION_ID) { dialog ->
            assertEquals(
                "The edit dialog showed a home page for a station that is not stored",
                "",
                text(dialog, R.id.add_edit_station_home_page_edit)
            )
            assertFalse(
                "The edit dialog offers to submit a station it never loaded",
                field<View>(dialog, R.id.add_edit_station_dialog_add_btn_view).isEnabled
            )
        }
    }

    /**
     * Shows the dialog for [mediaId] over a real Activity and hands it to [assertions], then takes
     * it down again so the next test class does not inherit it.
     */
    private fun openDialogFor(mediaId: String, assertions: (EditStationDialog) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = EditStationDialog()
                dialog.arguments = EditStationDialog.makeBundle(mediaId)
                dialog.showNow(activity.supportFragmentManager, EditStationDialog.DIALOG_TAG)
                try {
                    assertions(dialog)
                } finally {
                    dialog.dismiss()
                    activity.supportFragmentManager.executePendingTransactions()
                }
            }
        }
    }

    private fun <T : View> field(dialog: EditStationDialog, id: Int): T {
        return dialog.requireView().findViewById(id)
    }

    private fun text(dialog: EditStationDialog, id: Int): String {
        return field<EditText>(dialog, id).text.toString()
    }

    private fun selection(dialog: EditStationDialog, id: Int): String {
        return field<Spinner>(dialog, id).selectedItem.toString()
    }

    /**
     * The spinners only hold what the dialog builds them from, so the seeded station has to name a
     * genre and a country the adapters know or the selection would say nothing.
     */
    private fun genre() = AppUtils.predefinedCategories().first()

    private fun country() = LocationService.COUNTRY_CODE_TO_NAME.values.sorted().first()

    private companion object {

        /**
         * Well clear of the ids [DeviceLocalsStorage.getId] hands out itself, and of the ones
         * `LocalStationsFixture` seeds, because the browse tree keeps entries keyed by a station
         * id that no browse invalidates.
         */
        const val STATION_ID = "1950000001"

        const val STATION_NAME = "Edited Station Fixture"

        const val STREAM_URL = "$UNREACHABLE_ORIGIN/stream.mp3"

        const val HOME_PAGE = "$UNREACHABLE_ORIGIN/home"
    }
}
