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

package com.yuriy.openradio.shared.model.media

import androidx.media3.common.MediaItem

/**
 * How much of each paged browse list every client has been answered with, so that a client asking
 * for more gets what it lacks rather than whatever page another client has reached (fnr-1557f).
 *
 * The phone UI and an Android Auto head unit browse the same [BrowseTree], and both lists grow
 * from either one's requests. A client is answered with a list whole when it asks for the first
 * page, and from then on asks for more by asking for any later page: the page number a client
 * sends counts the pages it asked for, not the pages of the list it holds, which the first page
 * may have answered with several of at once. So a client that asks for more is answered with the
 * part of the list it has not been answered with, and only when it has the whole list does the
 * provider get asked for the page after it.
 *
 * That part is cut to the page size the client asked with, and the client is recorded as holding
 * only what it was answered with. A client that fell pages behind another would otherwise get all
 * those pages in one answer, and Media3 rejects an answer larger than the page size: inline it
 * only logs the rejection, but for an answer completed off the main thread it throws on the main
 * looper and ends the process.
 *
 * A client is recorded against the [BrowseTree.PagedList.stamp] of the list it was answered from,
 * and holds nothing of a list stored again since. Safe to use from any thread.
 */
class ClientPages {

    private val mLock = Any()

    /** Written under [mLock]. */
    private val mPositions = HashMap<Key, Position>()

    /**
     * Answers [client] with the whole of [list], which is what a request for the first page of
     * [mediaId] gets, and records that it has it.
     *
     * @return the items to answer with.
     */
    fun answerWhole(client: Any, mediaId: String, list: BrowseTree.PagedList): List<MediaItem> {
        synchronized(mLock) {
            mPositions[Key(client, mediaId)] = Position(list.stamp, list.children.size)
        }
        return list.children
    }

    /**
     * Tells what a request of [client] for more of [list], the list held under [mediaId], needs.
     * When the client lacks part of [list], it is answered with at most [pageSize] items of that
     * part, and recorded as holding them. When it holds all of [list], the next page has to be
     * fetched, and the client is answered with [answerRest] once the page is stored. A client
     * that holds no part of [list], because it never asked for its first page or [list] is not
     * the list it was answered from, or because no list is held, is answered with nothing.
     */
    fun next(client: Any, mediaId: String, list: BrowseTree.PagedList?, pageSize: Int): Next {
        require(pageSize > 0) { "Page size $pageSize is not positive" }
        synchronized(mLock) {
            val held = list ?: return Next.Answer(emptyList())
            val position = positionIn(client, mediaId, held) ?: return Next.Answer(emptyList())
            if (position.size == held.children.size) {
                return Next.Fetch(held.pageCount)
            }
            return Next.Answer(answerFrom(client, mediaId, held, position, pageSize))
        }
    }

    /**
     * Answers [client] with at most [pageSize] items of the part of [list] it has not been
     * answered with, and records that it has them. That is the page fetched for it, or the
     * earliest page it lacks when other requests added pages meanwhile, or nothing when an earlier
     * answer gave it that page already, or when it holds no part of [list] (see [next]).
     *
     * @param list The list held under [mediaId] once the fetched page was stored, or null when the
     *   page was dropped.
     */
    fun answerRest(client: Any, mediaId: String, list: BrowseTree.PagedList?, pageSize: Int): List<MediaItem> {
        require(pageSize > 0) { "Page size $pageSize is not positive" }
        synchronized(mLock) {
            val held = list ?: return emptyList()
            val position = positionIn(client, mediaId, held) ?: return emptyList()
            return answerFrom(client, mediaId, held, position, pageSize)
        }
    }

    /** Forgets everything [client] was answered with, for a client that has disconnected. */
    fun forget(client: Any) {
        synchronized(mLock) {
            mPositions.keys.removeAll { it.client == client }
        }
    }

    /**
     * @return where [client] is in [list], or null when it holds no part of it.
     */
    private fun positionIn(client: Any, mediaId: String, list: BrowseTree.PagedList): Position? {
        val position = mPositions[Key(client, mediaId)] ?: return null
        return if (position.stamp == list.stamp) position else null
    }

    /**
     * Returns at most [pageSize] items of what [client] lacked of [list] at [position], and
     * records that it holds them.
     */
    private fun answerFrom(
        client: Any,
        mediaId: String,
        list: BrowseTree.PagedList,
        position: Position,
        pageSize: Int
    ): List<MediaItem> {
        val children = list.children
        val end = minOf(children.size, position.size + pageSize)
        mPositions[Key(client, mediaId)] = Position(list.stamp, end)
        return children.subList(position.size, end).toList()
    }

    /** What a request for more of a paged list needs, see [next]. */
    sealed interface Next {

        /** The request is answered with [items], and needs no page from the provider. */
        class Answer(val items: List<MediaItem>) : Next {
            override fun toString() = "Answer(${items.map { it.mediaId }})"
        }

        /** The provider has to be asked for [page], the page that follows the list. */
        class Fetch(val page: Int) : Next {
            override fun toString() = "Fetch($page)"
        }
    }

    private data class Key(val client: Any, val mediaId: String)

    /** How many items of the list stamped [stamp] a client has been answered with. */
    private class Position(val stamp: Long, val size: Int)
}
