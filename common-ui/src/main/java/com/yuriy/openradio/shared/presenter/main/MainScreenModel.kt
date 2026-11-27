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
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yuriy.openradio.shared.model.ServiceCommander
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.MediaItemsSubscription
import com.yuriy.openradio.shared.model.media.MediaLibraryClient
import com.yuriy.openradio.shared.model.media.MediaResourceManagerListener
import com.yuriy.openradio.shared.model.media.PlaybackState
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The main screen's state holder: one browse stack per tab, the answers of the media library
 * filtered to the node on top of the selected tab's stack, paging and scroll positions, and what
 * [NowPlayingTracker] says plays, published as one [StateFlow]. It holds no View, Activity or
 * Compose type, so a JVM test drives it with a fake [MediaLibraryClient].
 *
 * It is a process singleton: [start] builds a client when the screen comes up and [stop] releases
 * it, and the tabs and stacks outlive the activity in between. All calls are on the main thread,
 * which is also where [MediaLibraryClient] delivers its answers.
 *
 * @param mClientFactory builds the client for [start], handing it the listener for playback events.
 * @param connectivity asked before a row tap opens or plays anything; tabs are not gated.
 * @param mTracker what plays and every station's heart; the model hands it the library's events.
 * @param scope runs the service commands the model sends.
 */
