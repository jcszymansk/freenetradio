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

package com.yuriy.openradio.shared.model.storage.images

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers which artwork [ArtworkGrants] lets the car's media browser read, against a recording
 * grant function in place of `Context.grantUriPermission`.
 */
class ArtworkGrantsTest {

    private val mGranted = mutableListOf<Pair<String, Uri>>()

    private val mGrants = ArtworkGrants { packageName, uri -> mGranted.add(packageName to uri) }

    @Test
    fun theArtworkOfEveryItemIsGrantedToTheBrowserInOrder() {
        val first = ImagesStore.buildImageUri("first", "https://radio.example/first.png")
        val second = ImagesStore.buildImageUri("second", "/data/local/second.jpg")

        mGrants.grant(CAR_PACKAGE, listOf(item("first", first), item("second", second)))

        assertEquals(listOf(CAR_PACKAGE to first, CAR_PACKAGE to second), mGranted)
    }

    /**
     * Category icons are app resources and the placeholder station points at a web image; the
     * browser reads neither through the artwork provider, and a grant on a foreign authority
     * would throw.
     */
    @Test
    fun artworkTheProviderDoesNotServeIsNotGranted() {
        val ours = ImagesStore.buildImageUri("station", "https://radio.example/station.png")

        mGrants.grant(
            CAR_PACKAGE,
            listOf(
                item("category", Uri.parse("android.resource://com.github.jcszymansk.freenetradio/drawable/1")),
                item("web", Uri.parse("https://radio.example/icon.png")),
                item("bare", null),
                item("station", ours)
            )
        )

        assertEquals(listOf(CAR_PACKAGE to ours), mGranted)
    }

    @Test
    fun anEmptyListGrantsNothing() {
        mGrants.grant(CAR_PACKAGE, emptyList())

        assertEquals(emptyList<Pair<String, Uri>>(), mGranted)
    }

    private fun item(mediaId: String, artwork: Uri?): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(MediaMetadata.Builder().setArtworkUri(artwork).build())
            .build()
    }

    companion object {
        private const val CAR_PACKAGE = "com.google.android.projection.gearhead"
    }
}
