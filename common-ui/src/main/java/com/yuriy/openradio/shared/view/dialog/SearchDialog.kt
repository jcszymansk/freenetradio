/*
 * Copyright 2017-2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import com.yuriy.openradio.shared.R
import com.yuriy.openradio.shared.dependencies.BrowseSearchDependency
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommonUi
import com.yuriy.openradio.shared.presenter.main.BrowseSearch
import com.yuriy.openradio.shared.utils.SafeToast
import com.yuriy.openradio.shared.utils.findButton
import com.yuriy.openradio.shared.utils.findEditText

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/20/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
class SearchDialog : BaseDialogFragment(), BrowseSearchDependency {

    private lateinit var mBrowseSearch: BrowseSearch

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        DependencyRegistryCommonUi.injectBrowseSearch(this)
        val view = inflater.inflate(
            R.layout.dialog_search,
            requireActivity().findViewById(R.id.dialog_search_root)
        )
        setWindowDimensions(view, 0.8f, 0.4f)
        val searchEditView = view.findEditText(R.id.search_dialog_edit_txt_view)
        val searchBtn = view.findButton(R.id.search_dialog_btn_view)
        searchBtn.setOnClickListener {

            val query = searchEditView.text.toString().trim()
            if (query.isEmpty()) {
                SafeToast.showAnyThread(context, getString(R.string.empty_search_msg))
                return@setOnClickListener
            }

            mBrowseSearch.search(query)
            dialog?.dismiss()
        }
        return createAlertDialog(view)
    }

    override fun configureWith(browseSearch: BrowseSearch) {
        mBrowseSearch = browseSearch
    }

    companion object {
        /**
         * Tag string to use in logging message.
         */
        private val CLASS_NAME = SearchDialog::class.java.simpleName

        /**
         * Tag string to use in dialog transactions.
         */
        val DIALOG_TAG = CLASS_NAME + "_DIALOG_TAG"
    }
}
