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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/**
 * Covers the two resolvers without a name server.
 *
 * [DnsMirrorUrlResolver] is built here only over a [RecordingHostLookup], never over
 * [DnsHostLookup], so a case that reaches the lookup records the name it asked for instead of
 * sending a query off the machine. That is what lets the pass-through cases assert that nothing
 * was looked up: a prefix check that let every address through to the lookup would otherwise go
 * unnoticed, because substituting a mirror for a prefix an address does not carry leaves it as it was.
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
        val lookup = RecordingHostLookup { listOf(MIRROR_HOST) }
        val resolver = DnsMirrorUrlResolver(lookup)

        val url = resolver.resolve(Uri.parse(ARTWORK_URL), NO_PARAMETERS)

        assertEquals(ARTWORK_URL, url.toString())
        assertEquals(emptyList<String>(), lookup.mNames)
    }

    @Test
    fun mirrorResolverAnswersWithNothingForAnAddressItCannotParse() {
        val lookup = RecordingHostLookup { listOf(MIRROR_HOST) }
        val resolver = DnsMirrorUrlResolver(lookup)

        assertNull(resolver.resolve(Uri.parse(UNKNOWN_SCHEME), NO_PARAMETERS))
        assertEquals(emptyList<String>(), lookup.mNames)
    }

    @Test
    fun mirrorResolverPutsALookedUpMirrorInPlaceOfThePrefix() {
        val lookup = RecordingHostLookup { listOf(MIRROR_HOST) }
        val resolver = DnsMirrorUrlResolver(lookup)

        val url = resolver.resolve(Uri.parse(RADIO_BROWSER_URL), NO_PARAMETERS)

        assertEquals("https://$MIRROR_HOST$RADIO_BROWSER_PATH", url.toString())
        assertEquals(listOf(MIRROR_LIST_NAME), lookup.mNames)
    }

    @Test
    fun mirrorResolverSpreadsRequestsOverTheMirrorsItWasGiven() {
        val mirrors = setOf("de2.mirror.example.test", "fr2.mirror.example.test")
        val resolver = DnsMirrorUrlResolver(RecordingHostLookup { mirrors.toList() })

        val picked = (1..PICKS).map {
            resolver.resolve(Uri.parse(RADIO_BROWSER_URL), NO_PARAMETERS)!!.host
        }.toSet()

        assertEquals(mirrors, picked)
    }

    @Test
    fun mirrorResolverLooksTheMirrorsUpOnceAndKeepsThem() {
        val lookup = RecordingHostLookup { listOf(MIRROR_HOST) }
        val resolver = DnsMirrorUrlResolver(lookup)

        resolver.resolve(Uri.parse(RADIO_BROWSER_URL), NO_PARAMETERS)
        val url = resolver.resolve(Uri.parse(RADIO_BROWSER_URL), NO_PARAMETERS)

        assertEquals("https://$MIRROR_HOST$RADIO_BROWSER_PATH", url.toString())
        assertEquals(listOf(MIRROR_LIST_NAME), lookup.mNames)
    }

    @Test
    fun mirrorResolverFallsBackToARecordedMirrorWhenTheLookupFails() {
        val resolver = DnsMirrorUrlResolver(
            RecordingHostLookup { throw UnknownHostException(it) }
        )

        val urls = (1..PICKS).map {
            resolver.resolve(Uri.parse(RADIO_BROWSER_URL), NO_PARAMETERS)!!
        }

        assertEquals(RECORDED_MIRRORS, urls.map { it.host }.toSet())
        assertEquals(setOf(RADIO_BROWSER_PATH), urls.map { it.path }.toSet())
    }

    @Test
    fun mirrorResolverFallsBackToARecordedMirrorWhenTheLookupFindsNone() {
        val resolver = DnsMirrorUrlResolver(RecordingHostLookup { emptyList() })

        val url = resolver.resolve(Uri.parse(RADIO_BROWSER_URL), NO_PARAMETERS)!!

        assertTrue("${url.host} is not a recorded mirror", url.host in RECORDED_MIRRORS)
    }

    @Test
    fun mirrorResolverLooksUpAgainAfterALookupThatFailed() {
        var fail = true
        val lookup = RecordingHostLookup {
            if (fail) throw UnknownHostException(it) else listOf(MIRROR_HOST)
        }
        val resolver = DnsMirrorUrlResolver(lookup)

        resolver.resolve(Uri.parse(RADIO_BROWSER_URL), NO_PARAMETERS)
        fail = false
        val url = resolver.resolve(Uri.parse(RADIO_BROWSER_URL), NO_PARAMETERS)

        assertEquals("https://$MIRROR_HOST$RADIO_BROWSER_PATH", url.toString())
        assertEquals(listOf(MIRROR_LIST_NAME, MIRROR_LIST_NAME), lookup.mNames)
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

        const val ARTWORK_URL = "https://images.example.test/station/7/logo.png"

        const val RADIO_BROWSER_PATH = "/json/stations/bycountrycodeexact/PL"

        const val RADIO_BROWSER_URL =
            "${UrlLayerRadioBrowserImpl.BASE_URL_PREFIX}$RADIO_BROWSER_PATH"

        const val MIRROR_HOST = "de2.mirror.example.test"

        /** The name Radio Browser publishes its mirrors under. */
        const val MIRROR_LIST_NAME = "all.api.radio-browser.info"

        /** The mirrors the resolver falls back to, recorded by hand in production as well. */
        val RECORDED_MIRRORS = setOf(
            "de1.api.radio-browser.info", "fr1.api.radio-browser.info", "nl1.api.radio-browser.info"
        )

        /**
         * Picks enough for every mirror to come up at least once: with three to choose from, the
         * chance that one never does is below one in a billion.
         */
        const val PICKS = 50

        const val DATASET_URL = "https://data.example.test/db/index/webradios.min.json"

        const val UNKNOWN_SCHEME = "httpx://data.example.test/webradios.min.json"
    }
}

/**
 * Answers a lookup from [mAnswer] and records every name it was asked for, so a test can tell
 * whether the resolver reached for a name server at all.
 */
private class RecordingHostLookup(private val mAnswer: (String) -> List<String>) : HostLookup {

    val mNames = mutableListOf<String>()

    override fun hostsOf(name: String): List<String> {
        mNames.add(name)
        return mAnswer(name)
    }
}
