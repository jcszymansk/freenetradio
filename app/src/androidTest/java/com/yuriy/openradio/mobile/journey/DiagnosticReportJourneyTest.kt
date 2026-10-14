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

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.inspector.WindowInspector
import android.widget.Button
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.view.dialog.GeneralSettingsDialog
import com.yuriy.openradio.testing.withActivity
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.yuriy.openradio.shared.R as DialogR

/**
 * The diagnostic report journey (TASK-015): the user asks for a report in the general settings,
 * confirms, and the application hands an `ACTION_SEND` to the Android chooser.
 *
 * The chooser itself is intercepted, because it is another application's screen and the receiving
 * application is the user's choice. What is asserted is everything the receiving application
 * gets: a content Uri from this application's `FileProvider`, the read grant for it, and a report
 * that the Uri actually resolves to. The Uri is read through the content resolver, which runs the
 * provider's path mapping, so a report written somewhere `provider_paths.xml` does not name fails
 * here rather than in the receiving application.
 *
 * Intercepting a start without blocking every other one needs the filterless
 * [Instrumentation.ActivityMonitor] of API 26, and the warning the user confirms is a dialog this
 * application keeps no handle to, so its window is found through the [WindowInspector] of API 29.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class DiagnosticReportJourneyTest {

    @get:Rule
    val mStack = PresenterStackRule()

    private lateinit var mContext: Context

    private lateinit var mProfile: JourneyProfile

    private lateinit var mReports: File

    private lateinit var mLegacyReports: File

    private val mChooser = ChooserMonitor()

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mReports = File(mContext.cacheDir, REPORTS_DIR_NAME)
        mLegacyReports = File(mContext.filesDir, LEGACY_REPORTS_DIR_NAME)
        mReports.deleteRecursively()
        mLegacyReports.deleteRecursively()
        mProfile = JourneyProfile(mContext)
        mProfile.start()
        InstrumentationRegistry.getInstrumentation().addMonitor(mChooser)
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().removeMonitor(mChooser)
        mReports.deleteRecursively()
        mLegacyReports.deleteRecursively()
        mProfile.clearApplicationData()
        mProfile.finish()
    }

    /**
     * The report is shared from the cache directory, through the `FileProvider`, with read access
     * granted, and the Uri the receiving application gets resolves to the collected report.
     */
    @Test
    fun aPreparedReportIsSharedFromTheCacheThroughTheFileProvider() {
        val send = prepareReport()

        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/zip", send.type)
        assertTrue(
            "The receiving application is not granted read access to the report",
            send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        )
        val uri = streamOf(send)
        assertEquals(ContentResolver.SCHEME_CONTENT, uri.scheme)
        assertEquals("${mContext.packageName}.provider", uri.authority)
        assertEquals(REPORTS_DIR_NAME, uri.pathSegments.first())
        assertTrue(
            "The shared report is not a file in the cache directory",
            File(mReports, uri.lastPathSegment!!).isFile
        )

        val (entry, text) = readReport(uri)
        assertEquals(LOG_ENTRY_NAME, entry)
        assertTrue("The report carries no device information", text.contains("Device Information"))
        assertTrue(
            "The report carries no application build information",
            text.contains("Package Name: ${mContext.packageName}")
        )
    }

    /**
     * Preparing a report clears what is old enough to have been read, and leaves a report shared
     * earlier the same day readable for a receiving application that has not opened it yet.
     * Reports the application used to keep in persistent storage go too.
     */
    @Test
    fun preparingAReportKeepsRecentReportsAndDeletesExpiredAndLegacyOnes() {
        assertTrue(mReports.mkdirs())
        val now = System.currentTimeMillis()
        val recent = File(mReports, "diagnostic-report-recent.zip")
        recent.writeBytes(REPORT_BYTES)
        assertTrue(recent.setLastModified(now - TimeUnit.HOURS.toMillis(1)))
        val expired = File(mReports, "diagnostic-report-expired.zip")
        expired.writeBytes(REPORT_BYTES)
        assertTrue(expired.setLastModified(now - TimeUnit.HOURS.toMillis(25)))
        assertTrue(mLegacyReports.mkdirs())
        File(mLegacyReports, "adb_logs.zip").writeBytes(REPORT_BYTES)

        val shared = streamOf(prepareReport())

        assertNotEquals(
            "The report shared this time replaced an earlier one",
            recent.name,
            shared.lastPathSegment
        )
        assertFalse("An expired report was kept", expired.exists())
        assertFalse("Reports in persistent storage were kept", mLegacyReports.exists())
        val earlier = Uri.parse(
            "content://${mContext.packageName}.provider/$REPORTS_DIR_NAME/${recent.name}"
        )
        val read = mContext.contentResolver.openInputStream(earlier)!!.use { it.readBytes() }
        assertTrue(
            "A report shared an hour ago is no longer readable", REPORT_BYTES.contentEquals(read)
        )
    }

    /**
     * Opens the general settings, asks for a report, confirms the warning, and returns the
     * `ACTION_SEND` the chooser was started with.
     */
    private fun prepareReport(): Intent {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val dialogs = JourneyDialogs(scenario)
            BrowseListView(scenario).awaitRows()
            val general = dialogs.openFromDrawer(R.id.nav_general, GeneralSettingsDialog.DIALOG_TAG)
            dialogs.click(general, DialogR.id.send_logs_btn)
            confirmTheWarning(scenario)
            val chooser = mChooser.awaitChooser()
            dialogs.dismiss(GeneralSettingsDialog.DIALOG_TAG)
            @Suppress("DEPRECATION")
            return chooser.getParcelableExtra(Intent.EXTRA_INTENT)
                ?: throw AssertionError("The chooser was started without the intent to share")
        }
    }

    /**
     * Presses the button that confirms the warning about what a report contains.
     */
    private fun confirmTheWarning(scenario: ActivityScenario<MainActivity>) {
        val label = mContext.getString(DialogR.string.diagnostic_report_continue)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WARNING_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            val clicked = scenario.withActivity { _ ->
                WindowInspector.getGlobalWindowViews()
                    .firstNotNullOfOrNull { root ->
                        root.findViewById<Button>(android.R.id.button1)
                            ?.takeIf { it.isShown && it.text.toString() == label }
                    }
                    ?.performClick()
            }
            if (clicked == true) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("The warning did not come up within $WARNING_TIMEOUT_SECONDS seconds")
    }

    @Suppress("DEPRECATION")
    private fun streamOf(send: Intent): Uri {
        return send.getParcelableExtra(Intent.EXTRA_STREAM)
            ?: throw AssertionError("The share carries no report")
    }

    /**
     * @return the name of the report's first entry and its text, read through the content
     *   resolver the way a receiving application reads it.
     */
    private fun readReport(uri: Uri): Pair<String, String> {
        val input = mContext.contentResolver.openInputStream(uri)
            ?: throw AssertionError("The content resolver cannot open $uri")
        return ZipInputStream(input).use { zip ->
            val entry = zip.nextEntry ?: throw AssertionError("The report is an empty archive")
            entry.name to zip.bufferedReader().readText()
        }
    }

    /**
     * Records the chooser the application starts and keeps it from opening, while every other
     * start goes ahead.
     */
    private class ChooserMonitor : Instrumentation.ActivityMonitor() {

        private val mChoosers = LinkedBlockingQueue<Intent>()

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_CHOOSER) {
                return null
            }
            mChoosers.add(intent)
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }

        fun awaitChooser(): Intent {
            return mChoosers.poll(CHOOSER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                ?: throw AssertionError("No chooser was started within $CHOOSER_TIMEOUT_SECONDS seconds")
        }
    }

    private companion object {

        const val REPORTS_DIR_NAME = "diagnostic_reports"

        const val LEGACY_REPORTS_DIR_NAME = "logs"

        const val LOG_ENTRY_NAME = "adb_logs.txt"

        /**
         * Covers dumping logcat, which on a device that has been up for a while is several
         * megabytes.
         */
        const val CHOOSER_TIMEOUT_SECONDS = 30L

        const val WARNING_TIMEOUT_SECONDS = 10L

        const val POLL_MILLIS = 50L

        val REPORT_BYTES = byteArrayOf(1, 2, 3)
    }
}
