/*
 * Copyright 2014 - 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.media.utils.MediaConstants
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.yuriy.openradio.R
import com.yuriy.openradio.shared.broadcast.BTConnectionReceiver
import com.yuriy.openradio.shared.broadcast.BecomingNoisyReceiver
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.extentions.isEnded
import com.yuriy.openradio.shared.extentions.isPlayEnabled
import com.yuriy.openradio.shared.model.media.BrowseTree
import com.yuriy.openradio.shared.model.media.ClientPages
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.media.isInvalid
import com.yuriy.openradio.shared.model.media.item.MediaItemCommand
import com.yuriy.openradio.shared.model.media.item.MediaItemCommandDependencies
import com.yuriy.openradio.shared.model.net.DownloaderLayer
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.images.ArtworkGrants
import com.yuriy.openradio.shared.model.timer.SleepTimerListener
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.service.player.OpenRadioPlayer
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.IntentUtils
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import com.yuriy.openradio.shared.utils.MediaItemHelper
import com.yuriy.openradio.shared.utils.NetUtils
import com.yuriy.openradio.shared.utils.NetworkPermit
import com.yuriy.openradio.shared.utils.PlaylistRead
import com.yuriy.openradio.shared.utils.ResolvedStream
import com.yuriy.openradio.shared.utils.SafeToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.TreeSet
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/13/14
 * E-Mail: chernyshov.yuriy@gmail.com
 */
@UnstableApi
class OpenRadioService : MediaLibraryService() {

    /**
     * Player to play Radio stream.
     */
    private val mPlayer by lazy {
        OpenRadioPlayer(
            applicationContext,
            PlayerListener(),
            mPresenter.getEqualizerLayer(),
            mBrowseTree,
            { mPresenter.isPlaybackBlockedByMobileNetwork() }
        )
    }

    /**
     * Media Session.
     */
    private lateinit var mSession: MediaLibrarySession
    private var mCurrentSearchQuery = AppUtils.EMPTY_STRING

    /**
     * Track selected Radio Station.
     */
    private var mActiveRS = RadioStation.INVALID_INSTANCE

    /**
     * How far the current play request has got with a station url the player could not read.
     */
    private var mPlaylistFallback: PlaylistFallback = PlaylistFallback.Idle()

    /**
     * The network permits of the playlist reads under way, revoked when the mobile-network policy
     * pauses the station. Only touched on the main thread.
     */
    private val mPlaylistReads = mutableSetOf<NetworkPermit>()
    private val mUiScope: CoroutineScope
    private val mScope: CoroutineScope
    private val mCommandScope: CoroutineScope

    private val mBTConnectionReceiver = BTConnectionReceiver(

        object : BTConnectionReceiver.Listener {
            override fun onSameDeviceConnected() {
                handleBTSameDeviceConnected()
            }

            override fun onDisconnected() {
                handlePauseRequest()
            }
        }
    )
    private var mNoisyAudioStreamReceiver = BecomingNoisyReceiver(

        object : BecomingNoisyReceiver.Listener {
            override fun onAudioBecomingNoisy() {
                handlePauseRequest()
            }
        }
    )
    private val mNetMonitorListener = NetworkMonitorListenerImpl()

    private val mStartIds: ConcurrentLinkedQueue<Int>
    private var mCurrentParentId = AppUtils.EMPTY_STRING

    /**
     * The country the list held under [MediaId.MEDIA_ID_COUNTRY_STATIONS] was built for. That id
     * names no country and stands for whichever one is stored, so the browse tree alone cannot tell
     * the list of the stored country from one built before the country changed.
     *
     * It is written right after the list, without a lock: if two browses land out of order, the
     * code left behind names a country other than the stored one, so the next browse rebuilds the
     * list.
     */
    @Volatile
    private var mCountryStationsCode = AppUtils.EMPTY_STRING
    private var mIsRestoreState = false
    private lateinit var mPresenter: OpenRadioServicePresenter
    private lateinit var mDownloader: DownloaderLayer
    private val mSleepTimerListener = SleepTimerListenerImpl()

    private val mBrowseTree: BrowseTree by lazy {
        BrowseTree()
    }

    /** How much of each paged list in [mBrowseTree] every controller has been answered with. */
    private val mClientPages = ClientPages()

