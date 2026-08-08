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