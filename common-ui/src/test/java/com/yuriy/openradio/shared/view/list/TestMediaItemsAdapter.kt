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

package com.yuriy.openradio.shared.view.list

import android.view.ViewGroup
import androidx.media3.common.MediaItem

/**
 * The smallest concrete [MediaItemsAdapter] a JVM test can hold: it inherits the row collection
 * that is the subject of these tests and refuses the view holder calls, which need a real
 * [android.view.View] and therefore a device.
 */
internal class TestMediaItemsAdapter : MediaItemsAdapter() {

    /**
     * Media ids of the rows currently held, in list order.
     */
    fun mediaIds(): List<String> {
        return (0 until itemCount).map { checkNotNull(getItem(it)) { "No row at $it of $itemCount" }.mediaId }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemViewHolder {
        throw AssertionError("View holders are not used by this test")
    }

    override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) = Unit

    companion object {

        fun mediaItem(mediaId: String): MediaItem {
            return MediaItem.Builder().setMediaId(mediaId).build()
        }
    }
}
