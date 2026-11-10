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

package com.yuriy.openradio.shared.model.media

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * What a screen needs from the media library service: browsing its nodes, starting a station,
 * sending the service its custom commands and reading what plays. [MediaResourcesManager] answers
 * it over a Media3 MediaBrowser; a JVM test answers it with a fake.
 */
interface MediaLibraryClient {

    /**
     * Fetches the children of [parentId] and keeps [callback] subscribed to later changes of it.
     * A [page] above zero fetches only that page and delivers it with `replace == false`.
     * A failed fetch reaches [MediaItemsSubscription.onError].
     */
    fun subscribe(parentId: String, callback: MediaItemsSubscription?, page: Int = 0, bundle: Bundle = Bundle())

    /**
     * Fetches the first page of [parentId] once, without touching the current subscription.
     */
    fun fetchChildren(parentId: String, callback: MediaItemsSubscription)

    fun playFromMediaId(mediaItem: MediaItem, parentMediaId: String)

    /**
     * Asks the session to play. With the queue empty, as in a service created again after the
     * process was killed, the session answers it through `onPlaybackResumption`, which builds the
     * playlist around the latest station and starts it.
     */
    fun play()

    suspend fun sendCommand(command: String, parameters: Bundle?): Boolean

    suspend fun sendCommand(command: String, parameters: Bundle?, resultCallback: ((Int, Bundle?) -> Unit)): Boolean

    val mediaMetadata: MediaMetadata?

    val currentMediaItem: MediaItem?

    val isPlaying: Boolean

    fun clean()
}
