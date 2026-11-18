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

package com.yuriy.openradio.shared.presenter.main

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.yuriy.openradio.shared.model.media.MediaItemsSubscription
import com.yuriy.openradio.shared.model.media.MediaLibraryClient
import com.yuriy.openradio.shared.model.media.MediaResourceManagerListener
import com.yuriy.openradio.shared.model.media.PlaybackState
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.MediaItemHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * A library client that records what the model asks and lets a test answer it the way the
 * manager does, at whatever point the user has reached by then.
 */
internal class FakeLibraryClient(val listener: MediaResourceManagerListener) : MediaLibraryClient {

    data class Request(val parentId: String, val page: Int, val query: String)

    val subscriptions = mutableListOf<Request>()
    val fetches = mutableListOf<String>()
    val plays = mutableListOf<Pair<String, String>>()
    var playRequests = 0
    val commands = mutableListOf<Pair<String, Bundle?>>()
    var cleaned = false
    private var mSubscriber: MediaItemsSubscription? = null
    private var mFetcher: MediaItemsSubscription? = null

    override var mediaMetadata: MediaMetadata? = null
    override var currentMediaItem: MediaItem? = null
    override var playbackState: PlaybackState = PlaybackState()

    override fun subscribe(parentId: String, callback: MediaItemsSubscription?, page: Int, bundle: Bundle) {
        mSubscriber = callback
        subscriptions += Request(parentId, page, AppUtils.getSearchQueryFromBundle(bundle))
    }

    override fun fetchChildren(parentId: String, callback: MediaItemsSubscription) {
        mFetcher = callback
        fetches += parentId
    }

    override fun playFromMediaId(mediaItem: MediaItem, parentMediaId: String) {
        plays += mediaItem.mediaId to parentMediaId
    }

    override fun play() {
        playRequests++
    }

    override suspend fun sendCommand(command: String, parameters: Bundle?): Boolean {
        commands += command to parameters
        return true
    }

    override suspend fun sendCommand(
        command: String,
        parameters: Bundle?,
        resultCallback: (Int, Bundle?) -> Unit
    ): Boolean {
        commands += command to parameters
        return true
    }

    override fun clean() {
        cleaned = true
    }

    fun answer(parentId: String, children: List<MediaItem>, replace: Boolean = true) {
        checkNotNull(mSubscriber) { "Nothing subscribed" }.onChildrenLoaded(parentId, children, replace)
    }

    fun fail(parentId: String) {
        checkNotNull(mSubscriber) { "Nothing subscribed" }.onError(parentId)
    }

    fun answerFetch(parentId: String, children: List<MediaItem>) {
        checkNotNull(mFetcher) { "Nothing fetched" }.onChildrenLoaded(parentId, children, true)
    }

    fun lastSubscription(): Request = subscriptions.last()
}

internal class FakeSourcesLayer(var source: Source = Source.RADIO_BROWSER) : SourcesLayer {

    override fun getAllSources(): Set<Source> = Source.entries.toSet()

    override fun getActiveSource(): Source = source

    override fun setActiveSource(source: Source) {
        this.source = source
    }
}

/**
 * A started, connected model over a [FakeLibraryClient]. [Dispatchers.Unconfined] runs the
 * model's command coroutines to completion inside the call that launched them, because the fake
 * never suspends.
 */
internal class ModelUnderTest(source: Source = Source.RADIO_BROWSER) {

    val sources = FakeSourcesLayer(source)
    var online = true
    var lastStation: RadioStation = RadioStation.INVALID_INSTANCE
    val favoriteIds = mutableSetOf<String>()
    private var mClient: FakeLibraryClient? = null
    private val mScope = CoroutineScope(Dispatchers.Unconfined)
    val model = MainScreenModel(
        { listener -> FakeLibraryClient(listener).also { mClient = it } },
        sources,
        { online },
        NowPlayingTracker({ lastStation }, { favoriteIds.contains(it.id) }, mScope),
        mScope
    )

    val client: FakeLibraryClient
        get() = checkNotNull(mClient) { "The model was not started" }

    val state: MainScreenState
        get() = model.state.value

    fun startAndConnect(): ModelUnderTest {
        model.start()
        client.listener.onConnected()
        return this
    }

    fun rows(): List<RowState> = (state.content as ListContent.Loaded).rows

    fun rowIds(): List<String> = rows().map { it.key }
}

/**
 * A listener for clients the tracker tests build themselves; the tracker is fed directly.
 */
internal val SILENT_LISTENER = object : MediaResourceManagerListener {
    override fun onConnected() = Unit
    override fun onPlaybackStateChanged(state: PlaybackState) = Unit
    override fun onMetadataChanged(metadata: MediaMetadata) = Unit
}

/**
 * The metadata the service pushes for [item] while it plays: the item's own, with the subtitle
 * replaced by the stream's [track], as `OpenRadioPlayer.updateStreamMetadata` does.
 */
internal fun pushedMetadata(item: MediaItem, track: String): MediaMetadata {
    return item.mediaMetadata.buildUpon().setSubtitle(track).build()
}

/**
 * Makes the fake player hold the station [id] and play it, and pushes its metadata.
 */
internal fun ModelUnderTest.play(
    id: String = "s1",
    name: String = "Arrow Classic Rock",
    track: String = "Iggy Pop - Candy",
): ModelUnderTest {
    val item = station(id, name)
    client.currentMediaItem = item
    client.playbackState = PlaybackState(Player.STATE_READY, true)
    client.listener.onMetadataChanged(pushedMetadata(item, track))
    return this
}

internal fun station(id: String, name: String = id, favorite: Boolean = false): MediaItem {
    val extras = Bundle()
    MediaItemHelper.updateFavoriteField(extras, favorite)
    return MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(extras)
                .build()
        )
        .build()
}

internal fun category(id: String, title: String = id): MediaItem {
    return MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setExtras(Bundle())
                .build()
        )
        .build()
}
