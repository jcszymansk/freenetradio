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

package com.yuriy.openradio.shared.service

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Drives a real [MediaBrowser] against the real [OpenRadioService].
 *
 * The session and its player live on the application main looper, so every browser call has to be
 * issued there while the calling test thread waits for the returned future. Both halves of that
 * dance are wrapped here, always with an explicit deadline: several browse nodes answer through a
 * [com.google.common.util.concurrent.SettableFuture] that production code can leave unset, and an
 * unbounded wait would hang the whole instrumentation run instead of failing one test.
 *
 * A [MediaBrowser] is also a [androidx.media3.session.MediaController], so the same connection
 * carries transport control and the player state the session pushes back. Browsing and playing
 * share one connection here because they share one in the application too: the phone UI selects a
 * station from the list it just browsed, and the service answers that selection out of the browse
 * tree the browse filled.
 */
@UnstableApi
internal class ServiceBrowser {

    private val mInstrumentation = InstrumentationRegistry.getInstrumentation()

    private val mChildrenChanges = LinkedBlockingQueue<ChildrenChanged>()

    private val mSearchChanges = LinkedBlockingQueue<SearchResultChanged>()

    private var mBrowser: MediaBrowser? = null

    private val mListener = object : MediaBrowser.Listener {

        override fun onChildrenChanged(
            browser: MediaBrowser,
            parentId: String,
            itemCount: Int,
            params: LibraryParams?
        ) {
            mChildrenChanges.add(ChildrenChanged(parentId, itemCount))
        }

        override fun onSearchResultChanged(
            browser: MediaBrowser,
            query: String,
            itemCount: Int,
            params: LibraryParams?
        ) {
            mSearchChanges.add(SearchResultChanged(query, itemCount))
        }
    }

    private val mPlayerEvents = PlayerEvents()

    /**
     * Connects to the service, and refuses to hand over one that the caller's first browse would
     * start playing.
     *
     * Every class that reaches the service browses page 0 of something straight after connecting,
     * and a page-0 browse is what posts `maybeCreateInitialPlaylist`. When the service holds an
     * active station and the player's queue is empty, that path downloads the provider's new
     * stations and plays whatever comes back, which is the one way this suite could put internet
     * audio through the speakers. The service outlives every test class, so whether it is in that
     * state is decided by whichever class ran before, and asking here is what turns an accident of
     * ordering into a checked precondition.
     *
     * A refused connection is released before the refusal is thrown, so a teardown asking
     * [isConnected] sees nothing to undo and the refusal stays the failure that is reported.
     */
    fun connect() {
        val context = mInstrumentation.targetContext
        val holder = AtomicReference<ListenableFuture<MediaBrowser>>()
        mInstrumentation.runOnMainSync {
            holder.set(
                MediaBrowser.Builder(
                    context,
                    SessionToken(context, ComponentName(context, OpenRadioService::class.java))
                )
                    .setListener(mListener)
                    .buildAsync()
            )
        }
        mBrowser = holder.get().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        onMain { addListener(mPlayerEvents) }
        if (canABrowseStartPlayback()) {
            val activeStationId = activeStationId()
            release()
            throw AssertionError(
                "The service holds active station $activeStationId and an empty queue, so the " +
                    "first page-0 browse would download new stations and start playing them. A " +
                    "class that ran before this one left it that way. A class that parks through " +
                    "LocalStationsFixture fails its own teardown when it cannot park, so look " +
                    "for that failure first."
            )
        }
    }

    /**
     * @return whether a page-0 browse would make the service build a playlist and start playing,
     *   which takes an empty queue and an active station. The service publishes the active
     *   station's id in its session extras for exactly this question.
     */
    fun canABrowseStartPlayback(): Boolean {
        return mediaItemCount() == 0 && holdsAnActiveStation()
    }

    /**
     * @return the id the service publishes for its active station, which it has to hold one for.
     */
    fun activeStationId(): String {
        return read { sessionExtras.getString(OpenRadioService.EXTRA_ACTIVE_STATION_ID) }
            ?: throw AssertionError("The service publishes no active station")
    }

    private fun holdsAnActiveStation(): Boolean {
        return read { sessionExtras.containsKey(OpenRadioService.EXTRA_ACTIVE_STATION_ID) }
    }

    fun release() {
        val browser = mBrowser ?: return
        mBrowser = null
        mInstrumentation.runOnMainSync {
            browser.removeListener(mPlayerEvents)
            browser.release()
        }
    }

    /**
     * @return whether [connect] has completed and [release] has not been called since.
     *
     * Every other call here throws without a connection, which is right in a test body: a browse
     * that cannot reach the service is a failure. A teardown running after a setup that never got
     * this far is the one caller that has to ask first, because there the throw would replace the
     * failure that stopped the setup.
     */
    fun isConnected(): Boolean {
        return mBrowser != null
    }

