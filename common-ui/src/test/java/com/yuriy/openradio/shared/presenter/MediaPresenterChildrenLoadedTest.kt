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

package com.yuriy.openradio.shared.presenter

import android.os.Bundle
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.service.OpenRadioStore
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import com.yuriy.openradio.shared.view.list.TestMediaItemsAdapter
import com.yuriy.openradio.shared.view.list.TestMediaItemsAdapter.Companion.mediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the decision [MediaPresenterImpl.handleChildrenLoaded] makes between replacing the rows of
 * the browsed node and appending to them. A refresh of a node the user is already looking at is the
 * only case where the two differ, and it is the case the paginated nodes depend on: the service
 * says `replace`, the catalogue has not changed, and the rows must still be thrown away.
 */
class MediaPresenterChildrenLoadedTest {

    private val mAdapter = TestMediaItemsAdapter()
    private val mPresenter = listOnlyPresenter(mAdapter)

    @Test
    fun refreshOfTheSameCatalogueReplacesItsRows() {
        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a"), mediaItem("b")), false)

        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("c")), true)

        assertEquals(listOf("c"), mAdapter.mediaIds())
        assertEquals(COUNTRY_STATIONS, mAdapter.parentId)
    }

    @Test
    fun nextPageOfTheSameCatalogueAppendsToItsRows() {
        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a"), mediaItem("b")), false)

        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("c")), false)

        assertEquals(listOf("a", "b", "c"), mAdapter.mediaIds())
        assertEquals(COUNTRY_STATIONS, mAdapter.parentId)
    }

    @Test
    fun anotherCatalogueReplacesTheRowsOfThePreviousOne() {
        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a"), mediaItem("b")), false)

        mPresenter.handleChildrenLoaded(MediaId.MEDIA_ID_COUNTRIES_LIST, listOf(mediaItem("pl")), false)

        assertEquals(listOf("pl"), mAdapter.mediaIds())
        assertEquals(MediaId.MEDIA_ID_COUNTRIES_LIST, mAdapter.parentId)
    }

    @Test
    fun endOfListPageLeavesTheLoadedRowsAndTheirNodeAlone() {
        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a"), mediaItem("b")), false)

        mPresenter.handleChildrenLoaded(
            MediaId.MEDIA_ID_COUNTRIES_LIST, listOf(MediaItemBuilder.buildMediaItemListEnded()), true
        )

        assertEquals(listOf("a", "b"), mAdapter.mediaIds())
        assertEquals(COUNTRY_STATIONS, mAdapter.parentId)
        assertEquals(COUNTRY_STATIONS, savedParentId())
    }

    /**
     * An ended page for a node the list is not showing yet must not make the presenter believe it
     * is showing that node: otherwise the node's first real page counts as a further page of the
     * same catalogue and is appended to the rows of the node the user came from.
     */
    @Test
    fun firstRealPageAfterAnEndedPageForAnotherNodeReplacesTheRows() {
        mPresenter.handleChildrenLoaded(MediaId.MEDIA_ID_COUNTRIES_LIST, listOf(mediaItem("pl")), false)
        mPresenter.handleChildrenLoaded(
            COUNTRY_STATIONS, listOf(MediaItemBuilder.buildMediaItemListEnded()), false
        )

        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a")), false)

        assertEquals(listOf("a"), mAdapter.mediaIds())
        assertEquals(COUNTRY_STATIONS, mAdapter.parentId)
    }

    /**
     * The node the presenter believes it shows, read back the way the activity persists it.
     */
    private fun savedParentId(): String {
        val state = Bundle()
        mPresenter.handleSaveInstanceState(state)
        return OpenRadioStore.getCurrentParentId(state)
    }

    private companion object {

        /**
         * A node [com.yuriy.openradio.shared.utils.AppUtils.isSameCatalogue] does report as
         * unchanged on a second visit, unlike root, favorites and the local stations. Only such a
         * node can tell the two halves of the decision apart.
         */
        const val COUNTRY_STATIONS = MediaId.MEDIA_ID_COUNTRY_STATIONS
    }
}
