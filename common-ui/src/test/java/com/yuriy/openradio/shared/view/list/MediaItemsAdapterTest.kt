package com.yuriy.openradio.shared.view.list

import android.view.ViewGroup
import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaItemsAdapterTest {

    @Test
    fun refreshedChildrenReplaceExistingItems() {
        val adapter = TestAdapter()
        adapter.updateData(listOf(mediaItem("old")), false)

        adapter.updateData(listOf(mediaItem("refreshed")), true)

        assertEquals(1, adapter.itemCount)
        assertEquals("refreshed", adapter.getItem(0)?.mediaId)
    }

    @Test
    fun pagedChildrenAppendToExistingItems() {
        val adapter = TestAdapter()
        adapter.updateData(listOf(mediaItem("first")), false)

        adapter.updateData(listOf(mediaItem("second")), false)

        assertEquals(2, adapter.itemCount)
        assertEquals("first", adapter.getItem(0)?.mediaId)
        assertEquals("second", adapter.getItem(1)?.mediaId)
    }

    private fun mediaItem(id: String): MediaItem {
        return MediaItem.Builder().setMediaId(id).build()
    }

    private class TestAdapter : MediaItemsAdapter() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemViewHolder {
            throw AssertionError("View holders are not used by this test")
        }

        override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) = Unit
    }
}
