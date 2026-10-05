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

import androidx.media3.common.MediaItem
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.MediaItemsSubscription
import com.yuriy.openradio.shared.view.list.TestMediaItemsAdapter
import com.yuriy.openradio.shared.view.list.TestMediaItemsAdapter.Companion.mediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers what [MediaPresenterImpl] lets through from the media browser to the activity. The
 * browser answers on a coroutine, both when the presenter subscribes and when the service reports
 * that the subscribed node changed, and every answer reaches the presenter through the one
 * subscription it hands the browser. Feeding that subscription directly is therefore what either
 * path does once its fetch returns, at whatever point the user has reached by then.
 *
 * The activity's side is played by [RenderingSubscription], which renders the way MainActivity
 * does, so the adapter shows what the user would see.
 */
class MediaPresenterShownNodeTest {

    private val mAdapter = TestMediaItemsAdapter()
    private val mPresenter = listOnlyPresenter(mAdapter)
    private val mActivity = RenderingSubscription(mPresenter)
    private val mBrowser: MediaItemsSubscription

    init {
        mPresenter.attachSubscription(mActivity)
        mBrowser = mPresenter.browserSubscription
    }

    @Test
    fun aRefreshOfTheNodeLeftByGoingBackDoesNotReplaceTheRootList() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mBrowser.onChildrenLoaded(MediaId.MEDIA_ID_ROOT, ROOT_ROWS, true)
        mPresenter.addMediaItemToStack(LOCALS)
        mBrowser.onChildrenLoaded(LOCALS, listOf(mediaItem("station")), true)

        mPresenter.handleBackPressed()
        mBrowser.onChildrenLoaded(MediaId.MEDIA_ID_ROOT, ROOT_ROWS, true)
        mBrowser.onChildrenLoaded(LOCALS, emptyList(), true)

