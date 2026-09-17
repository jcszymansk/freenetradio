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

package android.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UriTest {

    @Test
    fun parseSplitsEveryHierarchicalPart() {
        val uri = Uri.parse("https://radio.example/api/stations?country=PL&limit=20#top")

        assertEquals("https", uri.getScheme())
        assertEquals("radio.example", uri.getAuthority())
        assertEquals("/api/stations", uri.getPath())
        assertEquals("country=PL&limit=20", uri.getQuery())
        assertEquals("top", uri.getFragment())
        assertEquals("stations", uri.getLastPathSegment())
        assertEquals("PL", uri.getQueryParameter("country"))
        assertEquals("20", uri.getQueryParameter("limit"))
        assertNull(uri.getQueryParameter("offset"))
    }

    @Test
    fun parseKeepsOpaqueAndRelativeFormsIntact() {
        val relative = Uri.parse("stations/latest.mp3")

        assertNull(relative.getScheme())
        assertNull(relative.getAuthority())
        assertEquals("stations/latest.mp3", relative.getPath())
        assertEquals("stations/latest.mp3", relative.toString())

        val authorityOnly = Uri.parse("content://com.example.provider")

        assertEquals("com.example.provider", authorityOnly.getAuthority())
        assertEquals("", authorityOnly.getPath())
        assertNull(authorityOnly.getQuery())
    }

    @Test
    fun builderEncodesSegmentsAndParameters() {
        val uri = Uri.Builder()
            .scheme("content")
            .authority("com.example.provider")
            .appendPath("images")
            .appendPath("north pole")
            .appendQueryParameter("id", "station/1")
            .appendQueryParameter("url", "https://radio.example/logo.png")
            .build()

        assertEquals(
            "content://com.example.provider/images/north%20pole" +
                "?id=station%2F1&url=https%3A%2F%2Fradio.example%2Flogo.png",
            uri.toString()
        )
        assertEquals("station/1", uri.getQueryParameter("id"))
        assertEquals("https://radio.example/logo.png", uri.getQueryParameter("url"))
        assertEquals("north pole", uri.getLastPathSegment())
    }

    @Test
    fun buildUponPreservesTheOriginalAndAppends() {
        val original = Uri.parse("content://com.example.provider?id=1")
        val extended = original.buildUpon().appendQueryParameter("load", "success").build()

        assertEquals("content://com.example.provider?id=1", original.toString())
        assertEquals("content://com.example.provider?id=1&load=success", extended.toString())
    }

    @Test
    fun encodeKeepsUnreservedSymbolsAndDecodeRestoresThem() {
        assertEquals("a-b_c.d~e!f'g(h)i*j", Uri.encode("a-b_c.d~e!f'g(h)i*j"))
        assertEquals("a%20b%2Fc%3Fd%26e%3Df", Uri.encode("a b/c?d&e=f"))
        assertEquals("%C5%BC%C3%B3%C5%82w", Uri.encode("żółw"))

        assertEquals("a b/c?d&e=f", Uri.decode("a%20b%2Fc%3Fd%26e%3Df"))
        assertEquals("żółw", Uri.decode("%C5%BC%C3%B3%C5%82w"))
        assertNull(Uri.decode(null))
    }

    @Test
    fun decodeLeavesMalformedEscapesAlone() {
        assertEquals("100% sure", Uri.decode("100% sure"))
        assertEquals("tail%2", Uri.decode("tail%2"))
    }

    @Test
    fun theEmptyUriHasNothingInIt() {
        assertEquals("", Uri.EMPTY.toString())
        assertNull(Uri.EMPTY.getScheme())
        assertNull(Uri.EMPTY.getAuthority())
        assertEquals("", Uri.EMPTY.getPath())
        assertNull(Uri.EMPTY.getQuery())
        assertNull(Uri.EMPTY.getLastPathSegment())
        assertEquals(Uri.EMPTY, Uri.parse(""))
    }

    @Test
    fun equalityAndOrderFollowTheStringForm() {
        assertEquals(Uri.parse("https://a.example/b"), Uri.parse("https://a.example/b"))
        assertEquals(
            Uri.parse("https://a.example/b").hashCode(),
            Uri.parse("https://a.example/b").hashCode()
        )
        assertEquals(true, Uri.parse("https://a.example") < Uri.parse("https://b.example"))
    }
}
