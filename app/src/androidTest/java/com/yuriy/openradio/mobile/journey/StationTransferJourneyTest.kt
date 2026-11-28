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
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import com.yuriy.openradio.mobile.view.settings.SettingsTags
import com.yuriy.openradio.shared.model.storage.makeStation
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Export stations and Import stations from Settings, through the activity result launchers, with
 * [DocumentAnswerer] in place of the document UI and an app-private file as the chosen document.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class StationTransferJourneyTest {

    @get:Rule
    val mStack = BrowseStacksRule()

    @get:Rule
    val mCompose = createEmptyComposeRule()

    private lateinit var mContext: Context
    private lateinit var mProfile: JourneyProfile
    private lateinit var mFile: File
    private lateinit var mDocuments: DocumentAnswerer

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mFile = File(mContext.filesDir, FILE_NAME)
        mFile.delete()
        mProfile = JourneyProfile(mContext)
        mProfile.start()
        mDocuments = DocumentAnswerer(Uri.fromFile(mFile))
        mDocuments.install()
    }

    @After
    fun tearDown() {
        mDocuments.remove()
        mFile.delete()
        mProfile.finish()
    }

    @Test
    fun exportedStationsComeBackOnImport() {
        mProfile.storages.favorites.addAll(setOf(makeStation("a", sortId = 1), makeStation("b", sortId = 2)))

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            val settings = SettingsView(mCompose)
            MainScreenView(mCompose).awaitTabs(mProfile.cleanInstallTabs())
            settings.openFromOverflow()

            settings.click(SettingsTags.EXPORT)
            val export = mDocuments.await()
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, export.action)
            assertEquals("text/plain", export.type)
            assertEquals("openradio_data", export.getStringExtra(Intent.EXTRA_TITLE))
            await("the export to be written") { mFile.length() > 0 }
            settings.awaitGone(SettingsTags.PROGRESS)

            for (station in mProfile.storages.favorites.getAll()) {
                mProfile.storages.favorites.remove(station)
            }
            settings.click(SettingsTags.IMPORT)
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, mDocuments.await().action)
            await("the import to restore the favorites") {
                mProfile.storages.freshFavorites().getAll().map { it.id }.toSet() == setOf("a", "b")
            }

            settings.back(MainScreenTags.TABS)
        }
    }

    private fun await(what: String, done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (done()) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("Waited $TIMEOUT_SECONDS seconds for $what")
    }

    private companion object {
        const val FILE_NAME = "station-transfer-journey.dat"
        const val TIMEOUT_SECONDS = 20L
        const val POLL_MILLIS = 50L
    }
}
