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
import androidx.annotation.MainThread
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.MediaLibraryClient
import com.yuriy.openradio.shared.model.media.PlaybackState
import com.yuriy.openradio.shared.model.media.isInvalid
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioStore
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.MediaItemHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What plays, for every view of it: the mini player, the playing row of each list, and Now playing
 * with its Station details. It also owns the hearts tapped since the service last answered, keyed
 * by the normalized media id, so a station's heart agrees wherever the station is shown, and it
 * remembers the flag of each station in the answers it accepts, under the same key.
 *
 * It holds no View, Activity or Compose type. [MainScreenModel] hands it the library's events and
 * builds its state from it. Main thread only.
 *
 * @param mLastStation the station a service with an empty queue would resume.
 * @param mFavorites asked whether that station is a favourite, since no list has delivered it.
 * @param mScope runs the service commands the tracker sends.
 */
class NowPlayingTracker(
    private val mLastStation: LastPlayedStation,
    private val mFavorites: FavoriteLookup,
    private val mScope: CoroutineScope,
) {

    /**
     * The station shown.
     *
     * @param id its media id as the player or the store names it, or null when the player named
     *   none.
     * @param isFavorite its flag as the service last delivered it, an accepted answer's flag for
     *   the station winning over its item's.
     */
    private data class Current(
        val id: String?,
        val name: String,
        val track: String,
        val artworkUri: String?,
        val facts: StationFacts,
        val isFavorite: Boolean,
    )

    private var mClient: MediaLibraryClient? = null
    private var mCurrent: Current? = null
    private var mPlayback = PlaybackState()

    /**
     * Hearts tapped since the service last answered for the station, by normalized media id; a
     * fresh answer wins.
     */
    private val mFavoriteOverrides = HashMap<String, Boolean>()

    /**
     * Each station's flag from the latest accepted answer that held it, by normalized media id,
     * so the shown station's heart does not depend on whether its metadata or a list answer came
     * first.
     */
    private val mAnsweredFavorites = HashMap<String, Boolean>()

    /** The media id of the station shown, or null while there is none. */
    val playingId: String?
        get() = mCurrent?.id

    /** Whether a station is shown; Now playing closes when none is. */
    val hasStation: Boolean
        get() = mCurrent != null

    @MainThread
    fun attach(client: MediaLibraryClient) {
        mClient = client
    }

    /**
     * Lets the client go. What is shown stays until the next [onConnected] decides again.
     */
    @MainThread
    fun detach() {
        mClient = null
    }

    /**
     * What is shown when the screen connects. A service that holds a station has pushed its
     * metadata already. One with an empty queue, as after the process was killed, pushes none, and
     * a play request would resume [mLastStation]'s station, so that one is shown, paused. Without
     * one nothing is shown, because the button would resume nothing.
     */
    @MainThread
    fun onConnected() {
        val client = mClient ?: return
        if (client.currentMediaItem != null) {
            client.mediaMetadata?.let(::onMetadataChanged)
            return
        }
        mPlayback = PlaybackState()
        val last = mLastStation.get()
        if (last.isInvalid()) {
            mCurrent = null
            return
        }
        mCurrent = Current(
            id = last.id,
            name = last.name,
            track = AppUtils.EMPTY_STRING,
            artworkUri = if (last.imageUrl.isEmpty()) null else last.imageUri.toString(),
            facts = StationDetails.of(last),
            isFavorite = mFavorites.isFavorite(last),
        )
    }

    @MainThread
    fun onPlaybackStateChanged(state: PlaybackState) {
        mPlayback = state
        val id = mClient?.currentMediaItem?.mediaId ?: return
        mCurrent = mCurrent?.copy(id = id)
    }

    /**
     * Shows the station [metadata] names, read with the player's current item, or leaves what is
     * shown when [metadata] names none: once a station was chosen it stays.
     */
    @MainThread
    fun onMetadataChanged(metadata: MediaMetadata) {
        val client = mClient ?: return
        val title = metadata.title?.toString()
        if (title.isNullOrEmpty()) {
            return
        }
        val item = client.currentMediaItem
        val previous = mCurrent
        mCurrent = Current(
            id = item?.mediaId ?: previous?.id,
            name = title,
            track = MediaItemHelper.getDisplayDescription(metadata, AppUtils.EMPTY_STRING),
            artworkUri = if (item == null) previous?.artworkUri else MainScreenRows.artworkUri(item),
            facts = item?.let { StationDetails.of(it) } ?: previous?.facts ?: StationFacts.NONE,
            isFavorite = item?.let {
                mAnsweredFavorites[key(it.mediaId)] ?: MediaItemHelper.isFavoriteField(it.mediaMetadata)
            } ?: previous?.isFavorite ?: false,
        )
        mPlayback = client.playbackState
    }

    /**
     * Pauses the playing station or resumes the last played one. With nothing loaded, as after a
     * cold start, the toggle command would find an empty player and do nothing, so the session is
     * asked to play instead, which resumes the latest station ([MediaLibraryClient.play]).
     */
    @MainThread
    fun togglePlayback() {
        val client = mClient ?: return
        if (client.currentMediaItem == null) {
            client.play()
            return
        }
        mScope.launch { client.sendCommand(OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM, Bundle()) }
    }

    /** The heart a list row of [item] shows. */
    fun isFavorite(item: MediaItem): Boolean {
        return mFavoriteOverrides[key(item.mediaId)] ?: MediaItemHelper.isFavoriteField(item.mediaMetadata)
    }

    /** Whether the row [mediaId], a plain or a search-result id, is the station shown. */
    fun isCurrent(mediaId: String): Boolean {
        val playing = playingId ?: return false
        return key(mediaId) == key(playing)
    }

    /** A list row's heart: the station [item] is marked or unmarked and the service is told. */
    @MainThread
    fun toggleFavorite(item: MediaItem) {
        mark(item.mediaId, !isFavorite(item))
    }

    /**
     * The shown station's heart, whether or not any list holds the station.
     *
     * @return false when no station is shown or the player named none, and nothing was sent.
     */
    @MainThread
    fun toggleCurrentFavorite(): Boolean {
        val id = mCurrent?.id
        if (id == null) {
            AppLogger.w("$TAG no current station to mark")
            return false
        }
        mark(id, !currentFavorite())
        return true
    }

    /**
     * Takes an answer of the service that the screen accepted: its flags win over the hearts
     * tapped before it, and it is learnt as the flag of every station it holds, so the shown
     * station's heart follows it even when the station is shown after the answer arrives.
     */
    @MainThread
    fun onAnswer(children: List<MediaItem>) {
        val currentKey = mCurrent?.id?.let(::key)
        for (child in children) {
            val childKey = key(child.mediaId)
            val isFavorite = MediaItemHelper.isFavoriteField(child.mediaMetadata)
            mFavoriteOverrides.remove(childKey)
            mAnsweredFavorites[childKey] = isFavorite
            if (childKey == currentKey) {
                mCurrent = mCurrent?.copy(isFavorite = isFavorite)
            }
        }
    }

    /** The mini player, or null while it is hidden. */
    fun miniPlayer(): MiniPlayerState? {
        val current = mCurrent ?: return null
        return MiniPlayerState(
            name = current.name,
            initials = MainScreenRows.initials(current.name),
            track = current.track,
            isPlaying = mPlayback.isPlaying,
        )
    }

    /** Now playing and Station details, or null while no station is shown. */
    fun nowPlaying(): NowPlayingState? {
        val current = mCurrent ?: return null
        return NowPlayingState(
            name = current.name,
            initials = MainScreenRows.initials(current.name),
            track = current.track,
            artworkUri = current.artworkUri,
            isPlaying = mPlayback.isPlaying,
            isBuffering = mPlayback.isBuffering,
            isFavorite = currentFavorite(),
            streamUrl = current.facts.streamUrl.trim(),
            details = StationDetails.rows(current.facts),
        )
    }

    private fun currentFavorite(): Boolean {
        val current = mCurrent ?: return false
        val id = current.id ?: return current.isFavorite
        return mFavoriteOverrides[key(id)] ?: current.isFavorite
    }

    /**
     * Shows [marked] at once for every id of the station, and tells the service about [mediaId],
     * the id of the row or station the heart was tapped on, as the list hearts always have.
     */
    private fun mark(mediaId: String, marked: Boolean) {
        mFavoriteOverrides[key(mediaId)] = marked
        val command = if (marked) OpenRadioService.CMD_MARK_FAVORITE else OpenRadioService.CMD_UNMARK_FAVORITE
        mScope.launch { mClient?.sendCommand(command, OpenRadioStore.makeUpdateIsFavoriteBundle(mediaId)) }
    }

    private fun key(mediaId: String): String = MediaId.normalizeFromSearchId(mediaId)

    private companion object {
        const val TAG = "NPT"
    }
}
