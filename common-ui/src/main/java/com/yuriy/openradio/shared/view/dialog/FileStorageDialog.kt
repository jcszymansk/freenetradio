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

package com.yuriy.openradio.shared.view.dialog

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import com.yuriy.openradio.shared.R
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.FileStoreManagerDependency
import com.yuriy.openradio.shared.model.storage.FileStoreManager
import com.yuriy.openradio.shared.utils.SafeToast
import com.yuriy.openradio.shared.utils.findImageButton
import com.yuriy.openradio.shared.utils.findProgressBar
import com.yuriy.openradio.shared.utils.gone
import com.yuriy.openradio.shared.utils.visible

/**
 *
 */
class FileStorageDialog : BaseDialogFragment(), FileStoreManagerDependency {

    private lateinit var mFileStoreManager: FileStoreManager
    private lateinit var mProgress: ProgressBar

    enum class Command {
        UPLOAD, DOWNLOAD
    }

    override fun configureWith(fileStoreManager: FileStoreManager) {
        mFileStoreManager = fileStoreManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DependencyRegistryCommonUi.injectFileStoreManager(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideProgress()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = inflater.inflate(
            R.layout.dialog_file_storage,
            requireActivity().findViewById(R.id.file_root)
        )
        setWindowDimensions(view, 0.9f, 0.5f)
        val download = view.findImageButton(R.id.file_storage_download_btn)
        download.setOnClickListener {
            handleCommand(Command.DOWNLOAD)
        }
        val upload = view.findImageButton(R.id.file_storage_upload_btn)
        upload.setOnClickListener {
            handleCommand(Command.UPLOAD)
        }
        mProgress = view.findProgressBar(R.id.file_storage_progress)
        hideProgress()
        return createAlertDialog(view)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        mFileStoreManager.onActivityResult(
            requireContext().applicationContext,
            requestCode,
            resultCode,
            data,
            {
                hideProgress()
                SafeToast.showAnyThread(
                    context, getString(R.string.success)
                )
            },
            {
                hideProgress()
                SafeToast.showAnyThread(
                    context, getString(R.string.failure)
                )
            }
        )
    }

    private fun handleCommand(command: Command) {
        showProgress()
        when (command) {
            Command.UPLOAD -> {
                mFileStoreManager.upload(this) {
                    hideProgress()
                    SafeToast.showAnyThread(
                        context, getString(R.string.failure)
                    )
                }
            }

            Command.DOWNLOAD -> {
                mFileStoreManager.download(this) {
                    hideProgress()
                    SafeToast.showAnyThread(
                        context, getString(R.string.failure)
                    )
                }
            }
        }
    }

    private fun showProgress() {
        val activity = activity ?: return
        activity.runOnUiThread {
            mProgress.visible()
        }
    }

    private fun hideProgress() {
        val activity = activity ?: return
        activity.runOnUiThread {
            mProgress.gone()
        }
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = FileStorageDialog::class.java.simpleName

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"
    }
}
