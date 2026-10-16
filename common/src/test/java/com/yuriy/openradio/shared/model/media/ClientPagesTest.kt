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

        assertFetches(1, mPages.next(PHONE, NODE, list))
    }

    @Test
    fun aFetchedPageIsAnsweredWithWhatItAdded() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)

        val stored = mTree.append(NODE, list, data("second"))

        assertIds(listOf("second"), mPages.answerRest(PHONE, NODE, stored))
        assertFetches(2, mPages.next(PHONE, NODE, stored))
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

        assertFetches(1, mPages.next(PHONE, NODE, list))
        val stored = mTree.append(NODE, list, data("second"))
        assertIds(listOf("second"), mPages.answerRest(PHONE, NODE, stored))

        assertFetches(2, mPages.next(PHONE, NODE, stored))
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
        mPages.answerRest(PHONE, NODE, stored)

        assertAnswers(listOf("second"), mPages.next(CAR, NODE, stored))
        assertFetches(2, mPages.next(CAR, NODE, stored))
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
        assertFetches(1, mPages.next(PHONE, NODE, list))
        assertFetches(1, mPages.next(CAR, NODE, list))

        val byPhone = mTree.append(NODE, list, data("second"))
        val byCar = mTree.append(NODE, list, data("second"))

        assertIds(listOf("second"), mPages.answerRest(PHONE, NODE, byPhone))
        assertIds(listOf("second"), mPages.answerRest(CAR, NODE, byCar))
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
        assertFetches(1, mPages.next(PHONE, NODE, list))
        assertFetches(1, mPages.next(PHONE, NODE, list))

        val first = mTree.append(NODE, list, data("second"))
        val second = mTree.append(NODE, list, data("second"))

        assertIds(listOf("second"), mPages.answerRest(PHONE, NODE, first))
        assertIds(emptyList(), mPages.answerRest(PHONE, NODE, second))
        assertIds(listOf("first", "second"), mTree[NODE]!!)
    }

    /**
     * A page the deadline answered empty never reached the client, so its next request fetches
     * that page again rather than the one after it (fnr-3ktp).
     */
    @Test
    fun aPageThatNeverReachedTheClientIsFetchedAgain() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        assertFetches(1, mPages.next(PHONE, NODE, list))

        assertFetches(1, mPages.next(PHONE, NODE, list))
    }

    /** Coming back to a list the tree still holds answers it whole, and the client goes on after it. */
    @Test
    fun theWholeListAnsweredAgainMovesTheClientToItsEnd() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        val stored = mTree.append(NODE, list, data("second"))!!
        mPages.answerWhole(CAR, NODE, stored)

        assertIds(listOf("first", "second"), mPages.answerWhole(PHONE, NODE, stored))
        assertFetches(2, mPages.next(PHONE, NODE, stored))
        assertFetches(2, mPages.next(CAR, NODE, stored))
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

        assertAnswers(emptyList(), mPages.next(PHONE, NODE, mTree.getPagedList(NODE)))
        assertIds(emptyList(), mPages.answerRest(PHONE, NODE, mTree.getPagedList(NODE)))
        mTree.invalidate(NODE)
        assertAnswers(emptyList(), mPages.next(CAR, NODE, mTree.getPagedList(NODE)))
        assertIds(emptyList(), mPages.answerRest(CAR, NODE, null))
    }

    @Test
    fun aClientThatWasNeverAnsweredWithTheListIsAnsweredWithNothing() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)

        assertAnswers(emptyList(), mPages.next(CAR, NODE, list))
        assertAnswers(emptyList(), mPages.next(PHONE, OTHER_NODE, list))
    }

    @Test
    fun aForgottenClientIsAnsweredWithNothingAndTheOthersAreKept() {
        val list = mTree.store(NODE, data("first"))
        mPages.answerWhole(PHONE, NODE, list)
        mPages.answerWhole(CAR, NODE, list)

        mPages.forget(PHONE)

        assertAnswers(emptyList(), mPages.next(PHONE, NODE, list))
        assertFetches(1, mPages.next(CAR, NODE, list))
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

        /** Clients are told apart by equality only, as the service keys them by its controllers. */
        val PHONE = Any()
        val CAR = Any()
    }
}
