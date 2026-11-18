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

package com.yuriy.openradio.mobile.view.main

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkCacheTest {

    @Test
    fun sampleSizeIsOneForAnImageSmallerThanTheTile() {
        assertEquals(1, ArtworkCache.sampleSize(40, 40, TILE_PX))
    }

    @Test
    fun sampleSizeIsOneForAnImageOfExactlyTheTileSize() {
        assertEquals(1, ArtworkCache.sampleSize(76, 76, TILE_PX))
        assertEquals(1, ArtworkCache.sampleSize(151, 151, TILE_PX))
    }

    @Test
    fun sampleSizeHalvesOnceTheShorterSideAllowsIt() {
        assertEquals(2, ArtworkCache.sampleSize(152, 152, TILE_PX))
    }

    @Test
    fun sampleSizeFollowsTheShorterSideOfANonSquareImage() {
        assertEquals(2, ArtworkCache.sampleSize(1000, 160, TILE_PX))
        assertEquals(2, ArtworkCache.sampleSize(160, 1000, TILE_PX))
    }

    @Test
    fun sampleSizeOfALargeImageStaysAtLeastTheTile() {
        assertEquals(32, ArtworkCache.sampleSize(4096, 4096, TILE_PX))
    }

    @Test
    fun sampleSizeIsOneForUnknownBounds() {
        assertEquals(1, ArtworkCache.sampleSize(0, 500, TILE_PX))
        assertEquals(1, ArtworkCache.sampleSize(500, 0, TILE_PX))
        assertEquals(1, ArtworkCache.sampleSize(-1, 500, TILE_PX))
        assertEquals(1, ArtworkCache.sampleSize(500, -1, TILE_PX))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sampleSizeRejectsANonPositiveTarget() {
        ArtworkCache.sampleSize(500, 500, 0)
    }

    @Test
    fun aSecondRequestForTheSameIdDoesNotLoadAgain() = runBlocking {
        val loader = RecordingLoader(mapOf(URI_A to bitmap(10, 10)))
        val cache = ArtworkCache(loader::load)

        val first = cache.load(ID_A, URI_A)
        val second = cache.load(ID_A, URI_A)

        assertNotNull(first)
        assertSame(first, second)
        assertEquals(1, loader.callsFor(URI_A))
        assertSame(first, cache.cached(ID_A))
    }

    @Test
    fun aRememberedFailureIsNotLoadedAgainUntilInvalidated() = runBlocking {
        val loader = RecordingLoader(emptyMap())
        val cache = ArtworkCache(loader::load)

        assertNull(cache.load(ID_A, URI_A))
        assertNull(cache.load(ID_A, URI_A))
        assertEquals(1, loader.callsFor(URI_A))

        cache.invalidate(ID_A)
        assertNull(cache.load(ID_A, URI_A))
        assertEquals(2, loader.callsFor(URI_A))
    }

    @Test
    fun invalidateBumpsOnlyThatIdsRevision() {
        val cache = ArtworkCache(RecordingLoader(emptyMap())::load)

        assertEquals(0, cache.revision(ID_A))
        assertEquals(0, cache.revision(ID_B))

        cache.invalidate(ID_A)

        assertEquals(1, cache.revision(ID_A))
        assertEquals(0, cache.revision(ID_B))
    }

    @Test
    fun invalidateDropsOnlyThatIdsResult() = runBlocking {
        val loader = RecordingLoader(mapOf(URI_A to bitmap(10, 10), URI_B to bitmap(10, 10)))
        val cache = ArtworkCache(loader::load)
        cache.load(ID_A, URI_A)
        cache.load(ID_B, URI_B)

        cache.invalidate(ID_A)

        assertNull(cache.cached(ID_A))
        assertNotNull(cache.cached(ID_B))

        cache.load(ID_A, URI_A)
        cache.load(ID_B, URI_B)

        assertEquals(2, loader.callsFor(URI_A))
        assertEquals(1, loader.callsFor(URI_B))
    }

    @Test
    fun theCacheEvictsTheLeastRecentlyUsedByBytes() = runBlocking {
        val loader = RecordingLoader(
            mapOf(URI_A to bitmap(10, 10), URI_B to bitmap(10, 10), URI_C to bitmap(10, 10)),
        )
        // Each 10x10 ARGB_8888 bitmap is 400 bytes, so the third one does not fit.
        val cache = ArtworkCache(loader::load, maxBytes = 1000)

        cache.load(ID_A, URI_A)
        cache.load(ID_B, URI_B)
        cache.load(ID_C, URI_C)

        assertNull(cache.cached(ID_A))
        assertNotNull(cache.cached(ID_B))
        assertNotNull(cache.cached(ID_C))
    }

    @Test
    fun aLoadThatFinishesAfterItsIdWasInvalidatedIsNotRemembered() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<ImageBitmap?>()
        val loader = RecordingLoader(mapOf(URI_A to bitmap(10, 10)))
        val cache = ArtworkCache({ uri ->
            loader.record(uri)
            started.complete(Unit)
            gate.await()
        })

        val pending = async { cache.load(ID_A, URI_A) }
        started.await()
        cache.invalidate(ID_A)
        gate.complete(bitmap(10, 10))
        pending.await()

        assertNull(cache.cached(ID_A))

        cache.load(ID_A, URI_A)
        assertEquals(2, loader.callsFor(URI_A))
    }

    private fun bitmap(width: Int, height: Int): ImageBitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

    /** Hands out the configured bitmap per uri and counts how often each uri was loaded. */
    private class RecordingLoader(private val mBitmaps: Map<String, ImageBitmap?>) {
        private val mCalls = HashMap<String, Int>()

        fun callsFor(uri: String): Int = mCalls[uri] ?: 0

        suspend fun load(uri: String): ImageBitmap? {
            record(uri)
            return mBitmaps[uri]
        }

        fun record(uri: String) {
            mCalls[uri] = callsFor(uri) + 1
        }
    }

    private companion object {
        const val TILE_PX = 76
        const val ID_A = "a"
        const val ID_B = "b"
        const val ID_C = "c"
        const val URI_A = "content://images/a"
        const val URI_B = "content://images/b"
        const val URI_C = "content://images/c"
    }
}
