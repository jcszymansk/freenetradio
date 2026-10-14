package com.yuriy.openradio.shared.model.media

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    /**
     * TASK-041: the playlist the service builds for its active station used to be stored under that
     * station's id, which answered every later selection of the station with the playlist instead
     * of the list it was selected from.
     */
    @Test
    fun aPlaylistDoesNotShadowTheListAStationIsBrowsedIn() {
        val tree = BrowseTree()
        val listed = station("listed")
        val sibling = station("sibling")
        tree["parent"] = data(listed, sibling)

        tree.setPlaylist(data(listed))

        assertNull(tree[listed.id])
        assertSame(tree["parent"], tree.getMediaItemsByMediaId(listed.id))
    }

    @Test
    fun aListBrowsedAfterThePlaylistAlsoTakesPrecedenceOverIt() {
        val tree = BrowseTree()
        val listed = station("listed")
        tree.setPlaylist(data(listed))

        tree["parent"] = data(listed, station("sibling"))

        assertSame(tree["parent"], tree.getMediaItemsByMediaId(listed.id))
    }

    @Test
    fun thePlaylistAnswersForWhatNoListHolds() {
        val tree = BrowseTree()
        val first = station("first")
        val second = station("second")
        val playlist = data(first, second)

        tree.setPlaylist(playlist)

        assertSame(playlist.children, tree.getMediaItemsByMediaId(second.id))
        assertSame(playlist.children[0], tree.getMediaItemByMediaId(first.id))
        assertSame(first, tree.getRadioStationByMediaId(first.id))
        assertTrue(tree.getMediaItemsByMediaId("missing").isEmpty())
    }

    @Test
    fun aSelectionPrefersTheListBeingBrowsed() {
        val tree = BrowseTree()
        val shared = station("shared")
        val alone = station("alone")
        tree["favorites"] = data(shared)
        tree["country"] = data(station("other"), shared)
        tree.setPlaylist(data(shared, alone))

        assertSame(tree["country"], tree.getMediaItemsByMediaId(shared.id, "country"))
        assertSame(tree["favorites"], tree.getMediaItemsByMediaId(shared.id, "favorites"))
        assertSame(tree["favorites"], tree.getMediaItemsByMediaId(shared.id, "unrelated"))
        assertSame(
            tree.getMediaItemsByMediaId(alone.id),
            tree.getMediaItemsByMediaId(alone.id, "country")
        )
        assertTrue(tree.getMediaItemsByMediaId("missing", "country").isEmpty())
    }

    @Test
    fun replacingThePlaylistDropsOnlyWhatNothingElseHolds() {
        val tree = BrowseTree()
        val listed = station("listed")
        val dropped = station("dropped")
        val added = station("added")
        tree["parent"] = data(listed)
        tree.setPlaylist(data(listed, dropped))

        tree.setPlaylist(data(added))

        assertSame(tree["parent"]!![0], tree.getMediaItemByMediaId(listed.id))
        assertSame(listed, tree.getRadioStationByMediaId(listed.id))
        assertNull(tree.getMediaItemByMediaId(dropped.id))
        assertSame(RadioStation.INVALID_INSTANCE, tree.getRadioStationByMediaId(dropped.id))
        assertTrue(tree.getMediaItemsByMediaId(dropped.id).isEmpty())
        assertSame(added, tree.getRadioStationByMediaId(added.id))
    }

    @Test
    fun invalidatingAListKeepsWhatThePlaylistHolds() {
        val tree = BrowseTree()
        val queued = station("queued")
        val listedOnly = station("listed-only")
        val playlist = data(queued)
        tree.setPlaylist(playlist)
        tree["parent"] = data(queued, listedOnly)

        tree.invalidate("parent")

        assertSame(playlist.children, tree.getMediaItemsByMediaId(queued.id))
        assertSame(queued, tree.getRadioStationByMediaId(queued.id))
        assertNotNull(tree.getMediaItemByMediaId(queued.id))
        assertNull(tree.getMediaItemByMediaId(listedOnly.id))
        assertSame(RadioStation.INVALID_INSTANCE, tree.getRadioStationByMediaId(listedOnly.id))
    }

    @Test
    fun replacingAListKeepsWhatAnotherListHolds() {
        val tree = BrowseTree()
        val shared = station("shared")
        tree["one"] = data(shared)
        tree["two"] = data(shared)

        tree["one"] = data(station("replacement"))

        assertSame(shared, tree.getRadioStationByMediaId(shared.id))
        assertNotNull(tree.getMediaItemByMediaId(shared.id))
        assertSame(tree["two"], tree.getMediaItemsByMediaId(shared.id))
    }

    private fun station(id: String) = RadioStation.makeDefaultInstance(id)

    /**
     * @return browse data holding one item per station, under the station's id, the way the
     *   service lists stations.
     */
    private fun data(vararg stations: RadioStation): BrowseTree.BrowseData {
        return BrowseTree.BrowseData(
            stations.map { MediaItem.Builder().setMediaId(it.id).build() }.toMutableList(),
            stations.toMutableSet()
        )
    }
}
