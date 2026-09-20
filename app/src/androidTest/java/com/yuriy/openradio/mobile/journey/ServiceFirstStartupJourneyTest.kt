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

package com.yuriy.openradio.mobile.journey

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.service.ActivityPresence
import com.yuriy.openradio.shared.service.LocalAudioFixture
import com.yuriy.openradio.shared.service.LocalStationsFixture
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sixth phone journey: the service is started, browsed and playing before any Activity exists,
 * and the Activity that opens afterwards agrees with it.
 *
 * This is the order Android Auto imposes. A head unit binds the [OpenRadioService] directly, walks
 * the browse tree and starts a station without the phone's own screen ever being created, and the
 * user opens the phone app later, if at all. Every other journey launches an Activity as its first
 * act, so nothing else in this suite says what the application does in that order.
 *
 * What makes the cases below mean anything is [ActivityPresence]: launching no Activity is not the
 * same claim as the process holding none, and the process is shared with every other test class in
 * the run. So each case states what the process was holding while the service worked, and names
 * what it found when that is not nothing.
 *
 * The station is a device-local one pointing at a generated WAV, for the reason every offline
 * journey uses one: with networking disabled it is the only kind of station there is, and the
 * player opens the file through the data source it builds for a stream.
 *
 * One half of "service first" is out of reach here and deliberately not claimed. The service
 * shares this process with the instrumentation, so it is already created by the time this class
 * runs and no test can make it start from scratch: `CMD_STOP_SERVICE` ends in
 * `Process.killProcess` and would take the run with it. What is pinned is the ordering against the
 * Activity, which is the half the contract is about. A genuinely new process per test is TASK-031.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class ServiceFirstStartupJourneyTest {

    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val mPresence = ActivityPresence()

    private val mAudio = LocalAudioFixture(mContext)

    private val mProfile = JourneyProfile(mContext)

    private val mStations = LocalStationsFixture(mProfile.storages, mProfile.browser)

    private lateinit var mStation: RadioStation

    /**
     * Brings up the profile the way a head unit would: the browser connects, the tree is refreshed
     * and the station is browsed, all with the process holding no Activity.
     *
     * The wait comes first and the assertion last, so the whole of that is bracketed rather than
     * sampled. Waiting is what the front of the bracket needs, because the previous class's
     * [ActivityScenario] hands its Activity to the framework to destroy and this one would
     * otherwise race that teardown; by the back of the bracket nothing has launched one, so a
     * plain read is enough.
     *
     * The fixtures are built as fields rather than here on purpose. JUnit runs [tearDown] whether
     * or not this method finished, and the very first thing it does can fail, so a fixture that
     * only exists once the setup has got past a given line is one the cleanup can be handed
     * uninitialized. Constructing them costs nothing and connects to nothing; [start] is where the
     * work is.
     */
    @Before
    fun setUp() {
        mPresence.awaitNone(
            "An Activity from an earlier test was still alive when this journey started, so " +
                "nothing it does with the service is a service first start."
        )
        mProfile.start()
        // The player outlives every test class in the run, so a case that asserts what playing
        // does has to start from a player that is not.
        mProfile.browser.stop()
        mStation = mStations.seed(mAudio.wav(WAV_NAME)).first()
        mProfile.browser.forgetNotifications()
        mPresence.assertNone(
            "An Activity appeared while the service was being connected, refreshed and browsed."
        )
    }

    @After
    fun tearDown() {
        mStations.parkThePlayer()
        mProfile.finish()
        mAudio.delete()
    }

    /**
     * The first half of the ordering: everything the phone's browse tree offers offline is served
     * while the process holds no Activity at all.
     *
     * The service is asserted to be running as well as answering, because that is the claim a head
     * unit depends on: the media service is up on its own, with no screen of the application's
     * anywhere. On API 26 and above `getRunningServices` reports only the caller's own services,
     * which is exactly the question being asked.
     *
     * Marking a station is included because browsing is not all a head unit does. The favorite
     * command is the one mutation it can make, and the node the root grows because of it has to be
     * served back to the same connection.
     */
    @Test
    fun theServiceServesAndMutatesItsTreeWithNoActivityInTheProcess() {
        mPresence.assertNone("An Activity is alive before this case browsed anything.")
        assertTrue(
            "The media service is not running, so nothing here says what it serves on its own",
            theMediaServiceIsRunning()
        )

        val root = mProfile.browser.libraryRoot()

        assertEquals(
            "The service refused the library root to a browser with no Activity behind it",
            LibraryResult.RESULT_SUCCESS,
            root.resultCode
        )
        assertEquals(MediaId.MEDIA_ID_ROOT, root.value?.mediaId)
        assertEquals(
            "The root served with no Activity is not the offline catalogue",
            seededRoot().map { it.mediaId },
            mProfile.browser.mediaIds(MediaId.MEDIA_ID_ROOT)
        )
        assertEquals(
            "The locals node served with no Activity does not hold the seeded station",
            listOf(mStation.id),
            mProfile.browser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )

        markTheStationThroughTheSession()

        assertEquals(
            "Marking a station through the session did not put the favorites node on the root",
            markedRoot().map { it.mediaId },
            mProfile.browser.mediaIds(MediaId.MEDIA_ID_ROOT)
        )
        assertEquals(
            "The favorites node the mark produced does not hold the station",
            listOf(mStation.id),
            mProfile.browser.mediaIds(MediaId.MEDIA_ID_FAVORITES_LIST)
        )
        mPresence.assertNone("An Activity started while the service was serving.")
    }

    /**
     * The second half of the ordering, at its plainest: the Activity that opens afterwards shows
     * the list the service had already answered.
     *
     * The rendered rows are compared against both the expected menu and the ids the service
     * actually served, so a UI and a service that agree on something wrong fail as loudly as one
     * that disagrees.
     */
    @Test
    fun theActivityRendersTheRootTheServiceAnsweredBeforeItExisted() {
        val served = mProfile.browser.mediaIds(MediaId.MEDIA_ID_ROOT)
        mPresence.assertNone("An Activity was alive when the service answered the root.")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val rendered = awaitSeededRoot(BrowseListView(scenario))

            assertEquals(
                "The Activity renders a different root than the service answered before it existed",
                served,
                rendered.map { it.mediaId }
            )
        }
    }

    /**
     * State a head unit produced before the phone's screen existed has to be on that screen when
     * it opens, in every list the station appears in.
     *
     * The mark is made through the session rather than through a box on a row: that is the gesture
     * a car offers, and it is the whole point of the ordering. Both nodes are read because they
     * are built from different places - `MediaItemFavoritesList` from the favorites store, the
     * locals list from the locals store with the favorite state bound onto each row - and a mark
     * that reached only one of them is the failure this case is looking for.
     */
    @Test
    fun aStationMarkedBeforeTheActivityExistedIsMarkedInTheListsItOpens() {
        markTheStationThroughTheSession()
        mPresence.assertNone("An Activity was alive when the station was marked.")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val list = BrowseListView(scenario)
            assertEquals(
                "The Activity does not offer the node the mark produced before it existed",
                markedRoot(),
                list.awaitRows("the favorites node") { it == markedRoot() }
            )

            val navigation = JourneyNavigation(scenario)
            try {
                navigation.open(MediaId.MEDIA_ID_FAVORITES_LIST)

                assertEquals(
                    "The favorites node does not show the station marked before the Activity existed",
                    listOf(BrowseRow(mStation.id, mStation.name)),
                    list.awaitRows("the station's own row") { it.size == 1 }
                )
                list.awaitRowFavorite(mStation.id, true)
            } finally {
                navigation.returnToRoot()
            }
            try {
                navigation.open(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)

                assertEquals(
                    "The locals list does not show the seeded station",
                    listOf(BrowseRow(mStation.id, mStation.name)),
                    list.awaitRows("the station's own row") { it.size == 1 }
                )
                list.awaitRowFavorite(mStation.id, true)
            } finally {
                navigation.returnToRoot()
            }
        }
    }

    /**
     * A station started before the Activity existed, which is a car that was already playing when
     * the user picked their phone up.
     *
     * The bar is the phone's whole playback screen and it starts down, so the Activity is what
     * raises it, out of the metadata the session pushes to the controller it built for itself.
     * Nothing hands it the station.
     *
     * What the bar comes up showing when nothing was started is the station of whichever case ran
     * before, because the session outlives them all and `parkThePlayer` hands the next one a queue.
     * The name waited for is what separates the two, and [LocalStationsFixture] makes it unique for
     * the whole run rather than for this class.
     *
     * The queue and the current item are read before and after, because the risk here is not only
     * that the Activity shows nothing. A second controller connecting is also the moment the
     * service could rebuild what it was playing, and a phone that reshuffles the car's queue on
     * being opened would pass every assertion about the bar.
     */
    @Test
    fun aStationPlayingBeforeTheActivityExistedIsOnTheBarTheActivityPutsUp() {
        startTheStationThroughTheSession()
        mPresence.assertNone("An Activity was alive when the session started the station.")
        val queue = mProfile.browser.queueMediaIds()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val nowPlaying = NowPlayingView(scenario)

            nowPlaying.awaitTitle(mStation.name)

            assertEquals(
                "The bar came up for the station but not for what the player says about it",
                liveStreamDescription(),
                nowPlaying.description()
            )
            assertTrue(
                "Opening the Activity stopped the station the session was already playing",
                mProfile.browser.isPlaying()
            )
            assertEquals(
                "Opening the Activity moved the player onto some other station",
                mStation.id,
                mProfile.browser.currentMediaId()
            )
            assertEquals(
                "Opening the Activity rebuilt the queue the session was already holding",
                queue,
                mProfile.browser.queueMediaIds()
            )
        }
    }

    /**
     * The same ordering read backwards: the phone app goes away and the car keeps working.
     *
     * `MainActivity.onDestroy` releases the presenter and the browser it built, and a release that
     * took the session with it would leave a head unit holding a dead connection. The browser this
     * asserts through is the one that was connected before any Activity existed, so what it gets
     * back is the service serving the process on its own again.
     */
    @Test
    fun theServiceGoesOnServingAndPlayingAfterTheActivityIsClosed() {
        startTheStationThroughTheSession()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitSeededRoot(BrowseListView(scenario))
            NowPlayingView(scenario).awaitTitle(mStation.name)
        }

        mPresence.awaitNone(
            "The Activity did not go away, so what follows is not the service on its own."
        )
        assertEquals(
            "The service stopped serving the root once the Activity was closed",
            seededRoot().map { it.mediaId },
            mProfile.browser.mediaIds(MediaId.MEDIA_ID_ROOT)
        )
        assertEquals(
            "The service stopped serving the locals node once the Activity was closed",
            listOf(mStation.id),
            mProfile.browser.mediaIds(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST)
        )
        assertTrue(
            "Closing the phone app stopped the station the session was playing",
            mProfile.browser.isPlaying()
        )
        assertEquals(
            "Closing the phone app moved the player onto some other station",
            mStation.id,
            mProfile.browser.currentMediaId()
        )
    }

    /**
     * Waits for the list to be the root of the profile every case here starts from: a cleared
     * install holding one station of the user's own.
     *
     * This is a precondition rather than a restatement of the cold launch journey. The presenter
     * is one of the registry's singletons and its node stack outlives the Activity, so a case that
     * left the stack inside a node would send the next case's Activity straight back there.
     *
     * @return the rendered rows, for a case that has something else to say about them.
     */
    private fun awaitSeededRoot(list: BrowseListView): List<BrowseRow> {
        val rows = list.awaitRows("the root of the seeded profile") { it == seededRoot() }
        assertEquals(
            "This case did not start at the root of the seeded profile. A case that walks into a " +
                "node leaves the presenter's stack there, and it is a registry singleton that " +
                "outlives the Activity, so the next case launches back into that node.",
            seededRoot(),
            rows
        )
        return rows
    }

    /**
     * Marks the seeded station the way a head unit does, through the session's own command.
     *
     * The command that marks a station is `CMD_FAVORITE_OFF`: both favorite commands name the
     * state the control is leaving rather than the one it is asking for, which is also how the
     * phone's own check box sends them.
     *
     * The subscription is part of the gesture rather than scaffolding: a client that is showing a
     * list subscribes to it, and `notifyChildrenChanged` reaches subscribers only, so it is also
     * what makes the root the mark rebuilt observable from here.
     *
     * The store is read first so that a station which arrived already marked fails here instead of
     * passing the case this is setting up, and read back afterwards through a storage that has
     * cached no answers, because `FavoritesStorage` answers out of a map the writer fills as it
     * goes.
     */
    private fun markTheStationThroughTheSession() {
        assertEquals(
            "The station is already a favorite, so marking it proves nothing",
            emptyList<String>(),
            storedFavorites()
        )
        assertEquals(
            "The session refused a subscription to the root",
            LibraryResult.RESULT_SUCCESS,
            mProfile.browser.subscribe(MediaId.MEDIA_ID_ROOT).resultCode
        )
        mProfile.browser.forgetNotifications()

        assertEquals(
            "The session refused the favorite command",
            SessionResult.RESULT_SUCCESS,
            mProfile.browser.command(
                OpenRadioService.CMD_FAVORITE_OFF,
                OpenRadioStore.makeUpdateIsFavoriteBundle(mStation.id)
            ).resultCode
        )

        mProfile.browser.awaitChildrenChanged(MediaId.MEDIA_ID_ROOT)
        assertEquals(
            "The favorite command did not reach the store",
            listOf(mStation.id),
            storedFavorites()
        )
    }

    /**
     * Starts the seeded station the way a head unit does, by handing the session the one item it
     * picked out of the list it browsed.
     *
     * The url is asserted as well as the station, because a player that became ready on a playlist
     * the service built for itself would otherwise read as this station playing.
     */
    private fun startTheStationThroughTheSession() {
        mProfile.browser.setMediaItem(mStations.item(mStation))
        mProfile.browser.prepareAndPlay()

        mProfile.browser.awaitPlaying()
        assertEquals(
            "The session started some other station than the one it was handed",
            mStation.id,
            mProfile.browser.currentMediaId()
        )
        assertEquals(
            "The session opened something other than the station's own stream",
            mStation.getStreamUrlFixed(),
            mProfile.browser.currentMediaItemUri()
        )
    }

    /**
     * @return whether the media service is up in this process. On API 26 and above
     *   `getRunningServices` answers about the caller's own services only, which is the whole
     *   question here.
     */
    @Suppress("DEPRECATION")
    private fun theMediaServiceIsRunning(): Boolean {
        val manager = mContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(RUNNING_SERVICE_LIMIT).any {
            it.service.className == OpenRadioService::class.java.name
        }
    }

    private fun storedFavorites(): List<String> {
        return mProfile.storages.freshFavorites().getAll().map { it.id }
    }

    /**
     * @return the root of this journey's profile: the offline catalogue with the seeded station's
     *   node appended, which is what `MediaItemRoot` builds from a locals store holding one entry.
     */
    private fun seededRoot(): List<BrowseRow> {
        return mProfile.cleanInstallRoot() + mProfile.localsRow()
    }

    /**
     * @return the root once the station is marked. `MediaItemRoot` puts the favorites node first
     *   and the locals node last, with the catalogue between them.
     */
    private fun markedRoot(): List<BrowseRow> {
        return listOf(mProfile.favoritesRow()) + seededRoot()
    }

    /**
     * @return what the player writes on the description line of a stream that is open and has told
     *   it nothing else about itself, which is every moment of a generated WAV.
     */
    private fun liveStreamDescription(): String {
        return mContext.getString(com.yuriy.openradio.R.string.media_description_default)
    }

    private companion object {

        /**
         * The extension decides the format the player infers, so it has to be the real one.
         */
        const val WAV_NAME = "journey-service-first-startup.wav"

        /**
         * Far more services than this application has, so the list is never the reason a lookup
         * misses one.
         */
        const val RUNNING_SERVICE_LIMIT = 100
    }
}
