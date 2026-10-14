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

        // CMD_FAVORITE_OFF names the state the button is leaving, so it turns the favorite on.
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_FAVORITE_OFF,
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
                OpenRadioService.CMD_FAVORITE_ON,
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
                OpenRadioService.CMD_FAVORITE_OFF,
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
            mBrowser.command(OpenRadioService.CMD_FAVORITE_OFF).resultCode
        )
        assertEquals(listOf(station.id), mStorages.freshFavorites().getAll().map { it.id })

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(OpenRadioService.CMD_FAVORITE_ON).resultCode
        )
        assertTrue(mStorages.freshFavorites().getAll().isEmpty())

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_FAVORITE_OFF,
                OpenRadioStore.makeUpdateIsFavoriteBundle(station.id)
            ).resultCode
        )
        assertEquals(listOf(station.id), mStorages.freshFavorites().getAll().map { it.id })
    }

    /**
     * A command the service refuses has to leave the favorites store as it found it, which it did
     * not while the store was written before the browse tree was asked about the station.
     *
     * The playing station is removed from the browse tree, so the command resolves to a station
     * that is still active but has no media item left to update, and the service refuses it. That
     * is so whether the command names the station or names nothing and falls back to it.
     */
    @Test
    fun aRefusedFavoriteCommandLeavesTheStoreAsItFoundIt() {
        val kept = makeStation("fav-kept", name = "Kept favorite")
        mStorages.favorites.add(kept)
        val station = playAStation()

        mBrowser.subscribe(MediaId.MEDIA_ID_ROOT)
        mBrowser.subscribe(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        mStorages.freshLocals().remove(station)
        updateTreeAndAwaitRefresh()
        assertTrue(
            "The station is still in the browse tree, so the command would not be refused",
            mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST).isEmpty()
        )

        for (args in listOf(Bundle(), OpenRadioStore.makeUpdateIsFavoriteBundle(station.id))) {
            assertEquals(
                "A command for the playing station gone from the browse tree with $args",
                SessionResult.RESULT_ERROR_NOT_SUPPORTED,
                mBrowser.command(OpenRadioService.CMD_FAVORITE_OFF, args).resultCode
            )
            assertEquals(
                "A refused command with $args changed the favorites",
                listOf(kept.id),
                mStorages.freshFavorites().getAll().map { it.id }
            )
        }
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
            OpenRadioService.CMD_FAVORITE_ON,
            OpenRadioService.CMD_FAVORITE_OFF,
            OpenRadioService.CMD_NET_CHANGED,
            OpenRadioService.CMD_CLEAR_CACHE,
            OpenRadioService.CMD_MASTER_VOLUME_CHANGED,
            OpenRadioService.CMD_STOP_SERVICE,
            OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM,
            OpenRadioService.CMD_UPDATE_SORT_IDS,
            OpenRadioService.CMD_UPDATE_TREE
        )

        const val UNKNOWN_COMMAND = "com.github.jcszymansk.freenetradio.COMMAND.NO_SUCH_COMMAND"

        /**
         * The extension decides the format the player infers, so it has to be the real one.
         */
        const val WAV_NAME = "command-active-station.wav"
    }
}
