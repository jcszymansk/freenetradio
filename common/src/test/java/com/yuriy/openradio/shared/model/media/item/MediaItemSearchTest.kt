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

package com.yuriy.openradio.shared.model.media.item

import android.os.Bundle
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.utils.AppUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [MediaItemSearchFromService], the only browse command a search can reach. A phone client
 * never browses a search node: it asks for `getSearchResult`, which the service answers under
 * [MediaId.MEDIA_ID_SEARCH_FROM_SERVICE], and a head unit takes the same route through `search`.
 */
class MediaItemSearchTest {

    @Test
    fun theQueryComesFromTheBrowseOptions() {
        val presenter = RecordingPresenter(mSearchStations = stations("first", "second"))
        val listener = RecordingCommandListener()

        execute(presenter, listener, AppUtils.makeSearchQueryBundle("jazz"))

        listener.awaitResult().assertMediaIds(
            MediaId.makeSearchId("first"),
            MediaId.makeSearchId("second")
        )
        assertEquals(listOf("jazz"), presenter.searchRequests)
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, listener.pageNumber)
        listener.assertNoError()
    }

    @Test
    fun browseOptionsWithoutAQueryReuseTheCurrentOne() {
        val presenter = RecordingPresenter(mSearchStations = stations("first"))
        val listener = RecordingCommandListener()

        execute(presenter, listener, Bundle())

        listener.awaitResult()
        assertEquals(listOf(AppUtils.USE_CUR_SEARCH_QUERY), presenter.searchRequests)
    }

    @Test
    fun anEmptySearchResultIsDeliveredWithoutAnError() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        execute(presenter, listener, AppUtils.makeSearchQueryBundle("nothing"))

        listener.awaitResult()
        assertTrue(listener.items.isEmpty())
        listener.assertNoError()
    }

    /**
     * The tag is what keeps a searched station apart from a browsed one once it is on the phone:
     * the client strips the prefix back off before it asks the service to play the station.
     */
    @Test
    fun everyResultIsTaggedAsComingFromASearch() {
        val presenter = RecordingPresenter(mSearchStations = stations("first", "second"))
        val listener = RecordingCommandListener()

        execute(presenter, listener, AppUtils.makeSearchQueryBundle("jazz"))

        listener.awaitResult()
        for (mediaId in listener.mediaIds) {
            assertTrue("'$mediaId' is not recognizable as a search result", MediaId.isFromSearch(mediaId))
        }
        assertEquals(listOf("first", "second"), listener.mediaIds.map { MediaId.normalizeFromSearchId(it) })
    }

    @Test
    fun aRestoredInstanceDeliversTheCachedResultsWithoutSearchingAgain() {
        val presenter = RecordingPresenter(mSearchStations = stations("first"))
        val listener = RecordingCommandListener()

        execute(presenter, listener, AppUtils.makeSearchQueryBundle("jazz"), isSavedInstance = true)

        listener.awaitResult()
        assertTrue(listener.items.isEmpty())
        assertTrue(presenter.searchRequests.isEmpty())
        listener.assertNoError()
    }

    private fun execute(
        presenter: RecordingPresenter,
        listener: RecordingCommandListener,
        options: Bundle,
        isSavedInstance: Boolean = false
    ) {
        MediaItemSearchFromService().execute(
            listener.playbackStateListener,
            dependencies(
                presenter,
                listener,
                parentId = MediaId.MEDIA_ID_SEARCH_FROM_SERVICE,
                isSavedInstance = isSavedInstance,
                options = options
            )
        )
    }
}