    fun libraryRoot(): LibraryResult<MediaItem> {
        return await { getLibraryRoot(null) }
    }

    fun childrenResult(
        parentId: String,
        page: Int = 0,
        timeoutSeconds: Long = TIMEOUT_SECONDS
    ): LibraryResult<ImmutableList<MediaItem>> {
        return await(timeoutSeconds) {
            getChildren(parentId, page, DependencyRegistryCommon.PAGE_SIZE, null)
        }
    }

    /**
     * @return the children of [parentId], failing the test when the service answers with an error.
     */
    fun children(parentId: String): List<MediaItem> {
        val result = childrenResult(parentId)
        check(result.resultCode == LibraryResult.RESULT_SUCCESS) {
            "Loading children of $parentId failed with ${result.resultCode}"
        }
        return result.value ?: ImmutableList.of()
    }

    fun mediaIds(parentId: String): List<String> {
        return children(parentId).map { it.mediaId }
    }

    fun item(mediaId: String): LibraryResult<MediaItem> {
        return await { getItem(mediaId) }
    }

    fun subscribe(parentId: String): LibraryResult<Void> {
        return await { subscribe(parentId, null) }
    }

    fun unsubscribe(parentId: String): LibraryResult<Void> {
        return await { unsubscribe(parentId) }
    }

    fun search(query: String): LibraryResult<Void> {
        return await { search(query, null) }
    }

    fun searchResult(query: String): LibraryResult<ImmutableList<MediaItem>> {
        return await { getSearchResult(query, 0, DependencyRegistryCommon.PAGE_SIZE, null) }
    }

    fun command(action: String, args: Bundle = Bundle()): SessionResult {
        return await { sendCustomCommand(SessionCommand(action, Bundle()), args) }
    }

    fun availableSessionCommands(): SessionCommands {
        val holder = AtomicReference<SessionCommands>()
        mInstrumentation.runOnMainSync { holder.set(browser().availableSessionCommands) }
        return holder.get()
    }

