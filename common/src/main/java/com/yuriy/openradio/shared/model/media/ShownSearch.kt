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

import android.os.Bundle

/**
 * The search whose results a [MediaResourcesManager] subscription is showing.
 *
 * The service answers a search it has not finished by the browse deadline with no stations and,
 * when its provider answers later, tells the browser that searched through
 * `onSearchResultChanged` (fnr-2r55y). The phone searches with `getSearchResult`, which leaves no
 * subscription behind, so this is how it knows whether the query it is told of is the one on
 * screen, and what to load again for it. Used on the main thread only, as the browser's callbacks
 * and the manager's loads are.
 */
class ShownSearch {

    /**
     * One search being shown.
     *
     * @property parentId The browse stack id the results are shown under.
     * @property query The query the service was asked for, which is what it names when it tells
     *   of a changed result.
     * @property bundle The bundle the search was loaded with, to load it again the same way.
     * @property callback Where the results go.
     */
    class Search(
        val parentId: String,
        val query: String,
        val bundle: Bundle,
        val callback: MediaItemsSubscription
    )

    private var mSearch: Search? = null

    /** Makes [search] the one shown, in place of any shown before. */
    fun show(search: Search) {
        mSearch = search
    }

    /** Forgets the search shown, as the subscription has moved to something else. */
    fun clear() {
        mSearch = null
    }

    /**
     * @return the search to load again because the service has a new result for [query], or null
     *   when no search is shown or the one shown asked for another query.
     */
    fun changed(query: String): Search? = mSearch?.takeIf { it.query == query }
}
