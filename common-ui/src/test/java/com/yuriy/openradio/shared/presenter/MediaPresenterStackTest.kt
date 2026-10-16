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
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [MediaPresenterImpl.getMediaItemsStack], the test-only read the instrumented journeys use
 * to put the presenter's node stack back the way they found it. A journey decides from this list
 * whether pressing back is safe, so the list has to be the stack as it stands, bottom first, and
 * must not change under the caller while it decides.
 */
class MediaPresenterStackTest {

    private val mPresenter = listOnlyPresenter(TestMediaItemsAdapter())

    init {
        mPresenter.attachSubscription(IgnoringSubscription())
    }

    @Test
    fun aPresenterThatShowedNothingHasAnEmptyStack() {
        assertEquals(emptyList<String>(), mPresenter.getMediaItemsStack())
    }

    @Test
    fun theStackListsTheNodesBottomFirst() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_COUNTRIES_LIST)

        assertEquals(
            listOf(MediaId.MEDIA_ID_ROOT, MediaId.MEDIA_ID_COUNTRIES_LIST),
            mPresenter.getMediaItemsStack()
        )
    }

    @Test
    fun theStackFollowsGoingBackAndRemovingANode() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_COUNTRIES_LIST)
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_FAVORITES_LIST)

        mPresenter.handleBackPressed()
        assertEquals(
            listOf(MediaId.MEDIA_ID_ROOT, MediaId.MEDIA_ID_COUNTRIES_LIST),
            mPresenter.getMediaItemsStack()
        )
        mPresenter.unsubscribeFromItem(MediaId.MEDIA_ID_ROOT)
        assertEquals(listOf(MediaId.MEDIA_ID_COUNTRIES_LIST), mPresenter.getMediaItemsStack())
    }

    @Test
    fun aStackReadEarlierDoesNotChangeWhenThePresenterMoves() {
        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_ROOT)
        val before = mPresenter.getMediaItemsStack()

        mPresenter.addMediaItemToStack(MediaId.MEDIA_ID_COUNTRIES_LIST)

        assertEquals(listOf(MediaId.MEDIA_ID_ROOT), before)
    }

    /**
     * The activity's side, which these cases have no use for: what they assert is the stack, and
     * no answer is ever delivered.
     */
    private class IgnoringSubscription : MediaItemsSubscription {

        override fun onChildrenLoaded(parentId: String, children: List<MediaItem>, replace: Boolean) = Unit

        override fun onError(parentId: String) = Unit
    }
}
