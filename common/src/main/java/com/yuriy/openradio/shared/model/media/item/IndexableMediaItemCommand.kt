/*
 * Copyright 2018-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import com.yuriy.openradio.shared.model.net.UrlLayer

/**
 * Created by Chernyshov Yurii
 * At Android Studio
 * On 14/01/18
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * A command whose node the provider serves in pages. It fetches the page its request names,
 * [MediaItemCommandDependencies.pageNumber], and keeps no page index of its own: the presenter
 * shares one instance of each command among all clients and all nodes it serves, so an index on
 * it would be moved by every one of them (fnr-1557f).
 */
abstract class IndexableMediaItemCommand internal constructor() : MediaItemCommandImpl() {

    /** The second page coming back empty is reported, the first is not. */
    override fun doLoadNoDataReceived(pageNumber: Int): Boolean {
        return pageNumber == UrlLayer.FIRST_PAGE_INDEX + 1
    }
}
