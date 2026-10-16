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
import com.yuriy.openradio.shared.utils.AppUtils
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pins which search [ShownSearch] hands back to be loaded again when the service tells of a new
 * result for a query: the one shown, only for its own query, and nothing once the subscription
 * has moved on (fnr-2r55y).
 */
class ShownSearchTest {

    private val mShown = ShownSearch()

    @Test
    fun theShownSearchIsLoadedAgainForItsQuery() {
        val search = search("Jazz")
        mShown.show(search)

        assertSame(search, mShown.changed("Jazz"))
    }

    @Test
    fun aResultForAnotherQueryLoadsNothing() {
        mShown.show(search("Jazz"))

        assertNull(mShown.changed("Rock"))
    }

    @Test
    fun aResultWithNoSearchShownLoadsNothing() {
        assertNull(mShown.changed("Jazz"))
    }

    @Test
    fun aClearedSearchIsNotLoadedAgain() {
        mShown.show(search("Jazz"))
        mShown.clear()

        assertNull(mShown.changed("Jazz"))
    }

    @Test
    fun aLaterSearchTakesThePlaceOfTheOneShownBefore() {
        mShown.show(search("Jazz"))
        val later = search("Rock")
        mShown.show(later)

        assertNull(mShown.changed("Jazz"))
        assertSame(later, mShown.changed("Rock"))
    }

    private fun search(query: String): ShownSearch.Search {
        return ShownSearch.Search(
            MediaId.MEDIA_ID_SEARCH_FROM_APP,
            query,
            AppUtils.makeSearchQueryBundle(query),
            IgnoringSubscription
        )
    }

    private object IgnoringSubscription : MediaItemsSubscription {

        override fun onChildrenLoaded(parentId: String, children: List<MediaItem>, replace: Boolean) = Unit

        override fun onError(parentId: String) = Unit
    }
}
