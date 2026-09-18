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
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.makeStation
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

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mStorages = ServiceStorages(mContext)
        mStorages.clear()
        mBrowser = ServiceBrowser()
        mBrowser.connect()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.forgetNotifications()
    }

    @After
    fun tearDown() {
        mStorages.clear()
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.release()
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
     * back over a real connection: [favoriteCommandRejectsAStationOutsideTheBrowseTree] and both
     * halves of [sortUpdateRejectsAMissingOrEmptyStationId].
     */
    @Test
    fun rejectsAnUnknownCustomCommand() {
        assertEquals(
            SessionResult.RESULT_ERROR_PERMISSION_DENIED,
            mBrowser.command(UNKNOWN_COMMAND).resultCode
        )
        // A denied command must not have reached the handler, so nothing may have moved.
        assertTrue(mStorages.freshFavorites().getAll().isEmpty())
        assertTrue(mStorages.freshLocals().getAll().isEmpty())
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
                station.id, "After edit", "https://example.test/edited",
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

    @Test
    fun favoriteCommandRejectsAStationOutsideTheBrowseTree() {
        assertEquals(
            SessionResult.RESULT_ERROR_NOT_SUPPORTED,
            mBrowser.command(
                OpenRadioService.CMD_FAVORITE_OFF,
                OpenRadioStore.makeUpdateIsFavoriteBundle("never-browsed")
            ).resultCode
        )
        assertTrue(mStorages.freshFavorites().getAll().isEmpty())
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

    @Test
    fun sortUpdateForAnUnsortableCategoryIsAcceptedAndChangesNothing() {
        val station = makeStation(mStorages.locals.getId(), isLocal = true)
        mStorages.locals.add(station)
        mBrowser.command(OpenRadioService.CMD_UPDATE_TREE)
        mBrowser.children(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            mBrowser.command(
                OpenRadioService.CMD_UPDATE_SORT_IDS,
                OpenRadioStore.makeUpdateSortIdsBundle(station.id, 7, MediaId.MEDIA_ID_ROOT)
            ).resultCode
        )

        assertEquals(
            listOf(0),
            mStorages.freshLocals().getAll().map { it.sortId }
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

    @Test
    fun argumentLessCommandsAreAccepted() {
        for (action in listOf(
            OpenRadioService.CMD_NET_CHANGED,
            OpenRadioService.CMD_TOGGLE_LAST_PLAYED_ITEM,
            OpenRadioService.CMD_CLEAR_CACHE,
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
    }
}
