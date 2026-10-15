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

package com.yuriy.openradio.shared.utils

import android.net.Uri
import wseemann.media.jplaylistparser.playlist.PlaylistEntry

/**
 * A stream that a station's playlist named, and the MIME type the player is to open it with.
 *
 * The player chooses how to read a stream from its MIME type before its url, so the type travels
 * with the url from the playlist to the player instead of being guessed again from the url alone.
 */
data class ResolvedStream(val url: String, val mimeType: String) {

    companion object {

        /**
         * The stream [entry] names, with the type the entry carries, or else the one its url has.
         */
        fun of(entry: PlaylistEntry): ResolvedStream {
            val url = entry[PlaylistEntry.URI]
            val mimeType = entry[PlaylistEntry.MIME_TYPE].ifEmpty { AppUtils.getMimeTypeFromUri(Uri.parse(url)) }
            return ResolvedStream(url, mimeType)
        }
    }
}
