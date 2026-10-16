/*
 * Copyright 2017-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.service.player

import android.content.Context
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.StringRes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.session.MediaSession
import com.yuriy.openradio.R
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.media.BrowseTree
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.PlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min


/**
 * Created by Chernyshov Yurii
 * At Android Studio
 * On 11/04/17
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * Wrapper over ExoPlayer.
 *
 * @param mContext  Application context.
 * @param mListener Listener for the wrapper's events.
 * @param mIsPlaybackBlocked Whether the mobile-network policy forbids playback now, the check every
 * play request goes through. Asked on the main thread.
 * @param mStreamRetrySchedule When to try again a stream ExoPlayer gave up for a lost network, and
 * when to give the station up instead.
 */
@UnstableApi
class OpenRadioPlayer(
    private val mContext: Context,
    private val mListener: Listener,
    private val mEqualizerLayer: EqualizerLayer,
    private val mBrowseTree: BrowseTree,
    private val mIsPlaybackBlocked: () -> Boolean,
    private val mStreamRetrySchedule: StreamRetrySchedule = StreamRetrySchedule()
) : Player {
    /**
     * Listener for the main public events.
     */
    interface Listener {

        fun onHandledError(error: PlaybackException)

        fun onPlaybackStateChanged(mediaItem: MediaItem)

        /**
         * A retry of a stream the network stopped came due while the mobile-network policy forbids
         * playback, so the player did not make it. The station still awaits a network it may use,
         * and nothing tries it again until that network connects.
         */
        fun onStreamRetryBlocked()
    }

    /**
     * The active ExoPlayer.
     */
    private var mPlayer: Player

    private val mListeners = arrayListOf<Player.Listener>()

    private val mPlaylist: MutableList<MediaItem> = arrayListOf()

    /**
     * The last queue that held anything, as it stood before the latest change that could have
     * emptied it, at the item that was current then. It outlives the queue on purpose, see
     * [getResumption].
     */
    private var mLastQueue = MediaSession.MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)

    /**
     * Handler for the ExoPlayer to handle events.
     */
    private val mUiScope = CoroutineScope(Dispatchers.Main)

    /**
     * Listener of the ExoPlayer components events.
     */
    private val mComponentListener = ComponentListener()

    /**
     * Decides what every playback error means for the stream being played.
     */
    private val mErrorClassifier = PlaybackErrorClassifier()

    /**
     * Whether a regained network should restart the stream. The player, its listener and the
     * network policy pause write it and the connectivity receiver reads it, all on the main thread.
     */
    private var mNetworkRecovery = NetworkRecovery.IDLE

    /**
     * Whether the player was stopped while the service finds a stream to replace one it could not
     * read, and has not been prepared, stopped or paused since. The station is still meant to play
     * meanwhile. Written and read on the main thread.
     */
    private var mIsAwaitingStream = false

    /**
     * The pending retry of a stream ExoPlayer gave up for a lost network. A network-connected
     * event resumes such a stream too, but a phone that loses its signal in a dead zone usually
     * keeps reporting the network it had, so that event may never come (TASK-018). Pending only
     * while [mNetworkRecovery] awaits the network; [NO_STREAM_RETRY] when none is. Started and
     * cancelled on the main thread.
     */
    private var mStreamRetry: Job = NO_STREAM_RETRY

    private val mExoPlayer: Player by lazy {
        AppLogger.i("Init ExoPlayer")
        val trackSelector = DefaultTrackSelector(mContext)
        trackSelector.parameters = DefaultTrackSelector.Parameters.Builder(mContext).build()
        val builder = ExoPlayer.Builder(
            mContext, ExoPlayerUtils.buildRenderersFactory(mContext)
        )
        builder.setTrackSelector(trackSelector)
        builder.setMediaSourceFactory(DefaultMediaSourceFactory(ExoPlayerUtils.getDataSourceFactory(mContext)!!))
        builder.setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    AppPreferencesManager.getMinBuffer(mContext),
                    AppPreferencesManager.getMaxBuffer(mContext),
                    AppPreferencesManager.getPlayBuffer(mContext),
                    AppPreferencesManager.getPlayBufferRebuffer(mContext)
                )
                .build()
        )
        builder.setWakeMode(C.WAKE_MODE_NETWORK)
        // Do not handle this via player - it handles OFF but doesnt ON.
        //builder.setHandleAudioBecomingNoisy(true)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setFlags(0)
            .setUsage(C.USAGE_MEDIA)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            .build()
        builder.setAudioAttributes(audioAttributes, true)
        val player = builder.build()
        player.addListener(mComponentListener)
        player.playWhenReady = true
        player
    }

    init {
        mPlayer = mExoPlayer
        mPlayer.volume = AppPreferencesManager.getMasterVolume(
            mContext,
            OpenRadioService.MASTER_VOLUME_DEFAULT
        ).toFloat() / 100.0f
        mEqualizerLayer.init((mExoPlayer as ExoPlayer).audioSessionId)
    }

    fun getPlaylist() : List<MediaItem> {
        return ArrayList(mPlaylist)
    }

    /**
     * What a play request that finds the queue empty loads: the queue as it stood just before it
     * was emptied, at the item that was current then, which is what was playing. Media3 asks only
     * when the player has no current item, which for this player means an empty queue, so the
     * answer is not meant for a loaded one.
     */
    fun getResumption(): MediaSession.MediaItemsWithStartPosition {
        return mLastQueue
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Player interface
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        mPlayer.setAudioAttributes(audioAttributes, handleAudioFocus)
    }

    override fun setVolume(value: Float) {
        mPlayer.volume = value
    }

    override fun pause() {
        updateNetworkRecovery(mNetworkRecovery.onStopRequested(), "pause")
        forgetAwaitedStream("pause")
        mPlayer.pause()
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        replaceMediaItems(index, index + 1, mutableListOf(mediaItem))
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
        rememberQueue()
        mPlayer.replaceMediaItems(fromIndex, toIndex, mediaItems)
        // The player ignores a range that starts past the end of the queue and clamps one that runs
        // past it, so the copy does too.
        if (fromIndex > mPlaylist.size) {
            return
        }
        val range = mPlaylist.subList(fromIndex, min(toIndex, mPlaylist.size))
        range.clear()
        range.addAll(mediaItems)
    }

    override fun release() {
        mPlaylist.clear()
        mEqualizerLayer.deinit()
        reset()
        mPlayer.release()
    }

    override fun play() {
        mPlayer.play()
    }

    override fun prepare() {
        forgetAwaitedStream("prepare")
        // Whoever prepares the stream from outside, the user or the service resuming it on a
        // network that connected, starts a new attempt at it, which gets retries of its own.
        mStreamRetrySchedule.reset()
        mPlayer.prepare()
    }

    override fun getApplicationLooper(): Looper {
        return mPlayer.applicationLooper
    }

    override fun addListener(listener: Player.Listener) {
        mPlayer.addListener(listener)
        mListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        mPlayer.removeListener(listener)
        mListeners.remove(listener)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        AppLogger.d("$TAG setMediaItems ${mediaItems.size}")
        rememberQueue()
        forgetAwaitedStream("set media items")
        mPlayer.setMediaItems(mediaItems)
        mPlaylist.clear()
        mPlaylist.addAll(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        AppLogger.d("$TAG setMediaItems ${mediaItems.size} $resetPosition")
        rememberQueue()
        forgetAwaitedStream("set media items")
        mPlayer.setMediaItems(mediaItems, resetPosition)
        mPlaylist.clear()
        mPlaylist.addAll(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        AppLogger.d("$TAG setMediaItems ${mediaItems.size} $startIndex $startPositionMs")
        rememberQueue()
        forgetAwaitedStream("set media items")
        mPlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
        mPlaylist.clear()
        mPlaylist.addAll(mediaItems)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        AppLogger.d("$TAG setMediaItem $mediaItem")
        forgetAwaitedStream("set media items")
        mPlayer.setMediaItem(mediaItem)
        mPlaylist.clear()
        mPlaylist.add(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        AppLogger.d("$TAG setMediaItem $mediaItem $startPositionMs")
        forgetAwaitedStream("set media items")
        mPlayer.setMediaItem(mediaItem, startPositionMs)
        mPlaylist.clear()
        mPlaylist.add(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        AppLogger.d("$TAG setMediaItem $mediaItem $resetPosition")
        forgetAwaitedStream("set media items")
        mPlayer.setMediaItem(mediaItem, resetPosition)
        mPlaylist.clear()
        mPlaylist.add(mediaItem)
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        AppLogger.d("$TAG addMediaItem $mediaItem")
        mPlayer.addMediaItem(mediaItem)
        mPlaylist.add(mediaItem)
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        AppLogger.d("$TAG addMediaItem $index $mediaItem")
        mPlayer.addMediaItem(index, mediaItem)
        mPlaylist.add(index, mediaItem)
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        AppLogger.d("$TAG addMediaItems ${mediaItems.size}")
        mPlayer.addMediaItems(mediaItems)
        mPlaylist.addAll(mediaItems)
    }

    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
        AppLogger.d("$TAG addMediaItems $index ${mediaItems.size}")
        mPlayer.addMediaItems(index, mediaItems)
        mPlaylist.addAll(index, mediaItems)
    }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        mPlayer.moveMediaItem(currentIndex, newIndex)
        mPlaylist.add(min(newIndex, mPlaylist.size), mPlaylist.removeAt(currentIndex))
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        mPlayer.moveMediaItems(fromIndex, toIndex, newIndex)
        // The player clamps a range that runs past the end of the queue, so the copy does too.
        val range = mPlaylist.subList(min(fromIndex, mPlaylist.size), min(toIndex, mPlaylist.size))
        val movedItems = range.toList()
        range.clear()
        mPlaylist.addAll(min(newIndex, mPlaylist.size), movedItems)
    }

    override fun removeMediaItem(index: Int) {
        rememberQueue()
        mPlayer.removeMediaItem(index)
        mPlaylist.removeAt(index)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        rememberQueue()
        mPlayer.removeMediaItems(fromIndex, toIndex)
        val removedItemsLength = toIndex - fromIndex
        for (i in removedItemsLength - 1 downTo 0) {
            mPlaylist.removeAt(fromIndex + i)
        }
    }

    override fun clearMediaItems() {
        forgetAwaitedStream("clear media items")
        rememberQueue()
        mPlayer.clearMediaItems()
        mPlaylist.clear()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return mPlayer.isCommandAvailable(command)
    }

    override fun canAdvertiseSession(): Boolean {
        return mPlayer.canAdvertiseSession()
    }

    override fun getAvailableCommands(): Player.Commands {
        return mPlayer.availableCommands
    }

    override fun getPlaybackState(): Int {
        return mPlayer.playbackState
    }

    override fun getPlaybackSuppressionReason(): Int {
        return mPlayer.playbackSuppressionReason
    }

    override fun isPlaying(): Boolean {
        return mPlayer.isPlaying
    }

    override fun getPlayerError(): PlaybackException? {
        return mPlayer.playerError
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady.not()) {
            updateNetworkRecovery(mNetworkRecovery.onStopRequested(), "play when ready off")
            forgetAwaitedStream("play when ready off")
        }
        mPlayer.playWhenReady = playWhenReady
    }

    override fun getPlayWhenReady(): Boolean {
        return mPlayer.playWhenReady
    }

    override fun setRepeatMode(repeatMode: Int) {
        mPlayer.repeatMode = repeatMode
    }

    override fun getRepeatMode(): Int {
        return mPlayer.repeatMode
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        mPlayer.shuffleModeEnabled = shuffleModeEnabled
    }

    override fun getShuffleModeEnabled(): Boolean {
        return mPlayer.shuffleModeEnabled
    }

    override fun isLoading(): Boolean {
        return mPlayer.isLoading
    }

    override fun seekToDefaultPosition() {
        mPlayer.seekToDefaultPosition()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        mPlayer.seekToDefaultPosition(mediaItemIndex)
    }

    override fun seekTo(positionMs: Long) {
        mPlayer.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        mPlayer.seekTo(mediaItemIndex, positionMs)
    }

    override fun getSeekBackIncrement(): Long {
        return mPlayer.seekBackIncrement
    }

    override fun seekBack() {
        mPlayer.seekBack()
    }

    override fun getSeekForwardIncrement(): Long {
        return mPlayer.seekForwardIncrement
    }

    override fun seekForward() {
        mPlayer.seekForward()
    }

    @Deprecated("Deprecated in Java")
    override fun hasPrevious(): Boolean {
        return mPlayer.hasPrevious()
    }

    @Deprecated("Deprecated in Java")
    override fun hasPreviousWindow(): Boolean {
        return mPlayer.hasPreviousWindow()
    }

    override fun hasPreviousMediaItem(): Boolean {
        return mPlayer.hasPreviousMediaItem()
    }

    @Deprecated("Deprecated in Java")
    override fun previous() {
        mPlayer.previous()
    }

    @Deprecated("Deprecated in Java")
    override fun seekToPreviousWindow() {
        mPlayer.seekToPreviousWindow()
    }

    override fun seekToPreviousMediaItem() {
        mPlayer.seekToPreviousMediaItem()
    }

    override fun getMaxSeekToPreviousPosition(): Long {
        return mPlayer.maxSeekToPreviousPosition
    }

    override fun seekToPrevious() {
        mPlayer.seekToPrevious()
    }

    @Deprecated("Deprecated in Java")
    override fun hasNext(): Boolean {
        return mPlayer.hasNext()
    }

    @Deprecated("Deprecated in Java")
    override fun hasNextWindow(): Boolean {
        return mPlayer.hasNextWindow()
    }

    override fun hasNextMediaItem(): Boolean {
        return mPlayer.hasNextMediaItem()
    }

    @Deprecated("Deprecated in Java")
    override fun next() {
        mPlayer.next()
    }

    @Deprecated("Deprecated in Java")
    override fun seekToNextWindow() {
        mPlayer.seekToNextWindow()
    }

    override fun seekToNextMediaItem() {
        mPlayer.seekToNextMediaItem()
    }

    override fun seekToNext() {
        mPlayer.seekToNext()
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        mPlayer.playbackParameters = playbackParameters
    }

    override fun setPlaybackSpeed(speed: Float) {
        mPlayer.setPlaybackSpeed(speed)
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return mPlayer.playbackParameters
    }

    override fun stop() {
        updateNetworkRecovery(mNetworkRecovery.onStopRequested(), "stop")
        forgetAwaitedStream("stop")
        mPlayer.stop()
    }

    override fun getCurrentTracks(): Tracks {
        return mPlayer.currentTracks
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        return mPlayer.trackSelectionParameters
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        mPlayer.trackSelectionParameters = parameters
    }

    override fun getMediaMetadata(): MediaMetadata {
        return mPlayer.mediaMetadata
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return mPlayer.playlistMetadata
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        mPlayer.playlistMetadata = mediaMetadata
    }

    override fun getCurrentManifest(): Any? {
        return mPlayer.currentManifest
    }

    override fun getCurrentTimeline(): Timeline {
        return mPlayer.currentTimeline
    }

    override fun getCurrentPeriodIndex(): Int {
        return mPlayer.currentPeriodIndex
    }

    @Deprecated("Deprecated in Java")
    override fun getCurrentWindowIndex(): Int {
        return mPlayer.currentWindowIndex
    }

    override fun getCurrentMediaItemIndex(): Int {
        return mPlayer.currentMediaItemIndex
    }

    @Deprecated("Deprecated in Java")
    override fun getNextWindowIndex(): Int {
        return mPlayer.nextWindowIndex
    }

    override fun getNextMediaItemIndex(): Int {
        return mPlayer.nextMediaItemIndex
    }

    @Deprecated("Deprecated in Java")
    override fun getPreviousWindowIndex(): Int {
        return mPlayer.previousWindowIndex
    }

    override fun getPreviousMediaItemIndex(): Int {
        return mPlayer.previousMediaItemIndex
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return mPlayer.currentMediaItem
    }

    override fun getMediaItemCount(): Int {
        return mPlayer.mediaItemCount
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        return mPlayer.getMediaItemAt(index)
    }

    override fun getDuration(): Long {
        return mPlayer.duration
    }

    override fun getCurrentPosition(): Long {
        return mPlayer.currentPosition
    }

    override fun getBufferedPosition(): Long {
        return mPlayer.bufferedPosition
    }

    override fun getBufferedPercentage(): Int {
        return mPlayer.bufferedPercentage
    }

    override fun getTotalBufferedDuration(): Long {
        return mPlayer.totalBufferedDuration
    }

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowDynamic(): Boolean {
        return mPlayer.isCurrentWindowDynamic
    }

    override fun isCurrentMediaItemDynamic(): Boolean {
        return mPlayer.isCurrentMediaItemDynamic
    }

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowLive(): Boolean {
        return mPlayer.isCurrentWindowLive
    }

    override fun isCurrentMediaItemLive(): Boolean {
        return mPlayer.isCurrentMediaItemLive
    }

    override fun getCurrentLiveOffset(): Long {
        return mPlayer.currentLiveOffset
    }

    @Deprecated("Deprecated in Java")
    override fun isCurrentWindowSeekable(): Boolean {
        return mPlayer.isCurrentWindowSeekable
    }

    override fun isCurrentMediaItemSeekable(): Boolean {
        return mPlayer.isCurrentMediaItemSeekable
    }

    override fun isPlayingAd(): Boolean {
        return mPlayer.isPlayingAd
    }

    override fun getCurrentAdGroupIndex(): Int {
        return mPlayer.currentAdGroupIndex
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return mPlayer.currentAdIndexInAdGroup
    }

    override fun getContentDuration(): Long {
        return mPlayer.contentDuration
    }

    override fun getContentPosition(): Long {
        return mPlayer.contentPosition
    }

    override fun getContentBufferedPosition(): Long {
        return mPlayer.contentBufferedPosition
    }

    override fun getAudioAttributes(): AudioAttributes {
        return mPlayer.audioAttributes
    }

    override fun getVolume(): Float {
        return mPlayer.volume
    }

    override fun clearVideoSurface() {
        mPlayer.clearVideoSurface()
    }

    override fun clearVideoSurface(surface: Surface?) {
        mPlayer.clearVideoSurface(surface)
    }

    override fun setVideoSurface(surface: Surface?) {
        mPlayer.setVideoSurface(surface)
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        mPlayer.setVideoSurfaceHolder(surfaceHolder)
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        mPlayer.clearVideoSurfaceHolder(surfaceHolder)
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        mPlayer.setVideoSurfaceView(surfaceView)
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        mPlayer.clearVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        mPlayer.setVideoTextureView(textureView)
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        mPlayer.clearVideoTextureView(textureView)
    }

    override fun getVideoSize(): VideoSize {
        return mPlayer.videoSize
    }

    override fun getSurfaceSize(): Size {
        return mPlayer.surfaceSize
    }

    override fun getCurrentCues(): CueGroup {
        return mPlayer.currentCues
    }

    override fun getDeviceInfo(): DeviceInfo {
        return mPlayer.deviceInfo
    }

    override fun getDeviceVolume(): Int {
        return mPlayer.deviceVolume
    }

    override fun isDeviceMuted(): Boolean {
        return mPlayer.isDeviceMuted
    }

    override fun setDeviceVolume(volume: Int, flags: Int) {
        mPlayer.setDeviceVolume(volume, flags)
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
        mPlayer.deviceVolume = volume
    }

    override fun increaseDeviceVolume(flags: Int) {
        mPlayer.increaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() {
        mPlayer.increaseDeviceVolume()
    }

    override fun decreaseDeviceVolume(flags: Int) {
        mPlayer.decreaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() {
        mPlayer.decreaseDeviceVolume()
    }

    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        mPlayer.setDeviceMuted(muted, flags)
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean) {
        mPlayer.isDeviceMuted = muted
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    fun invalidateMetaData() {
        mComponentListener.invalidateMetaData()
    }

    /**
     * Tells the user that no stream of the current station could be played.
     */
    fun reportUnplayableStream() {
        mComponentListener.reportUnplayableStream()
    }

    /**
     * Resets the player to its uninitialized state.
     */
    private fun reset() {
        AppLogger.d("$TAG reset")
        updateNetworkRecovery(mNetworkRecovery.onStopRequested(), "reset")
        forgetAwaitedStream("reset")
        stopCurrentPlayer()
    }

    /**
     * Pauses because the current network may not be used for playback. The network, not the user,
     * stopped the station, so a station the user wanted playing resumes once a network it may use
     * connects, while a station the user had paused stays paused.
     *
     * The player is stopped as well as paused, keeping its media items so that prepare() and
     * play() bring the station back. A paused ExoPlayer keeps loading until its buffer is full and
     * keeps the connection open, which spends the mobile data the policy exists to save; only a
     * stop releases the media source, and with it the connection. Media3 removes the playback
     * notification while the player is stopped, and shows it again once the station resumes.
     */
    fun pauseForNetworkPolicy() {
        updateNetworkRecovery(
            mNetworkRecovery.onPolicyPause(
                NetworkRecovery.isStreamPlayRequested(
                    mPlayer.playWhenReady, mPlayer.playbackState, mIsAwaitingStream
                )
            ),
            "network policy pause"
        )
        forgetAwaitedStream("network policy pause")
        // A station that is still awaiting the network stays one to resume on a network it may
        // use, but it must not be tried on the network it was paused for.
        cancelStreamRetry("network policy pause")
        mPlayer.pause()
        mPlayer.stop()
    }

    /**
     * Stops a stream the player could not read while the service finds the stream to play in its
     * place: by reading the station url as a playlist, or by taking the next stream that playlist
     * named. The user still wants the station playing, so this is not a stop for
     * [NetworkRecovery]: a policy pause that lands before the replacement starts resumes the
     * station once a network it may use connects.
     */
    fun stopForReplacementStream() {
        AppLogger.i("$TAG stop for a replacement stream, recovery stays $mNetworkRecovery")
        mIsAwaitingStream = true
        mPlayer.stop()
    }

    /**
     * Puts [mediaItem], the stream the service found for the current station after
     * [stopForReplacementStream], in place of the one the player could not read, and tells whether
     * to start it.
     *
     * The station keeps the stream either way, so playing it later does not search again. It
     * starts only while the station is still meant to play: a pause stays a pause, whether a
     * controller sent it or the service paused the player itself on noisy audio or a disconnected
     * Bluetooth device, which starts no new play request and so lets the search answer
     * (TASK-105). Losing the audio focus for good pauses the player in the same way.
     *
     * @return Whether the service should prepare and play the station now.
     */
    fun replaceAwaitedStream(mediaItem: MediaItem): Boolean {
        replaceMediaItem(mPlayer.currentMediaItemIndex, mediaItem)
        if (mPlayer.playWhenReady.not()) {
            AppLogger.i("$TAG hold the replacement stream of a paused station")
            forgetAwaitedStream("held replacement")
            return false
        }
        return true
    }

    /**
     * Whether a regained network connection should start playback again, which it should only for
     * a station the network stopped while the user wanted it playing.
     */
    fun resumesOnReconnect(): Boolean {
        return mNetworkRecovery.resumesOnReconnect
    }

    /**
     * Keeps the queue before a change that may empty it, so that [getResumption] can still offer
     * it once the change has. An empty queue leaves the one kept earlier in place.
     */
    private fun rememberQueue() {
        if (mPlaylist.isEmpty()) {
            return
        }
        mLastQueue = MediaSession.MediaItemsWithStartPosition(
            ArrayList(mPlaylist), mPlayer.currentMediaItemIndex, C.TIME_UNSET
        )
    }

    /**
     * Tries the stream again after [delayMs], unless something ends the wait for the network
     * first. A retry that fails for the network again takes the next one from
     * [mStreamRetrySchedule], until the schedule is spent.
     *
     * The retry asks the mobile-network policy when it is due, as a play request does. The
     * service pauses the station when the network monitor reports a blocked network, but that
     * report may come after the retry, or not at all when the default network changes without
     * the event the monitor listens for (fnr-7shj).
     */
    private fun scheduleStreamRetry(delayMs: Long) {
        cancelStreamRetry("a new retry")
        AppLogger.i("$TAG retry the stream in $delayMs ms")
        mStreamRetry = mUiScope.launch {
            delay(delayMs)
            mStreamRetry = NO_STREAM_RETRY
            if (mNetworkRecovery != NetworkRecovery.AWAITING_NETWORK
                || mPlayer.playbackState != Player.STATE_IDLE
            ) {
                AppLogger.d("$TAG stream retry not needed, recovery $mNetworkRecovery")
                return@launch
            }
            if (mIsPlaybackBlocked()) {
                AppLogger.i("$TAG stream retry blocked by the mobile network policy")
                mListener.onStreamRetryBlocked()
                return@launch
            }
            AppLogger.i("$TAG retry the stream the network stopped")
            mPlayer.prepare()
        }
    }

    private fun cancelStreamRetry(event: String) {
        val retry = mStreamRetry
        if (!retry.isActive) {
            return
        }
        AppLogger.i("$TAG cancel the stream retry on $event")
        retry.cancel()
        mStreamRetry = NO_STREAM_RETRY
    }

    private fun forgetAwaitedStream(event: String) {
        if (mIsAwaitingStream) {
            AppLogger.i("$TAG no longer awaiting a stream on $event")
            mIsAwaitingStream = false
        }
    }

    private fun updateNetworkRecovery(next: NetworkRecovery, event: String) {
        val previous = mNetworkRecovery
        mNetworkRecovery = next
        if (next != NetworkRecovery.AWAITING_NETWORK) {
            cancelStreamRetry(event)
            mStreamRetrySchedule.reset()
        }
        if (previous != next) {
            AppLogger.i("$TAG network recovery $previous -> $next on $event")
        } else {
            AppLogger.d("$TAG network recovery stays $next on $event")
        }
    }

    private fun stopCurrentPlayer() {
        mPlayer.clearMediaItems()
        mPlayer.stop()
    }

    /**
     * Listener class for the players components events.
     */
    private inner class ComponentListener : Player.Listener {

        private var mStreamMetadata = AppUtils.EMPTY_STRING

        private val mBufferingLabel = mContext.getString(R.string.buffering_infinite)

        private val mLiveStreamLabel = mContext.getString(R.string.media_description_default)

        private val mTrackNumber = AtomicInteger(0)

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                var title = AppUtils.EMPTY_STRING
                // See https://en.wikipedia.org/wiki/ID3#ID3v2_frame_specification
                val msg = "Metadata entry:$entry"
                AppLogger.d(msg)
                when (entry) {
                    is IcyInfo -> {
                        title = entry.title ?: AppUtils.EMPTY_STRING
                    }

                    is TextInformationFrame -> {
                        when (entry.id) {
                            ExoPlayerUtils.METADATA_ID_TT2,
                            ExoPlayerUtils.METADATA_ID_TIT2 -> {
                                val values = entry.values
                                if (values.isNotEmpty()) {
                                    title = values[0]
                                }
                            }
                        }
                    }

                    else -> {
                        AppLogger.d(msg)
                    }
                }
                if (title.isEmpty()) {
                    continue
                }
                title = title.trim { it <= ' ' }
                if (title == mStreamMetadata) {
                    continue
                }
                updateStreamMetadata(title)
            }
        }

        override fun onPlaybackStateChanged(playerState: Int) {
            val mediaItem = mPlayer.currentMediaItem
            AppLogger.d(
                "$TAG playback ${PlayerUtils.playerStateToString(playerState)} for $mediaItem"
            )
            mediaItem?.let {
                mListener.onPlaybackStateChanged(it)
            }
            when (playerState) {
                Player.STATE_READY -> {
                    mErrorClassifier.reset()
                    updateNetworkRecovery(mNetworkRecovery.onPlaybackReady(), "ready")
                }

                Player.STATE_BUFFERING -> {
                    updateStreamMetadata(mBufferingLabel)
                }

                else -> {
                    //
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                updateNetworkRecovery(mNetworkRecovery.onPlayRequested(), "play requested")
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (player.isPlaying && mStreamMetadata == mBufferingLabel) {
                updateStreamMetadata(mLiveStreamLabel)
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                && events.contains(Player.EVENT_MEDIA_METADATA_CHANGED).not()
            ) {
                // Ensure metadata listeners are notified when a media item changes.
                if (mPlaylist.isNotEmpty()) {
                    for (listener in mListeners) {
                        listener.onMediaMetadataChanged(
                            mPlaylist[player.currentMediaItemIndex].mediaMetadata
                        )
                    }
                }
            }
        }

        override fun onPlayerError(exception: PlaybackException) {
            AppLogger.e("$TAG onPlayerError", exception)
            when (val decision = mErrorClassifier.classify(exception)) {
                PlaybackErrorDecision.NetworkLost -> {
                    updateNetworkRecovery(
                        mNetworkRecovery.onNetworkLost(mPlayer.playWhenReady), "network lost"
                    )
                    if (mNetworkRecovery == NetworkRecovery.AWAITING_NETWORK) {
                        retryStreamTheNetworkStopped()
                    } else {
                        updateStreamMetadata(mContext.getString(R.string.media_stream_network_failed))
                    }
                }

                is PlaybackErrorDecision.StreamRejected -> {
                    updateStreamMetadata(mContext.getString(toMessageRes(decision.reason)))
                }

                PlaybackErrorDecision.UnsupportedFormat -> {
                    mListener.onHandledError(exception)
                }

                PlaybackErrorDecision.WithinErrorBudget -> {
                    AppLogger.i("$TAG prepare the stream again after a tolerated error")
                    mPlayer.prepare()
                }

                PlaybackErrorDecision.Unrecoverable -> {
                    updateStreamMetadata(mContext.getString(R.string.media_stream_error))
                }
            }
        }

        fun invalidateMetaData() {
            updateStreamMetadata(mStreamMetadata)
        }

        /**
         * Tries again a stream the network stopped while the user wanted it playing, or gives the
         * station up once [mStreamRetrySchedule] is spent. Giving up stops only the player's own
         * retries: the station stays the one to resume on a network that connects, since the
         * outage may have been a real one that outlasted them.
         */
        private fun retryStreamTheNetworkStopped() {
            when (val retry = mStreamRetrySchedule.next()) {
                is StreamRetry.After -> {
                    scheduleStreamRetry(retry.delayMs)
                    updateStreamMetadata(mContext.getString(R.string.media_stream_network_failed))
                }

                StreamRetry.Exhausted -> {
                    AppLogger.w("$TAG stream retries spent, give the station up")
                    reportUnplayableStream()
                }
            }
        }

        fun reportUnplayableStream() {
            updateStreamMetadata(mContext.getString(R.string.media_stream_unplayable))
        }

        private fun updateStreamMetadata(msg: String) {
            mStreamMetadata = msg
            mUiScope.launch {
                // TODO: Need to find out how to break de-synchronization between report of metadata and
                //       invocation of this code in coroutine.
                val idx = mPlayer.currentMediaItemIndex
                if (idx >= mPlaylist.size) {
                    AppLogger.e("Update metadata, idx out of bounds")
                    return@launch
                }

                val radioStation = mBrowseTree.getRadioStationByMediaId(mPlayer.currentMediaItem?.mediaId ?: "")
                val metadata = mPlaylist[idx].mediaMetadata
                    .buildUpon()
                    .setSubtitle(msg)
                    // For Automotive:
                    .setArtist(msg)
                    // For Automotive:
                    .setAlbumTitle(radioStation.country)
                    .setExtras(mediaMetadata.extras)
                    // Use this incremental trick number to trigger metadata updates
                    .setTrackNumber(mTrackNumber.getAndIncrement())
                    .build()
                for (listener in mListeners) {
                    listener.onMediaMetadataChanged(metadata)
                }

                currentMediaItem?.let {
                    val curIndx = currentMediaItemIndex
                    val newItem = it.buildUpon().setMediaMetadata(metadata).build()
                    replaceMediaItem(curIndx, newItem)
                }
            }
        }

        @StringRes
        private fun toMessageRes(reason: StreamRejectionReason): Int {
            return when (reason) {
                StreamRejectionReason.FORBIDDEN -> R.string.media_stream_http_403
                StreamRejectionReason.NOT_FOUND -> R.string.media_stream_http_404
                StreamRejectionReason.OTHER -> R.string.media_stream_error
            }
        }
    }

    companion object {
        /**
         * String tag to use in logs.
         */
        private const val TAG = "ORP"

        /**
         * A retry that has already finished, standing for "no retry pending" so that
         * [mStreamRetry] is never null: it is not active and cancelling it does nothing.
         */
        private val NO_STREAM_RETRY: Job = Job().apply { complete() }
    }
}
