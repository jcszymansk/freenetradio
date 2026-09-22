/*
 * Copyright 2017-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.utils

import android.content.Context
import com.yuriy.openradio.shared.model.media.RadioStationToAdd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Validator that reaches the stream and the home page over the network to decide whether a
 * candidate Radio Station is usable.
 *
 * A candidate without a name or a stream url fails at once, before anything is probed. Otherwise
 * the stream is probed first and an unreachable one fails the candidate. The home page is optional:
 * an empty one is not probed at all, and an unreachable one warns ahead of the success.
 *
 * @param mUiScope Scope the answers are delivered on.
 * @param mScope   Scope the network probes run on.
 * @param mProbe   Answers whether a url can be opened.
 */
class RadioStationValidatorImpl(
    private val mUiScope: CoroutineScope,
    private val mScope: CoroutineScope,
    private val mProbe: ResourceProbe
) : RadioStationValidator {

    override fun validate(
        context: Context, rsToAdd: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onWarning: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    ) {
        if (rsToAdd.name.isEmpty()) {
            AppLogger.w("$CLASS_NAME candidate has no name")
            onFailure("Radio Station's name is invalid")
            return
        }
        val url = rsToAdd.url
        if (url.isEmpty()) {
            AppLogger.w("$CLASS_NAME candidate '${rsToAdd.name}' has no stream url")
            onFailure("Radio Station's url is invalid")
            return
        }

        mScope.launch {
            AppLogger.d("$CLASS_NAME probing stream $url")
            if (!mProbe.isReachable(context, url)) {
                AppLogger.w("$CLASS_NAME stream $url is unreachable")
                mUiScope.launch { onFailure("Radio Station's stream is invalid") }
                return@launch
            }
            val homePage = rsToAdd.homePage
            if (homePage.isEmpty()) {
                AppLogger.d("$CLASS_NAME candidate '${rsToAdd.name}' has no home page to probe")
            } else if (!mProbe.isReachable(context, homePage)) {
                AppLogger.w("$CLASS_NAME home page $homePage is unreachable")
                mUiScope.launch { onWarning("Radio Station's home page is invalid") }
            }
            AppLogger.d("$CLASS_NAME candidate '${rsToAdd.name}' validated")
            mUiScope.launch { onSuccess("Radio Station validated successfully") }
        }
    }

    companion object {
        private val CLASS_NAME = RadioStationValidatorImpl::class.java.simpleName
    }
}
