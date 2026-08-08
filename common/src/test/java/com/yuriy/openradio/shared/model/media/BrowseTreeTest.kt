package com.yuriy.openradio.shared.model.media

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseTreeTest {

    @Test
    fun replacementRemovesOldIndexesAndAppendAddsNewEntries() {
        val tree = BrowseTree()
        val parentId = "parent"
        val firstChild = MediaItem.Builder().setMediaId("first-child").build()
        val firstStation = RadioStation.makeDefaultInstance("first-station")
        val secondChild = MediaItem.Builder().setMediaId("second-child").build()
        val secondStation = RadioStation.makeDefaultInstance("second-station")
        val thirdChild = MediaItem.Builder().setMediaId("third-child").build()
        val thirdStation = RadioStation.makeDefaultInstance("third-station")

        tree[parentId] = BrowseTree.BrowseData(
            mutableListOf(firstChild),
            mutableSetOf(firstStation)
        )
        tree[parentId] = BrowseTree.BrowseData(
            mutableListOf(secondChild),
            mutableSetOf(secondStation)
        )

        assertEquals(mutableListOf(secondChild), tree[parentId])
        assertNull(tree.getMediaItemByMediaId(firstChild.mediaId))
        assertSame(RadioStation.INVALID_INSTANCE, tree.getRadioStationByMediaId(firstStation.id))
        assertSame(secondChild, tree.getMediaItemByMediaId(secondChild.mediaId))
        assertSame(secondStation, tree.getRadioStationByMediaId(secondStation.id))

        tree.append(
            parentId,
            BrowseTree.BrowseData(mutableListOf(thirdChild), mutableSetOf(thirdStation))
        )

        assertEquals(mutableListOf(secondChild, thirdChild), tree[parentId])
        assertSame(thirdChild, tree.getMediaItemByMediaId(thirdChild.mediaId))
        assertSame(thirdStation, tree.getRadioStationByMediaId(thirdStation.id))
    }

    @Test
    fun findsParentListsByParentOrChildId() {
        val tree = BrowseTree()
        val parentId = "parent"
        val child = MediaItem.Builder().setMediaId("child").build()
        tree[parentId] = BrowseTree.BrowseData(mutableListOf(child), mutableSetOf())

        assertEquals(tree[parentId], tree.getMediaItemsByMediaId(parentId))
        assertEquals(tree[parentId], tree.getMediaItemsByMediaId(child.mediaId))
        assertTrue(tree.getMediaItemsByMediaId("missing").isEmpty())
    }

    @Test
    fun invalidationRemovesParentAndChildIndexes() {
        val tree = BrowseTree()
        val parentId = "parent"
        val child = MediaItem.Builder().setMediaId("child").build()
        val station = RadioStation.makeDefaultInstance("station")
        tree[parentId] = BrowseTree.BrowseData(mutableListOf(child), mutableSetOf(station))

        tree.invalidate(parentId)

        assertNull(tree[parentId])
        assertTrue(tree.getMediaItemsByMediaId(parentId).isEmpty())
        assertTrue(tree.getMediaItemsByMediaId(child.mediaId).isEmpty())
        assertNull(tree.getMediaItemByMediaId(child.mediaId))
        assertSame(RadioStation.INVALID_INSTANCE, tree.getRadioStationByMediaId(station.id))
    }
}
