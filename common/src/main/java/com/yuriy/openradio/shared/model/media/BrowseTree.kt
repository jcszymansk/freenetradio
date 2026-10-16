/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import androidx.media3.common.MediaItem

/**
 * The children of every browse node the service has answered, and an index of the items and
 * stations in them by media id.
 *
 * Besides the browse lists it holds one playlist the service assembled itself, outside any browse.
 * That playlist is never a key: keying it by a station's id would answer every later selection of
 * that station with it instead of with the list the station was selected from (TASK-041). It only
 * answers for items no browse list holds.
 *
 * The service stores pages on its executor and the main thread, and reads them from session
 * callbacks and the player, so the tree is safe to use from any thread. Its whole state is one
 * immutable snapshot: a write builds the next one under a lock, so two writers cannot install
 * indexes that miss each other's lists, and a read answers from a single snapshot, so it never
 * sees a half-made update. A list a lookup returned never changes, however long its reader holds it.
 */
class BrowseTree {

    private val mLock = Any()

    @Volatile
    private var mState = State(emptyMap(), BrowseData(emptyList(), emptySet()))

    operator fun get(mediaId: String): List<MediaItem>? = mState.lists[mediaId]?.children

    operator fun set(mediaId: String, browseData: BrowseData) {
        update { it.withList(mediaId, browseData) }
    }

    /**
     * Replaces the playlist the service assembled itself. Its items and stations stay resolvable by
     * id for as long as it is held, so the player can report on them, but a browse list that holds
     * the same item takes precedence over it in [getMediaItemsByMediaId].
     */
    fun setPlaylist(browseData: BrowseData) {
        update { State(it.lists, browseData) }
    }

    /**
     * Adds a page to the end of the list held under [mediaId]. A page for a list that is not held,
     * because it was never stored or was invalidated since, is dropped.
     *
     * @return the position of the page's first item in the list, 0 when the page was dropped.
     */
    fun append(mediaId: String, browseData: BrowseData): Int {
        synchronized(mLock) {
            val held = mState.lists[mediaId] ?: return 0
            mState = mState.withList(
                mediaId,
                BrowseData(held.children + browseData.children, held.radioStations + browseData.radioStations)
            )
            return held.children.size
        }
    }

    /**
     * @return the list keyed by [mediaId], else the first browse list holding an item of that id,
     *   else the playlist when it holds one, else an empty list.
     */
    fun getMediaItemsByMediaId(mediaId: String): List<MediaItem> = mState.getMediaItemsByMediaId(mediaId)

    /**
     * @return the list a selection of [mediaId] made while browsing [parentId] plays: the list of
     *   [parentId] when it holds the item, which is the list the user selected it from, and
     *   otherwise what [getMediaItemsByMediaId] finds for it.
     */
    fun getMediaItemsByMediaId(mediaId: String, parentId: String): List<MediaItem> =
        mState.getMediaItemsByMediaId(mediaId, parentId)

    fun getMediaItemByMediaId(mediaId: String): MediaItem? = mState.mediaItems[mediaId]

    fun getRadioStationByMediaId(mediaId: String) = mState.radioStations[mediaId] ?: RadioStation.INVALID_INSTANCE

    fun invalidate(mediaId: String) {
        update { it.withList(mediaId, null) }
    }

    private fun update(next: (State) -> State) {
        synchronized(mLock) {
            mState = next(mState)
        }
    }

    /**
     * One version of the tree. The id indexes are built from everything it holds rather than
     * patched: removing the entries of a list that went away would also drop ids another list, or
     * the playlist, still holds.
     */
    private class State(val lists: Map<String, BrowseData>, val playlist: BrowseData) {

        val mediaItems: Map<String, MediaItem>
        val radioStations: Map<String, RadioStation>

        init {
            val mediaItemIndex = mutableMapOf<String, MediaItem>()
            val radioStationIndex = mutableMapOf<String, RadioStation>()
            for (browseData in listOf(playlist) + lists.values) {
                for (mediaItem in browseData.children) {
                    mediaItemIndex[mediaItem.mediaId] = mediaItem
                }
                for (radioStation in browseData.radioStations) {
                    radioStationIndex[radioStation.id] = radioStation
                }
            }
            mediaItems = mediaItemIndex
            radioStations = radioStationIndex
        }

        /**
         * @return this state with [browseData] held under [mediaId], or nothing held under it when
         *   [browseData] is null. A replaced list keeps its place in the search order.
         */
        fun withList(mediaId: String, browseData: BrowseData?): State {
            val next = LinkedHashMap(lists)
            if (browseData == null) {
                next.remove(mediaId)
            } else {
                next[mediaId] = browseData
            }
            return State(next, playlist)
        }

        fun getMediaItemsByMediaId(mediaId: String): List<MediaItem> {
            lists[mediaId]?.let { return it.children }
            for (browseData in lists.values) {
                if (browseData.children.holds(mediaId)) {
                    return browseData.children
                }
            }
            if (playlist.children.holds(mediaId)) {
                return playlist.children
            }
            return emptyList()
        }

        fun getMediaItemsByMediaId(mediaId: String, parentId: String): List<MediaItem> {
            val browsed = lists[parentId]?.children
            if (browsed != null && browsed.holds(mediaId)) {
                return browsed
            }
            return getMediaItemsByMediaId(mediaId)
        }

        private fun List<MediaItem>.holds(mediaId: String) = any { it.mediaId == mediaId }
    }

    /**
     * A list of items and the stations behind them. It keeps its own copies, so the caller's
     * collections stay the caller's and what the tree holds changes only through the tree.
     */
    class BrowseData(children: List<MediaItem>, radioStations: Set<RadioStation>) {
        val children: List<MediaItem> = children.toList()
        val radioStations: Set<RadioStation> = radioStations.toSet()
    }
}
