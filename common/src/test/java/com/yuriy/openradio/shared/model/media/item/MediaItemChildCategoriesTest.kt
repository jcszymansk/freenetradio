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

import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.net.UrlLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemChildCategoriesTest {

    @Test
    fun theCategoryIdIsTakenFromTheParentIdAndTheFirstPageIsRequested() {
        val presenter = RecordingPresenter(mCategoryStations = stations("first", "second"))
        val listener = RecordingCommandListener()

        MediaItemChildCategories().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock")
        )

        listener.awaitResult().assertMediaIds("first")
        assertEquals(listOf("rock" to UrlLayer.FIRST_PAGE_INDEX), presenter.categoryRequests)
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, listener.pageNumber)
        listener.assertNoError()
    }

    @Test
    fun browsingTheSameCategoryAgainAsksForTheNextPage() {
        val presenter = RecordingPresenter(mCategoryStations = stations("first", "second"))
        val command = MediaItemChildCategories()
        val parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock"

        val first = RecordingCommandListener()
        command.execute(first.playbackStateListener, dependencies(presenter, first, parentId = parentId))
        first.awaitResult()

        val second = RecordingCommandListener()
        command.execute(
            second.playbackStateListener,
            dependencies(presenter, second, parentId = parentId, isSameCatalogue = true)
        )

        second.awaitResult().assertMediaIds("second")
        assertEquals(
            listOf("rock" to UrlLayer.FIRST_PAGE_INDEX, "rock" to UrlLayer.FIRST_PAGE_INDEX + 1),
            presenter.categoryRequests
        )
        assertEquals(UrlLayer.FIRST_PAGE_INDEX + 1, second.pageNumber)
    }

    @Test
    fun leavingTheCategoryAndComingBackStartsOverAtTheFirstPage() {
        val presenter = RecordingPresenter(mCategoryStations = stations("first", "second"))
        val command = MediaItemChildCategories()
        val parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock"

        val first = RecordingCommandListener()
        command.execute(first.playbackStateListener, dependencies(presenter, first, parentId = parentId))
        first.awaitResult()

        val second = RecordingCommandListener()
        command.execute(second.playbackStateListener, dependencies(presenter, second, parentId = parentId))

        second.awaitResult().assertMediaIds("first")
        assertEquals(
            listOf("rock" to UrlLayer.FIRST_PAGE_INDEX, "rock" to UrlLayer.FIRST_PAGE_INDEX),
            presenter.categoryRequests
        )
    }

    @Test
    fun reachingTheEndOfTheSecondPageReportsThatNothingMoreArrived() {
        val presenter = RecordingPresenter(mCategoryStations = stations("only"))
        val command = MediaItemChildCategories()
        val parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock"

        val first = RecordingCommandListener()
        command.execute(first.playbackStateListener, dependencies(presenter, first, parentId = parentId))
        first.awaitResult()

        val second = RecordingCommandListener()
        command.execute(
            second.playbackStateListener,
            dependencies(presenter, second, parentId = parentId, isSameCatalogue = true)
        )

        second.awaitResult().awaitError()
        assertEquals(STRING_RESOURCE, second.error)
        assertTrue(second.items.isEmpty())
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, second.pageNumber)
    }

    @Test
    fun anEmptyFirstPageIsDeliveredWithoutAnError() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemChildCategories().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock")
        )

        listener.awaitResult()
        assertTrue(listener.items.isEmpty())
        assertEquals(UrlLayer.FIRST_PAGE_INDEX, listener.pageNumber)
        listener.assertNoError()
    }

    @Test
    fun aRestoredInstanceDeliversTheCachedNodeWithoutAskingTheProvider() {
        val presenter = RecordingPresenter(mCategoryStations = stations("first"))
        val listener = RecordingCommandListener()

        MediaItemChildCategories().execute(
            listener.playbackStateListener,
            dependencies(
                presenter,
                listener,
                parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock",
                isSavedInstance = true
            )
        )

        listener.awaitResult()
        assertTrue(listener.items.isEmpty())
        assertTrue(presenter.categoryRequests.isEmpty())
        listener.assertNoError()
    }
}
