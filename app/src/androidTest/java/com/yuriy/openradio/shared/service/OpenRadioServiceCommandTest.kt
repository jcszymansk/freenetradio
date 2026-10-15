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

package com.yuriy.openradio.shared.service

import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.makeStation
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the custom session commands [OpenRadioService] advertises, the browse refresh each one is
 * supposed to trigger, and what the service answers when the arguments are wrong.
 *
 * [OpenRadioService.CMD_STOP_SERVICE] is deliberately never sent: `closeService` ends in
 * `Process.killProcess(myPid())`, and the service shares its process with the instrumentation, so
 * sending it would kill the test run rather than fail it. The testing roadmap keeps that path as a
 * manual lifecycle check.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class OpenRadioServiceCommandTest {

    private lateinit var mContext: Context

    private lateinit var mStorages: ServiceStorages

    private lateinit var mBrowser: ServiceBrowser

    private lateinit var mAudio: LocalAudioFixture

    private lateinit var mStations: LocalStationsFixture

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mAudio = LocalAudioFixture(mContext)
        mStorages = ServiceStorages(mContext)
        mStorages.clear()
        mBrowser = ServiceBrowser()
        mStations = LocalStationsFixture(mStorages, mBrowser)
        mBrowser.connect()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.forgetNotifications()
    }

    @After
    fun tearDown() {
        mStations.parkThePlayer()
        mStorages.clear()
        if (mBrowser.isConnected()) {
            mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        }
        mBrowser.release()
        mAudio.delete()
    }

    /**
     * The advertised set has to be *exactly* the handled commands, in both directions. A handled
     * command that is not advertised can never be sent. An advertised command that is not handled
     * is what would make `onCustomCommand`'s "not supported" fallthrough reachable, and the set
     * asserted here is the evidence that it is not.
     */
    @Test
    fun advertisesExactlyTheCommandsItHandles() {
        val advertised = mBrowser.availableSessionCommands().commands
            .filter { it.commandCode == SessionCommand.COMMAND_CODE_CUSTOM }
            .map { it.customAction }

        assertEquals(HANDLED_COMMANDS.toSet(), advertised.toSet())
        assertEquals("The same action is advertised twice", advertised.size, advertised.toSet().size)
    }

    /**
     * An unknown action is refused before it leaves the controller: media3 checks it against the
     * session's advertised commands, and [advertisesExactlyTheCommandsItHandles] shows that set is
     * exactly the handled ones. `onCustomCommand`'s own fallthrough is therefore dead code that no
     * client can reach, which is why this asserts the denial a client actually sees rather than
     * the code that branch would return.
     *
     * The service's `RESULT_ERROR_NOT_SUPPORTED` is not left unverified by that. It is the answer
     * to a command that is advertised but cannot be carried out, and three cases observe it coming
     * back over a real connection: [aFavoriteCommandForAnUnknownStationIsRefusedWhileAStationPlays],
     * [aRefusedFavoriteCommandLeavesTheStoreAsItFoundIt] and
     * [sortUpdateRejectsAMissingOrEmptyStationId].
     */
    @Test
    fun rejectsAnUnknownCustomCommand() {
        val local = makeStation(mStorages.locals.getId(), name = "Kept local", isLocal = true)
        mStorages.locals.add(local)
        val favorite = makeStation("fav-kept", name = "Kept favorite")
        mStorages.favorites.add(favorite)

        assertEquals(
            SessionResult.RESULT_ERROR_PERMISSION_DENIED,
            mBrowser.command(UNKNOWN_COMMAND).resultCode
        )
        // A denied command must not have reached the handler, so what was stored has to be there.
        assertEquals(listOf(favorite.id), mStorages.freshFavorites().getAll().map { it.id })
        assertEquals(listOf(local.id), mStorages.freshLocals().getAll().map { it.id })
    }

    @Test
    fun localStationLifecycleRefreshesTheSubscription() {
        mBrowser.subscribe(MediaId.MEDIA_ID_ROOT)
        mBrowser.subscribe(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)

        val station = makeStation(mStorages.locals.getId(), name = "Before edit", isLocal = true)
        mStorages.locals.add(station)
        updateTreeAndAwaitRefresh()

        assertEquals(
            listOf(station.id),
            mBrowser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )
        assertTrue(
            "The root gained no locals node",
            mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT)
                .contains(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )

        assertTrue(
            "The local station could not be edited",
            mStorages.locals.update(
                station.id, "After edit", "$UNREACHABLE_ORIGIN/edited",
                null, null, null, null, false
            )
        )
        updateTreeAndAwaitRefresh()

        val edited = mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        assertEquals(listOf(station.id), edited.map { it.mediaId })
        assertEquals("After edit", edited.first().mediaMetadata.title)

        mStorages.freshLocals().remove(station)
        updateTreeAndAwaitRefresh()

        assertTrue(mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST).isEmpty())
        assertFalse(
            "The root kept a locals node with no local stations left",
            mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT)
                .contains(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )
    }

    @Test
    fun favoriteCommandsUpdateStorageAndRefreshTheRoot() {
        val station = makeStation(mStorages.locals.getId(), name = "Toggle me", isLocal = true)
        mStorages.locals.add(station)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        // The service resolves a favorite through the browse tree, so the node has to be browsed.
        mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        mBrowser.subscribe(MediaId.MEDIA_ID_ROOT)
        mBrowser.forgetNotifications()

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_MARK_FAVORITE,
                OpenRadioStore.makeUpdateIsFavoriteBundle(station.id)
            ).resultCode
        )
        mBrowser.awaitChildrenChanged(MediaId.MEDIA_ID_ROOT)

        assertEquals(
            listOf(station.id),
            mStorages.freshFavorites().getAll().map { it.id }
        )
        assertTrue(
            mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT).contains(MediaId.MEDIA_ID_FAVORITES_LIST)
        )

        mBrowser.forgetNotifications()
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_UNMARK_FAVORITE,
                OpenRadioStore.makeUpdateIsFavoriteBundle(station.id)
            ).resultCode
        )
        mBrowser.awaitChildrenChanged(MediaId.MEDIA_ID_ROOT)

        assertTrue(mStorages.freshFavorites().getAll().isEmpty())
        assertFalse(
            mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT).contains(MediaId.MEDIA_ID_FAVORITES_LIST)
        )
    }

    /**
     * Unmarking the only favorite from inside the favorites list empties the store, and the list
     * a controller is standing in has to be told so. The service used to name the favorites list
     * only while the store still held something afterwards, so this case notified the root alone
     * and left the removed station on screen.
     */
    @Test
    fun unmarkingTheLastFavoriteInsideTheFavoritesListRefreshesIt() {
        val station = makeStation("fav-only", name = "Only favorite")
        mStorages.favorites.add(station)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        assertEquals(listOf(station.id), mBrowser.mediaIds(MediaId.MEDIA_ID_FAVORITES_LIST))
        subscribeToTheFavoriteNodes(mBrowser)

        val (resultCode, notified) = sendAFavoriteCommandAndCollectItsNotifications(
            mBrowser, mBrowser, OpenRadioService.CMD_UNMARK_FAVORITE, station.id
        )

        assertEquals(SessionResult.RESULT_SUCCESS, resultCode)
        assertEquals(
            "Unmarking the last favorite did not notify both the root and the favorites list",
            FAVORITE_NODES,
            notified.toSet()
        )
        assertTrue(mStorages.freshFavorites().getAll().isEmpty())
        assertTrue(mBrowser.children(MediaId.MEDIA_ID_FAVORITES_LIST).isEmpty())
        assertFalse(
            "The root kept the favorites node with no favorites left",
            mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT).contains(MediaId.MEDIA_ID_FAVORITES_LIST)
        )
    }

    /**
     * Unmarking one of several favorites from inside the favorites list refreshes it, and the root
     * keeps the node because the store still holds the other one.
     */
    @Test
    fun unmarkingOneOfSeveralFavoritesRefreshesTheListAndKeepsTheNode() {
        val removed = makeStation("fav-removed", name = "Removed favorite")
        val kept = makeStation("fav-kept", name = "Kept favorite")
        mStorages.favorites.add(removed)
        mStorages.favorites.add(kept)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        assertEquals(
            listOf(removed.id, kept.id),
            mBrowser.mediaIds(MediaId.MEDIA_ID_FAVORITES_LIST)
        )
        subscribeToTheFavoriteNodes(mBrowser)

        val (resultCode, notified) = sendAFavoriteCommandAndCollectItsNotifications(
            mBrowser, mBrowser, OpenRadioService.CMD_UNMARK_FAVORITE, removed.id
        )

        assertEquals(SessionResult.RESULT_SUCCESS, resultCode)
        assertEquals(FAVORITE_NODES, notified.toSet())
        assertEquals(listOf(kept.id), mStorages.freshFavorites().getAll().map { it.id })
        assertEquals(listOf(kept.id), mBrowser.mediaIds(MediaId.MEDIA_ID_FAVORITES_LIST))
        assertTrue(
            "The root lost the favorites node while a favorite is left",
            mBrowser.mediaIds(MediaId.MEDIA_ID_ROOT).contains(MediaId.MEDIA_ID_FAVORITES_LIST)
        )
    }

    /**
     * The phone UI, Android Auto and any other controller share one service, and which of them
     * browsed last is an accident of timing. One controller here shows the favorites list while
     * another browses the locals last and marks and then unmarks a station there; the favorites
     * list has to be refreshed both times. The service used to pick the node to notify from the
     * last browse, so it told the first controller nothing.
     */
    @Test
    fun theFavoritesListIsRefreshedWhicheverControllerBrowsedLast() {
        val kept = makeStation("fav-kept", name = "Kept favorite")
        mStorages.favorites.add(kept)
        val station = makeStation(mStorages.locals.getId(), name = "Marked elsewhere", isLocal = true)
        mStorages.locals.add(station)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        assertEquals(listOf(kept.id), mBrowser.mediaIds(MediaId.MEDIA_ID_FAVORITES_LIST))
        subscribeToTheFavoriteNodes(mBrowser)

        val other = ServiceBrowser()
        try {
            other.connect()
            // The service resolves a favorite through the browse tree, so the node has to be browsed.
            assertEquals(
                listOf(station.id),
                other.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            )

            val (marked, markNotified) = sendAFavoriteCommandAndCollectItsNotifications(
                other, mBrowser, OpenRadioService.CMD_MARK_FAVORITE, station.id
            )

            assertEquals(SessionResult.RESULT_SUCCESS, marked)
            assertEquals(
                "Marking a station from the locals list did not notify the favorites list " +
                    "another controller shows",
                FAVORITE_NODES,
                markNotified.toSet()
            )
            assertEquals(
                setOf(kept.id, station.id),
                mBrowser.mediaIds(MediaId.MEDIA_ID_FAVORITES_LIST).toSet()
            )

            other.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            val (unmarked, unmarkNotified) = sendAFavoriteCommandAndCollectItsNotifications(
                other, mBrowser, OpenRadioService.CMD_UNMARK_FAVORITE, station.id
            )

            assertEquals(SessionResult.RESULT_SUCCESS, unmarked)
            assertEquals(
                "Unmarking a station from the locals list did not notify the favorites list " +
                    "another controller shows",
                FAVORITE_NODES,
                unmarkNotified.toSet()
            )
            assertEquals(listOf(kept.id), mBrowser.mediaIds(MediaId.MEDIA_ID_FAVORITES_LIST))
        } finally {
            other.release()
        }
    }

    /**
     * Marking a station that already is a favorite is answered as skipped, so a caller can tell
     * it changed nothing. The favorite is stored under a name of its own: a command that wrote
     * the store anyway would replace it with the name the browse tree holds.
     */
    @Test
    fun markingAStationThatIsAlreadyAFavoriteIsSkipped() {
        val station = makeStation(mStorages.locals.getId(), name = "In the tree", isLocal = true)
        mStorages.locals.add(station)
        mStorages.favorites.add(makeStation(station.id, name = "Stored favorite"))

        assertEquals(
            SessionResult.RESULT_INFO_SKIPPED,
            sendAFavoriteCommandThatNotifiesNothing(OpenRadioService.CMD_MARK_FAVORITE, station)
        )
        assertEquals(
            listOf(station.id to "Stored favorite"),
            mStorages.freshFavorites().getAll().map { it.id to it.name }
        )
    }

    /**
     * Unmarking a station that is not a favorite is answered as skipped, which is what a caller
     * that sent the command the wrong way round, or acted on a stale favorite box, gets to see.
     */
    @Test
    fun unmarkingAStationThatIsNotAFavoriteIsSkipped() {
        val kept = makeStation("fav-kept", name = "Kept favorite")
        mStorages.favorites.add(kept)
        val station = makeStation(mStorages.locals.getId(), name = "Never marked", isLocal = true)
        mStorages.locals.add(station)

        assertEquals(
            SessionResult.RESULT_INFO_SKIPPED,
            sendAFavoriteCommandThatNotifiesNothing(OpenRadioService.CMD_UNMARK_FAVORITE, station)
        )
        assertEquals(listOf(kept.id), mStorages.freshFavorites().getAll().map { it.id })
    }

    /**
     * A favorite command about a station the service has never heard of is refused and changes
     * nothing, even while another station plays.
     *
     * The playing station is set up here rather than inherited. Once something has played, the
     * service keeps its active station for the life of the process, and that is the station a
     * command that names nothing falls back to. A command that names some other station must not
     * fall back to it, and a case that assumed nothing had played would pass whether it did or not.
     */
    @Test
    fun aFavoriteCommandForAnUnknownStationIsRefusedWhileAStationPlays() {
        val kept = makeStation("fav-kept", name = "Kept favorite")
        mStorages.favorites.add(kept)
        val station = playAStation()

        assertEquals(
            SessionResult.RESULT_ERROR_NOT_SUPPORTED,
            mBrowser.command(
                OpenRadioService.CMD_MARK_FAVORITE,
                OpenRadioStore.makeUpdateIsFavoriteBundle("never-browsed")
            ).resultCode
        )

        assertEquals(
            "A command about an unknown station changed the favorites, and ${station.id} is " +
                "the station that was playing",
            listOf(kept.id),
            mStorages.freshFavorites().getAll().map { it.id }
        )
    }

    /**
     * The favorite button the session publishes names no station, because it only ever concerns
     * the one that is playing; a controller may also name that station outright. Both have to keep
     * working while the commands that name an unknown station are refused.
     */
    @Test
    fun aFavoriteCommandNamingNothingOrThePlayingStationMarksIt() {
        val station = playAStation()

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(OpenRadioService.CMD_MARK_FAVORITE).resultCode
        )
        assertEquals(listOf(station.id), mStorages.freshFavorites().getAll().map { it.id })

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(OpenRadioService.CMD_UNMARK_FAVORITE).resultCode
        )
        assertTrue(mStorages.freshFavorites().getAll().isEmpty())

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_MARK_FAVORITE,
                OpenRadioStore.makeUpdateIsFavoriteBundle(station.id)
            ).resultCode
        )
        assertEquals(listOf(station.id), mStorages.freshFavorites().getAll().map { it.id })
    }

    /**
     * A command the service refuses has to leave the favorites store as it found it, which it did
     * not while the store was written before the browse tree was asked about the station.
     *
     * The playing station is out of the browse tree here, the state in which the old code wrote
     * the store on its way to refusing a command about a station it had never heard of.
     */
    @Test
    fun aRefusedFavoriteCommandLeavesTheStoreAsItFoundIt() {
        val kept = makeStation("fav-kept", name = "Kept favorite")
        mStorages.favorites.add(kept)
        val station = playAStationThenDropItFromTheBrowseTree()

        assertEquals(
            SessionResult.RESULT_ERROR_NOT_SUPPORTED,
            mBrowser.command(
                OpenRadioService.CMD_MARK_FAVORITE,
                OpenRadioStore.makeUpdateIsFavoriteBundle("never-browsed")
            ).resultCode
        )
        assertEquals(
            "A refused command changed the favorites, and ${station.id} is the station that " +
                "was playing",
            listOf(kept.id),
            mStorages.freshFavorites().getAll().map { it.id }
        )
    }

    /**
     * The now playing station can leave the browse tree while it plays, as it does when a
     * controller showing the favorites list unmarks it there. The favorite button still concerns
     * it, so marking it again has to work whether the command names nothing or names the station.
     */
    @Test
    fun aFavoriteCommandMarksThePlayingStationGoneFromTheBrowseTree() {
        val kept = makeStation("fav-kept", name = "Kept favorite")
        mStorages.favorites.add(kept)
        val station = playAStationThenDropItFromTheBrowseTree()

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(OpenRadioService.CMD_MARK_FAVORITE).resultCode
        )
        assertEquals(
            setOf(kept.id, station.id),
            mStorages.freshFavorites().getAll().map { it.id }.toSet()
        )

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_UNMARK_FAVORITE,
                OpenRadioStore.makeUpdateIsFavoriteBundle(station.id)
            ).resultCode
        )
        assertEquals(listOf(kept.id), mStorages.freshFavorites().getAll().map { it.id })

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_MARK_FAVORITE,
                OpenRadioStore.makeUpdateIsFavoriteBundle(station.id)
            ).resultCode
        )
        assertEquals(
            setOf(kept.id, station.id),
            mStorages.freshFavorites().getAll().map { it.id }.toSet()
        )
    }

    /**
     * Android Auto shows the favorite button the session publishes for the playing station, and
     * pressing it sends back exactly the command the service put in it, naming no station. The
     * button has to mark the station while it is not a favorite, and the one that replaces it has
     * to unmark it again.
     */
    /**
     * Playing a station from the favorites list hands the player that list as its queue, and the
     * queue's stations resolve only through the favorites list's entry in the browse tree. The
     * phone holds one subscription at a time, so once it moves to the locals nobody re-browses
     * the favorites list. A favorite change made there must leave the entry alone, or the next
     * station in the queue resolves to nothing and the service keeps the previous one as active,
     * which the car's favorite button then marks or unmarks in its place.
     */
    @Test
    fun aFavoriteChangeElsewhereKeepsTheQueuedFavoritesResolvable() {
        val url = mAudio.wav(WAV_NAME)
        val first = makeStation("fav-queued-first", name = "Queued first", url = url)
        val second = makeStation("fav-queued-second", name = "Queued second", url = url)
        mStorages.favorites.add(first)
        mStorages.favorites.add(second)
        val local = mStations.seed(url).first()

        val favorites = mBrowser.children(MediaId.MEDIA_ID_FAVORITES_LIST)
        assertEquals(listOf(first.id, second.id), favorites.map { it.mediaId })
        mBrowser.setMediaItem(favorites.first())
        mBrowser.prepareAndPlay()
        mBrowser.awaitPlaying()
        awaitActiveStation(first.id)
        assertEquals(listOf(first.id, second.id), mBrowser.queueMediaIds())

        // The service resolves a favorite through the browse tree, so the node has to be browsed.
        assertEquals(listOf(local.id), mBrowser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST))
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_MARK_FAVORITE,
                OpenRadioStore.makeUpdateIsFavoriteBundle(local.id)
            ).resultCode
        )

        mBrowser.seekToNext()
        awaitActiveStation(second.id)
    }

    @Test
    fun theFavoriteButtonTheSessionPublishesMarksAndUnmarksThePlayingStation() {
        val station = playAStation()

        pressTheFavoriteButton(OpenRadioService.CMD_MARK_FAVORITE)
        assertEquals(listOf(station.id), mStorages.freshFavorites().getAll().map { it.id })

        pressTheFavoriteButton(OpenRadioService.CMD_UNMARK_FAVORITE)
        assertTrue(mStorages.freshFavorites().getAll().isEmpty())

        awaitTheFavoriteButton(OpenRadioService.CMD_MARK_FAVORITE)
    }

    @Test
    fun sortUpdateReordersLocalsAndRefreshesThem() {
        val stations = (0 until 3).map {
            makeStation(mStorages.locals.getId(), name = "Local $it", isLocal = true)
        }
        stations.forEach(mStorages.locals::add)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        assertEquals(
            stations.map { it.id },
            mBrowser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )

        mBrowser.subscribe(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        mBrowser.forgetNotifications()
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_UPDATE_SORT_IDS,
                OpenRadioStore.makeUpdateSortIdsBundle(
                    stations.last().id, 0, MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
                )
            ).resultCode
        )
        mBrowser.awaitChildrenChanged(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)

        val reordered = mBrowser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        assertEquals(
            "The moved station did not lead the list",
            stations.last().id,
            reordered.first()
        )
        assertEquals(stations.map { it.id }.toSet(), reordered.toSet())
        assertEquals(
            "The new order was not persisted",
            reordered,
            mStorages.freshLocals().getAll().map { it.id }
        )
    }

    @Test
    fun sortUpdateReordersFavoritesAndRefreshesThem() {
        val stations = (0 until 3).map { makeStation("fav-$it", name = "Favorite $it") }
        stations.forEach(mStorages.favorites::add)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        assertEquals(
            stations.map { it.id },
            mBrowser.mediaIds(MediaId.MEDIA_ID_FAVORITES_LIST)
        )

        mBrowser.subscribe(MediaId.MEDIA_ID_FAVORITES_LIST)
        mBrowser.forgetNotifications()
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_UPDATE_SORT_IDS,
                OpenRadioStore.makeUpdateSortIdsBundle(
                    stations.last().id, 0, MediaId.MEDIA_ID_FAVORITES_LIST
                )
            ).resultCode
        )
        mBrowser.awaitChildrenChanged(MediaId.MEDIA_ID_FAVORITES_LIST)

        assertEquals(
            stations.last().id,
            mBrowser.mediaIds(MediaId.MEDIA_ID_FAVORITES_LIST).first()
        )
    }

    @Test
    fun sortUpdateRejectsAMissingOrEmptyStationId() {
        val stations = (0 until 2).map {
            makeStation(mStorages.locals.getId(), name = "Local $it", isLocal = true)
        }
        stations.forEach(mStorages.locals::add)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        val before = mBrowser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)

        val noStationId = Bundle().apply {
            putInt(OpenRadioStore.EXTRA_KEY_SORT_IDS, 0)
            putString(
                OpenRadioStore.EXTRA_KEY_MEDIA_ID,
                MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
            )
        }
        assertEquals(
            SessionResult.RESULT_ERROR_NOT_SUPPORTED,
            mBrowser.command(OpenRadioService.CMD_UPDATE_SORT_IDS, noStationId).resultCode
        )
        assertEquals(
            SessionResult.RESULT_ERROR_NOT_SUPPORTED,
            mBrowser.command(
                OpenRadioService.CMD_UPDATE_SORT_IDS,
                OpenRadioStore.makeUpdateSortIdsBundle(
                    "", 0, MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
                )
            ).resultCode
        )
        assertEquals(
            SessionResult.RESULT_ERROR_NOT_SUPPORTED,
            mBrowser.command(OpenRadioService.CMD_UPDATE_SORT_IDS, Bundle()).resultCode
        )

        assertEquals(
            "A rejected sort update still reordered the list",
            before,
            mBrowser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )
    }

    /**
     * The request asks for exactly what [sortUpdateReordersLocalsAndRefreshesThem] asks for, the
     * last local moved to the front, and names the root instead of the locals node. Had the command
     * applied it to the locals anyway, the order read back would lead with that station.
     *
     * The order is what is asserted, not the sort ids: `getAll` renumbers them from zero on every
     * read, so any stored values read back as `0, 1, 2`.
     */
    @Test
    fun sortUpdateForAnUnsortableCategoryIsAcceptedAndChangesNothing() {
        val stations = (0 until 3).map {
            makeStation(mStorages.locals.getId(), name = "Local $it", isLocal = true)
        }
        stations.forEach(mStorages.locals::add)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        val before = mBrowser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        assertEquals(stations.map { it.id }, before)

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_UPDATE_SORT_IDS,
                OpenRadioStore.makeUpdateSortIdsBundle(
                    stations.last().id, 0, MediaId.MEDIA_ID_ROOT
                )
            ).resultCode
        )

        assertEquals(
            "A sort update for the root reordered the stored locals",
            before,
            mStorages.freshLocals().getAll().map { it.id }
        )
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        assertEquals(
            "A sort update for the root reordered the locals node",
            before,
            mBrowser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )
    }

    @Test
    fun masterVolumeCommandStoresTheValueAndFallsBackToTheDefault() {
        val original = AppPreferencesManager.getMasterVolume(
            mContext, OpenRadioService.MASTER_VOLUME_DEFAULT
        )
        try {
            assertEquals(
                SessionResult.RESULT_SUCCESS,
                mBrowser.command(
                    OpenRadioService.CMD_MASTER_VOLUME_CHANGED,
                    OpenRadioStore.makeMasterVolumeChangedBundle(42)
                ).resultCode
            )
            assertEquals(
                42,
                AppPreferencesManager.getMasterVolume(
                    mContext, OpenRadioService.MASTER_VOLUME_DEFAULT
                )
            )

            // An absent extra is not an error; the service falls back to full volume.
            assertEquals(
                SessionResult.RESULT_SUCCESS,
                mBrowser.command(OpenRadioService.CMD_MASTER_VOLUME_CHANGED).resultCode
            )
            assertEquals(
                OpenRadioService.MASTER_VOLUME_DEFAULT,
                AppPreferencesManager.getMasterVolume(mContext, 0)
            )
        } finally {
            AppPreferencesManager.setMasterVolume(mContext, original)
        }
    }

    /**
     * Only that the session takes each command is asserted here. What they do is asserted where
     * there is something to observe: `CMD_NET_CHANGED` by
     * `OpenRadioServiceRecoveryTest.aNetworkChangeLeavesAnAllowedStreamPlaying`,
     * `CMD_TOGGLE_LAST_PLAYED_ITEM` by
     * `OfflinePlaybackJourneyTest.tappingTheNowPlayingBarPausesAndResumesTheStation`, and
     * `CMD_UPDATE_TREE` by the tests in this class that wait on [updateTreeAndAwaitRefresh].
     *
     * `CMD_CLEAR_CACHE` is not in this loop. It answers immediately and finishes on a coroutine
     * that outlives the test, and what it goes on to empty is process-wide state other cases seed,
     * so firing it without waiting would let it reach into whatever runs next.
     * `OpenRadioServiceBrowseTest.reconnectsOnAnEmptyProfileAfterEveryStoreIsCleared` covers it
     * with a completion signal to wait on.
     */
    @Test
    fun argumentLessCommandsAreAccepted() {
        for (action in listOf(
            OpenRadioService.CMD_NET_CHANGED,
            OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM,
            OpenRadioService.CMD_UPDATE_TREE
        )) {
            assertEquals(
                "$action was refused",
                SessionResult.RESULT_SUCCESS,
                mBrowser.command(action).resultCode
            )
        }
    }

    /**
     * Plays a station seeded for this test and waits until the service holds it as its active
     * station.
     */
    private fun playAStation(): RadioStation {
        val station = mStations.seed(mAudio.wav(WAV_NAME)).first()
        mBrowser.setMediaItem(mStations.item(station))
        mBrowser.prepareAndPlay()
        mBrowser.awaitPlaying()
        awaitActiveStation(station.id)
        return station
    }

    /**
     * Plays a station, then removes it from the browse tree while it keeps playing, so the
     * service still holds it as its active station but no browse tree index knows its id.
     */
    private fun playAStationThenDropItFromTheBrowseTree(): RadioStation {
        val station = playAStation()
        mBrowser.subscribe(MediaId.MEDIA_ID_ROOT)
        mBrowser.subscribe(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        mStorages.freshLocals().remove(station)
        updateTreeAndAwaitRefresh()
        assertTrue(
            "The station is still in the browse tree",
            mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST).isEmpty()
        )
        return station
    }

    /**
     * Sends the favorite command [action] about the local [station] while the root, the favorites
     * and the locals are subscribed, asserts that it notified none of them, and returns the
     * result code the service answered.
     */
    private fun sendAFavoriteCommandThatNotifiesNothing(action: String, station: RadioStation): Int {
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        subscribeToTheFavoriteNodes(mBrowser)
        // The service resolves a favorite through the browse tree, so the node has to be browsed.
        assertTrue(
            "The station is not in the browse tree",
            mBrowser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST).contains(station.id)
        )

        val (resultCode, notified) = sendAFavoriteCommandAndCollectItsNotifications(
            mBrowser, mBrowser, action, station.id
        )

        assertEquals(
            "A favorite command that changed nothing notified subscribers",
            emptyList<String>(),
            notified
        )
        return resultCode
    }

    /**
     * Subscribes [browser] to every node a favorite command can notify, and to the locals, whose
     * push [sendAFavoriteCommandAndCollectItsNotifications] waits for.
     */
    private fun subscribeToTheFavoriteNodes(browser: ServiceBrowser) {
        browser.subscribe(MediaId.MEDIA_ID_ROOT)
        browser.subscribe(MediaId.MEDIA_ID_FAVORITES_LIST)
        browser.subscribe(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
    }

    /**
     * Has [sender] send the favorite command [action] about [stationId], and returns the result
     * code the service answered together with the nodes [observer] was notified of because of it,
     * in the order they arrived. [observer] has to be subscribed through
     * [subscribeToTheFavoriteNodes].
     *
     * `CMD_UPDATE_TREE` notifies the root and then the locals, and the session delivers its pushes
     * to a controller in the order it sent them, so it is sent behind the command as a barrier:
     * everything that reached [observer] before those two was the command's.
     */
    private fun sendAFavoriteCommandAndCollectItsNotifications(
        sender: ServiceBrowser,
        observer: ServiceBrowser,
        action: String,
        stationId: String
    ): Pair<Int, List<String>> {
        observer.forgetNotifications()

        val resultCode = sender.command(
            action, OpenRadioStore.makeUpdateIsFavoriteBundle(stationId)
        ).resultCode

        observer.command(OpenRadioService.CMD_UPDATE_TREE)
        val changes = observer.childrenChangesUntil(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
            .map { it.parentId }
        assertEquals(
            "The barrier did not arrive as the root and then the locals, so the command notified " +
                "the locals or something was left over: $changes",
            listOf(MediaId.MEDIA_ID_ROOT, MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST),
            changes.takeLast(2)
        )
        return resultCode to changes.dropLast(2)
    }

    /**
     * Presses the favorite button the session publishes once it is the one that sends [action],
     * the way a head unit does: with the button's own command, and its extras as the arguments.
     */
    private fun pressTheFavoriteButton(action: String) {
        val command = awaitTheFavoriteButton(action)
        assertEquals(
            "The session refused its own favorite button",
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(command.customAction, command.customExtras).resultCode
        )
    }

    /**
     * Waits until the session's custom layout is a single favorite button that sends [action].
     *
     * The layout is pushed to the controller, and the service publishes it from a coroutine after
     * the station starts, so it is not there the instant playback or a command returns.
     */
    private fun awaitTheFavoriteButton(action: String): SessionCommand {
        mBrowser.awaitPlayback("a favorite button that sends $action") {
            mBrowser.customLayout().map { it.sessionCommand?.customAction } == listOf(action)
        }
        return checkNotNull(mBrowser.customLayout().single().sessionCommand)
    }

    /**
     * Waits until the service has taken [id] as its active station.
     *
     * The service sets that and writes the last played station from the same callback, on a
     * coroutine, so the store is what a test can see it through.
     */
    private fun awaitActiveStation(id: String) {
        mBrowser.awaitPlayback("the service to take $id as its active station") {
            mStorages.freshLatest().get().id == id
        }
    }

    /**
     * `CMD_UPDATE_TREE` invalidates the root and the locals node, so wait for both pushes before
     * reading either node back.
     */
    private fun updateTreeAndAwaitRefresh() {
        mBrowser.forgetNotifications()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.awaitChildrenChanged(MediaId.MEDIA_ID_ROOT)
        mBrowser.awaitChildrenChanged(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
    }

    private companion object {

        val HANDLED_COMMANDS = listOf(
            OpenRadioService.CMD_MARK_FAVORITE,
            OpenRadioService.CMD_UNMARK_FAVORITE,
            OpenRadioService.CMD_NET_CHANGED,
            OpenRadioService.CMD_CLEAR_CACHE,
            OpenRadioService.CMD_MASTER_VOLUME_CHANGED,
            OpenRadioService.CMD_STOP_SERVICE,
            OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM,
            OpenRadioService.CMD_UPDATE_SORT_IDS,
            OpenRadioService.CMD_UPDATE_TREE
        )

        /**
         * The nodes a favorite command that changed the store notifies: the root gains or loses
         * the favorites node, and the favorites list gains or loses the station.
         */
        val FAVORITE_NODES = setOf(MediaId.MEDIA_ID_ROOT, MediaId.MEDIA_ID_FAVORITES_LIST)

        const val UNKNOWN_COMMAND = "com.github.jcszymansk.freenetradio.COMMAND.NO_SUCH_COMMAND"

        /**
         * The extension decides the format the player infers, so it has to be the real one.
         */
        const val WAV_NAME = "command-active-station.wav"
    }
}
