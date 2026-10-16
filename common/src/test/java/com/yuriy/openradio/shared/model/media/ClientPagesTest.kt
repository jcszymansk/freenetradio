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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Covers what each browse client is answered with when it asks for more of a paged list, with
 * the list held in a real [BrowseTree] the way the service holds it (fnr-1557f).
 */
class ClientPagesTest {

    private val mTree = BrowseTree()

    private val mPages = ClientPages()

    @Test
    fun aClientWithTheWholeListHasTheNextPageFetched() {
        val list = mTree.store(NODE, data("first"))
        assertIds(listOf("first"), mPages.answerWhole(PHONE, NODE, list))

        assertFetches(1, mPages.next(PHONE, NODE, list, PAGE_SIZE))
    }

    @Test
    fun aFetchedPageIsAnsweredWithWhatItAdded() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)

        val stored = mTree.append(NODE, list, data("second"))

        assertIds(listOf("second"), mPages.answerRest(PHONE, NODE, stored, PAGE_SIZE))
        assertFetches(2, mPages.next(PHONE, NODE, stored, PAGE_SIZE))
    }

    /**
     * The phone UI asking for a page while Android Auto browses elsewhere, or the other way round,
     * changes nothing the other one is answered with.
     */
    @Test
    fun aClientIsNotMovedOnByAnotherClient() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        mPages.answerWhole(CAR, NODE, list)
        mPages.answerWhole(CAR, OTHER_NODE, mTree.store(OTHER_NODE, data("other")))

        assertFetches(1, mPages.next(PHONE, NODE, list, PAGE_SIZE))
        val stored = mTree.append(NODE, list, data("second"))
        assertIds(listOf("second"), mPages.answerRest(PHONE, NODE, stored, PAGE_SIZE))

        assertFetches(2, mPages.next(PHONE, NODE, stored, PAGE_SIZE))
    }

    /**
     * A page one client had fetched is what the other client lacks next, so it is answered from
     * the list without asking the provider again, and neither client skips it.
     */
    @Test
    fun aPageAnotherClientFetchedIsAnsweredFromTheList() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        mPages.answerWhole(CAR, NODE, list)
        val stored = mTree.append(NODE, list, data("second"))
        mPages.answerRest(PHONE, NODE, stored, PAGE_SIZE)

        assertAnswers(listOf("second"), mPages.next(CAR, NODE, stored, PAGE_SIZE))
        assertFetches(2, mPages.next(CAR, NODE, stored, PAGE_SIZE))
    }

    /**
     * Two clients that asked for the same page at once both fetched it. The tree keeps it once,
     * and each client is answered with it once.
     */
    @Test
    fun twoClientsThatFetchedOnePageAtOnceEachGetItOnce() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        mPages.answerWhole(CAR, NODE, list)
        assertFetches(1, mPages.next(PHONE, NODE, list, PAGE_SIZE))
        assertFetches(1, mPages.next(CAR, NODE, list, PAGE_SIZE))

        val byPhone = mTree.append(NODE, list, data("second"))
        val byCar = mTree.append(NODE, list, data("second"))

        assertIds(listOf("second"), mPages.answerRest(PHONE, NODE, byPhone, PAGE_SIZE))
        assertIds(listOf("second"), mPages.answerRest(CAR, NODE, byCar, PAGE_SIZE))
        assertIds(listOf("first", "second"), mTree[NODE]!!)
    }

    /**
     * A client that asks for more twice before the first page arrives fetches the page twice.
     * It is answered with the page once, and the second request with nothing.
     */
    @Test
    fun twoOverlappingRequestsOfOneClientGetThePageOnce() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        assertFetches(1, mPages.next(PHONE, NODE, list, PAGE_SIZE))
        assertFetches(1, mPages.next(PHONE, NODE, list, PAGE_SIZE))

        val first = mTree.append(NODE, list, data("second"))
        val second = mTree.append(NODE, list, data("second"))

        assertIds(listOf("second"), mPages.answerRest(PHONE, NODE, first, PAGE_SIZE))
        assertIds(emptyList(), mPages.answerRest(PHONE, NODE, second, PAGE_SIZE))
        assertIds(listOf("first", "second"), mTree[NODE]!!)
    }

    /**
     * A client that fell pages behind another is answered one page at a time, since Media3
     * rejects an answer larger than the page size asked for, and has the page after the list
     * fetched only once it holds all of it.
     */
    @Test
    fun aClientPagesBehindIsAnsweredOnePageAtATime() {
        val list = mTree.store(NODE, data("a1", "a2"))
        mPages.answerWhole(PHONE, NODE, list)
        mPages.answerWhole(CAR, NODE, list)
        val second = mTree.append(NODE, list, data("b1", "b2"))
        mPages.answerRest(CAR, NODE, second, PAGE_SIZE)
        val third = mTree.append(NODE, second!!, data("c1", "c2"))
        mPages.answerRest(CAR, NODE, third, PAGE_SIZE)

        assertAnswers(listOf("b1", "b2"), mPages.next(PHONE, NODE, third, PAGE_SIZE))
        assertAnswers(listOf("c1", "c2"), mPages.next(PHONE, NODE, third, PAGE_SIZE))
        assertFetches(3, mPages.next(PHONE, NODE, third, PAGE_SIZE))
    }

    /**
     * A page fetched for a client arrives after another client has added it and the page after
     * it. The client is answered with one page, completed off the main thread where a larger
     * answer would end the process, and with the rest when it asks again.
     */
    @Test
    fun aPageArrivingAfterOtherPagesWereAddedIsAnsweredWithOnePage() {
        val list = mTree.store(NODE, data("a1", "a2"))
        mPages.answerWhole(PHONE, NODE, list)
        mPages.answerWhole(CAR, NODE, list)
        assertFetches(1, mPages.next(PHONE, NODE, list, PAGE_SIZE))
        val second = mTree.append(NODE, list, data("b1", "b2"))
        mPages.answerRest(CAR, NODE, second, PAGE_SIZE)
        mPages.answerRest(CAR, NODE, mTree.append(NODE, second!!, data("c1", "c2")), PAGE_SIZE)

        val late = mTree.append(NODE, list, data("b1", "b2"))

        assertIds(listOf("b1", "b2"), mPages.answerRest(PHONE, NODE, late, PAGE_SIZE))
        assertAnswers(listOf("c1", "c2"), mPages.next(PHONE, NODE, late, PAGE_SIZE))
    }

    /**
     * Asking for a page records nothing; only an answer does. That is the part of fnr-3ktp this
     * class owns: a page the deadline answered empty never reaches [ClientPages.answerRest],
     * because `OpenRadioService.callWhenSourceReady` answers empty items before storing anything,
     * so the client's next request fetches that page again rather than the one after it.
     */
    @Test
    fun aPageThatNeverReachedTheClientIsFetchedAgain() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        assertFetches(1, mPages.next(PHONE, NODE, list, PAGE_SIZE))

        assertFetches(1, mPages.next(PHONE, NODE, list, PAGE_SIZE))
    }

    /** Coming back to a list the tree still holds answers it whole, and the client goes on after it. */
    @Test
    fun theWholeListAnsweredAgainMovesTheClientToItsEnd() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        val stored = mTree.append(NODE, list, data("second"))!!
        mPages.answerWhole(CAR, NODE, stored)

        assertIds(listOf("first", "second"), mPages.answerWhole(PHONE, NODE, stored))
        assertFetches(2, mPages.next(PHONE, NODE, stored, PAGE_SIZE))
        assertFetches(2, mPages.next(CAR, NODE, stored, PAGE_SIZE))
    }

    /**
     * A client holds a list the tree does not once the list was invalidated or stored again,
     * for instance for another country. A later page would extend the client's list with a
     * list it does not belong to, so the client is answered with nothing until it asks for the
     * first page again.
     */
    @Test
    fun aClientHoldingAListTheTreeNoLongerHoldsIsAnsweredWithNothing() {
        val old = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, old)
        mPages.answerWhole(CAR, NODE, old)
        val replacement = mTree.store(NODE, data("replacement"))
        mPages.answerWhole(CAR, NODE, replacement)
        mTree.append(NODE, replacement, data("second"))

        assertAnswers(emptyList(), mPages.next(PHONE, NODE, mTree.getPagedList(NODE), PAGE_SIZE))
        assertIds(emptyList(), mPages.answerRest(PHONE, NODE, mTree.getPagedList(NODE), PAGE_SIZE))
        mTree.invalidate(NODE)
        assertAnswers(emptyList(), mPages.next(CAR, NODE, mTree.getPagedList(NODE), PAGE_SIZE))
        assertIds(emptyList(), mPages.answerRest(CAR, NODE, null, PAGE_SIZE))
    }

    @Test
    fun aClientThatWasNeverAnsweredWithTheListIsAnsweredWithNothing() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)

        assertAnswers(emptyList(), mPages.next(CAR, NODE, list, PAGE_SIZE))
        assertAnswers(emptyList(), mPages.next(PHONE, OTHER_NODE, list, PAGE_SIZE))
    }

    @Test
    fun aForgottenClientIsAnsweredWithNothingAndTheOthersAreKept() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        mPages.answerWhole(CAR, NODE, list)

        mPages.forget(PHONE)

        assertAnswers(emptyList(), mPages.next(PHONE, NODE, list, PAGE_SIZE))
        assertFetches(1, mPages.next(CAR, NODE, list, PAGE_SIZE))
    }

    /** Media3 never asks with a page size below one; a caller that does is a bug, not a request. */
    @Test
    fun aPageSizeBelowOneIsRejected() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)

        assertThrows(IllegalArgumentException::class.java) { mPages.next(PHONE, NODE, list, 0) }
        assertThrows(IllegalArgumentException::class.java) { mPages.answerRest(PHONE, NODE, list, 0) }
        assertFetches(1, mPages.next(PHONE, NODE, list, PAGE_SIZE))
    }

    private fun assertFetches(page: Int, next: ClientPages.Next) {
        assertEquals("Not asked to fetch page $page: $next", page, (next as ClientPages.Next.Fetch).page)
    }

    private fun assertAnswers(ids: List<String>, next: ClientPages.Next) {
        assertIds(ids, (next as ClientPages.Next.Answer).items)
    }

    private fun assertIds(ids: List<String>, items: List<MediaItem>) {
        assertEquals(ids, items.map { it.mediaId })
    }

    private fun data(vararg ids: String): BrowseTree.BrowseData {
        val stations = ids.map { RadioStation.makeDefaultInstance(it) }
        return BrowseTree.BrowseData(stations.map { MediaItem.Builder().setMediaId(it.id).build() }, stations.toSet())
    }

    private companion object {
        const val NODE = "node"
        const val OTHER_NODE = "other-node"

        /** The page size the clients ask with, the size of the multi-item pages above. */
        const val PAGE_SIZE = 2

        /** Clients are told apart by equality only, as the service keys them by its controllers. */
        val PHONE = Any()
        val CAR = Any()
    }
}