        assertEquals(ROOT_IDS, mAdapter.mediaIds())
        assertEquals(MediaId.MEDIA_ID_ROOT, mAdapter.parentId)
        assertEquals(listOf(MediaId.MEDIA_ID_ROOT, LOCALS, MediaId.MEDIA_ID_ROOT), mActivity.loadedParents)
    }

    @Test
    fun aRefreshThatArrivesBeforeTheRootDoesNotOutliveIt() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mPresenter.addMediaItemToStack(LOCALS)
        mBrowser.onChildrenLoaded(LOCALS, listOf(mediaItem("station")), true)

        mPresenter.handleBackPressed()
        mBrowser.onChildrenLoaded(LOCALS, emptyList(), true)
        mBrowser.onChildrenLoaded(MediaId.MEDIA_ID_ROOT, ROOT_ROWS, true)

        assertEquals(ROOT_IDS, mAdapter.mediaIds())
        assertEquals(MediaId.MEDIA_ID_ROOT, mAdapter.parentId)
        assertEquals(listOf(LOCALS, MediaId.MEDIA_ID_ROOT), mActivity.loadedParents)
    }

    @Test
    fun aRefreshOfTheNodeLeftByGoingDeeperDoesNotReplaceTheChildList() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mBrowser.onChildrenLoaded(MediaId.MEDIA_ID_ROOT, ROOT_ROWS, true)

        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_COUNTRIES_LIST)
        mBrowser.onChildrenLoaded(MediaId.MEDIA_ID_COUNTRIES_LIST, listOf(mediaItem("pl")), true)
        mBrowser.onChildrenLoaded(MediaId.MEDIA_ID_ROOT, ROOT_ROWS + mediaItem(LOCALS), true)

        assertEquals(listOf("pl"), mAdapter.mediaIds())
        assertEquals(MediaId.MEDIA_ID_COUNTRIES_LIST, mAdapter.parentId)
    }

    @Test
    fun aLatePageOfTheNodeLeftIsNotAppendedToTheShownList() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mPresenter.addMediaItemToStack(COUNTRY_STATIONS)
        mBrowser.onChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a")), true)

        mPresenter.handleBackPressed()
        mBrowser.onChildrenLoaded(MediaId.MEDIA_ID_ROOT, ROOT_ROWS, true)
        mBrowser.onChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("b")), false)

        assertEquals(ROOT_IDS, mAdapter.mediaIds())
        assertEquals(MediaId.MEDIA_ID_ROOT, mAdapter.parentId)
    }

    @Test
    fun theShownNodeKeepsGettingItsRefreshesAndPages() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mPresenter.addMediaItemToStack(COUNTRY_STATIONS)

        mBrowser.onChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a")), true)
        mBrowser.onChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("b")), false)

        assertEquals(listOf("a", "b"), mAdapter.mediaIds())
        mBrowser.onChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("c")), true)

        assertEquals(listOf("c"), mAdapter.mediaIds())
        assertEquals(listOf(COUNTRY_STATIONS, COUNTRY_STATIONS, COUNTRY_STATIONS), mActivity.loadedParents)
    }

    @Test
    fun anErrorReachesTheActivityOnlyForTheShownNode() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mPresenter.addMediaItemToStack(LOCALS)
        mPresenter.handleBackPressed()

        mBrowser.onError(LOCALS)
        mBrowser.onError(MediaId.MEDIA_ID_ROOT)

        assertEquals(listOf(MediaId.MEDIA_ID_ROOT), mActivity.failedParents)
    }

    @Test
    fun anAnswerArrivingBeforeAnyNodeIsShownIsDropped() {
        mBrowser.onChildrenLoaded(MediaId.MEDIA_ID_ROOT, ROOT_ROWS, true)
        mBrowser.onError(MediaId.MEDIA_ID_ROOT)

        assertEquals(emptyList<String>(), mActivity.loadedParents)
        assertEquals(emptyList<String>(), mActivity.failedParents)
        assertEquals(emptyList<String>(), mAdapter.mediaIds())
    }

    /**
     * An id that is asked for again while it is deeper in the stack is the node shown from then
     * on, so its answer has to get through and the answer of the node it replaced must not.
     */
    @Test
    fun askingAgainForANodeDeeperInTheStackShowsThatNode() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_COUNTRIES_LIST)
        mPresenter.addMediaItemToStack(COUNTRY_STATIONS)

        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_COUNTRIES_LIST)
        mBrowser.onChildrenLoaded(MediaId.MEDIA_ID_COUNTRIES_LIST, listOf(mediaItem("pl")), true)
        mBrowser.onChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a")), true)

        assertEquals(MediaId.MEDIA_ID_COUNTRIES_LIST, mPresenter.getCurrentCategory())
        assertEquals(listOf("pl"), mAdapter.mediaIds())
        assertEquals(MediaId.MEDIA_ID_COUNTRIES_LIST, mAdapter.parentId)
    }

    @Test
    fun askingAgainForTheShownNodeKeepsOneEntryForIt() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mPresenter.addMediaItemToStack(LOCALS)
        mPresenter.addMediaItemToStack(LOCALS)

        mPresenter.handleBackPressed()

        assertEquals(MediaId.MEDIA_ID_ROOT, mPresenter.getCurrentCategory())
    }

    /**
     * Records what reaches the activity and renders the children the way MainActivity's
     * subscription does, by handing them to the presenter.
     */
    private class RenderingSubscription(private val mPresenter: MediaPresenter) : MediaItemsSubscription {

        val loadedParents = mutableListOf<String>()
        val failedParents = mutableListOf<String>()

        override fun onChildrenLoaded(parentId: String, children: List<MediaItem>, replace: Boolean) {
            loadedParents.add(parentId)
            mPresenter.handleChildrenLoaded(parentId, children, replace)
        }

        override fun onError(parentId: String) {
            failedParents.add(parentId)
        }
    }

    private companion object {

        const val LOCALS = MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST

        /**
         * A paginated node, the only kind whose answers can append to the rows already shown.
         */
        const val COUNTRY_STATIONS = MediaId.MEDIA_ID_COUNTRY_STATIONS

        val ROOT_IDS = listOf(MediaId.MEDIA_ID_COUNTRIES_LIST, MediaId.MEDIA_ID_ALL_CATEGORIES)

        val ROOT_ROWS = ROOT_IDS.map { mediaItem(it) }
    }
}
