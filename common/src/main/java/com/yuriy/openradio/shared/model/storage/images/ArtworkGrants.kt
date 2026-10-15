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

package com.yuriy.openradio.shared.model.storage.images

import android.net.Uri
import androidx.media3.common.MediaItem

/**
 * Lets another application read the artwork of the media items handed to it.
 *
 * [ImagesProvider] is not exported, so a media browser in another process can open an artwork uri
 * only after it has been granted that uri. Each grant covers one image, which keeps the browser
 * to the artwork of the items it was actually shown.
 *
 * @param mGrantRead grants read access to one uri to the named package; in the service this is
 *                   `Context.grantUriPermission` with `FLAG_GRANT_READ_URI_PERMISSION`.
 */
class ArtworkGrants(private val mGrantRead: (packageName: String, uri: Uri) -> Unit) {

    /**
     * Grants [packageName] the artwork of every item in [items] that [ImagesProvider] serves.
     */
    fun grant(packageName: String, items: List<MediaItem>) {
        for (item in items) {
            val artwork = item.mediaMetadata.artworkUri ?: continue
            if (ImagesStore.isAuthorised(artwork)) {
                mGrantRead(packageName, artwork)
            }
        }
    }
}