    private val mExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Opens the artwork of browsed items to the Android Auto browser, which reads it from another
     * process while the artwork provider itself is not exported.
     */
    private val mArtworkGrants = ArtworkGrants(
        { browser -> mSession.isAutoCompanionController(browser) },
        { packageName, uri -> grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    )

    /**
     * The button a controller shows beside the transport controls while the active station is
     * not a favorite.
     */
    private lateinit var mMarkFavoriteButton: CommandButton

    /**
     * The button a controller shows beside the transport controls while the active station is a
     * favorite.
     */
    private lateinit var mUnmarkFavoriteButton: CommandButton

    /**
     * Default constructor.
     */
    init {
        TAG = "ORS[" + hashCode() + "]"
        DependencyRegistryCommon.inject(this)
        mStartIds = ConcurrentLinkedQueue()
        mUiScope = CoroutineScope(Dispatchers.Main)
        mScope = CoroutineScope(Dispatchers.IO)
        mCommandScope = MediaItemCommandDependencies.newCommandScope()
    }

    interface ResultListener {

        fun onResult(
            items: List<MediaItem> = ArrayList(),
            radioStations: Set<RadioStation> = TreeSet(),
            pageNumber: Int = UrlLayer.FIRST_PAGE_INDEX
        )
    }

    /**
     * @param downloader Reads every playlist that a station's playlist names, see
     * [NetUtils.extractUrlsFromPlaylist].
     */
    fun configureWith(presenter: OpenRadioServicePresenter, downloader: DownloaderLayer) {
        mPresenter = presenter
        mDownloader = downloader
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mSession
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.i("$TAG on create")
        if (AppPreferencesManager.lastKnownRadioStationEnabled(applicationContext)) {
            setActiveRS(mPresenter.getLastRadioStation())
        }
        registerReceivers()
        mSession = with(
            MediaLibrarySession.Builder(
                this, mPlayer, ServiceCallback()
            )
        ) {
            setId(packageName)
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@OpenRadioService,
                        0,
                        sessionIntent,
                        if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE
                        else PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
            build()
        }

        mMarkFavoriteButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.favorite))
            .setEnabled(true)
            .setIconResId(R.drawable.ic_favorite_off)
            .setSessionCommand(SessionCommand(CMD_MARK_FAVORITE, Bundle()))
            .build()
        mUnmarkFavoriteButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.favorite))
            .setEnabled(true)
            .setIconResId(R.drawable.ic_favorite_on)
            .setSessionCommand(SessionCommand(CMD_UNMARK_FAVORITE, Bundle()))
            .build()

        publishActiveStation()
        updateFavoriteState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i("$TAG on start $intent ${IntentUtils.intentBundleToString(intent)}, id:$startId")
        mStartIds.add(startId)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i("$TAG on destroy")
        mUiScope.cancel("Cancel on destroy")
        mScope.cancel("Cancel on destroy")
        unregisterReceivers()
        mPresenter.close()
        mSession.run {
            release()
            if (player.playbackState != Player.STATE_IDLE) {
                player.release()
            }
        }
    }

    /**
     * Tells subscribers that the favorites store changed: the root gains or loses the favorites
     * node, and the favorites list gains or loses the station.
     *
     * Both are always named. Which list a controller shows is not something the service knows:
     * the last parent it answered a browse for is shared by every connected controller, so the
     * phone UI, Android Auto and any other browser each move it. A notification reaches only the
     * controllers subscribed to the node it names, so naming one nobody shows sends nothing.
     *
     * The favorites list is told without being invalidated: a browse always rebuilds it, so it
     * needs no invalidation to be fresh.
     */
    private fun notifyFavoritesChanged() {
        notifyChildrenChanged(MediaId.MEDIA_ID_ROOT)
        mSession.notifyChildrenChanged(MediaId.MEDIA_ID_FAVORITES_LIST, Int.MAX_VALUE, null)
    }

    private fun registerReceivers() {
        mBTConnectionReceiver.register(applicationContext)
        mNoisyAudioStreamReceiver.register(applicationContext)
        mPresenter.startNetworkMonitor(applicationContext, mNetMonitorListener)
        mPresenter.getSleepTimerModel().addSleepTimerListener(mSleepTimerListener)
    }

    private fun unregisterReceivers() {
        mBTConnectionReceiver.unregister(applicationContext)
        mNoisyAudioStreamReceiver.unregister(applicationContext)
        mPresenter.stopNetworkMonitor(applicationContext)
        mPresenter.getSleepTimerModel().removeSleepTimerListener(mSleepTimerListener)
    }

    /**
     * Tells every controller the id of the station the service holds as active, under
     * [EXTRA_ACTIVE_STATION_ID] in the session extras. Nothing is published while it holds none.
     *
     * The active station decides what a page-0 browse does when the queue is empty: with one, the
     * service builds a playlist around it and starts playing. That state is otherwise invisible
     * from outside the service, and a controller that is about to browse may need to know it.
     */
    @MainThread
    private fun publishActiveStation() {
        if (mActiveRS.isInvalid()) {
            return
        }
        AppLogger.d("$TAG publish active station ${mActiveRS.id}")
        mSession.setSessionExtras(
            Bundle().apply { putString(EXTRA_ACTIVE_STATION_ID, mActiveRS.id) }
        )
    }

    private fun updateFavoriteState() {
        if (mActiveRS != RadioStation.INVALID_INSTANCE) {
            val button =
                if (mPresenter.isRadioStationFavorite(mActiveRS)) mUnmarkFavoriteButton else mMarkFavoriteButton
            mSession.setCustomLayout(listOf(button))
        }
    }

    private fun notifyChildrenChanged(mediaId: String) {
        mBrowseTree.invalidate(mediaId)
        mSession.notifyChildrenChanged(mediaId, Int.MAX_VALUE, null)
    }

    @MainThread
    private fun onHandledError(exception: PlaybackException) {
        AppLogger.e("$TAG player handled exception", exception)
        if (exception.cause !is UnrecognizedInputFormatException) {
            return
        }
        val mediaId = mPlayer.currentMediaItem?.mediaId
        if (mediaId.isNullOrEmpty()) {
            AppLogger.e("$TAG unrecognized stream without a current media item")
            mPlayer.stop()
            return
        }
        advancePlaylistFallback(mPlaylistFallback.onUnrecognized(mediaId))
    }

    @MainThread
    private fun advancePlaylistFallback(step: PlaylistFallback.Step) {
        AppLogger.i("$TAG playlist fallback $mPlaylistFallback -> ${step.next}: ${step.action}")
        mPlaylistFallback = step.next
        when (val action = step.action) {
            PlaylistFallback.Action.Ignore -> Unit
            is PlaylistFallback.Action.Resolve -> resolvePlaylist(action.resolution)
            is PlaylistFallback.Action.Play -> playResolvedStream(action.mediaId, action.stream)
            is PlaylistFallback.Action.GiveUp -> {
                mPlayer.stop()
                mPlayer.reportUnplayableStream()
            }
            is PlaylistFallback.Action.PauseForNetworkPolicy -> pauseForNetworkPolicyUiThread()
        }
    }

    /**
     * Reads the url the source of [resolution] names as a playlist, because the player could not
     * recognise what it serves as a stream: the station url of the media item [resolution] names,
     * or a stream an earlier read of it named. The read is part of playing the station, so it does
     * not start, and does not go on to the playlists it names, while the network may not be used
     * for playback.
     */
    @MainThread
    private fun resolvePlaylist(resolution: PlaylistFallback.Resolution) {
        mPlayer.stopForReplacementStream()
        val playlistUrl = when (val source = resolution.source) {
            PlaylistFallback.Source.Station -> getStationUrl(resolution.mediaId)
            is PlaylistFallback.Source.Stream -> source.url
        }
        if (playlistUrl.isEmpty()) {
            AppLogger.e("$TAG no url to resolve for $resolution")
            advancePlaylistFallback(mPlaylistFallback.onResolved(resolution, emptyList()))
            return
        }
        if (mPresenter.isPlaybackBlockedByMobileNetwork()) {
            AppLogger.i("$TAG playlist $playlistUrl for $resolution not read on blocked mobile data")
            advancePlaylistFallback(mPlaylistFallback.onReadBlocked(resolution))
            return
        }
        AppLogger.i("$TAG resolve playlist $playlistUrl for $resolution")
        val permit = NetworkPermit { mPresenter.isPlaybackBlockedByMobileNetwork().not() }
        mPlaylistReads.add(permit)
        mScope.launch {
            val read = NetUtils.extractUrlsFromPlaylist(applicationContext, mDownloader, playlistUrl, permit)
            mUiScope.launch {
                mPlaylistReads.remove(permit)
                AppLogger.i("$TAG playlist read $read for $resolution")
                when (read) {
                    PlaylistRead.Blocked -> advancePlaylistFallback(mPlaylistFallback.onReadBlocked(resolution))
                    is PlaylistRead.Streams -> advancePlaylistFallback(mPlaylistFallback.onResolved(resolution, read.streams))
                    PlaylistRead.Failed -> advancePlaylistFallback(mPlaylistFallback.onResolved(resolution, emptyList()))
                }
            }
        }
    }

    /**
     * The url the station of [mediaId] was listed with. The browse tree holds every station of the
     * queue the player was handed, so it knows the station of every item the player plays.
     */
    private fun getStationUrl(mediaId: String): String {
        val station = mBrowseTree.getRadioStationByMediaId(mediaId)
        if (station.isInvalid()) {
            return AppUtils.EMPTY_STRING
        }
        return station.getStreamUrlFixed()
    }

    @MainThread
    private fun playResolvedStream(mediaId: String, stream: ResolvedStream) {
        val current = mPlayer.currentMediaItem
        if (current == null || current.mediaId != mediaId) {
            AppLogger.w("$TAG drop stream $stream of $mediaId, now at ${current?.mediaId}")
            return
        }
        AppLogger.i("$TAG play $stream in place of $mediaId")
        mPlayer.stopForReplacementStream()
        if (mPlayer.replaceAwaitedStream(MediaItemBuilder.withStream(current, stream)).not()) {
            AppLogger.i("$TAG keep $stream of $mediaId paused")
            return
        }
        if (mPresenter.isPlaybackBlockedByMobileNetwork()) {
            AppLogger.i("$TAG hold $stream of $mediaId until a network it may use connects")
            pauseForNetworkPolicyUiThread()
            return
        }
        handlePlayRequestUiThread()
    }

    /**
     * Return Radio Station object by provided Media Id.
     *
     * @param mediaId Media Id of the station.
     *
     * @return [RadioStation] or `null`.
     */
    private fun getRadioStationByMediaId(mediaId: String?): RadioStation {
        if (mediaId.isNullOrEmpty()) {
            AppLogger.e("$TAG get rs by invalid id")
            return RadioStation.INVALID_INSTANCE
        }
        return mBrowseTree.getRadioStationByMediaId(mediaId)
    }

    /**
     * Handle a request to play Radio Station.
     */
    private fun handlePlayRequest() {
        mUiScope.launch {
            handlePlayRequestUiThread()
        }
    }

    private fun handlePlayRequestUiThread() {
        if (mPresenter.isPlaybackBlockedByMobileNetwork()) {
            SafeToast.showAnyThread(
                applicationContext,
                getString(R.string.mobile_network_disabled)
            )
            return
        }
        mPlayer.prepare()
        mPlayer.play()
    }

    /**
     * Clears the app data, then every browse list built from it: a page-0 browse answers from the
     * browse tree whenever it holds the node, so a list left there would keep serving what the
     * clear removed. The tree goes after the presenter, so a browse in between cannot fill it again
     * from what is about to be cleared, and the subscribers of each dropped list are told after
     * that, so whatever they browse next reaches the presenter.
     */
    private fun handleClearCache(context: Context) {
        mScope.launch {
            mPresenter.clear()
            val dropped = mBrowseTree.invalidateAll()
            AppLogger.i("$TAG clear dropped browse lists $dropped")
            mUiScope.launch {
                for (mediaId in dropped) {
                    mSession.notifyChildrenChanged(mediaId, Int.MAX_VALUE, null)
                }
            }
            SafeToast.showAnyThread(context, getString(R.string.clear_completed))
        }
    }

    private fun handleMasterVolumeChanged(context: Context, masterVolume: Int) {
        AppPreferencesManager.setMasterVolume(context, masterVolume)
        mUiScope.launch {
            mPlayer.volume = masterVolume / 100.0f
        }
    }

    /**
     * Handle a request to pause radio stream with reason provided.
     */
    private fun handlePauseRequest() {
        mUiScope.launch { mPlayer.pause() }
    }

    private fun closeService() {
        handleStopRequest()
        stopSelfResultInt()
        // TODO: There is on start command received after stop command ... need to find out the reason.
        //       Also, when swipe the UI, progress indicator still on for the current media.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /**
     * Handle a request to stop music.
     */
    private fun handleStopRequest() {
        mUiScope.launch { mPlayer.stop() }
    }

    private fun togglePlayableItem() {
        when {
            mPlayer.isPlaying -> mPlayer.pause()
            mPlayer.isPlayEnabled -> mPlayer.play()
            mPlayer.isEnded -> mPlayer.seekTo(C.TIME_UNSET)
            else -> {
                AppLogger.e(
                    "$TAG unhandled toggle case " +
                            "playing:${mPlayer.isPlaying} " +
                            "play enabled:${mPlayer.isPlayEnabled} " +
                            "play ended:${mPlayer.isEnded} "
                )
            }
        }
    }

    private fun stopSelfResultInt() {
        AppLogger.i("$TAG stop self with size of ${mStartIds.size}")
        while (mStartIds.isEmpty().not()) {
            val id = mStartIds.poll() ?: continue
            val result = stopSelfResult(id)
            AppLogger.i("$TAG service " + (if (result) "stopped" else "not stopped") + " for $id")
        }
    }

    /**
     * Handles event when Bluetooth connected to same device within application lifetime.
     */
    private fun handleBTSameDeviceConnected() {
        val autoPlay = AppPreferencesManager.isBtAutoPlay(applicationContext)
        if (autoPlay.not()) {
            return
        }
        handlePlayRequest()
    }

    /**
     * Pauses because playback over the mobile network is disabled. A station the user wanted
     * playing resumes once a network it may use connects.
     */
    private fun handleMobileNetworkBlocked() {
        mUiScope.launch { pauseForNetworkPolicyUiThread() }
    }

    /**
     * Pauses the station for the mobile-network policy. A playlist read under way is abandoned,
     * because its answer would only start a station the policy holds paused; the station reads its
     * playlist again when it resumes. Every read still under way, abandoned earlier or not, has its
     * permit revoked, which disconnects a transfer waiting on a server that holds its answer back.
     * Revoking waits for a connection that is starting, so it runs off the main thread.
     */
    @MainThread
    private fun pauseForNetworkPolicyUiThread() {
        SafeToast.showAnyThread(
            applicationContext,
            getString(R.string.mobile_network_disabled)
        )
        if (mPlaylistReads.isNotEmpty()) {
            val revoked = mPlaylistReads.toList()
            mPlaylistReads.clear()
            AppLogger.i("$TAG revoke ${revoked.size} playlist reads for the network policy")
            mScope.launch {
                for (permit in revoked) {
                    permit.revoke()
                }
            }
        }
        if (mPlaylistFallback.isReadPending) {
            val next = mPlaylistFallback.abandonRead()
            AppLogger.i("$TAG playlist fallback $mPlaylistFallback -> $next: abandoned for the network policy")
            mPlaylistFallback = next
        }
        mPlayer.pauseForNetworkPolicy()
    }

    /**
     * Resumes the station the network stopped, if it stopped one, once a network it may use is
     * there: a connection that is not blocked, or the mobile one the user has just allowed. The
     * decision runs on the same main-thread queue as [handleMobileNetworkBlocked]'s pause, so a
     * usable network reported right after a blocked one sees the station that pause stopped.
     */
    private fun resumeStationTheNetworkStopped() {
        mUiScope.launch {
            if (mPlayer.resumesOnReconnect().not()) {
                AppLogger.d("$TAG network usable, nothing to resume")
                return@launch
            }
            AppLogger.i("$TAG network usable, resume the station the network stopped")
            handlePlayRequestUiThread()
        }
    }

    private fun setActiveRS(value: RadioStation) {
        if (value.isInvalid()) {
            AppLogger.w("$TAG cant set invalid ars")
            return
        }
        mActiveRS = value
        AppLogger.i("$TAG set ars $mActiveRS")
    }

    @MainThread
    private fun maybeCreateInitialPlaylist() {
        val rs = mActiveRS
        if (rs == RadioStation.INVALID_INSTANCE) {
            return
        }
        val mediaItemCount = mPlayer.mediaItemCount
        if (mediaItemCount != 0) {
            return
        }
        buildInitialPlaylist(rs) { playlist ->
            mPlayer.setMediaItems(playlist.mediaItems, playlist.startIndex, playlist.startPositionMs)
            handlePlayRequestUiThread()
        }
    }

    /**
     * Builds the playlist the service plays around [rs] when it has no queue of its own: the
     * favorites, or the provider's new stations when there are none, with [rs] at the head when
     * neither holds it. [onBuilt] gets it on the main thread, positioned on [rs].
     */
    private fun buildInitialPlaylist(
        rs: RadioStation,
        onBuilt: (MediaSession.MediaItemsWithStartPosition) -> Unit
    ) {
        mScope.launch {
            AppLogger.d("$TAG create init npl")
            val radioStations = TreeSet<RadioStation>()
            val favorites = mPresenter.getAllFavorites()
            if (favorites.isEmpty().not()) {
                AppLogger.d("$TAG create init npl fill with fav")
                radioStations.addAll(favorites)
            } else {
                AppLogger.d("$TAG create init npl fill with new stations")
                radioStations.addAll(mPresenter.getNewStations())
            }
            AppLogger.d("$TAG create init npl with ${radioStations.size}")
            val list = radioStations.map { MediaItemBuilder.buildPlayable(it) }.toMutableList()
            val playlistStations = radioStations.toMutableSet()
            if (radioStations.none { it.id == rs.id }) {
                list.add(0, MediaItemBuilder.buildPlayable(rs))
                playlistStations.add(rs)
            }
            val pos = list.indexOfFirst { it.mediaId == rs.id }
            mUiScope.launch {
                // The playlist is what keeps every station of the queue resolvable, the one at its
                // head included, which no list may hold:
                mBrowseTree.setPlaylist(BrowseTree.BrowseData(list, playlistStations))
                onBuilt(MediaSession.MediaItemsWithStartPosition(list, pos, C.TIME_UNSET))
            }
        }
    }


    /**
     * Listener for Exo Player events.
     */
    private inner class PlayerListener : OpenRadioPlayer.Listener {

        override fun onHandledError(error: PlaybackException) {
            this@OpenRadioService.onHandledError(error)
        }

        /**
         * The network monitor has not reported the blocked network yet, or never will, so this
         * is the pause it would have made.
         */
        override fun onStreamRetryBlocked() {
            pauseForNetworkPolicyUiThread()
        }

        override fun onPlaybackStateChanged(mediaItem: MediaItem) {
            mScope.launch {
                mBrowseTree.getRadioStationByMediaId(mediaItem.mediaId).let {
                    if (mediaItem.mediaMetadata.isPlayable == false) {
                        return@let
                    }
                    // TODO: Investigate why metadata with valid fields has empty media id.
                    if (it == RadioStation.INVALID_INSTANCE) {
                        AppLogger.w("$TAG playable has empty media id $it")
                        return@let
                    }
                    setActiveRS(it)
                    mPresenter.setLastRadioStation(it)
                    mUiScope.launch {
                        // Update custom commands:
                        publishActiveStation()
                        updateFavoriteState()
                    }
                }
            }
        }
    }

    private inner class NetworkMonitorListenerImpl : NetworkMonitorListener {

        override fun onConnectivityChange(isConnected: Boolean) {
            if (isConnected.not()) {
                return
            }
            if (mPresenter.isPlaybackBlockedByMobileNetwork()) {
                handleMobileNetworkBlocked()
                return
            }
            resumeStationTheNetworkStopped()
        }
    }

    private inner class SleepTimerListenerImpl : SleepTimerListener {

        override fun onComplete() {
            AppLogger.i("$TAG on sleep timer completed")
            mUiScope.launch {
                closeService()
            }
        }
    }

    private inner class ServiceCallback : MediaLibrarySession.Callback {

        private val mSessionCmdSuccess = Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        private val mSessionCmdNotSupported =
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        private val mSessionCmdSkipped = Futures.immediateFuture(SessionResult(SessionResult.RESULT_INFO_SKIPPED))

        /**
         * Answers a play request that finds the queue empty with the queue the player had before
         * it was emptied. A service created since remembers none, as after the process was killed,
         * and then the active station, which `onCreate` restores from the latest one, is what was
         * playing: the answer is the playlist a page-0 browse would build around it.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val resumption = mPlayer.getResumption()
            val rs = mActiveRS
            if (resumption.mediaItems.isNotEmpty() || rs.isInvalid()) {
                AppLogger.d(
                    "$TAG [$controller] Playback Resumption of ${resumption.mediaItems.size} items " +
                            "at ${resumption.startIndex}"
                )
                return Futures.immediateFuture(resumption)
            }
            AppLogger.i("$TAG [$controller] Playback Resumption builds a playlist around ${rs.id}")
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            buildInitialPlaylist(rs) { future.set(it) }
            return future
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: @Player.Command Int
        ): Int {
            if (PlaylistFallback.isPlayRequest(playerCommand)) {
                AppLogger.d("$TAG [$controller] play request $playerCommand, fallback was $mPlaylistFallback")
                mPlaylistFallback = mPlaylistFallback.newRequest()
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            AppLogger.d("$TAG [$controllerInfo] Media Button Event $intent")
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            AppLogger.d("$TAG [$browser] Subscribe to $parentId")
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onUnsubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String
        ): ListenableFuture<LibraryResult<Void>> {
            AppLogger.d("$TAG [$browser] Unsubscribe from $parentId")
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        @UnstableApi
        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            AppLogger.i(
                "$TAG [$browser] GetLibraryRoot for clientPkgName=${browser.packageName}, clientUid=${browser.uid}"
            )
            val rootExtras = Bundle().apply {
                putBoolean(
                    MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED,
                    true
                )
            }
            //mIsRestoreState = OpenRadioStore.getRestoreState(rootHints)
            val libraryParams = LibraryParams.Builder().setExtras(rootExtras).build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(MediaItemBuilder.buildRootMediaItem(), libraryParams)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            AppLogger.d("$TAG [$browser] GetChildren for $parentId page $page pageSize $pageSize")
            return callWhenSourceReady(browser, parentId, page) { children ->
                AppLogger.d("$TAG GetChildren for $parentId page $page return ${children.size}")
                mArtworkGrants.grant(browser, children)
                LibraryResult.ofItemList(
                    children,
                    LibraryParams.Builder().build()
                )
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            AppLogger.d("$TAG [$browser] GetItem for $mediaId")
            val item = mBrowseTree.getMediaItemByMediaId(mediaId)
                ?: return Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            mArtworkGrants.grant(browser, listOf(item))
            return Futures.immediateFuture(LibraryResult.ofItem(item, LibraryParams.Builder().build()))
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            AppLogger.d("$TAG [$browser] Search for '$query'")
            mCurrentSearchQuery = query
            return callWhenSearchReady(query) {
                mSession.notifySearchResultChanged(browser, query, it, params)
                LibraryResult.ofVoid()
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            // This happens when search item clicked, browser provides no clue as to the query the item belongs to.
            // Maybe redesign is needed?
            val queryStr = if (query == AppUtils.USE_CUR_SEARCH_QUERY) {
                mCurrentSearchQuery
            } else {
                mCurrentSearchQuery = query
                query
            }
            val list = mBrowseTree[queryStr] ?: emptyList()
            // TODO:
            //val fromIndex = max(page * pageSize, list.size - 1)
            //val toIndex = max(fromIndex + pageSize, list.size)
            AppLogger.d("$TAG [$browser] GetSearchResult for '$queryStr'")
            // This happens when Search involved from non Automotive UI (onSearch is skipped currently).
            // Maybe need to redesign?
            if (list.isEmpty()) {
                return callWhenSearchReady(queryStr) {
                    val found = mBrowseTree[queryStr] ?: emptyList()
                    mArtworkGrants.grant(browser, found)
                    LibraryResult.ofItemList(found, LibraryParams.Builder().build())
                }
            }
            mArtworkGrants.grant(browser, list)
            return Futures.immediateFuture(
                LibraryResult.ofItemList(
                    list,
                    LibraryParams.Builder().build()
                )
            )
        }

        /**
         * What a controller adds joins the queue, so it joins the browse tree's playlist as well
         * and stays resolvable after the list it came from is invalidated.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            AppLogger.d("$TAG AddMediaItems ${mediaItems.size}")
            val added = if (mediaItems.size != 1) {
                mBrowseTree.getQueue(mediaItems.map { it.mediaId })
            } else {
                mBrowseTree.getSelection(mediaItems[0].mediaId, mCurrentParentId)
            }
            mBrowseTree.addToPlaylist(added)
            return Futures.immediateFuture(added.children.toMutableList())
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            AppLogger.d("$TAG Connect $controller")
            if (
                session.isMediaNotificationController(controller)
                || session.isAutomotiveController(controller)
                || session.isAutoCompanionController(controller)
            ) {
                AppLogger.w("$TAG session is not notif, not automotive, not auto")
            }
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    // Add custom commands
                    .add(SessionCommand(CMD_MARK_FAVORITE, Bundle()))
                    .add(SessionCommand(CMD_UNMARK_FAVORITE, Bundle()))
                    .add(SessionCommand(CMD_NET_CHANGED, Bundle()))
                    .add(SessionCommand(CMD_CLEAR_CACHE, Bundle()))
                    .add(SessionCommand(CMD_MASTER_VOLUME_CHANGED, Bundle()))
                    .add(SessionCommand(CMD_STOP_SERVICE, Bundle()))
                    .add(SessionCommand(CMD_TOGGLE_LAST_PLAYED_ITEM, Bundle()))
                    .add(SessionCommand(CMD_UPDATE_SORT_IDS, Bundle()))
                    .add(SessionCommand(CMD_UPDATE_TREE, Bundle()))
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            AppLogger.d("$TAG Disconnect $controller")
            mClientPages.forget(controller)
        }

        /**
         * The queue the player is handed becomes the browse tree's playlist, which keeps its
         * stations resolvable after the list it came from is invalidated. The answer is built
         * here rather than by the default implementation, which would pass it through
         * [onAddMediaItems] and add the queue to the playlist a second time.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            AppLogger.d("$TAG SetMediaItems ${mediaItems.size} $startIndex")

            // TODO: It is weird that selection from the list of playable result in items of size 1, unless
            //       there is additional configuration somewhere to specify to load a whole playlist.

            if (mediaItems.size != 1) {
                val queue = mBrowseTree.getQueue(mediaItems.map { it.mediaId })
                mBrowseTree.setPlaylist(queue)
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(queue.children, startIndex, startPositionMs)
                )
            }
            val mediaId = mediaItems[0].mediaId
            // The selection carries no parent, and the list the client browsed last is the one it was made from.
            val selection = mBrowseTree.getSelection(mediaId, mCurrentParentId)
            var items = selection.children

            // This is a case when Google Assistant ask to play music but nothing selected.
            if (items.isEmpty()) {
                val newItems = mutableListOf<MediaItem>()
                for (item in mBrowseTree.getMediaItemsByMediaId(mCurrentParentId)) {
                    // Skip media items that can not be played.
                    if (item.localConfiguration == null) {
                        val msg = try {
                            "${item.mediaId}-${IntentUtils.bundleToString(item.mediaMetadata.toBundle())}"
                        } catch (exception: Exception) {
                            "Can't create a message: ${exception.message}"
                        }
                        AppLogger.w(msg)
                        continue
                    }
                    newItems.add(item)
                }
                // Still empty list? Add something to listen.
                if (newItems.isEmpty()) {
                    newItems.add(MediaItemBuilder.buildDefaultPlayable())
                }
                items = newItems

                // The default playable is no station, so the tree finds no station for it.
                val radioStations = mBrowseTree.getQueue(items.map { it.mediaId }).radioStations
                mBrowseTree.setPlaylist(BrowseTree.BrowseData(items, radioStations))
                mPlayer.setMediaItems(items, 0, C.TIME_UNSET)
            } else {
                mBrowseTree.setPlaylist(selection)
            }

            var pos = 0
            for ((idx, i) in items.withIndex()) {
                if (i.mediaId == mediaId) {
                    pos = idx
                    break
                }
            }
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, pos, C.TIME_UNSET))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            AppLogger.d("$TAG CustomCommand ${customCommand.customAction}")
            return when (customCommand.customAction) {
                CMD_NET_CHANGED -> {
                    if (mPresenter.isPlaybackBlockedByMobileNetwork()) {
                        handleMobileNetworkBlocked()
                    } else {
                        resumeStationTheNetworkStopped()
                    }
                    return mSessionCmdSuccess
                }

                CMD_MARK_FAVORITE -> {
                    session.setCustomLayout(listOf(mUnmarkFavoriteButton))
                    return handleFavorite(args, true)
                }

                CMD_UNMARK_FAVORITE -> {
                    session.setCustomLayout(listOf(mMarkFavoriteButton))
                    return handleFavorite(args, false)
                }

                CMD_STOP_SERVICE -> {
                    closeService()
                    return mSessionCmdSuccess
                }

                CMD_TOGGLE_LAST_PLAYED_ITEM -> {
                    togglePlayableItem()
                    return mSessionCmdSuccess
                }

                CMD_UPDATE_SORT_IDS -> {
                    val mediaId = args.getString(OpenRadioStore.EXTRA_KEY_MEDIA_IDS)
                    val sortId = args.getInt(OpenRadioStore.EXTRA_KEY_SORT_IDS, 0)
                    val categoryMediaId =
                        args.getString(OpenRadioStore.EXTRA_KEY_MEDIA_ID) ?: MediaId.MEDIA_ID_ROOT
                    if (mediaId.isNullOrEmpty()) {
                        return mSessionCmdNotSupported
                    }
                    mPresenter.updateSortIds(
                        mediaId, sortId, categoryMediaId,
                    )
                    notifyChildrenChanged(categoryMediaId)
                    return mSessionCmdSuccess
                }

                CMD_CLEAR_CACHE -> {
                    handleClearCache(applicationContext)
                    return mSessionCmdSuccess
                }

                CMD_MASTER_VOLUME_CHANGED -> {
                    handleMasterVolumeChanged(
                        applicationContext,
                        args.getInt(OpenRadioStore.EXTRA_KEY_MASTER_VOLUME, MASTER_VOLUME_DEFAULT)
                    )
                    return mSessionCmdSuccess
                }

                CMD_UPDATE_TREE -> {
                    notifyChildrenChanged(MediaId.MEDIA_ID_ROOT)
                    notifyChildrenChanged(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
                    return mSessionCmdSuccess
                }

                else -> {
                    mSessionCmdNotSupported
                }
            }
        }

        /**
         * Runs [action] on the children that answer [page] of [parentId] for [client] once they
         * are available, through a future that the result listener completes.
         *
         * The first page is the whole list the tree holds, or a list fetched anew. A later page is
         * the part of the list [client] has not been answered with, and only a client that has
         * the whole list has the page after it fetched: the clients share the list, not their
         * place in it, which [mClientPages] keeps for each (fnr-1557f).
         *
         * The children are taken from the list the page was stored in, not read from the tree
         * afterwards: the main thread may invalidate the node between the two, and a read then
         * would find no list to answer from (fnr-1bz3d).
         *
         * The return type is pinned to [LibraryResult] rather than left open so that a parent id
         * no command answers can be completed with an error. Leaving that future unset would hang
         * the browse request for as long as the client keeps waiting.
         */
        private fun <V : Any> callWhenSourceReady(
            client: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            action: (children: List<MediaItem>) -> LibraryResult<V>
        ): ListenableFuture<LibraryResult<V>> {
            mCurrentParentId = parentId
            // If Parent Id contains Country Code - use it in the API.
            val countryCode = MediaId.getCountryCode(parentId, mPresenter.getCountryCode())
            // A country stations list built for a country no longer stored is not the node's list
            // any more. The new country's list starts at its first page, and a later page of the
            // old one gets nothing, so nothing of the new country is appended to the list the
            // client holds.
            val isOtherCountry =
                parentId == MediaId.MEDIA_ID_COUNTRY_STATIONS && countryCode != mCountryStationsCode
            if (isOtherCountry) {
                mBrowseTree.invalidate(parentId)
            }
            val held = mBrowseTree.getPagedList(parentId)
            val fetchedPage = if (page == 0) {
                if (held != null
                    //  Do not cache these categories, always fetch fresh data.
                    && parentId != MediaId.MEDIA_ID_FAVORITES_LIST
                    && parentId != MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
                ) {
                    return Futures.immediateFuture(action(mClientPages.answerWhole(client, parentId, held)))
                }
                UrlLayer.FIRST_PAGE_INDEX
            } else {
                when (val next = mClientPages.next(client, parentId, held)) {
                    is ClientPages.Next.Answer -> return Futures.immediateFuture(action(next.items))
                    is ClientPages.Next.Fetch -> next.page
                }
            }
            val id = MediaId.getId(parentId, AppUtils.EMPTY_STRING)
            val command = mPresenter.getMediaItemCommand(id)
            AppLogger.d("$TAG get source for $id by $command")
            // Use this feature object to return response of the async nature in order it issued via this method.
            val future = SettableFuture.create<LibraryResult<V>>()
            val dependencies = MediaItemCommandDependencies(
                applicationContext, mPresenter, countryCode, parentId,
                fetchedPage, mIsRestoreState, Bundle(), mCommandScope,
                object : ResultListener {

                    override fun onResult(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int) {

                        if (items.isEmpty()) {
                            future.set(action(emptyList()))
                            return
                        }

                        mExecutorService.submit {

                            AppLogger.d("$TAG loaded [$pageNumber:${items.size}] for $parentId")
                            val browseData = BrowseTree.BrowseData(items, radioStations)
                            val children = if (pageNumber == 0) {
                                val stored = mBrowseTree.store(parentId, browseData)
                                // Marked only once the list is in the tree: marked before, a browse
                                // in between would take the old country's list, still held, for
                                // the new one's.
                                if (parentId == MediaId.MEDIA_ID_COUNTRY_STATIONS) {
                                    mCountryStationsCode = countryCode
                                }
                                mUiScope.launch { maybeCreateInitialPlaylist() }
                                mClientPages.answerWhole(client, parentId, stored)
                            } else {
                                // A later page is only fetched to continue the list held, so
                                // held is that list.
                                val stored = held?.let { mBrowseTree.append(parentId, it, browseData) }
                                mClientPages.answerRest(client, parentId, stored)
                            }

                            future.set(action(children))
                        }
                    }
                }
            )
            if (command != null) {
                dependencies.execute(
                    command,
                    MediaItemCommand.IUpdatePlaybackState { error ->
                        AppLogger.e("$TAG update playback state error $error")
                    }
                )
            } else {
                AppLogger.w("$TAG no command for parentId: $parentId")
                future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
            return future
        }

        private fun <V : Any> callWhenSearchReady(
            query: String,
            action: (size: Int) -> LibraryResult<V>
        ): ListenableFuture<LibraryResult<V>> {
            val id = MediaId.MEDIA_ID_SEARCH_FROM_SERVICE
            val future = SettableFuture.create<LibraryResult<V>>()
            val dependencies = MediaItemCommandDependencies(
                applicationContext, mPresenter, Country.COUNTRY_CODE_DEFAULT, id,
                UrlLayer.FIRST_PAGE_INDEX, mIsRestoreState, AppUtils.makeSearchQueryBundle(query), mCommandScope,

                object : ResultListener {

                    override fun onResult(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int) {

                        mExecutorService.submit {

                            mBrowseTree[query] = BrowseTree.BrowseData(items, radioStations)
                            val size = radioStations.size
                            val result = action(size)
                            future.set(result)
                        }
                    }
                }
            )
            dependencies.execute(
                mPresenter.getSearchCommand(),
                MediaItemCommand.IUpdatePlaybackState { error ->
                    AppLogger.e("$TAG update playback state error $error")
                }
            )
            return future
        }

        /**
         * Marks or unmarks the station [args] name as a favorite.
         *
         * The answer tells the three outcomes apart: success when the favorites store changed,
         * [SessionResult.RESULT_INFO_SKIPPED] when the station already was in the requested
         * state, and [SessionResult.RESULT_ERROR_NOT_SUPPORTED] when the station is unknown. A
         * caller that only wants the station in that state, such as a button pressed twice or
         * one of two racing clients, can take a skip as success; a caller that sent the wrong
         * command or acted on stale metadata learns that nothing happened. Only a change is
         * followed by updating the browse tree, the player metadata and the subscribers, and the
         * store is written only once the station is known, so a refused command leaves it as it
         * found it.
         */
        private fun handleFavorite(args: Bundle, isFavorite: Boolean): ListenableFuture<SessionResult> {
            // The favorite button the session publishes, which is what the automotive UI shows,
            // names no station: it only ever concerns the now playing one. A command that does
            // name a station is about that station and no other.
            val mediaId = OpenRadioStore.extractMediaId(args).ifEmpty { mActiveRS.id }
            var rs = getRadioStationByMediaId(mediaId)
            // The active station is not always one the browse tree holds: the last played station
            // the service restores when it starts is held by nothing until a playlist is built
            // around it. The button is published for it from the start, and it concerns that
            // station.
            if (rs.isInvalid() && mediaId == mActiveRS.id) {
                rs = RadioStation.makeCopyInstance(mActiveRS)
            }
            if (rs.isInvalid()) {
                AppLogger.e("$TAG can't handle fav. for '$mediaId'")
                return mSessionCmdNotSupported
            }
            if (mPresenter.updateRadioStationFavorite(rs, isFavorite).not()) {
                AppLogger.i("$TAG fav. for '$mediaId' already $isFavorite, nothing changed")
                return mSessionCmdSkipped
            }
            mBrowseTree.getMediaItemByMediaId(mediaId)?.let {
                MediaItemHelper.updateFavoriteField(it.mediaMetadata, isFavorite)
            }
            val currentMediaItem = mPlayer.currentMediaItem
            if (currentMediaItem?.mediaId == mediaId) {
                MediaItemHelper.updateFavoriteField(currentMediaItem.mediaMetadata, isFavorite)
            }
            mPlayer.invalidateMetaData()
            notifyFavoritesChanged()
            return mSessionCmdSuccess
        }
    }

    companion object {

        const val CMD_MARK_FAVORITE = "com.github.jcszymansk.freenetradio.COMMAND.MARK_FAVORITE"
        const val CMD_UNMARK_FAVORITE = "com.github.jcszymansk.freenetradio.COMMAND.UNMARK_FAVORITE"
        const val CMD_NET_CHANGED = "com.github.jcszymansk.freenetradio.COMMAND.NET_CHANGED"
        const val CMD_STOP_SERVICE = "com.github.jcszymansk.freenetradio.COMMAND.STOP_SERVICE"
        const val CMD_TOGGLE_LAST_PLAYED_ITEM =
            "com.github.jcszymansk.freenetradio.COMMAND.TOGGLE_LAST_PLAYED_ITEM"
        const val CMD_UPDATE_SORT_IDS = "com.github.jcszymansk.freenetradio.COMMAND.UPDATE_SORT_IDS"
        const val CMD_CLEAR_CACHE = "com.github.jcszymansk.freenetradio.COMMAND.CLEAR_CACHE"
        const val CMD_MASTER_VOLUME_CHANGED =
            "com.github.jcszymansk.freenetradio.COMMAND.MASTER_VOLUME_CHANGED"
        const val CMD_UPDATE_TREE = "com.github.jcszymansk.freenetradio.COMMAND.UPDATE_TREE"

        /**
         * Session extras key holding the id of the service's active station, absent while it has none.
         */
        const val EXTRA_ACTIVE_STATION_ID = "com.github.jcszymansk.freenetradio.EXTRA.ACTIVE_STATION_ID"

        private lateinit var TAG: String

        const val MASTER_VOLUME_DEFAULT = 100
    }
}
