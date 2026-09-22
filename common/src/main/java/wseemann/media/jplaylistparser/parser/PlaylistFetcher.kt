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

package wseemann.media.jplaylistparser.parser

/**
 * The only way the parsers reach the network.
 *
 * A playlist names other addresses, and some of them are playlists again: an ASX `ENTRYREF`, or an
 * entry whose url has a playlist extension. Those are read through this seam, which the
 * application backs with its `DownloaderLayer`, so that a playlist downloaded from anywhere cannot
 * make the parsers open a connection of their own.
 */
fun interface PlaylistFetcher {

    /**
     * @param url Address named by a playlist.
     * @return The content at [url], or an empty array when it could not be read.
     */
    fun fetch(url: String): ByteArray
}
