package com.yuriy.openradio.shared.view.list

import com.yuriy.openradio.shared.view.list.TestMediaItemsAdapter.Companion.mediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaItemsAdapterTest {

    @Test
    fun refreshedChildrenReplaceExistingItems() {
        val adapter = TestMediaItemsAdapter()
        adapter.updateData(listOf(mediaItem("old")), false)

        adapter.updateData(listOf(mediaItem("refreshed")), true)

        assertEquals(listOf("refreshed"), adapter.mediaIds())
    }

    @Test
    fun pagedChildrenAppendToExistingItems() {
        val adapter = TestMediaItemsAdapter()
        adapter.updateData(listOf(mediaItem("first")), false)

        adapter.updateData(listOf(mediaItem("second")), false)

        assertEquals(listOf("first", "second"), adapter.mediaIds())
    }
}
