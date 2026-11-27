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

package com.yuriy.openradio.mobile.view.activity

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.utils.AppLogger

/**
 * What Now playing needs from Android that the model must not touch: the clipboard, the share
 * chooser and the browser. Each is started only by the user's tap and carries only the station's
 * stream URL and, for a share, its name.
 */
internal class NowPlayingPlatform(private val mActivity: AppCompatActivity) {

    fun copyStreamUrl(url: String) {
        val clipboard = mActivity.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(mActivity.getString(R.string.now_playing_url_clip_label), url))
        if (showsCopiedToast(Build.VERSION.SDK_INT)) {
            Toast.makeText(mActivity, R.string.now_playing_url_copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareStreamUrl(name: String, url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TEXT
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, name)
        }
        mActivity.startActivity(Intent.createChooser(send, mActivity.getString(R.string.now_playing_menu_share_url)))
    }

    /**
     * Opens [url] in whatever handles it. A device with no browser answers nothing, which is
     * logged rather than shown: the row stays as it was.
     */
    fun openHomePage(url: String) {
        try {
            mActivity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (failure: ActivityNotFoundException) {
            AppLogger.w("$TAG nothing opens '$url': $failure")
        }
    }

    companion object {

        private const val TAG = "NowPlayingPlatform"

        private const val MIME_TEXT = "text/plain"

        /**
         * Android 13 shows its own confirmation of every copy to the clipboard; earlier versions
         * show none, so the app shows one there.
         */
        fun showsCopiedToast(sdkInt: Int): Boolean = sdkInt <= Build.VERSION_CODES.S_V2
    }
}
