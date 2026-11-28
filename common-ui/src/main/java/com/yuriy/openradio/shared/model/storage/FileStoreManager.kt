/*
 * Copyright 2024 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.StorageManagerDependency
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.IntentUtils

class FileStoreManager : StorageManagerDependency {

    private lateinit var mStorageManagerLayer: StorageManagerLayer

    init {
        DependencyRegistryCommonUi.injectStorageManagerLayer(this)
    }

    override fun configureWith(storageManagerLayer: StorageManagerLayer) {
        mStorageManagerLayer = storageManagerLayer
    }

    fun upload(activity: AppCompatActivity, onFailure: () -> Unit) {
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(activity, exportIntent(), REQUEST_CODE_CREATE_FILE).not()) {
            onFailure()
        }
    }

    fun upload(dialogFragment: DialogFragment, onFailure: () -> Unit) {
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(dialogFragment, exportIntent(), REQUEST_CODE_CREATE_FILE).not()) {
            onFailure()
        }
    }

    fun download(activity: AppCompatActivity, onFailure: () -> Unit) {
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(activity, importIntent(), REQUEST_CODE_OPEN_FILE).not()) {
            onFailure()
        }
    }

    fun download(dialogFragment: DialogFragment, onFailure: () -> Unit) {
        // Even though it is deprecated, Android officially recommends this approach:
        // https://developer.android.com/training/data-storage/shared/documents-files
        // Was what recommended as alternative to deprecation require much more time to implement as it relies
        // on APIs available since Android 13.
        if (IntentUtils.startActivityForResultSafe(dialogFragment, importIntent(), REQUEST_CODE_OPEN_FILE).not()) {
            onFailure()
        }
    }

    fun onActivityResult(
        context: Context,
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val document = documentOf(resultCode, intent)
        when (requestCode) {
            REQUEST_CODE_CREATE_FILE -> {
                if (document != null && exportTo(context, document)) {
                    onSuccess()
                } else {
                    onFailure()
                }
            }

            REQUEST_CODE_OPEN_FILE -> {
                if (document != null && importFrom(context, document)) {
                    onSuccess()
                } else {
                    onFailure()
                }
            }
        }
    }

    /**
     * Asks the document UI where to write the stations file: an openable text document named
     * `openradio_data`.
     */
    fun exportIntent(): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(FILE_TYPE)
            .putExtra(Intent.EXTRA_TITLE, FILE_NAME)
    }

    /**
     * Asks the document UI which stations file to read.
     */
    fun importIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(FILE_TYPE)
    }

    /**
     * Writes favorites and device local stations to the document at [document].
     *
     * The document comes from a picker the user left a while ago, so it may be gone or no longer
     * writable by the time the result arrives; the content resolver throws in that case, and the
     * caller has to report a failure rather than crash.
     *
     * @return true when the whole data file was written.
     */
    fun exportTo(context: Context, document: Uri): Boolean {
        return try {
            val outputStream = context.contentResolver?.openOutputStream(document)
            if (outputStream == null) {
                AppLogger.e("$TAG no output stream for uri: $document")
                return false
            }
            outputStream.use {
                val data = hashMapOf(
                    KEY_FAV to mStorageManagerLayer.getAllFavoritesAsString(),
                    KEY_LOC to mStorageManagerLayer.getAllDeviceLocalsAsString()
                )
                it.write(AppUtils.encodeBase64(AppUtils.serializeMap(data)))
            }
            true
        } catch (e: Exception) {
            AppLogger.e("$TAG error writing data file to uri: $document", e)
            false
        }
    }

    /**
     * Merges the favorites and device local stations of the document at [document] into the
     * stores. A file that decodes to nothing usable changes nothing and still counts as read.
     *
     * @return false when the document could not be opened or read.
     */
    fun importFrom(context: Context, document: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(document)
            if (inputStream == null) {
                AppLogger.e("$TAG no input stream for uri: $document")
                return false
            }
            inputStream.use {
                val fileContent = it.readBytes()
                val data = AppUtils.deserializeMap(AppUtils.decodeBase64(fileContent))
                if (data.isEmpty().not()) {
                    val fav: String? = data[KEY_FAV]
                    if (!fav.isNullOrEmpty()) {
                        mStorageManagerLayer.mergeFavorites(fav)
                    }
                    val loc: String? = data[KEY_LOC]
                    if (!loc.isNullOrEmpty()) {
                        mStorageManagerLayer.mergeDeviceLocals(loc)
                    }
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e("$TAG error reading data file from uri: $document", e)
            false
        }
    }

    companion object {

        private const val TAG = "FSM"
        private const val KEY_FAV = "favorites"
        private const val KEY_LOC = "locals"
        private const val FILE_NAME = "openradio_data"
        private const val FILE_TYPE = "text/plain"

        /**
         * Request code of the "save the data file" activity result.
         */
        const val REQUEST_CODE_CREATE_FILE = 1234

        /**
         * Request code of the "open the data file" activity result.
         */
        const val REQUEST_CODE_OPEN_FILE = 5678

        /**
         * Returns the document an activity result carries, or null when the result is not OK,
         * has no intent or has no URI, which the caller reports as a failure.
         */
        fun documentOf(resultCode: Int, data: Intent?): Uri? {
            if (resultCode != Activity.RESULT_OK) {
                AppLogger.e("$TAG activity result is not OK")
                return null
            }
            val document = data?.data
            if (document == null) {
                AppLogger.e("$TAG activity result carries no document")
            }
            return document
        }
    }
}
