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

package com.yuriy.openradio.shared.model.media

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        val stored = BrowseTree.BrowseData(mutableListOf(child), mutableSetOf())
        tree[parentId] = stored

        assertSame(stored.children, tree[parentId])
        assertSame(stored.children, tree.getMediaItemsByMediaId(parentId))
        assertSame(stored.children, tree.getMediaItemsByMediaId(child.mediaId))
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
     * A clear of the app data leaves every list the tree holds stale, not only one node, so the
     * service drops them all and tells the subscribers of each id it gets back.
     */
    @Test
    fun invalidatingEverythingDropsEveryListAndNamesThem() {
        val tree = BrowseTree()
        val first = station("first")
        val second = station("second")
        tree["first-parent"] = data(first)
        tree["second-parent"] = data(second)

        val dropped = tree.invalidateAll()

        assertEquals(setOf("first-parent", "second-parent"), dropped)
        assertNull(tree["first-parent"])
        assertNull(tree["second-parent"])
        assertNull(tree.getMediaItemByMediaId(first.id))
        assertSame(RadioStation.INVALID_INSTANCE, tree.getRadioStationByMediaId(second.id))
        assertTrue(tree.invalidateAll().isEmpty())
    }

    /**
     * The playlist is the player's queue, which a clear does not stop, so its stations have to
     * stay resolvable for the player to report on them.
     */
    @Test
    fun invalidatingEverythingKeepsThePlaylist() {
        val tree = BrowseTree()
        val queued = station("queued")
        val sibling = station("sibling")
        val playlist = data(queued)
        tree["parent"] = data(queued, sibling)
        tree.setPlaylist(playlist)

        tree.invalidateAll()

        assertSame(playlist.children, tree.getMediaItemsByMediaId(queued.id))
        assertSame(queued, tree.getRadioStationByMediaId(queued.id))
        assertSame(RadioStation.INVALID_INSTANCE, tree.getRadioStationByMediaId(sibling.id))
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
        val browsed = data(listed, sibling)
        tree["parent"] = browsed

        tree.setPlaylist(data(listed))

        assertNull(tree[listed.id])
        assertSame(browsed.children, tree.getMediaItemsByMediaId(listed.id))
    }

    @Test
    fun aListBrowsedAfterThePlaylistAlsoTakesPrecedenceOverIt() {
        val tree = BrowseTree()
        val listed = station("listed")
        tree.setPlaylist(data(listed))
        val browsed = data(listed, station("sibling"))

        tree["parent"] = browsed

        assertSame(browsed.children, tree.getMediaItemsByMediaId(listed.id))
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
        val favorites = data(shared)
        val country = data(station("other"), shared)
        val playlist = data(shared, alone)
        tree["favorites"] = favorites
        tree["country"] = country
        tree.setPlaylist(playlist)

        assertSame(country, tree.getSelection(shared.id, "country"))
        assertSame(favorites, tree.getSelection(shared.id, "favorites"))
        assertSame(favorites, tree.getSelection(shared.id, "unrelated"))
        assertSame(playlist, tree.getSelection(alone.id, "country"))
        assertTrue(tree.getSelection("missing", "country").children.isEmpty())
        assertTrue(tree.getSelection("missing", "country").radioStations.isEmpty())
    }

    /**
     * A selection of a browse node plays that node's own list, not the list that shows the node
     * as one of its items.
     */
    @Test
    fun aSelectionOfAHeldListsOwnIdIsThatList() {
        val tree = BrowseTree()
        val favorite = station("favorite")
        val favorites = data(favorite)
        tree["root"] = BrowseTree.BrowseData(
            listOf(MediaItem.Builder().setMediaId("favorites").build()),
            emptySet()
        )
        tree["favorites"] = favorites

        val selection = tree.getSelection("favorites", "unrelated")

        assertSame(favorites, selection)
        assertEquals(setOf(favorite), selection.radioStations)
    }

    /**
     * fnr-tzth: a list selected to play is the player's queue from then on, so the service makes
     * it the playlist, and its stations have to resolve after the list itself is invalidated.
     */
    @Test
    fun aSelectionMadeThePlaylistStaysResolvableAfterItsListIsInvalidated() {
        val tree = BrowseTree()
        val playing = station("playing")
        val next = station("next")
        tree["locals"] = data(playing, next)

        tree.setPlaylist(tree.getSelection(playing.id, "locals"))
        tree.invalidate("locals")

        assertSame(next, tree.getRadioStationByMediaId(next.id))
        assertNotNull(tree.getMediaItemByMediaId(next.id))
        assertEquals(listOf(playing.id, next.id), tree.getMediaItemsByMediaId(next.id).map { it.mediaId })
    }

    @Test
    fun aQueueHoldsTheItemsTheTreeKnowsWithTheirStationsInTheOrderAsked() {
        val tree = BrowseTree()
        val first = station("first")
        val second = station("second")
        val queued = station("queued")
        tree["parent"] = data(first, second)
        tree.setPlaylist(data(queued))

        val queue = tree.getQueue(listOf(queued.id, "missing", second.id))

        assertEquals(listOf(queued.id, second.id), queue.children.map { it.mediaId })
        assertSame(tree.getMediaItemByMediaId(second.id), queue.children[1])
        assertEquals(setOf(queued, second), queue.radioStations)
    }

    @Test
    fun addingToThePlaylistKeepsWhatItHeld() {
        val tree = BrowseTree()
        val playing = station("playing")
        val added = station("added")
        tree.setPlaylist(data(playing))
        tree["locals"] = data(added)

        tree.addToPlaylist(tree.getSelection(added.id, "locals"))
        tree.invalidate("locals")

        assertEquals(listOf(playing.id, added.id), tree.getMediaItemsByMediaId(added.id).map { it.mediaId })
        assertSame(playing, tree.getRadioStationByMediaId(playing.id))
        assertSame(added, tree.getRadioStationByMediaId(added.id))
    }

    @Test
    fun replacingThePlaylistDropsOnlyWhatNothingElseHolds() {
        val tree = BrowseTree()
        val listed = station("listed")
        val dropped = station("dropped")
        val added = station("added")
        val browsed = data(listed)
        tree["parent"] = browsed
        tree.setPlaylist(data(listed, dropped))

        tree.setPlaylist(data(added))

        assertSame(browsed.children[0], tree.getMediaItemByMediaId(listed.id))
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
        val kept = data(shared)
        tree["one"] = data(shared)
        tree["two"] = kept

        tree["one"] = data(station("replacement"))

        assertSame(shared, tree.getRadioStationByMediaId(shared.id))
        assertSame(kept.children[0], tree.getMediaItemByMediaId(shared.id))
        assertSame(kept.children, tree.getMediaItemsByMediaId(shared.id))
    }

    @Test
    fun appendAnswersWhereThePageStarts() {
        val tree = BrowseTree()
        tree["parent"] = data(station("first"), station("second"))

        assertEquals(2, tree.append("parent", data(station("third"))))
        assertEquals(3, tree.append("parent", data(station("fourth"))))
        assertEquals(0, tree.append("missing", data(station("dropped"))))
        assertNull(tree["missing"])
        assertNull(tree.getMediaItemByMediaId("dropped"))
    }

    @Test
    fun aStoredListIsNotChangedByItsCaller() {
        val tree = BrowseTree()
        val first = station("first")
        val children = mutableListOf(item(first))
        val radioStations = mutableSetOf(first)
        tree["parent"] = BrowseTree.BrowseData(children, radioStations)

        val later = station("later")
        children.add(item(later))
        radioStations.add(later)

        assertEquals(listOf(first.id), tree["parent"]?.map { it.mediaId })
        assertNull(tree.getMediaItemByMediaId(later.id))
        assertSame(RadioStation.INVALID_INSTANCE, tree.getRadioStationByMediaId(later.id))
    }

    /**
     * The session hands a list a lookup returned to its client after the lookup, while the
     * executor may already be storing the next page of the same node.
     */
    @Test
    fun aListALookupReturnedIsNotChangedByALaterPage() {
        val tree = BrowseTree()
        val first = station("first")
        tree["parent"] = data(first, station("second"))
        val byParent = tree["parent"]!!.iterator()
        val byChild = tree.getSelection(first.id, "parent").children.iterator()
        byParent.next()
        byChild.next()

        tree.append("parent", data(station("third")))

        assertEquals("second", byParent.next().mediaId)
        assertEquals(false, byParent.hasNext())
        assertEquals("second", byChild.next().mediaId)
        assertEquals(false, byChild.hasNext())
    }

    @Test
    fun aLookupThatWalksTheListsDoesNotThrowWhileListsAreStored() {
        val tree = BrowseTree()
        val failures = runConcurrently(
            { i -> tree["list-$i"] = data(station("station-$i")) },
            { _ ->
                check(tree.getMediaItemsByMediaId("missing").isEmpty())
                check(tree.getSelection("missing", "list-0").children.isEmpty())
            }
        )

        assertEquals(emptyList<Throwable>(), failures)
    }

    /**
     * The executor stores pages while the main thread replaces the playlist; neither may install
     * an index that misses what the other stored.
     */
    @Test
    fun concurrentWritersKeepEveryListIndexed() {
        val tree = BrowseTree()
        val failures = runConcurrently(
            { i -> tree["list-$i"] = data(station("station-$i")) },
            { i -> tree.setPlaylist(data(station("playlist-$i"))) }
        )

        assertEquals(emptyList<Throwable>(), failures)
        for (i in 0 until ROUNDS) {
            assertNotNull("list-$i", tree["list-$i"])
            assertEquals("station-$i", tree.getRadioStationByMediaId("station-$i").id)
            assertNotNull("station-$i", tree.getMediaItemByMediaId("station-$i"))
        }
        assertEquals("playlist-${ROUNDS - 1}", tree.getRadioStationByMediaId("playlist-${ROUNDS - 1}").id)
    }

    /**
     * Runs [first] and [second] for [ROUNDS] rounds each on two threads released together.
     *
     * @return what either of them threw.
     */
    private fun runConcurrently(first: (Int) -> Unit, second: (Int) -> Unit): List<Throwable> {
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(first, second).map { body ->
                executor.submit {
                    start.await()
                    for (i in 0 until ROUNDS) {
                        body(i)
                    }
                }
            }
            start.countDown()
            return futures.mapNotNull {
                try {
                    it.get(1, TimeUnit.MINUTES)
                    null
                } catch (exception: ExecutionException) {
                    exception.cause
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun station(id: String) = RadioStation.makeDefaultInstance(id)

    private fun item(station: RadioStation) = MediaItem.Builder().setMediaId(station.id).build()

    /**
     * @return browse data holding one item per station, under the station's id, the way the
     *   service lists stations.
     */
    private fun data(vararg stations: RadioStation): BrowseTree.BrowseData {
        return BrowseTree.BrowseData(
            stations.map { item(it) },
            stations.toSet()
        )
    }

    private companion object {
        /** Enough overlapping writes that an unguarded tree fails on the first run. */
        const val ROUNDS = 2000
    }
}
