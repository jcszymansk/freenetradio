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
 * Besides the browse lists it holds one playlist: the queue the service handed the player, whether
 * a controller selected it from a browse list or the service assembled it itself. A browse list is
 * invalidated or replaced whenever its source changes, also while its stations play and with no
 * controller left to browse it again, so the playlist is what keeps the queued stations
 * resolvable. It is never a key: keying it by a station's id would answer every later selection of
 * that station with it instead of with the list the station was selected from (TASK-041). It only
 * answers for items no browse list holds.
 *
 * The service stores pages on its executor and the main thread, and reads them from session
 * callbacks and the player, so the tree is safe to use from any thread. Its whole state is one
 * immutable snapshot: a write builds the next one under a lock, so two writers cannot install
 * indexes that miss each other's lists, and a read answers from a single snapshot, so it never
 * sees a half-made update. A list a lookup returned never changes, however long its reader holds it.
 *
 * A browse list is held as a [PagedList], so that a later page is added only to the list it was
 * fetched to continue: the provider numbers pages from the start of the list, and the tree is what
 * knows how many of them the list has.
 */
class BrowseTree {

    private val mLock = Any()

    @Volatile
    private var mState = State(emptyMap(), EMPTY)

    /** The stamp of the list stored last, see [PagedList.stamp]. Written under [mLock]. */
    private var mLastStamp = 0L

    operator fun get(mediaId: String): List<MediaItem>? = mState.lists[mediaId]?.children

    operator fun set(mediaId: String, browseData: BrowseData) {
        store(mediaId, browseData)
    }

    /**
     * Holds [browseData] under [mediaId] as a new list of one page, in place of whatever was held.
     *
     * @return the list as stored.
     */
    fun store(mediaId: String, browseData: BrowseData): PagedList {
        synchronized(mLock) {
            val stored = PagedList(browseData, 1, ++mLastStamp)
            mState = mState.withList(mediaId, stored)
            return stored
        }
    }

    /** @return the list held under [mediaId] with its pages, or null when none is held. */
    fun getPagedList(mediaId: String): PagedList? = mState.lists[mediaId]

    /**
     * Replaces the playlist with the queue the player has just been handed. Its items and stations
     * stay resolvable by id for as long as it is held, so the player can report on them, but a
     * browse list that holds the same item takes precedence over it in [getMediaItemsByMediaId].
     */
    fun setPlaylist(browseData: BrowseData) {
        update { State(it.lists, browseData) }
    }

    /**
     * Adds what was added to the player's queue to the end of the playlist.
     */
    fun addToPlaylist(browseData: BrowseData) {
        update {
            State(
                it.lists,
                BrowseData(
                    it.playlist.children + browseData.children,
                    it.playlist.radioStations + browseData.radioStations
                )
            )
        }
    }

    /**
     * Adds [browseData] to the list held under [mediaId] as the page that follows [after], the
     * list as it was when the page was asked for.
     *
     * A page for a list that is not held any more, because it was invalidated or stored again
     * since, is dropped: it continues a list nobody holds. A page that another request has
     * already added to the list is not added again, so two requests that fetched the same page
     * leave it in the list once.
     *
     * @return the list with the page in it, which is what a request for the page is answered
     *   from, or null when the page was dropped. The answer does not read the list again, as the
     *   list may be invalidated as soon as the page is in it.
     */
    fun append(mediaId: String, after: PagedList, browseData: BrowseData): PagedList? {
        synchronized(mLock) {
            val held = mState.lists[mediaId]
            if (held == null || held.stamp != after.stamp) {
                return null
            }
            if (held.pageCount > after.pageCount) {
                return held
            }
            val extended = PagedList(
                BrowseData(
                    held.children + browseData.children,
                    held.data.radioStations + browseData.radioStations
                ),
                held.pageCount + 1,
                held.stamp
            )
            mState = mState.withList(mediaId, extended)
            return extended
        }
    }

    /**
     * @return the list keyed by [mediaId], else the first browse list holding an item of that id,
     *   else the playlist when it holds one, else an empty list.
     */
    fun getMediaItemsByMediaId(mediaId: String): List<MediaItem> = mState.getMediaItemsByMediaId(mediaId)

