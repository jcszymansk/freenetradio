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

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.annotation.MainThread
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.yuriy.openradio.shared.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** One finished load; a failed or empty load is remembered as [Missing]. */
private sealed interface ArtworkResult {
    data class Loaded(val bitmap: ImageBitmap) : ArtworkResult
    data object Missing : ArtworkResult
}

/**
 * Station logos of the main screen, decoded once per image id and kept within [maxBytes].
 *
 * A finished download invalidates only its own id, so only the rows that show that logo redraw;
 * the revision of each id is a snapshot state that a row reads for that id alone. Main thread
 * only: the revisions and the LRU are touched without locks.
 */
@MainThread
internal class ArtworkCache(
    private val mLoader: suspend (uri: String) -> ImageBitmap?,
    maxBytes: Int = MAX_BYTES,
) {
    private val mResults = object : LruCache<String, ArtworkResult>(maxBytes) {
        override fun sizeOf(key: String, value: ArtworkResult): Int = when (value) {
            is ArtworkResult.Loaded -> value.bitmap.asAndroidBitmap().byteCount
            ArtworkResult.Missing -> 1
        }
    }
    private val mRevisions = HashMap<String, MutableIntState>()

    fun revision(imageId: String): Int = revisionOf(imageId).intValue

    fun cached(imageId: String): ImageBitmap? = (mResults.get(imageId) as? ArtworkResult.Loaded)?.bitmap

    /**
     * Returns the logo of [imageId], asking the loader only when nothing is remembered for it.
     * A result that arrives after [invalidate] of the same id is returned but not remembered.
     */
    suspend fun load(imageId: String, uri: String): ImageBitmap? {
        val known = mResults.get(imageId)
        if (known != null) {
            return (known as? ArtworkResult.Loaded)?.bitmap
        }
        val requestedRevision = revision(imageId)
        val bitmap = withContext(Dispatchers.IO) { mLoader(uri) }
        if (revision(imageId) == requestedRevision) {
            mResults.put(imageId, bitmap?.let { ArtworkResult.Loaded(it) } ?: ArtworkResult.Missing)
        }
        return bitmap
    }

    fun invalidate(imageId: String) {
        mResults.remove(imageId)
        revisionOf(imageId).intValue++
    }

    private fun revisionOf(imageId: String): MutableIntState =
        mRevisions.getOrPut(imageId) { mutableIntStateOf(0) }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024

        /**
         * The largest power of two by which an image of this size can be sampled and still be at
         * least [targetPx] on its shorter side, which is the side a crop keeps whole.
         */
        fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
            require(targetPx > 0) { "target must be positive, was $targetPx" }
            val shorter = minOf(width, height)
            if (shorter <= 0) {
                return 1
            }
            var sample = 1
            while (shorter / (sample * 2) >= targetPx) {
                sample *= 2
            }
            return sample
        }

        /** A cache that decodes from the images provider, sampled down to [targetPx]. */
        fun forProvider(context: Context, targetPx: Int): ArtworkCache {
            val appContext = context.applicationContext
            return ArtworkCache({ uri -> readArtwork(appContext, uri, targetPx) })
        }
    }
}

/** Without a provided cache nothing loads, so the tiles show their initials. */
internal val LocalArtworkCache: ProvidableCompositionLocal<ArtworkCache> =
    staticCompositionLocalOf { ArtworkCache({ null }) }

private fun readArtwork(context: Context, uri: String, targetPx: Int): ImageBitmap? {
    return try {
        val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = ArtworkCache.sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    } catch (failure: IOException) {
        AppLogger.w("ArtworkCache $uri not read: $failure")
        null
    } catch (failure: SecurityException) {
        AppLogger.w("ArtworkCache $uri not readable: $failure")
        null
    }
}
