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
import android.os.Bundle
import androidx.media.MediaSessionManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers which browser [ArtworkGrants] grants artwork to and which artwork it grants, against a
 * recording grant function in place of `Context.grantUriPermission` and a car check that
 * recognises one browser in place of `MediaSession.isAutoCompanionController`.
 */
class ArtworkGrantsTest {

    private val mGranted = mutableListOf<Pair<String, Uri>>()

    private val mCar = browser(CAR_PACKAGE)

    private val mGrants = ArtworkGrants({ it === mCar }, { packageName, uri -> mGranted.add(packageName to uri) })

    @Test
    fun theArtworkOfEveryItemIsGrantedToTheBrowserInOrder() {
        val first = ImagesStore.buildImageUri("first", "https://radio.example/first.png")
        val second = ImagesStore.buildImageUri("second", "/data/local/second.jpg")

        mGrants.grant(mCar, listOf(item("first", first), item("second", second)))

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
            mCar,
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
        mGrants.grant(mCar, emptyList())

        assertEquals(emptyList<Pair<String, Uri>>(), mGranted)
    }

    /**
     * Any application may connect to the media browser service; one that is not Android Auto
     * must not be able to read the artwork store through the items it browses.
     */
    @Test
    fun aBrowserThatIsNotTheCarIsGrantedNothing() {
        val station = ImagesStore.buildImageUri("station", "https://radio.example/station.png")

        mGrants.grant(browser("com.example.other"), listOf(item("station", station)))

        assertEquals(emptyList<Pair<String, Uri>>(), mGranted)
    }

    /**
     * The grant goes to the package of the browser that asked, not to a fixed car package, so the
     * name the grant carries is the one the session verified against the caller's uid.
     */
    @Test
    fun theGrantNamesThePackageOfTheBrowser() {
        val station = ImagesStore.buildImageUri("station", "https://radio.example/station.png")
        val car = browser("com.example.car")

        ArtworkGrants({ it === car }, { packageName, uri -> mGranted.add(packageName to uri) })
            .grant(car, listOf(item("station", station)))

        assertEquals(listOf("com.example.car" to station), mGranted)
    }

    private fun item(mediaId: String, artwork: Uri?): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(MediaMetadata.Builder().setArtworkUri(artwork).build())
            .build()
    }

    private fun browser(packageName: String): MediaSession.ControllerInfo {
        return MediaSession.ControllerInfo.createTestOnlyControllerInfo(
            MediaSessionManager.RemoteUserInfo(packageName, 0, 0),
            MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION,
            MediaSession.ControllerInfo.LEGACY_CONTROLLER_INTERFACE_VERSION,
            false,
            Bundle()
        )
    }

    companion object {
        private const val CAR_PACKAGE = "com.google.android.projection.gearhead"
    }
}