class MainScreenModel(
    private val mClientFactory: (MediaResourceManagerListener) -> MediaLibraryClient,
    private val mSourcesLayer: SourcesLayer,
    private val mConnectivity: ConnectivityGate,
    private val mTracker: NowPlayingTracker,
    private val mScope: CoroutineScope,
) : BrowseSearch, BrowseReload, ScreenClose {

    private var mClient: MediaLibraryClient? = null
    private var mNextSerial = 0
    private var mTabs: List<Tab> = Tab.entries.toList()
    private var mSelected = Tab.FAV
    private val mStacks: MutableMap<Tab, List<BrowseLevel>> = rootStacks()

    /**
     * The root node's own row for the user's country, which COUNTRY shows under YOUR COUNTRY, or
     * null while the root offers none.
     */
    private var mCountryItem: MediaItem? = null

    private var mClosed = false

    /** Declared before [mState], whose initial [buildState] reads it. */
    private var mPlayerScreen = PlayerScreen.NONE

    private val mState = MutableStateFlow(buildState())

    val state: StateFlow<MainScreenState> = mState.asStateFlow()

    /**
     * Lets through only the answers for the node on top of the selected tab's stack.
     *
     * The library fetches on a coroutine, both when asked and when the service reports that the
     * subscribed node changed, and the user can switch tabs or walk back while a fetch is in
     * flight. The stacks follow the user synchronously, so an answer whose parent is not the shown
     * top was asked for a node that is no longer shown, and is dropped. A page answer is taken
     * only while this model has a page request out for that level.
     */
    private val mShownNode = object : MediaItemsSubscription {

        override fun onChildrenLoaded(parentId: String, children: List<MediaItem>, replace: Boolean) {
            val top = shownTop(parentId, "${children.size} children") ?: return
            mTracker.onAnswer(children)
            val updated = when {
                replace -> top.copy(
                    items = children, inFlight = false, failed = false,
                    page = 0, pageInFlight = false, endReached = false
                )
                top.pageInFlight && top.items != null -> top.copy(
                    items = top.items + children, page = top.page + 1, pageInFlight = false,
                    endReached = children.isEmpty()
                )
                else -> {
                    AppLogger.w("$TAG drop an unasked page of '$parentId'")
                    return
                }
            }
            replaceTop(updated)
            emit()
        }

        override fun onError(parentId: String) {
            val top = shownTop(parentId, "an error") ?: return
            replaceTop(
                if (top.pageInFlight) top.copy(pageInFlight = false) else top.copy(inFlight = false, failed = true)
            )
            emit()
        }
    }

    /**
     * Reads the user's country row from the root node's children.
     */
    private val mRootAnswer = object : MediaItemsSubscription {

        override fun onChildrenLoaded(parentId: String, children: List<MediaItem>, replace: Boolean) {
            mCountryItem = children.firstOrNull { it.mediaId == MediaId.MEDIA_ID_COUNTRY_STATIONS }
            emit()
        }

        override fun onError(parentId: String) {
            AppLogger.w("$TAG root not read, YOUR COUNTRY keeps '${mCountryItem?.mediaMetadata?.title}'")
        }
    }

    private val mLibraryListener = object : MediaResourceManagerListener {

        override fun onConnected() {
            showTop()
            refreshRoot()
            mTracker.onConnected()
            emit()
        }

        override fun onPlaybackStateChanged(state: PlaybackState) {
            mTracker.onPlaybackStateChanged(state)
            emit()
        }

        override fun onMetadataChanged(metadata: MediaMetadata) {
            mTracker.onMetadataChanged(metadata)
            emit()
        }
    }

    @MainThread
    fun start() {
        if (mClient != null) {
            AppLogger.w("$TAG start while started")
            return
        }
        mClosed = false
        mTabs = Tab.visibleFor(mSourcesLayer.getActiveSource())
        if (mSelected !in mTabs) {
            mSelected = Tab.FAV
        }
        val client = mClientFactory(mLibraryListener)
        mClient = client
        mTracker.attach(client)
        emit()
    }

    @MainThread
    fun stop() {
        val client = mClient ?: return
        mClient = null
        mTracker.detach()
        client.clean()
        for (tab in Tab.entries) {
            mStacks[tab] = stack(tab).map { it.copy(inFlight = false, pageInFlight = false) }
        }
        emit()
    }

    @MainThread
    fun selectTab(tab: Tab) {
        if (tab !in mTabs) {
            AppLogger.w("$TAG tab $tab is not offered by this provider")
            return
        }
        if (tab != mSelected) {
            mSelected = tab
            showTop()
            return
        }
        val levels = stack(tab)
        if (levels.size > 1) {
            mStacks[tab] = levels.take(1)
            showTop()
        }
    }

    /**
     * Closes Station details, else Now playing, else pops one level of the selected tab.
     *
     * @return false at the tab's root with no player screen open, where back leaves the screen.
     */
    @MainThread
    fun back(): Boolean {
        when (mPlayerScreen) {
            PlayerScreen.STATION_DETAILS -> mPlayerScreen = PlayerScreen.NOW_PLAYING
            PlayerScreen.NOW_PLAYING -> mPlayerScreen = PlayerScreen.NONE
            PlayerScreen.NONE -> return popSelectedTab()
        }
        emit()
        return true
    }

    private fun popSelectedTab(): Boolean {
        val levels = stack(mSelected)
        if (levels.size < 2) {
            return false
        }
        mStacks[mSelected] = levels.dropLast(1)
        showTop()
        return true
    }

    /**
     * The mini player's tap: Now playing for the station shown. Nothing opens while none is.
     */
    @MainThread
    fun openNowPlaying() {
        if (!mTracker.hasStation) {
            AppLogger.w("$TAG no station for Now playing")
            return
        }
        mPlayerScreen = PlayerScreen.NOW_PLAYING
        emit()
    }

    /** Now playing's "Station details". */
    @MainThread
    fun openStationDetails() {
        if (mPlayerScreen != PlayerScreen.NOW_PLAYING) {
            AppLogger.w("$TAG station details asked from $mPlayerScreen")
            return
        }
        mPlayerScreen = PlayerScreen.STATION_DETAILS
        emit()
    }

    /** Now playing's chevron: Now playing and Station details close together. */
    @MainThread
    fun closePlayer() {
        mPlayerScreen = PlayerScreen.NONE
        emit()
    }

    /** Now playing's heart ([NowPlayingTracker.toggleCurrentFavorite]). */
    @MainThread
    fun toggleCurrentFavorite() {
        if (mTracker.toggleCurrentFavorite()) {
            emit()
        }
    }

    /**
     * Opens the row [rowId]: a category is pushed onto the selected tab's stack, a station plays
     * from the shown node.
     */
    @MainThread
    fun open(rowId: String) {
        val item = itemOf(rowId)
        if (item == null) {
            AppLogger.w("$TAG no row '$rowId' to open")
            return
        }
        if (!mConnectivity.allows()) {
            return
        }
        val metadata = item.mediaMetadata
        when {
            metadata.isBrowsable == true -> push(newLevel(item.mediaId, metadata.title?.toString().orEmpty(), Bundle()))
            metadata.isPlayable == true -> mClient?.playFromMediaId(item, top().mediaId)
            else -> AppLogger.w("$TAG row '$rowId' neither browses nor plays")
        }
    }

    /**
     * Pushes a search for [query] onto the selected tab, in place of an earlier search there and
     * of everything opened from it.
     */
    @MainThread
    override fun search(query: String) {
        val levels = stack(mSelected).takeWhile { it.mediaId != MediaId.MEDIA_ID_SEARCH_FROM_APP }
        mStacks[mSelected] = levels
        push(newLevel(MediaId.MEDIA_ID_SEARCH_FROM_APP, query, AppUtils.makeSearchQueryBundle(query)))
    }

    /**
     * Asks again for the node on top of the selected tab: the UI's "Retry", and the station
     * settings dialog after a sort.
     */
    @MainThread
    override fun reload() {
        showTop()
    }

    @MainThread
    fun loadMore() {
        val client = mClient ?: return
        val top = top()
        if (top.items == null || top.inFlight || top.pageInFlight || top.endReached) {
            return
        }
        if (!MediaId.isRefreshable(top.mediaId, mSourcesLayer)) {
            return
        }
        replaceTop(top.copy(pageInFlight = true))
        client.subscribe(top.mediaId, mShownNode, top.page + 1, top.bundle)
        emit()
    }

    /**
     * Stores where the list of the level [levelKey] stands. Not published: the list that reported
     * it already stands there, and the value is read when the level is shown again.
     */
    @MainThread
    fun rememberScroll(levelKey: String, position: ScrollPosition) {
        for (tab in Tab.entries) {
            val levels = stack(tab)
            val index = levels.indexOfFirst { it.key == levelKey }
            if (index >= 0) {
                mStacks[tab] = levels.toMutableList().also { it[index] = levels[index].copy(scroll = position) }
                return
            }
        }
    }

    @MainThread
    fun toggleFavorite(rowId: String) {
        val item = itemOf(rowId)?.takeIf { it.mediaMetadata.isPlayable == true }
        if (item == null) {
            AppLogger.w("$TAG no station '$rowId' to mark")
            return
        }
        mTracker.toggleFavorite(item)
        emit()
    }

    /**
     * @return what the station settings dialog needs for the row [rowId], or null when the node
     *   on top is not sortable (only FAV and the locals node are) or holds no such station.
     */
    @MainThread
    fun longPressTarget(rowId: String): StationSettingsTarget? {
        val top = top()
        if (!MediaId.isSortable(top.mediaId)) {
            return null
        }
        val items = top.items ?: return null
        val item = items.firstOrNull { it.mediaId == rowId } ?: return null
        return StationSettingsTarget(item, top.mediaId, items.size)
    }

    /**
     * Reads the root node's children again, for the user's country row. Called after the country
     * changed and the service dropped the root it had built for the old one.
     */
    @MainThread
    fun refreshRoot() {
        mClient?.fetchChildren(MediaId.MEDIA_ID_ROOT, mRootAnswer)
    }

    @VisibleForTesting
    fun snapshot(): BrowseSnapshot {
        return BrowseSnapshot(
            mSelected,
            Tab.entries.associateWith { tab -> stack(tab).map { it.mediaId } },
            mPlayerScreen,
        )
    }

    /**
     * The service's commands, sent through whichever client the screen holds, or answered false
     * while there is none. Dialogs reach the service through it.
     */
    val serviceCommander: ServiceCommander = object : ServiceCommander {

        override suspend fun sendCommand(command: String, parameters: Bundle): Boolean {
            return mClient?.sendCommand(command, parameters) ?: false
        }

        override suspend fun sendCommand(
            command: String,
            parameters: Bundle,
            resultCallback: (Int, Bundle?) -> Unit
        ): Boolean {
            return mClient?.sendCommand(command, parameters, resultCallback) ?: false
        }
    }

    /** The mini player's and Now playing's button ([NowPlayingTracker.togglePlayback]). */
    @MainThread
    fun togglePlayback() {
        mTracker.togglePlayback()
    }

    /**
     * Back at a tab's root: the screen is left, and the service is stopped as it was by the old
     * main screen. Sent before the activity finishes, so it does not wait for a dispatch the
     * activity's teardown could overtake.
     */
    @MainThread
    fun leave() {
        mScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mClient?.sendCommand(OpenRadioService.CMD_STOP_SERVICE, Bundle())
        }
    }

    override fun close() {
        mStacks.clear()
        mStacks.putAll(rootStacks())
        mSelected = Tab.FAV
        mClosed = true
        mPlayerScreen = PlayerScreen.NONE
        emit()
    }

    private fun showTop() {
        val client = mClient
        if (client != null) {
            val top = top()
            replaceTop(top.copy(inFlight = true, failed = false, pageInFlight = false))
            client.subscribe(top.mediaId, mShownNode, 0, top.bundle)
        }
        emit()
    }

    private fun push(level: BrowseLevel) {
        mStacks[mSelected] = stack(mSelected) + level
        showTop()
    }

    private fun shownTop(parentId: String, answer: String): BrowseLevel? {
        val top = top()
        if (mClient != null && top.mediaId == parentId) {
            return top
        }
        AppLogger.w("$TAG drop $answer for '$parentId', the shown node is '${top.mediaId}'")
        return null
    }

    private fun itemOf(rowId: String): MediaItem? {
        val top = top()
        return top.items?.firstOrNull { it.mediaId == rowId }
            ?: mCountryItem?.takeIf { isCountryRoot(top) && it.mediaId == rowId }
    }

    private fun stack(tab: Tab): List<BrowseLevel> = mStacks.getValue(tab)

    private fun top(): BrowseLevel = stack(mSelected).last()

    private fun replaceTop(level: BrowseLevel) {
        mStacks[mSelected] = stack(mSelected).dropLast(1) + level
    }

    private fun newLevel(mediaId: String, title: String?, bundle: Bundle): BrowseLevel {
        return BrowseLevel(mNextSerial++, mediaId, title, bundle)
    }

    private fun rootStacks(): MutableMap<Tab, List<BrowseLevel>> {
        return Tab.entries.associateWithTo(LinkedHashMap()) { listOf(newLevel(it.rootId, null, Bundle())) }
    }

    private fun isCountryRoot(level: BrowseLevel): Boolean = level.mediaId == MediaId.MEDIA_ID_COUNTRIES_LIST

    /**
     * Publishes the state. Now playing and Station details close when no station is shown any
     * more (REQ-015), so the screen never covers the main screen with nothing.
     */
    private fun emit() {
        if (!mTracker.hasStation) {
            mPlayerScreen = PlayerScreen.NONE
        }
        mState.value = buildState()
    }

    private fun buildState(): MainScreenState {
        val levels = stack(mSelected)
        val top = levels.last()
        return MainScreenState(
            tabs = mTabs,
            selectedTab = mSelected,
            title = top.title,
            canGoBack = levels.size > 1,
            levelKey = top.key,
            scroll = top.scroll,
            content = content(top),
            isRefreshing = top.inFlight && top.items != null,
            isLoadingPage = top.pageInFlight,
            playingId = mTracker.playingId,
            miniPlayer = mTracker.miniPlayer(),
            showAddFab = top.mediaId == MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST,
            isClosed = mClosed,
            playerScreen = mPlayerScreen,
            nowPlaying = mTracker.nowPlaying(),
        )
    }

    private fun content(top: BrowseLevel): ListContent {
        val items = top.items ?: return if (top.failed) ListContent.Error else ListContent.Loading
        val rows = rowsOf(top, items)
        if (rows.isNotEmpty()) {
            return ListContent.Loaded(rows)
        }
        return ListContent.Empty(
            when (top.mediaId) {
                MediaId.MEDIA_ID_FAVORITES_LIST -> EmptyKind.NO_FAVORITES
                MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST -> EmptyKind.NO_STATIONS
                else -> EmptyKind.NOTHING_FOUND
            }
        )
    }

    private fun rowsOf(top: BrowseLevel, items: List<MediaItem>): List<RowState> {
        val longPress = MediaId.isSortable(top.mediaId)
        val rows = items.map { row(it, longPress) }
        val country = mCountryItem
        if (!isCountryRoot(top) || country == null) {
            return rows
        }
        val yours = listOf(RowState.Section(SectionKind.YOUR_COUNTRY), row(country, false))
        return if (rows.isEmpty()) yours else yours + RowState.Section(SectionKind.ALL_COUNTRIES) + rows
    }

    private fun row(item: MediaItem, longPress: Boolean): RowState {
        if (item.mediaMetadata.isPlayable != true) {
            return MainScreenRows.category(item)
        }
        return MainScreenRows.station(
            item,
            isFavorite = mTracker.isFavorite(item),
            isCurrent = mTracker.isCurrent(item.mediaId),
            canLongPress = longPress,
        )
    }

    private companion object {
        const val TAG = "MSM"
    }
}