    /**
     * Waits for a children-changed push for [parentId], discarding notifications for other nodes.
     */
    fun awaitChildrenChanged(parentId: String): ChildrenChanged {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) {
                break
            }
            val change = mChildrenChanges.poll(remaining, TimeUnit.NANOSECONDS) ?: break
            if (change.parentId == parentId) {
                return change
            }
        }
        throw AssertionError("No children-changed notification arrived for $parentId")
    }

    fun awaitSearchResultChanged(query: String): SearchResultChanged {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) {
                break
            }
            val change = mSearchChanges.poll(remaining, TimeUnit.NANOSECONDS) ?: break
            if (change.query == query) {
                return change
            }
        }
        throw AssertionError("No search-result-changed notification arrived for '$query'")
    }

    fun forgetNotifications() {
        mChildrenChanges.clear()
        mSearchChanges.clear()
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Transport
    //
    // A MediaBrowser is a MediaController, so the same connection that browses also plays. Every
    // call below goes through onMain for the same reason the browse calls do: the session and its
    // player live on the application main looper, and a controller call issued from the test
    // thread is rejected. Reads go through it too, because what a controller reports is state it
    // was pushed from the session, and that push happens on that looper.
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Hands the session a single item, which is what selecting a station in a list does. The
     * service answers by expanding it into the playlist the item belongs to.
     */
    fun setMediaItem(mediaItem: MediaItem) {
        onMain { this.setMediaItem(mediaItem) }
    }

    fun prepareAndPlay() {
        onMain {
            this.prepare()
            this.play()
        }
    }

    fun play() {
        onMain { this.play() }
    }

    fun pause() {
        onMain { this.pause() }
    }

    fun stop() {
        onMain { this.stop() }
    }

    fun seekToNext() {
        onMain { seekToNextMediaItem() }
    }

    fun seekToPrevious() {
        onMain { seekToPreviousMediaItem() }
    }

    fun clearMediaItems() {
        onMain { this.clearMediaItems() }
    }

    fun playbackState(): Int {
        return read { playbackState }
    }

    fun isPlaying(): Boolean {
        return read { isPlaying }
    }

    fun currentMediaId(): String? {
        return read { currentMediaItem?.mediaId }
    }

    fun currentMediaItemIndex(): Int {
        return read { currentMediaItemIndex }
    }

    fun mediaItemCount(): Int {
        return read { mediaItemCount }
    }

    fun queueMediaIds(): List<String> {
        return read { (0 until mediaItemCount).map { getMediaItemAt(it).mediaId } }
    }

    fun currentMediaItemUri(): String? {
        return read { currentMediaItem?.localConfiguration?.uri?.toString() }
    }

    fun playerError(): PlaybackException? {
        return read { playerError }
    }

    /**
     * Waits until the controller reports a state that satisfies [predicate].
     *
     * The controller's view of the player is pushed to it, so nothing it reports is true the
     * instant a transport call returns; a test that asserts straight after one is asserting the
     * state before its own command. [description] names what was being waited for, because the
     * failure is otherwise a bare timeout.
     */
    fun awaitPlayback(
        description: String,
        timeoutSeconds: Long = TIMEOUT_SECONDS,
        predicate: () -> Boolean
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (true) {
            if (predicate()) {
                return
            }
            if (System.nanoTime() >= deadline) {
                throw AssertionError(
                    "Timed out after ${timeoutSeconds}s waiting for $description. " +
                            "State is ${playbackState()}, playing ${isPlaying()}, " +
                            "item ${currentMediaId()}, error ${playerError()}"
                )
            }
            Thread.sleep(POLL_MILLIS)
        }
    }

    fun awaitPlaying(timeoutSeconds: Long = TIMEOUT_SECONDS) {
        awaitPlayback("playback to start", timeoutSeconds) { isPlaying() }
    }

    fun awaitPlaybackState(state: Int, timeoutSeconds: Long = TIMEOUT_SECONDS) {
        awaitPlayback("playback state $state", timeoutSeconds) { playbackState() == state }
    }

    /**
     * @return every metadata push the controller has seen since [forgetPlayerEvents].
     */
    fun metadataUpdates(): List<MediaMetadata> {
        return mPlayerEvents.metadata()
    }

    /**
     * Waits for a metadata push whose subtitle is [subtitle], which is where the player reports
     * what a stream is doing: buffering, live, or the reason it failed.
     */
    fun awaitMetadataSubtitle(subtitle: String, timeoutSeconds: Long = TIMEOUT_SECONDS) {
        awaitPlayback("metadata subtitle '$subtitle'", timeoutSeconds) {
            metadataUpdates().any { it.subtitle?.toString() == subtitle }
        }
    }

    fun mediaItemTransitions(): List<String> {
        return mPlayerEvents.transitions()
    }

    fun forgetPlayerEvents() {
        mPlayerEvents.forget()
    }

    private fun onMain(call: MediaBrowser.() -> Unit) {
        mInstrumentation.runOnMainSync { browser().call() }
    }

    private fun <T> read(call: MediaBrowser.() -> T): T {
        val holder = AtomicReference<T>()
        mInstrumentation.runOnMainSync { holder.set(browser().call()) }
        return holder.get()
    }

    private fun <T> await(
        timeoutSeconds: Long = TIMEOUT_SECONDS,
        call: MediaBrowser.() -> ListenableFuture<T>
    ): T {
        val holder = AtomicReference<ListenableFuture<T>>()
        mInstrumentation.runOnMainSync { holder.set(browser().call()) }
        return holder.get().get(timeoutSeconds, TimeUnit.SECONDS)
    }

    private fun browser(): MediaBrowser {
        return mBrowser ?: throw IllegalStateException("Browser is not connected")
    }

    data class ChildrenChanged(val parentId: String, val itemCount: Int)

    data class SearchResultChanged(val query: String, val itemCount: Int)

    /**
     * Records what the session pushes to the controller about the player.
     *
     * Every callback arrives on the application main looper and the recorded lists are read from
     * the test thread, so both sides are synchronized on the recorder itself.
     */
    private class PlayerEvents : Player.Listener {

        private val mMetadata = mutableListOf<MediaMetadata>()

        private val mTransitions = mutableListOf<String>()

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            synchronized(this) { mMetadata.add(mediaMetadata) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            synchronized(this) { mTransitions.add(mediaItem?.mediaId ?: NO_ITEM) }
        }

        fun metadata(): List<MediaMetadata> {
            return synchronized(this) { ArrayList(mMetadata) }
        }

        fun transitions(): List<String> {
            return synchronized(this) { ArrayList(mTransitions) }
        }

        fun forget() {
            synchronized(this) {
                mMetadata.clear()
                mTransitions.clear()
            }
        }
    }

    companion object {

        const val TIMEOUT_SECONDS = 15L

        /**
         * The [connect] precondition, for a class that reaches the service through something
         * other than a [ServiceBrowser]: an Activity, whose own browser browses the root as soon
         * as it connects, or a `MediaResourcesManager` under test. Call it before that first
         * connection.
         */
        fun assertABrowseCannotStartPlayback() {
            val browser = ServiceBrowser()
            browser.connect()
            browser.release()
        }

        /**
         * What a media item transition reports when the queue has run out of items.
         */
        const val NO_ITEM = "<none>"

        private const val POLL_MILLIS = 50L
    }
}
