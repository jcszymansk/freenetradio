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

package com.yuriy.openradio.shared.model.net

import android.net.Uri
import androidx.core.util.Pair
import java.net.URL

/**
 * Turns a request [Uri] into the [URL] a connection is actually opened against.
 *
 * This is the only seam in the application that may reach a name server, which is why it is
 * separate from [UrlLayer]: the Uri builders there are pure string work and must stay reachable
 * from a test, while an implementation of this interface must not be.
 */
interface ConnectionUrlResolver {

    /**
     * @param uri Address the caller wants to read.
     * @param parameters Request parameters, carried for diagnostics only.
     * @return The URL to connect to, or null when [uri] cannot be parsed as one.
     */
    fun resolve(uri: Uri, parameters: List<Pair<String, String>>): URL?
}
