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
 */
class BrowseTree {

    private val mMediaIdToChildren = mutableMapOf<String, MutableList<MediaItem>>()
    private val mMediaIdToChildrenRadioStations = mutableMapOf<String, MutableSet<RadioStation>>()
    private var mMediaIdToMediaItem = mutableMapOf<String, MediaItem>()
    private var mMediaIdToRadioStation = mutableMapOf<String, RadioStation>()
    private var mPlaylist = BrowseData(mutableListOf(), mutableSetOf())

    operator fun get(mediaId: String) = mMediaIdToChildren[mediaId]

    operator fun set(mediaId: String, browseData: BrowseData) {
        mMediaIdToChildren[mediaId] = browseData.children
        mMediaIdToChildrenRadioStations[mediaId] = browseData.radioStations
        reindex()
    }

    /**
     * Replaces the playlist the service assembled itself. Its items and stations stay resolvable by
     * id for as long as it is held, so the player can report on them, but a browse list that holds
     * the same item takes precedence over it in [getMediaItemsByMediaId].
     */
    fun setPlaylist(browseData: BrowseData) {
        mPlaylist = browseData
        reindex()
    }

    fun append(mediaId: String, browseData: BrowseData) {
        mMediaIdToChildren[mediaId]?.plusAssign(browseData.children)
        mMediaIdToChildrenRadioStations[mediaId]?.plusAssign(browseData.radioStations)
        index(browseData.children, browseData.radioStations, mMediaIdToMediaItem, mMediaIdToRadioStation)
    }

    /**
     * @return the list keyed by [mediaId], else the first browse list holding an item of that id,
     *   else the playlist when it holds one, else an empty list.
     */
    fun getMediaItemsByMediaId(mediaId: String): MutableList<MediaItem> {
        mMediaIdToChildren[mediaId]?.let { return it }
        for (children in mMediaIdToChildren.values) {
            if (children.holds(mediaId)) {
                return children
            }
        }
        if (mPlaylist.children.holds(mediaId)) {
            return mPlaylist.children
        }
        return mutableListOf()
    }

    /**
     * @return the list a selection of [mediaId] made while browsing [parentId] plays: the list of
     *   [parentId] when it holds the item, which is the list the user selected it from, and
     *   otherwise what [getMediaItemsByMediaId] finds for it.
     */
    fun getMediaItemsByMediaId(mediaId: String, parentId: String): MutableList<MediaItem> {
        val browsed = mMediaIdToChildren[parentId]
        if (browsed != null && browsed.holds(mediaId)) {
            return browsed
        }
        return getMediaItemsByMediaId(mediaId)
    }

    fun getMediaItemByMediaId(mediaId: String): MediaItem? {
        return mMediaIdToMediaItem[mediaId]
    }

    fun getRadioStationByMediaId(mediaId: String) = mMediaIdToRadioStation[mediaId] ?: RadioStation.INVALID_INSTANCE

    fun invalidate(mediaId: String) {
        mMediaIdToChildren.remove(mediaId)
        mMediaIdToChildrenRadioStations.remove(mediaId)
        reindex()
    }

    /**
     * Rebuilds the id indexes from everything still held. Removing the entries of a list that went
     * away would also drop ids another list, or the playlist, still holds.
     */
    private fun reindex() {
        val mediaItems = mutableMapOf<String, MediaItem>()
        val radioStations = mutableMapOf<String, RadioStation>()
        index(mPlaylist.children, mPlaylist.radioStations, mediaItems, radioStations)
        for ((mediaId, children) in mMediaIdToChildren) {
            index(children, mMediaIdToChildrenRadioStations[mediaId].orEmpty(), mediaItems, radioStations)
        }
        mMediaIdToMediaItem = mediaItems
        mMediaIdToRadioStation = radioStations
    }

    private fun index(
        children: List<MediaItem>,
        radioStations: Set<RadioStation>,
        mediaItemIndex: MutableMap<String, MediaItem>,
        radioStationIndex: MutableMap<String, RadioStation>
    ) {
        for (mediaItem in children) {
            mediaItemIndex[mediaItem.mediaId] = mediaItem
        }
        for (radioStation in radioStations) {
            radioStationIndex[radioStation.id] = radioStation
        }
    }

    private fun List<MediaItem>.holds(mediaId: String) = any { it.mediaId == mediaId }

    class BrowseData(val children: MutableList<MediaItem>, val radioStations: MutableSet<RadioStation>)
}
