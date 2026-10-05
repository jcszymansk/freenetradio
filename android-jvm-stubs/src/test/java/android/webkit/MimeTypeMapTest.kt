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

package android.webkit

import org.junit.Assert.assertEquals
import org.junit.Test

class MimeTypeMapTest {

    @Test
    fun extensionComesFromTheLastPathSegment() {
        assertEquals("mp3", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live.mp3"))
        assertEquals("m3u8", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/hls/master.m3u8"))
        assertEquals("AAC", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live.AAC"))
    }

    @Test
    fun queryAndFragmentAreNotPartOfTheExtension() {
        assertEquals("mp3", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live.mp3?token=a.b"))
        assertEquals("ogg", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live.ogg#start"))
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live?format=.mp3"))
    }

    @Test
    fun streamsWithoutAnExtensionYieldNothing() {
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live"))
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://radio.example.com:8000/"))
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl(""))
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl(null))
    }

    @Test
    fun aFileNameWithACharacterOutsideThePlatformSetHasNoExtension() {
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live.m3u8;jsessionid=ab"))
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live.m3u8;jsessionid=a.b"))
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/my live.mp3"))
        assertEquals("mp3", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live(1)%20hq.mp3"))
    }

    /**
     * The platform cuts at the last `?` and `#`, so a second one leaves the first in the name.
     */
    @Test
    fun onlyTheLastQueryAndFragmentSeparatorsCut() {
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live.mp3?a=1?b=2"))
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/live.mp3#a#b"))
        assertEquals("mp3", MimeTypeMap.getFileExtensionFromUrl("live.mp3?a#b"))
    }

    @Test
    fun aDottedDirectoryDoesNotLeakIntoTheExtension() {
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://radio.example/v1.2/live"))
    }
}
