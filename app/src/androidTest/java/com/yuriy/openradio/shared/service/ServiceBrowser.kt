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
    }

    fun release() {
        val browser = mBrowser ?: return
        mBrowser = null
        mInstrumentation.runOnMainSync(browser::release)
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

    companion object {

        const val TIMEOUT_SECONDS = 15L
    }
}
