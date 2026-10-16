/*
 * Copyright 2015-2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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
import com.yuriy.openradio.shared.model.media.item.MediaItemCommand.IUpdatePlaybackState
import com.yuriy.openradio.shared.utils.AppUtils

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 8/31/15
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * This class is implementation of the [MediaItemCommand] that designed to prepare data to display radio stations of
 * child categories of category.
 */
class MediaItemChildCategories : IndexableMediaItemCommand() {

    override fun execute(playbackStateListener: IUpdatePlaybackState, dependencies: MediaItemCommandDependencies) {
        super.execute(playbackStateListener, dependencies)
        if (dependencies.isSavedInstance) {
            deliverResult(dependencies)
            return
        }
        val childMenuId = dependencies.parentId
            .replace(MediaId.MEDIA_ID_CHILD_CATEGORIES, AppUtils.EMPTY_STRING)
        val pageNumber = pageNumber
        val set = dependencies.presenter.getStationsInCategory(childMenuId, pageNumber)
        handleDataLoaded(
            playbackStateListener,
            dependencies,
            set,
            pageNumber
        ) { advancePast(pageNumber) }
    }
}
