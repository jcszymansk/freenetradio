/*
 * Copyright 2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.automotive.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yuriy.openradio.automotive.R
import com.yuriy.openradio.automotive.dependencies.DependencyRegistryAutomotive
import com.yuriy.openradio.shared.dependencies.CloudStoreManagerDependency
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.dependencies.FileStoreManagerDependency
import com.yuriy.openradio.shared.dependencies.LoggingLayerDependency
import com.yuriy.openradio.shared.dependencies.MediaPresenterDependency
import com.yuriy.openradio.shared.dependencies.SourcesLayerDependency
import com.yuriy.openradio.shared.model.logging.LoggingLayer
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.CloudStoreManager
import com.yuriy.openradio.shared.model.storage.FileStoreManager
import com.yuriy.openradio.shared.presenter.MediaPresenter
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioStore
import com.yuriy.openradio.shared.service.location.LocationService
import com.yuriy.openradio.shared.utils.SafeToast
import com.yuriy.openradio.shared.utils.findButton
import com.yuriy.openradio.shared.utils.findCheckBox
import com.yuriy.openradio.shared.utils.findEditText
import com.yuriy.openradio.shared.utils.findImageButton
import com.yuriy.openradio.shared.utils.findLinearLayout
import com.yuriy.openradio.shared.utils.findProgressBar
import com.yuriy.openradio.shared.utils.findSeekBar
import com.yuriy.openradio.shared.utils.findSpinner
import com.yuriy.openradio.shared.utils.findTextView
import com.yuriy.openradio.shared.utils.findToolbar
import com.yuriy.openradio.shared.utils.gone
import com.yuriy.openradio.shared.utils.visible
import com.yuriy.openradio.shared.view.dialog.AccountDialog
import com.yuriy.openradio.shared.view.dialog.CloudStorageDialog
import com.yuriy.openradio.shared.view.dialog.FileStorageDialog
import com.yuriy.openradio.shared.view.dialog.StreamBufferingDialog
import com.yuriy.openradio.shared.view.list.CountriesArrayAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutomotiveSettingsActivity : AppCompatActivity(), MediaPresenterDependency, SourcesLayerDependency,
    LoggingLayerDependency, CloudStoreManagerDependency, FileStoreManagerDependency {

    private lateinit var mMinBuffer: EditText
    private lateinit var mMaxBuffer: EditText
    private lateinit var mPlayBuffer: EditText
    private lateinit var mPlayBufferRebuffer: EditText
    private lateinit var mProgress: ProgressBar
    private lateinit var mMediaPresenter: MediaPresenter
    private lateinit var mPresenter: AutomotiveSettingsActivityPresenter
    private lateinit var mCloudStoreManager: CloudStoreManager
    private lateinit var mFileStoreManager: FileStoreManager
    private lateinit var mSourcesLayer: SourcesLayer
    private lateinit var mLoggingLayer: LoggingLayer
    private lateinit var mAccView: LinearLayout
    private lateinit var mAccEmailView: TextView
    private var mInitSrc: Source? = null
    private var mNewSrc: Source? = null
    private val mAccountDialogDismissedListener = AccountDialogDismissedListenerImpl()

    override fun configureWith(cloudStoreManager: CloudStoreManager) {
        mCloudStoreManager = cloudStoreManager
    }

    override fun configureWith(loggingLayer: LoggingLayer) {
        mLoggingLayer = loggingLayer
    }

    override fun configureWith(mediaPresenter: MediaPresenter) {
        mMediaPresenter = mediaPresenter
    }

    fun configureWith(presenter: AutomotiveSettingsActivityPresenter) {
        mPresenter = presenter
    }

    override fun configureWith(sourcesLayer: SourcesLayer) {
        mSourcesLayer = sourcesLayer
    }

    override fun configureWith(fileStoreManager: FileStoreManager) {
        mFileStoreManager = fileStoreManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.automotive_activity_settings)

        DependencyRegistryCommon.injectSourcesLayer(this)
        DependencyRegistryCommonUi.injectLoggingLayer(this)
        DependencyRegistryCommonUi.inject(this)
        DependencyRegistryCommonUi.injectCloudStoreManager(this)
        DependencyRegistryCommonUi.injectFileStoreManager(this)
        DependencyRegistryAutomotive.inject(this)

        val toolbar = findToolbar(R.id.automotive_settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val groupView = findViewById<RadioGroup>(R.id.sources_radio_group_car)
        handleRadioBtns(applicationContext, groupView) {
            if (it) {
                val view = LayoutInflater
                    .from(applicationContext)
                    .inflate(R.layout.automotive_dialog_restart, null) as LinearLayout
                val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat)
                    .setView(view)
                    .show()
                val cancelBtn = view.findViewById<Button>(R.id.automotive_dialog_restart_cancel_btn)
                cancelBtn.setOnClickListener {
                    dialog.cancel()
                    // Have to finish activity to apply check box selection (no other easy way ...)
                    this.finish()
                }
                val okBtn = view.findViewById<Button>(R.id.automotive_dialog_restart_ok_btn)
                okBtn.setOnClickListener {
                    dialog.cancel()
                    if (mNewSrc != null) {
                        mSourcesLayer.setActiveSource(mNewSrc!!)
                    }
                    // Give time to shared pref. to apply source selection.
                    Handler(Looper.getMainLooper()).postDelayed(
                        {
                            this.finish()
                            Runtime.getRuntime().exit(0)
                        },
                        500
                    )
                }
            }
        }

        val lastKnownRsEnabled = AppPreferencesManager.lastKnownRadioStationEnabled(applicationContext)
        val lastKnownRsEnableCheckView = findCheckBox(
            R.id.automotive_settings_enable_last_known_radio_station_check_view
        )
        lastKnownRsEnableCheckView.isChecked = lastKnownRsEnabled
        lastKnownRsEnableCheckView.setOnClickListener { view1: View ->
            val checked = (view1 as CheckBox).isChecked
            AppPreferencesManager.lastKnownRadioStationEnabled(applicationContext, checked)
        }

        val clearCache = findButton(R.id.automotive_settings_clear_cache_btn)
        clearCache.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                mMediaPresenter.getServiceCommander().sendCommand(OpenRadioService.CMD_CLEAR_CACHE)
            }
        }

        val array = LocationService.getCountries()
        val countryCode = mPresenter.getCountryCode()
        var idx = 0
        for ((i, item) in array.withIndex()) {
            if (item.code == countryCode) {
                idx = i
                break
            }
        }
        val adapter = CountriesArrayAdapter(applicationContext, array)
        val spinner = findSpinner(R.id.automotive_settings_default_country_spinner)
        spinner.adapter = adapter
        spinner.setSelection(idx)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val code = array[position].code
                mMediaPresenter.onLocationChanged(code)
                CoroutineScope(Dispatchers.Main).launch {
                    mMediaPresenter.getServiceCommander().sendCommand(OpenRadioService.CMD_UPDATE_TREE)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Not in ise.
            }
        }

        val playerVolSeek = findSeekBar(R.id.automotive_player_vol_seek_bar)
        playerVolSeek.progress =
            AppPreferencesManager.getMasterVolume(applicationContext, OpenRadioService.MASTER_VOLUME_DEFAULT)
        playerVolSeek.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    // Not in ise.
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    // Not in ise.
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val bundle = OpenRadioStore.makeMasterVolumeChangedBundle(seekBar.progress)
                        mMediaPresenter.getServiceCommander()
                            .sendCommand(OpenRadioService.CMD_MASTER_VOLUME_CHANGED, bundle)
                    }
                }
            }
        )

        val descView = findTextView(R.id.automotive_stream_buffering_desc_view)
        try {
            descView.text = String.format(
                resources.getString(R.string.stream_buffering_descr_automotive),
                resources.getInteger(com.yuriy.openradio.R.integer.min_buffer_val),
                resources.getInteger(com.yuriy.openradio.R.integer.max_buffer_val),
                resources.getInteger(com.yuriy.openradio.R.integer.min_buffer_sec),
                resources.getInteger(com.yuriy.openradio.R.integer.max_buffer_min)
            )
        } catch (e: Exception) {
            /* Ignore */
        }

        mMinBuffer = findEditText(R.id.automotive_min_buffer_edit_view)
        mMaxBuffer = findEditText(R.id.automotive_max_buffer_edit_view)
        mPlayBuffer = findEditText(R.id.automotive_play_buffer_edit_view)
        mPlayBufferRebuffer = findEditText(R.id.automotive_rebuffer_edit_view)
        val restoreBtn = findButton(R.id.automotive_buffering_restore_btn)
        restoreBtn.setOnClickListener {
            StreamBufferingDialog.handleRestoreButton(mMinBuffer, mMaxBuffer, mPlayBuffer, mPlayBufferRebuffer)
        }

        StreamBufferingDialog.handleOnCreate(
            applicationContext,
            mMinBuffer,
            mMaxBuffer,
            mPlayBuffer,
            mPlayBufferRebuffer
        )

        val fileUploadBtn = findImageButton(R.id.automotive_file_storage_upload_btn)
        val fileDownloadBtn = findImageButton(R.id.automotive_file_storage_download_btn)
        val downloadFromCloud = findImageButton(R.id.automotive_cloud_storage_download_btn)
        val accSignOut = findImageButton(R.id.automotive_account_sign_out_btn)
        val accDel = findImageButton(R.id.automotive_account_del_btn)
        mProgress = findProgressBar(R.id.automotive_cloud_storage_progress_view)
        mAccView = findLinearLayout(R.id.automotive_account_layout)
        mAccEmailView = findTextView(R.id.automotive_account_email_text_view)

        fileUploadBtn.setOnClickListener { uploadRadioStationsFile() }
        fileDownloadBtn.setOnClickListener { downloadRadioStationsFile() }
        downloadFromCloud.setOnClickListener { downloadRadioStationsCloud() }
        accSignOut.setOnClickListener { signOut() }
        accDel.setOnClickListener { deleteAccount() }

        hideProgress()

        val sendLogsProgress = findViewById<ProgressBar>(R.id.automotive_logs_progress)
        val sendLogs = findViewById<Button>(R.id.automotive_logs_btn)
        sendLogs.setOnClickListener {
            sendLogsProgress.visible()
            mLoggingLayer.collectAdbLogs(
                {
                    mLoggingLayer.sendLogsViaEmail(
                        it,
                        {
                            runOnUiThread {
                                sendLogsProgress.gone()
                                SafeToast.showAnyThread(
                                    applicationContext,
                                    getString(com.yuriy.openradio.shared.R.string.success)
                                )
                            }
                        },
                        {
                            runOnUiThread { sendLogsProgress.gone() }
                            SafeToast.showAnyThread(
                                applicationContext,
                                getString(com.yuriy.openradio.shared.R.string.failure)
                            )
                        }
                    )
                },
                {
                    runOnUiThread { sendLogsProgress.gone() }
                    SafeToast.showAnyThread(applicationContext, getString(com.yuriy.openradio.shared.R.string.failure))
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (mCloudStoreManager.isUserExist()) {
            showAccLayout()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AccountDialog.dismiss(supportFragmentManager)
        hideProgress()
        // In case a user selected a new source but did not restart.
        if (mInitSrc != null && mInitSrc != mNewSrc) {
            mSourcesLayer.setActiveSource(mInitSrc!!)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onPause() {
        super.onPause()
        StreamBufferingDialog.handleOnPause(
            applicationContext,
            mMinBuffer,
            mMaxBuffer,
            mPlayBuffer,
            mPlayBufferRebuffer
        )
    }

    private fun downloadRadioStationsCloud() {
        if (mCloudStoreManager.isUserExist().not()) {
            AccountDialog.show(supportFragmentManager, mAccountDialogDismissedListener)
        } else {
            showAccLayout()
            handleCloudCommand(CloudStorageDialog.Command.DOWNLOAD)
        }
    }

    private fun downloadRadioStationsFile() {
        handleFileCommand(FileStorageDialog.Command.DOWNLOAD)
    }

    private fun uploadRadioStationsFile() {
        handleFileCommand(FileStorageDialog.Command.UPLOAD)
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
                    AccountDialog.show(supportFragmentManager, mAccountDialogDismissedListener)
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
        val builder = AlertDialog.Builder(this)
        builder.setMessage(message)
        Handler(Looper.getMainLooper()).postDelayed(
            {
                builder.show()
            }, 500
        )
    }

    private fun showAccLayout() {
        runOnUiThread {
            mAccView.visible()
            mAccEmailView.text = mCloudStoreManager.getUserEmail()
        }
    }

    private fun hideAccLayout() {
        runOnUiThread {
            mAccView.gone()
        }
    }

    private fun showProgress() {
        mProgress.visible()
    }

    private fun hideProgress() {
        mProgress.gone()
    }

    private fun handleFileCommand(command: FileStorageDialog.Command) {
        showProgress()
        when (command) {
            FileStorageDialog.Command.UPLOAD -> {
                mFileStoreManager.upload(this) {
                    hideProgress()
                    SafeToast.showAnyThread(
                        applicationContext, getString(com.yuriy.openradio.shared.R.string.failure)
                    )
                }
            }
            FileStorageDialog.Command.DOWNLOAD -> {
                mFileStoreManager.download(this) {
                    hideProgress()
                    SafeToast.showAnyThread(
                        applicationContext, getString(com.yuriy.openradio.shared.R.string.failure)
                    )
                }
            }
        }
    }

    private fun handleCloudCommand(command: CloudStorageDialog.Command) {
        showProgress()
        mCloudStoreManager.getToken(
            {
                when (command) {
                    CloudStorageDialog.Command.DOWNLOAD -> {
                        mCloudStoreManager.download(
                            it,
                            {
                                hideProgress()
                                SafeToast.showAnyThread(
                                    applicationContext, getString(com.yuriy.openradio.shared.R.string.success)
                                )
                            },
                            {
                                hideProgress()
                                SafeToast.showAnyThread(
                                    applicationContext, getString(com.yuriy.openradio.shared.R.string.failure)
                                )
                            }
                        )
                    }

                    else -> {
                        // Ignore
                    }
                }
            },
            {
                hideProgress()
                SafeToast.showAnyThread(applicationContext, "Can't get Token")
            }
        )
    }

    @SuppressLint("InflateParams")
    private fun handleRadioBtns(
        context: Context,
        view: LinearLayout,
        onSelectChanged: (isSrcChanged: Boolean) -> Unit
    ) {
        mInitSrc = mSourcesLayer.getActiveSource()
        val sources = mSourcesLayer.getAllSources()
        for (source in sources) {
            val child = LayoutInflater.from(context).inflate(R.layout.automotive_source_view, null) as RadioButton
            child.text = source.srcName
            child.id = source.srcId
            child.tag = source.srcId
            if (source == mSourcesLayer.getActiveSource()) {
                child.isChecked = true
            }
            child.setOnCheckedChangeListener { buttonView, isChecked ->
                run {
                    val src = sources.elementAt(buttonView.id)
                    if (isChecked) {
                        mNewSrc = src
                    }
                    onSelectChanged(mInitSrc == src)
                }
            }
            view.addView(child)
        }
    }

    private class AccountDialogDismissedListenerImpl : AccountDialog.DialogDismissedListener {

        override fun onDialogDismissed() {
            // Do nothing.
        }
    }
}