    /**
     * @return the list a selection of [mediaId] made while browsing [parentId] plays, with its
     *   stations: the list of [parentId] when it holds the item, which is the list the user
     *   selected it from, and otherwise the list [getMediaItemsByMediaId] finds for it. Nothing
     *   when no list holds the item.
     */
    fun getSelection(mediaId: String, parentId: String): BrowseData =
        mState.getSelection(mediaId, parentId) ?: EMPTY

    /**
     * @return the items of [mediaIds] the tree holds, in that order, with their stations. An id
     *   the tree does not hold is left out.
     */
    fun getQueue(mediaIds: List<String>): BrowseData = mState.getQueue(mediaIds)

    fun getMediaItemByMediaId(mediaId: String): MediaItem? = mState.mediaItems[mediaId]

    fun getRadioStationByMediaId(mediaId: String) = mState.radioStations[mediaId] ?: RadioStation.INVALID_INSTANCE

    fun invalidate(mediaId: String) {
        update { it.withList(mediaId, null) }
    }

    /**
     * Drops every browse list, for when the data they were built from is gone. The playlist stays:
     * it is the player's queue, which outlives the lists it was selected from.
     *
     * @return the ids of the lists that were dropped.
     */
    fun invalidateAll(): Set<String> {
        synchronized(mLock) {
            val dropped = mState.lists.keys
            mState = State(emptyMap(), mState.playlist)
            return dropped
        }
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
    private class State(val lists: Map<String, PagedList>, val playlist: BrowseData) {

        val mediaItems: Map<String, MediaItem>
        val radioStations: Map<String, RadioStation>

        init {
            val mediaItemIndex = mutableMapOf<String, MediaItem>()
            val radioStationIndex = mutableMapOf<String, RadioStation>()
            for (browseData in listOf(playlist) + lists.values.map { it.data }) {
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
         * @return this state with [list] held under [mediaId], or nothing held under it when
         *   [list] is null. A replaced list keeps its place in the search order.
         */
        fun withList(mediaId: String, list: PagedList?): State {
            val next = LinkedHashMap(lists)
            if (list == null) {
                next.remove(mediaId)
            } else {
                next[mediaId] = list
            }
            return State(next, playlist)
        }

        fun getMediaItemsByMediaId(mediaId: String): List<MediaItem> {
            lists[mediaId]?.let { return it.children }
            return findHolder(mediaId)?.children ?: emptyList()
        }

        fun getSelection(mediaId: String, parentId: String): BrowseData? {
            val browsed = lists[parentId]
            if (browsed != null && browsed.children.holds(mediaId)) {
                return browsed.data
            }
            lists[mediaId]?.let { return it.data }
            return findHolder(mediaId)
        }

        fun getQueue(mediaIds: List<String>) = BrowseData(
            mediaIds.mapNotNull { mediaItems[it] },
            mediaIds.mapNotNull { radioStations[it] }.toSet()
        )

        /**
         * @return the first browse list holding an item of [mediaId], else the playlist when it
         *   holds one.
         */
        private fun findHolder(mediaId: String): BrowseData? {
            for (list in lists.values) {
                if (list.children.holds(mediaId)) {
                    return list.data
                }
            }
            if (playlist.children.holds(mediaId)) {
                return playlist
            }
            return null
        }

        private fun List<MediaItem>.holds(mediaId: String) = any { it.mediaId == mediaId }
    }

    /**
     * A browse list as the tree held it at one moment.
     *
     * @property data The items of the list and the stations behind them.
     * @property pageCount How many pages the list was built from, which is the number of the page
     *   that continues it, as pages are numbered from zero.
     * @property stamp Tells this list from one stored under the same id before or after it. Every
     *   [store] makes a new list with a stamp of its own, and every page added to it keeps it.
     */
    class PagedList internal constructor(val data: BrowseData, val pageCount: Int, val stamp: Long) {
        val children: List<MediaItem>
            get() = data.children
    }

    /**
     * A list of items and the stations behind them. It keeps its own copies, so the caller's
     * collections stay the caller's and what the tree holds changes only through the tree.
     */
    class BrowseData(children: List<MediaItem>, radioStations: Set<RadioStation>) {
        val children: List<MediaItem> = children.toList()
        val radioStations: Set<RadioStation> = radioStations.toSet()
    }

    private companion object {
        val EMPTY = BrowseData(emptyList(), emptySet())
    }
}
