/*
 * Copyright 2015-2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.model.media.item

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import com.yuriy.openradio.R
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.item.MediaItemCommand.IUpdatePlaybackState
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.service.OpenRadioService.ResultListener
import com.yuriy.openradio.shared.service.OpenRadioServicePresenter
import com.yuriy.openradio.shared.utils.AppLogger
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 10/13/15
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * [MediaItemCommandDependencies] is a class that designed to keep all necessary references that are
 * shared between implementations of the [MediaItemCommand] interface, includes application
 * context, name of the current category, list of all categories, etc ...
 */
class MediaItemCommandDependencies(
    /**
     * Context of the application.
     */
    val context: Context,
    val presenter: OpenRadioServicePresenter,
    val countryCode: String,
    val parentId: String,
    /**
     * The page of the node the request asks the provider for, counted from
     * [UrlLayer.FIRST_PAGE_INDEX]. A paged command fetches this page and no other, and every
     * answer carries it. The service decides it per request, from what the client asking already
     * holds, so a command keeps no page of its own that another client could move.
     */
    val pageNumber: Int,
    val isSavedInstance: Boolean,
    val options: Bundle,
    /**
     * Where [execute] runs a command, built by [newCommandScope] outside tests. Private, so that a
     * command cannot start work of its own that outlives the execution and escapes the deadline.
     */
    private val mScope: CoroutineScope,
    resultListener: ResultListener,
    /**
     * How long [execute] lets a command run before it answers the browse request without it. Only
     * tests choose anything but [MediaItemCommand.CMD_TIMEOUT_MS].
     */
    private val mTimeoutMillis: Long = MediaItemCommand.CMD_TIMEOUT_MS
) {

    private val mResultListener = FirstResultListener(resultListener)

    /**
     * Where a command delivers its result. Only the first result of an execution reaches the
     * listener these dependencies were made with, so an answer that arrives after [execute] has
     * answered for it cannot leave the client with a second word.
     */
    val resultListener: ResultListener = mResultListener

    private val mMediaItems = ArrayList<MediaItem>()

    private val mExecuted = AtomicBoolean(false)

    /**
     * Answers the browse request for [pageNumber], unless it has been answered already, and tells
     * whether it did.
     *
     * @return Whether this answered the request. False when the deadline, or an earlier result,
     * answered it first and this one was dropped.
     */
    fun answer(items: List<MediaItem>, radioStations: Set<RadioStation> = TreeSet()): Boolean {
        return mResultListener.offer(items, radioStations, pageNumber)
    }

    fun addMediaItem(item: MediaItem) {
        mMediaItems.add(item)
    }

    fun getMediaItems(): List<MediaItem> {
        return ArrayList(mMediaItems)
    }

    /**
     * Runs [command] for the browse request these dependencies were made for, on [Dispatchers.IO],
     * and answers the request with an empty result unless [command] has answered it within the
     * deadline. Every browse command runs through here, so none of them has to remember the
     * deadline: [MediaItemCommand.execute] only does its work and answers, blocking as long as that
     * takes.
     *
     * The deadline is kept beside the command rather than wrapped around it, because the presenter
     * calls a command makes block instead of suspending and never notice a cancellation: a timeout
     * around them could only fire once they returned, and a provider stuck on the network would
     * leave the request unanswered for good. The request is answered empty as well when [command]
     * returns without answering, fails, or is cancelled with [mScope], so that every execution
     * answers exactly once. Whatever [command] delivers after that is dropped. A failure of
     * [command] still fails the execution's coroutine, so it ends wherever [mScope] puts it, which
     * for a scope from [newCommandScope] is the log.
     *
     * A stuck [command] keeps its thread until the presenter call returns; nothing can interrupt it
     * safely in the middle of a cache or database write.
     *
     * Runs at most once per dependencies object: the answer gate and the collected media items
     * belong to one browse request, so a second execution would have its answer dropped and add its
     * items to the first one's.
     */
    fun execute(command: MediaItemCommand, playbackStateListener: IUpdatePlaybackState) {
        check(mExecuted.compareAndSet(false, true)) { "$CLASS_NAME $parentId has already executed a command" }
        // Undispatched, so the try below is entered before this returns: a job cancelled before a
        // thread picked it up would otherwise never run its finally, and never answer.
        mScope.launch(Dispatchers.IO + CoroutineName("$CLASS_NAME $parentId"), CoroutineStart.UNDISPATCHED) {
            val worker = launch { command.execute(playbackStateListener, this@MediaItemCommandDependencies) }
            try {
                if (withTimeoutOrNull(mTimeoutMillis) { worker.join() } == null) {
                    AppLogger.w("$CLASS_NAME $parentId not loaded within $mTimeoutMillis ms, answering empty")
                    worker.cancel()
                }
            } finally {
                mResultListener.answerIfUnanswered()
            }
        }
    }

    /**
     * Passes the first result to [mDelegate] and drops every later one. A browse request has a
     * single future, and a second result would arrive at it after the first had settled it.
     */
    private class FirstResultListener(private val mDelegate: ResultListener) : ResultListener {

        private val mAnswered = AtomicBoolean(false)

        override fun onResult(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int) {
            offer(items, radioStations, pageNumber)
        }

        /**
         * Passes the result to [mDelegate] if it is the first.
         *
         * @return Whether the result was passed on.
         */
        fun offer(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int): Boolean {
            if (mAnswered.compareAndSet(false, true).not()) {
                AppLogger.w("$CLASS_NAME dropped a result of ${items.size} items for an answered request")
                return false
            }
            mDelegate.onResult(items, radioStations, pageNumber)
            return true
        }

        /** Answers with an empty result, unless something has answered already. */
        fun answerIfUnanswered() {
            if (mAnswered.compareAndSet(false, true)) {
                mDelegate.onResult()
            }
        }
    }

    companion object {
        /**
         * String tag to use in the log message.
         */
        private val CLASS_NAME = MediaItemCommandDependencies::class.java.simpleName

        /**
         * Builds the scope the service hands to every browse command it executes.
         *
         * A command that throws fails the coroutine [execute] runs it in. The request is answered
         * empty all the same, but the failure has to end somewhere, and in a plain
         * `CoroutineScope(Dispatchers.IO)` it ends badly twice: it cancels the scope's job, so every
         * later command starts cancelled and is answered empty without asking anything, and with no
         * handler to take it, it reaches the thread's uncaught exception handler, which on Android
         * ends the process. A supervisor keeps the failure to the one execution, and the handler
         * ends it in [onFailure].
         *
         * @param onFailure Where a failure ends, with a message naming the execution. Only tests
         * choose anything but the error log.
         */
        fun newCommandScope(onFailure: (String, Throwable) -> Unit = AppLogger::e): CoroutineScope {
            val handler = CoroutineExceptionHandler { context, failure ->
                val execution = context[CoroutineName]?.name ?: context
                onFailure("Browse command failed, its request was answered empty: $execution", failure)
            }
            return CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
        }
    }
}

/**
 * Answers the browse request with the items collected so far, then reports that the node has
 * nothing to show. A request the deadline has already answered gets no report either: the client
 * has moved on, and a "no data" error arriving after its answer would describe nothing it asked.
 *
 * The result has to be delivered even when it is empty. `OpenRadioService.callWhenSourceReady`
 * completes the browse future from the result listener and from nowhere else, so a node that only
 * pushes the playback state leaves every client, the phone list and an Android Auto head unit
 * alike, waiting on a request that is never answered.
 */
internal fun MediaItemCommandDependencies.reportNoData(playbackStateListener: IUpdatePlaybackState) {
    if (answer(getMediaItems())) {
        playbackStateListener.updatePlaybackState(context.getString(R.string.no_data_message))
    }
}
