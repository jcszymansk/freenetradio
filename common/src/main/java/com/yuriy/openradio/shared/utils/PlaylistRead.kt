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

/**
 * What reading a station url as a playlist answered, see [NetUtils.extractUrlsFromPlaylist].
 */
sealed class PlaylistRead {

    /**
     * The read ran, and the playlist named [urls] in this order.
     */
    data class Urls(val urls: List<String>) : PlaylistRead()

    /**
     * The read stopped before a request it needed, because the network may not be used for
     * playback. Whatever it had read by then is not the playlist, so it is not answered.
     */
    object Blocked : PlaylistRead() {

        override fun toString() = "Blocked"
    }
}
