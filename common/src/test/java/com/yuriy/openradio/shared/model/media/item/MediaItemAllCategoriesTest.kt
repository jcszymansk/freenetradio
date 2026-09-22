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

import androidx.media3.common.MediaMetadata
import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.MediaId
import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemAllCategoriesTest {

    @Test
    fun eachCategoryBecomesABrowsableChildNode() {
        val categories = TreeSet<Category>()
        categories.add(Category("rock", "Rock", 42))
        categories.add(Category("jazz", "Jazz", 7))
        val presenter = RecordingPresenter(mCategories = categories)
        val listener = RecordingCommandListener()

        MediaItemAllCategories().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_ALL_CATEGORIES)
        )

        listener.awaitResult().assertMediaIds(
            MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock",
            MediaId.MEDIA_ID_CHILD_CATEGORIES + "jazz"
        )
        assertEquals(1, presenter.categoriesRequests)
        assertEquals("Rock", listener.items[0].mediaMetadata.title)
        assertEquals("42 $STRING_RESOURCE", listener.items[0].mediaMetadata.subtitle)
        for (item in listener.items) {
            assertTrue("${item.mediaId} is not browsable", item.mediaMetadata.isBrowsable == true)
            assertFalse("${item.mediaId} is playable", item.mediaMetadata.isPlayable == true)
            assertEquals(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS, item.mediaMetadata.mediaType)
        }
        listener.assertNoError()
    }

    /**
     * The empty result is the half that matters to a browsing client: the service completes the
     * browse request from it and from nothing else, so a node that only reported the error would
     * leave the caller waiting (TASK-029).
     */
    @Test
    fun anEmptyCatalogueIsDeliveredAndReportedAsAnError() {
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        MediaItemAllCategories().execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = MediaId.MEDIA_ID_ALL_CATEGORIES)
        )

        listener.awaitResult().awaitError()
        assertTrue(listener.items.isEmpty())
        assertEquals(1, listener.results)
        assertEquals(1, listener.errors)
        assertEquals(STRING_RESOURCE, listener.error)
    }

    /**
     * The presenter is given a category it would happily hand over, so the node arriving empty is
     * a decision the command made rather than an absence of data.
     */
    @Test
    fun aRestoredInstanceLeavesTheCategoriesToTheCacheWithoutAskingTheProvider() {
        val presenter = RecordingPresenter(mCategories = setOf(Category("rock", "Rock", 42)))
        val listener = RecordingCommandListener()

        MediaItemAllCategories().execute(
            listener.playbackStateListener,
            dependencies(
                presenter,
                listener,
                parentId = MediaId.MEDIA_ID_ALL_CATEGORIES,
                isSavedInstance = true
            )
        )

        listener.assertAnsweredFromCacheBeforeReturning()
        assertEquals(0, presenter.categoriesRequests)
        listener.assertNoError()
    }
}
