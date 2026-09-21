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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the two resolvers as far as they can be covered without a name server.
 *
 * [DnsMirrorUrlResolver] resolves Radio Browser mirrors over DNS, so no case here may hand it an
 * address carrying [UrlLayerRadioBrowserImpl.BASE_URL_PREFIX]: that is the one input which reaches
 * the lookup. Its other branch is worth pinning precisely because it is the one that keeps station
 * artwork and stream probes from being redirected at a Radio Browser mirror.
 */
class ConnectionUrlResolverTest {

    @Test
    fun directResolverKeepsTheAddressItIsGiven() {
        val resolver = DirectUrlResolver()

        val url = resolver.resolve(Uri.parse(DATASET_URL), NO_PARAMETERS)

        assertEquals(DATASET_URL, url.toString())
    }

    @Test
    fun directResolverAnswersWithNothingForAnAddressItCannotParse() {
        val resolver = DirectUrlResolver()

        assertNull(resolver.resolve(Uri.parse(UNKNOWN_SCHEME), NO_PARAMETERS))
    }

    @Test
    fun mirrorResolverPassesThroughAnAddressThatIsNotRadioBrowsers() {
        val resolver = DnsMirrorUrlResolver()

        val url = resolver.resolve(Uri.parse(ARTWORK_URL), NO_PARAMETERS)

        assertEquals(ARTWORK_URL, url.toString())
    }

    @Test
    fun mirrorResolverAnswersWithNothingForAnAddressItCannotParse() {
        val resolver = DnsMirrorUrlResolver()

        assertNull(resolver.resolve(Uri.parse(UNKNOWN_SCHEME), NO_PARAMETERS))
    }

    @Test
    fun parametersAreDiagnosticsAndDoNotChangeTheAddress() {
        val resolver = DirectUrlResolver()

        val url = resolver.resolve(
            Uri.parse(DATASET_URL), listOf(Pair("name", "Jazz & Blues"))
        )

        assertEquals(DATASET_URL, url.toString())
    }

    private companion object {

        val NO_PARAMETERS = emptyList<Pair<String, String>>()

        /**
         * Reserved by RFC 6761, and never looked up here: the pass-through branch returns before
         * any lookup can happen.
         */
        const val ARTWORK_URL = "https://images.example.test/station/7/logo.png"

        const val DATASET_URL = "https://data.example.test/db/index/webradios.min.json"

        const val UNKNOWN_SCHEME = "httpx://data.example.test/webradios.min.json"
    }
}
