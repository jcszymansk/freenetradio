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
import androidx.media3.session.MediaSession

/**
 * Lets the Android Auto media browser read the artwork of the media items handed to it.
 *
 * [ImagesProvider] is not exported, so a media browser in another process can open an artwork uri
 * only after it has been granted that uri. Each grant covers one image, which keeps the browser
 * to the artwork of the items it was actually shown.
 *
 * Any application may connect to the media browser service, so a grant to every browser would
 * reopen the artwork store, which holds the images the user picked for their own stations, to all
 * of them. Only the browser [mIsCar] recognises is granted anything. The phone UI needs no grant
 * because it reads the provider from the application's own process.
 *
 * @param mIsCar tells whether a browser is Android Auto; in the service this is
 *               `MediaSession.isAutoCompanionController`.
 * @param mGrantRead grants read access to one uri to the named package; in the service this is
 *                   `Context.grantUriPermission` with `FLAG_GRANT_READ_URI_PERMISSION`.
 */
class ArtworkGrants(
    private val mIsCar: (browser: MediaSession.ControllerInfo) -> Boolean,
    private val mGrantRead: (packageName: String, uri: Uri) -> Unit
) {

    /**
     * Grants [browser] the artwork of every item in [items] that [ImagesProvider] serves, when
     * [browser] is Android Auto.
     */
    fun grant(browser: MediaSession.ControllerInfo, items: List<MediaItem>) {
        if (!mIsCar(browser)) {
            return
        }
        for (item in items) {
            val artwork = item.mediaMetadata.artworkUri ?: continue
            if (ImagesStore.isAuthorised(artwork)) {
                mGrantRead(browser.packageName, artwork)
            }
        }
    }
}
