/*
 * Copyright 2024 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.utils

import com.yuriy.openradio.shared.model.media.MediaId
import org.junit.Assert
import org.junit.Test

class AppUtilsTest {

    @Test
    fun testSerializeMap() {
        val keyA = "KEY_FAV"
        val keyB = "KEY_LOC"
        val data = hashMapOf(
            keyA to "mStorageManagerLayer.getAllFavoritesAsString()",
            keyB to "mStorageManagerLayer.getAllDeviceLocalsAsString()"
        )

        val serialized = AppUtils.serializeMap(data)

        val deserialized = AppUtils.deserializeMap(serialized)

        Assert.assertEquals(data[keyA], deserialized[keyA])
        Assert.assertEquals(data[keyB], deserialized[keyB])

        val deserializedEmpty = AppUtils.deserializeMap(null)
        Assert.assertTrue(deserializedEmpty.isEmpty())
    }

    @Test
    fun malformedDataAndMissingEntriesAreSafe() {
        val imported = AppUtils.deserializeMap(
            AppUtils.serializeMap(mapOf("KEY_FAV" to "favorites"))
        )

        Assert.assertEquals("favorites", imported["KEY_FAV"])
        Assert.assertFalse(imported.containsKey("KEY_LOC"))
        Assert.assertTrue(AppUtils.deserializeMap(byteArrayOf()).isEmpty())
        Assert.assertTrue(AppUtils.deserializeMap(byteArrayOf(1, 2, 3)).isEmpty())
    }

    @Test
    fun identifiesCatalogueChanges() {
        Assert.assertTrue(AppUtils.isSameCatalogue("rock", "rock"))
        Assert.assertFalse(AppUtils.isSameCatalogue("rock", "jazz"))
        Assert.assertFalse(
            AppUtils.isSameCatalogue(MediaId.MEDIA_ID_ROOT, MediaId.MEDIA_ID_ROOT)
        )
        Assert.assertFalse(
            AppUtils.isSameCatalogue(MediaId.MEDIA_ID_FAVORITES_LIST, MediaId.MEDIA_ID_FAVORITES_LIST)
        )
        Assert.assertFalse(
            AppUtils.isSameCatalogue(
                MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST,
                MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
            )
        )
    }
}
