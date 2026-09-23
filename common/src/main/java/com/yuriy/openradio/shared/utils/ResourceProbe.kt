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

package com.yuriy.openradio.shared.utils

import android.content.Context

/**
 * Answers whether a url can be opened. [RadioStationValidatorImpl] asks it about a candidate's
 * stream and home page, and taking it as a collaborator is what lets the validator's rules be
 * tested without a network.
 */
fun interface ResourceProbe {

    /**
     * @param context Context the request is made with.
     * @param url     Url to open.
     * @return Whether [url] answered with a success status. Blocks until it does or fails.
     */
    fun isReachable(context: Context, url: String): Boolean
}
