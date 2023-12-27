/*
 * Copyright 2017-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.yuriy.openradio.shared.R
import com.yuriy.openradio.shared.dependencies.CloudStoreManagerDependency
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.model.storage.CloudStoreManager
import com.yuriy.openradio.shared.utils.SafeToast
import com.yuriy.openradio.shared.utils.findImageButton
import com.yuriy.openradio.shared.utils.findLinearLayout
import com.yuriy.openradio.shared.utils.findProgressBar
import com.yuriy.openradio.shared.utils.findTextView
import com.yuriy.openradio.shared.utils.gone
import com.yuriy.openradio.shared.utils.visible

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/20/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class CloudStorageDialog : BaseDialogFragment(), CloudStoreManagerDependency {

    private lateinit var mCloudStoreManager: CloudStoreManager
    private lateinit var mProgress: ProgressBar
    private lateinit var mAccView: LinearLayout
    private lateinit var mAccEmailView: TextView
    private val mAccountDialogDismissedListener = AccountDialogDismissedListenerImpl()

    enum class Command {
        UPLOAD, DOWNLOAD
    }

    override fun configureWith(cloudStoreManager: CloudStoreManager) {
        mCloudStoreManager = cloudStoreManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DependencyRegistryCommonUi.injectCloudStoreManager(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        AccountDialog.dismiss(parentFragmentManager)
        hideProgress()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = inflater.inflate(
            R.layout.dialog_cloud_storage,
            requireActivity().findViewById(R.id.storage_root)
        )
        setWindowDimensions(view, 0.9f, 0.5f)
        val uploadTo = view.findImageButton(R.id.cloud_storage_upload_btn)
        uploadTo.setOnClickListener { uploadRadioStations() }
        val downloadFrom = view.findImageButton(R.id.cloud_storage_download_btn)
        downloadFrom.setOnClickListener { downloadRadioStations() }
        val accSignOut = view.findImageButton(R.id.account_sign_out_btn)
        accSignOut.setOnClickListener { signOut() }
        val accDel = view.findImageButton(R.id.account_del_btn)
        accDel.setOnClickListener { deleteAccount() }
        mProgress = view.findProgressBar(R.id.cloud_storage_progress)
        mAccView = view.findLinearLayout(R.id.account_layout)
        mAccEmailView = view.findTextView(R.id.account_email_text_view)
        hideProgress()
        return createAlertDialog(view)
    }

    override fun onResume() {
        super.onResume()
        if (mCloudStoreManager.isUserExist().not()) {
            AccountDialog.show(parentFragmentManager, mAccountDialogDismissedListener)
        } else {
            showAccLayout()
        }
    }

    private fun uploadRadioStations() {
        if (mCloudStoreManager.isUserExist().not()) {
            AccountDialog.show(parentFragmentManager, mAccountDialogDismissedListener)
        } else {
            showAccLayout()
            handleCommand(Command.UPLOAD)
        }
    }

    private fun downloadRadioStations() {
        if (mCloudStoreManager.isUserExist().not()) {
            AccountDialog.show(parentFragmentManager, mAccountDialogDismissedListener)
        } else {
            showAccLayout()
            handleCommand(Command.DOWNLOAD)
        }
    }

    private fun handleCommand(command: Command) {
        showProgress()
        mCloudStoreManager.getToken(
            {
                when (command) {
                    Command.UPLOAD -> {
                        mCloudStoreManager.upload(
                            it,
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

                    Command.DOWNLOAD -> {
                        mCloudStoreManager.download(
                            it,
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
                }
            },
            {
                hideProgress()
                SafeToast.showAnyThread(context, "Can't get Token")
            }
        )
    }

    private fun signOut() {
        if (mCloudStoreManager.isUserExist()) {
            mCloudStoreManager.signOut()
            hideAccLayout()
        }
    }

    private fun deleteAccount() {
        if (mCloudStoreManager.isUserExist()) {
            mCloudStoreManager.deleteAccount(
                {
                    hideAccLayout()
                    AccountDialog.show(parentFragmentManager, mAccountDialogDismissedListener)
                    showAccDelResultDialog("Your account was deleted.")
                },
                {
                    showAccDelResultDialog("Sorry, your request to delete your account was not successful. Please try again. If the problem persists, please contact me at chernyshov.yuriy@gmail.com.")
                },
                {
                    showAccDelResultDialog("Sorry, your request to delete your account was not successful due to application internal error. Please re-start the application. If the problem persists, please contact me at chernyshov.yuriy@gmail.com.")
                },
                {
                    showAccDelResultDialog("Your session has expired. For your security, please re-enter your sign-in credentials. Thank you for your understanding.")
                }
            )
        }
    }

    private fun showAccDelResultDialog(message: String) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage(message)
        Handler(Looper.getMainLooper()).postDelayed(
            {
                builder.show()
            }, 500
        )
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

    private fun showAccLayout() {
        val activity = activity ?: return
        activity.runOnUiThread {
            mAccView.visible()
            mAccEmailView.text = mCloudStoreManager.getUserEmail()
        }
    }

    private fun hideAccLayout() {
        val activity = activity ?: return
        activity.runOnUiThread {
            mAccView.gone()
        }
    }

    private inner class AccountDialogDismissedListenerImpl : AccountDialog.DialogDismissedListener {

        override fun onDialogDismissed() {
            showAccLayout()
        }
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = CloudStorageDialog::class.java.simpleName

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"
    }
}
