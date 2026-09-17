package com.yuriy.openradio.shared.model.storage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.lang.ref.WeakReference

/**
 * Covers the data file that carries favorites and device local stations between installs. The
 * exchange normally runs through a Storage Access Framework document; here it runs through an
 * app private file addressed by a file Uri, which the content resolver serves the same way.
 */
@RunWith(AndroidJUnit4::class)
class FileStoreManagerTest {

    private lateinit var mContext: Context
    private lateinit var mFavorites: FavoritesStorage
    private lateinit var mLocals: DeviceLocalsStorage
    private lateinit var mLatest: LatestRadioStationStorage
    private lateinit var mManager: FileStoreManager
    private lateinit var mFile: File
    private var mSuccesses = 0
    private var mFailures = 0

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

        deliverExport()

        assertEquals(1, mSuccesses)
        assertEquals(0, mFailures)
        assertTrue(mFile.length() > 0)

        mFavorites.clear()
        mLocals.clear()
        deliverImport()

        assertEquals(2, mSuccesses)
        assertEquals(0, mFailures)
        assertEquals(setOf("a", "b"), mFavorites.getAll().map { it.id }.toSet())
        assertEquals(setOf("local-1"), mLocals.getAll().map { it.id }.toSet())
    }

    @Test
    fun importMergesIntoWhatIsAlreadyStored() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))
        deliverExport()

        mFavorites.addAll(setOf(makeStation("b", sortId = 2)))
        deliverImport()

        assertEquals(setOf("a", "b"), mFavorites.getAll().map { it.id }.toSet())
    }

    @Test
    fun importOfAFileWithoutLocalsLeavesTheStoredLocalsAlone() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))
        deliverExport()

        mFavorites.clear()
        mLocals.addAll(setOf(makeStation("local-2", sortId = 1, isLocal = true)))
        deliverImport()

        assertEquals(setOf("a"), mFavorites.getAll().map { it.id }.toSet())
        assertEquals(setOf("local-2"), mLocals.getAll().map { it.id }.toSet())
    }

    @Test
    fun anExportOfEmptyStoresImportsAsANoOp() {
        deliverExport()

        deliverImport()

        assertEquals(2, mSuccesses)
        assertEquals(0, mFailures)
        assertTrue(mFavorites.getAll().isEmpty())
        assertTrue(mLocals.getAll().isEmpty())
    }

    @Test
    fun aFileThatIsNotAnExportIsAcceptedAndChangesNothing() {
        // A file the user picked by mistake decodes to nothing usable, and the import reports
        // success rather than telling the user that nothing was restored. Pins that behaviour.
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))
        mFile.writeText("!!! this is not an exported data file !!!")

        deliverImport()

        assertEquals(1, mSuccesses)
        assertEquals(0, mFailures)
        assertEquals(setOf("a"), mFavorites.getAll().map { it.id }.toSet())
    }

    @Test
    fun aTruncatedExportIsAcceptedAndChangesNothing() {
        mFavorites.addAll(setOf(makeStation("a", sortId = 1)))
        mLocals.addAll(setOf(makeStation("local-1", sortId = 1, isLocal = true)))
        deliverExport()
        truncateExportInHalf()

        mFavorites.clear()
        mLocals.clear()
        deliverImport()

        assertEquals(2, mSuccesses)
        assertEquals(0, mFailures)
        assertTrue(mFavorites.getAll().isEmpty())
        assertTrue(mLocals.getAll().isEmpty())
    }

    @Test
    fun importingAFileThatIsNotThereIsReportedAsAFailure() {
        deliverImport()

        assertEquals(0, mSuccesses)
        assertEquals(1, mFailures)
    }

    @Test
    fun aResultThatIsNotOkIsReportedAsAFailure() {
        deliver(FileStoreManager.REQUEST_CODE_OPEN_FILE, Activity.RESULT_CANCELED, uriOfFile())

        assertEquals(0, mSuccesses)
        assertEquals(1, mFailures)
    }

    @Test
    fun aMissingIntentIsReportedAsAFailure() {
        mManager.onActivityResult(
            mContext, FileStoreManager.REQUEST_CODE_OPEN_FILE, Activity.RESULT_OK, null,
            ::onSuccess, ::onFailure
        )

        assertEquals(0, mSuccesses)
        assertEquals(1, mFailures)
    }

    @Test
    fun aResultWithoutAUriIsReportedAsAFailureForBothDirections() {
        deliver(FileStoreManager.REQUEST_CODE_OPEN_FILE, Activity.RESULT_OK, null)
        deliver(FileStoreManager.REQUEST_CODE_CREATE_FILE, Activity.RESULT_OK, null)

        assertEquals(0, mSuccesses)
        assertEquals(2, mFailures)
    }

    @Test
    fun anUnknownRequestCodeReportsNothingAtAll() {
        deliver(UNKNOWN_REQUEST_CODE, Activity.RESULT_OK, uriOfFile())

        assertEquals(0, mSuccesses)
        assertEquals(0, mFailures)
    }

    @Test
    fun anExportToAnUnwritableLocationCurrentlyThrows() {
        // Pins the defect tracked as TASK-026: the export branch has no error handling, so the
        // failure callback is never reached. Update this test together with the fix.
        val unreachable = Uri.fromFile(File(mContext.filesDir, "no-such-directory/$FILE_NAME"))

        assertThrows(FileNotFoundException::class.java) {
            deliver(FileStoreManager.REQUEST_CODE_CREATE_FILE, Activity.RESULT_OK, unreachable)
        }
        assertEquals(0, mSuccesses)
        assertEquals(0, mFailures)
    }

    private fun deliverExport() {
        deliver(FileStoreManager.REQUEST_CODE_CREATE_FILE, Activity.RESULT_OK, uriOfFile())
    }

    private fun deliverImport() {
        deliver(FileStoreManager.REQUEST_CODE_OPEN_FILE, Activity.RESULT_OK, uriOfFile())
    }

    private fun deliver(requestCode: Int, resultCode: Int, uri: Uri?) {
        val intent = Intent()
        if (uri != null) {
            intent.data = uri
        }
        mManager.onActivityResult(mContext, requestCode, resultCode, intent, ::onSuccess, ::onFailure)
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

    private fun onSuccess() {
        mSuccesses++
    }

    private fun onFailure() {
        mFailures++
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
