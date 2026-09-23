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

package com.yuriy.openradio.testing

import android.content.Context
import android.net.ConnectivityManager

/**
 * Asks the device whether it has a network, the way `NetworkLayerImpl` asks before it lets a fetch
 * go ahead.
 *
 * The suite demands more than the app does. `NetworkLayerImpl` also treats a network that exists
 * but is not connecting as offline, and this does not, because two of the app's own paths reach
 * the network without consulting that gate at all: the station validator and the artwork loader.
 * Only a device with no network whatsoever keeps those quiet.
 *
 * @param context any context of the process under test.
 * @return a description of the active network, or null when the device has none.
 */
@Suppress("DEPRECATION")
internal fun activeNetworkOf(context: Context): String? {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return manager.activeNetworkInfo?.toString()
}
