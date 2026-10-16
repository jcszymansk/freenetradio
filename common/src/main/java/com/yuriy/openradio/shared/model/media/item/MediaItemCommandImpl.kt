/*
 * Copyright 2017-2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.item.MediaItemCommand.IUpdatePlaybackState
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import java.util.TreeSet

/**
 * Created by Chernyshov Yurii
 * At Android Studio
 * On 14/01/18
 * E-Mail: chernyshov.yuriy@gmail.com
 */
abstract class MediaItemCommandImpl internal constructor() : MediaItemCommand {

    override fun execute(playbackStateListener: IUpdatePlaybackState, dependencies: MediaItemCommandDependencies) {

    }

    /**
     * @return Whether a request for [pageNumber] that found nothing reports "no data" besides
     * answering empty.
     */
    abstract fun doLoadNoDataReceived(pageNumber: Int): Boolean

    /**
     * Answers the browse request with the stations of [set], or with nothing when [set] is empty.
     */
    fun handleDataLoaded(
        playbackStateListener: IUpdatePlaybackState,
        dependencies: MediaItemCommandDependencies,
        set: Set<RadioStation>
    ) {
        if (set.isEmpty()) {
            if (doLoadNoDataReceived(dependencies.pageNumber)) {
                dependencies.reportNoData(playbackStateListener)
            } else {
                dependencies.answer(dependencies.getMediaItems())
            }
            return
        }
        deliverResult(dependencies, set)
    }

    fun deliverResult(dependencies: MediaItemCommandDependencies, set: Set<RadioStation> = TreeSet()) {
        for (radioStation in set) {
            dependencies.addMediaItem(
                MediaItemBuilder.buildPlayable(
                    radioStation, dependencies.presenter.isRadioStationFavorite(radioStation)
                )
            )
        }
        dependencies.answer(dependencies.getMediaItems(), set)
    }
}
