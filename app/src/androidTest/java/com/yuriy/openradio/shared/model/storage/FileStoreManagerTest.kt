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

package com.yuriy.openradio.shared.model.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.ref.WeakReference

/**
 * Covers the data file that carries favorites and device local stations between installs. The
 * exchange normally runs through a Storage Access Framework document; here it runs through an
 * app private file addressed by a file Uri, which the content resolver serves the same way, handed
 * straight to the entry points the activity's launchers call.
 */
@RunWith(AndroidJUnit4::class)
class FileStoreManagerTest {

    private lateinit var mContext: Context
    private lateinit var mFavorites: FavoritesStorage
    private lateinit var mLocals: DeviceLocalsStorage
    private lateinit var mLatest: LatestRadioStationStorage
    private lateinit var mManager: FileStoreManager
    private lateinit var mFile: File

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mFavorites = FavoritesStorage(WeakReference(mContext))
        mLatest = LatestRadioStationStorage(WeakReference(mContext))
        mLocals = DeviceLocalsStorage(WeakReference(mContext), mFavorites, mLatest)
        mManager = FileStoreManager()
        mManager.configureWith(StorageManagerLayerImpl(mFavorites, mLocals))
        mFile = File(mContext.filesDir, FILE_NAME)
        mFile.delete()
        clearAll()
    }

    @After
    fun tearDown() {
        mFile.delete()
        clearAll()
    }

    @Test
    fun exportWritesTheStoresAndImportBringsThemBack() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1), makeStation("b", sortId = 2)))
        mLocals.addAll(setOf(makeStation("local-1", sortId = 1, isLocal = true)))

        assertTrue(mManager.exportTo(mContext, uriOfFile()))
        assertTrue(mFile.length() > 0)

        mFavorites.clear()
        mLocals.clear()

        assertTrue(mManager.importFrom(mContext, uriOfFile()))
        assertEquals(setOf("a", "b"), mFavorites.getAll().map { it.id }.toSet())
        assertEquals(setOf("local-1"), mLocals.getAll().map { it.id }.toSet())
    }

    @Test
    fun importMergesIntoWhatIsAlreadyStored() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))
        assertTrue(mManager.exportTo(mContext, uriOfFile()))

        mFavorites.addAll(setOf(makeStation("b", sortId = 2)))

        assertTrue(mManager.importFrom(mContext, uriOfFile()))
        assertEquals(setOf("a", "b"), mFavorites.getAll().map { it.id }.toSet())
    }

    @Test
    fun importOfAFileWithoutLocalsLeavesTheStoredLocalsAlone() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))
        assertTrue(mManager.exportTo(mContext, uriOfFile()))

        mFavorites.clear()
        mLocals.addAll(setOf(makeStation("local-2", sortId = 1, isLocal = true)))

        assertTrue(mManager.importFrom(mContext, uriOfFile()))
        assertEquals(setOf("a"), mFavorites.getAll().map { it.id }.toSet())
        assertEquals(setOf("local-2"), mLocals.getAll().map { it.id }.toSet())
    }

    @Test
    fun anExportOfEmptyStoresImportsAsANoOp() {
        assertTrue(mManager.exportTo(mContext, uriOfFile()))
        assertTrue(mManager.importFrom(mContext, uriOfFile()))

        assertTrue(mFavorites.getAll().isEmpty())
        assertTrue(mLocals.getAll().isEmpty())
    }

    @Test
    fun aFileThatIsNotAnExportIsAcceptedAndChangesNothing() {
        // A file the user picked by mistake decodes to nothing usable, and the import reports
        // success rather than telling the user that nothing was restored. Pins that behaviour.
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))
        mFile.writeText("!!! this is not an exported data file !!!")

        assertTrue(mManager.importFrom(mContext, uriOfFile()))
        assertEquals(setOf("a"), mFavorites.getAll().map { it.id }.toSet())
    }

    @Test
    fun aTruncatedExportIsAcceptedAndChangesNothing() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))
        mLocals.addAll(setOf(makeStation("local-1", sortId = 1, isLocal = true)))
        assertTrue(mManager.exportTo(mContext, uriOfFile()))
        truncateExportInHalf()

        mFavorites.clear()
        mLocals.clear()

        assertTrue(mManager.importFrom(mContext, uriOfFile()))
        assertTrue(mFavorites.getAll().isEmpty())
        assertTrue(mLocals.getAll().isEmpty())
    }

    @Test
    fun importingAFileThatIsNotThereIsReportedAsAFailure() {
        assertFalse(mManager.importFrom(mContext, uriOfFile()))
    }

    @Test
    fun anOkResultCarriesTheChosenDocument() {
        val document = uriOfFile()

        val result = FileStoreManager.documentOf(Activity.RESULT_OK, Intent().setData(document))

        assertEquals(document, result)
    }

    @Test
    fun aResultThatIsNotOkCarriesNoDocument() {
        val result = FileStoreManager.documentOf(
            Activity.RESULT_CANCELED,
            Intent().setData(uriOfFile())
        )

        assertNull(result)
    }

    @Test
    fun aMissingIntentCarriesNoDocument() {
        assertNull(FileStoreManager.documentOf(Activity.RESULT_OK, null))
    }

    @Test
    fun aResultWithoutAUriCarriesNoDocument() {
        assertNull(FileStoreManager.documentOf(Activity.RESULT_OK, Intent()))
    }

    @Test
    fun anExportToALocationThatCannotBeOpenedIsReportedAsAFailure() {
        // The content resolver throws FileNotFoundException for a document that is gone or cannot
        // be written; the user has to hear about it instead of the app crashing (TASK-026).
        val unreachable = Uri.fromFile(File(mContext.filesDir, "no-such-directory/$FILE_NAME"))

        assertFalse(mManager.exportTo(mContext, unreachable))
    }

    @Test
    fun theExportIntentAsksForAnOpenableTextDocumentNamedAsBefore() {
        val intent = mManager.exportIntent()

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals(setOf(Intent.CATEGORY_OPENABLE), intent.categories)
        assertEquals("text/plain", intent.type)
        assertEquals("openradio_data", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun theImportIntentAsksForAnOpenableTextDocument() {
        val intent = mManager.importIntent()

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals(setOf(Intent.CATEGORY_OPENABLE), intent.categories)
        assertEquals("text/plain", intent.type)
    }

    @Test
    fun anUnknownRequestCodeReportsNothingAtAll() {
        // Goes with the request codes in the last task of this build.
        var reports = 0

        mManager.onActivityResult(
            mContext,
            UNKNOWN_REQUEST_CODE,
            Activity.RESULT_OK,
            Intent().setData(uriOfFile()),
            { reports++ },
            { reports++ }
        )

        assertEquals(0, reports)
    }

    private fun uriOfFile(): Uri {
        return Uri.fromFile(mFile)
    }

    /**
     * Keeps the leading half of the exported payload, rounded down to a whole group of Base64
     * characters so that decoding still succeeds and the object stream inside is the broken part.
     */
    private fun truncateExportInHalf() {
        val encoded = mFile.readText().filterNot { it.isWhitespace() }
        val kept = (encoded.length / 2) / 4 * 4
        assertTrue("The export is too small to truncate", kept > 0)
        mFile.writeText(encoded.substring(0, kept))
    }

    private fun clearAll() {
        mFavorites.clear()
        mLocals.clear()
        mLatest.clear()
    }

    private companion object {

        const val FILE_NAME = "file-store-manager-test.dat"

        const val UNKNOWN_REQUEST_CODE = 4321
    }
}
