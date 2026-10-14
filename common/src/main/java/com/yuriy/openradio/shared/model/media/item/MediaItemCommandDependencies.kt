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
import com.yuriy.openradio.shared.service.OpenRadioService.ResultListener
import com.yuriy.openradio.shared.service.OpenRadioServicePresenter
import com.yuriy.openradio.shared.utils.AppLogger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val isSameCatalogue: Boolean,
    val isSavedInstance: Boolean,
    val options: Bundle,
    val mScope: CoroutineScope,
    resultListener: ResultListener,
    /**
     * How long [launchWithDeadline] lets a command's work run before it answers the browse request
     * without it. Only tests choose anything but [MediaItemCommand.CMD_TIMEOUT_MS].
     */
    private val mTimeoutMillis: Long = MediaItemCommand.CMD_TIMEOUT_MS
) {

    private val mResultListener = FirstResultListener(resultListener)

    /**
     * Where a command delivers its result. Only the first result of an execution reaches the
     * listener these dependencies were made with, so an answer that arrives after
     * [launchWithDeadline] has answered for it cannot leave the client with a second word.
     */
    val resultListener: ResultListener = mResultListener

    private val mMediaItems = ArrayList<MediaItem>()

    fun addMediaItem(item: MediaItem) {
        mMediaItems.add(item)
    }

    fun getMediaItems(): List<MediaItem> {
        return ArrayList(mMediaItems)
    }

    /**
     * Runs [work] on [Dispatchers.IO] and answers the browse request with an empty result unless
     * [work] has answered it within the deadline.
     *
     * The deadline is kept beside [work] rather than wrapped around it, because the presenter
     * calls a command makes block instead of suspending and never notice a cancellation: a timeout
     * around them could only fire once they returned, and a provider stuck on the network would
     * leave the request unanswered for good. The request is answered empty as well when [work]
     * returns without answering, fails, or is cancelled with the job returned here, so that every
     * execution answers exactly once. Whatever [work] delivers after that is dropped.
     *
     * A stuck [work] keeps its thread until the presenter call returns; nothing can interrupt it
     * safely in the middle of a cache or database write.
     *
     * @return The job of the whole execution. Cancelling it still answers the request.
     */
    fun launchWithDeadline(work: suspend () -> Unit): Job {
        // Undispatched, so the try below is entered before this returns: a job cancelled before a
        // thread picked it up would otherwise never run its finally, and never answer.
        return mScope.launch(Dispatchers.IO, CoroutineStart.UNDISPATCHED) {
            val worker = launch { work() }
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
            if (mAnswered.compareAndSet(false, true).not()) {
                AppLogger.w("$CLASS_NAME dropped a result of ${items.size} items for an answered request")
                return
            }
            mDelegate.onResult(items, radioStations, pageNumber)
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
    }
}

/**
 * Answers the browse request with the items collected so far, then reports that the node has
 * nothing to show.
 *
 * The result has to be delivered even when it is empty. `OpenRadioService.callWhenSourceReady`
 * completes the browse future from the result listener and from nowhere else, so a node that only
 * pushes the playback state leaves every client, the phone list and an Android Auto head unit
 * alike, waiting on a request that is never answered.
 */
internal fun MediaItemCommandDependencies.reportNoData(playbackStateListener: IUpdatePlaybackState) {
    resultListener.onResult(getMediaItems())
    playbackStateListener.updatePlaybackState(context.getString(R.string.no_data_message))
}
