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

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.images.ImagesPersistenceLayer
import com.yuriy.openradio.shared.model.storage.preferencesContext
import com.yuriy.openradio.shared.utils.RadioStationValidator
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * What the manager does with the verdict of the validator it is given, and what reaches storage
 * afterwards.
 *
 * The injected validator is what keeps these cases off the network: the production one probes the
 * stream url over HTTP. The preference fake is what makes the second half assertable at all -
 * under `unitTests.returnDefaultValues` a bare `ContextWrapper(null)` answers every
 * `getSharedPreferences` with null, so every write goes nowhere and every read is the default.
 *
 * Both dispatchers are [Dispatchers.Unconfined], so the edit and remove paths run to completion on
 * the calling thread and every assertion below is about a finished operation rather than a race.
 */
class RadioStationManagerLayerImplTest {

    private val mContext = preferencesContext()

    private val mContextRef = WeakReference(mContext)

    private val mFavorites = FavoritesStorage(mContextRef)

    private val mLocals =
        DeviceLocalsStorage(mContextRef, mFavorites, LatestRadioStationStorage(mContextRef))

    @Test
    fun theCandidateIsHandedToTheValidator() {
        val validator = RecordingValidator(Verdict.SUCCESS)
        val candidate = candidate()

        manager(validator).addRadioStation(mContext, candidate, { }, { })

        assertSame(candidate, validator.mCandidates.single())
    }

    @Test
    fun anAcceptedCandidateIsReportedAsAdded() {
        val messages = mutableListOf<String>()

        manager(RecordingValidator(Verdict.SUCCESS))
            .addRadioStation(mContext, candidate(), { messages.add(it) }, { fail -> messages.add(fail) })

        assertEquals(listOf("Radio Station added successfully"), messages)
    }

    @Test
    fun aRejectedCandidateIsReportedAsFailure() {
        val failures = mutableListOf<String>()
        val successes = mutableListOf<String>()

        manager(RecordingValidator(Verdict.FAILURE))
            .addRadioStation(mContext, candidate(), { successes.add(it) }, { failures.add(it) })

        assertEquals(listOf(REJECTED), failures)
        assertTrue(successes.isEmpty())
    }

    /**
     * Pins today's behavior, which is wrong: the station is added and the caller is told it failed.
     * See task-038.
     */
    @Test
    fun aWarnedCandidateIsReportedAsBothFailureAndSuccess() {
        val failures = mutableListOf<String>()
        val successes = mutableListOf<String>()

        manager(RecordingValidator(Verdict.WARNING))
            .addRadioStation(mContext, candidate(), { successes.add(it) }, { failures.add(it) })

        assertEquals(listOf(WARNED), failures)
        assertEquals(listOf("Radio Station added successfully"), successes)
    }

    /**
     * The regression test for the add half of 13cc159: `addRadioStation` used to build the station
     * without its home page, so a user who typed one lost it the moment the dialog closed. Every
     * other field the candidate carries is asserted alongside it, because a manager that dropped
     * all of them would otherwise pass on the home page alone.
     */
    @Test
    fun anAcceptedCandidateIsStoredWithEverythingItCarries() {
        manager(RecordingValidator(Verdict.SUCCESS)).addRadioStation(mContext, candidate(), { }, { })

        val stored = storedStations().single()
        assertEquals("The stored station lost the typed home page", HOME_PAGE, stored.homePage)
        assertEquals("The stored station lost the typed name", NAME, stored.name)
        assertEquals("The stored station lost the typed stream url", URL, stored.getStreamUrlFixed())
        assertEquals("The stored station lost the chosen image", IMAGE_URL, stored.imageUrl)
        assertEquals("The stored station lost the chosen genre", GENRE, stored.genre)
        assertEquals("The stored station lost the chosen country", COUNTRY, stored.country)
        assertTrue("A station added by hand was not marked as the user's own", stored.isLocal)
    }

    /**
     * Reading the station back by the media id the store hands out is the lookup the edit and
     * remove dialogs do, and it is a different path through [DeviceLocalsStorage] than
     * [DeviceLocalsStorage.getAll].
     */
    @Test
    fun aStoredCandidateIsFoundByItsMediaId() {
        manager(RecordingValidator(Verdict.SUCCESS)).addRadioStation(mContext, candidate(), { }, { })

        val mediaId = storedStations().single().id
        assertEquals(
            "The stored station cannot be looked up by its own media id",
            HOME_PAGE,
            freshLocals()[mediaId].homePage
        )
    }

    @Test
    fun aRejectedCandidateIsNotStored() {
        manager(RecordingValidator(Verdict.FAILURE)).addRadioStation(mContext, candidate(), { }, { })

        assertTrue("A rejected candidate reached the store anyway", storedStations().isEmpty())
    }

    @Test
    fun anAcceptedCandidateJoinsFavoritesWhenAsked() {
        manager(RecordingValidator(Verdict.SUCCESS))
            .addRadioStation(mContext, candidate(addToFav = true), { }, { })

        val favorite = FavoritesStorage(WeakReference(mContext)).getAll().single()
        assertEquals("The favorite copy lost the typed home page", HOME_PAGE, favorite.homePage)
        assertEquals(
            "The favorite copy is not the station that was stored",
            storedStations().single().id,
            favorite.id
        )
    }

    @Test
    fun anAcceptedCandidateStaysOutOfFavoritesWhenNotAsked() {
        manager(RecordingValidator(Verdict.SUCCESS))
            .addRadioStation(mContext, candidate(addToFav = false), { }, { })

        assertTrue(
            "A station the user did not favorite was added to favorites",
            FavoritesStorage(WeakReference(mContext)).getAll().isEmpty()
        )
    }

    /**
     * The edit half of 13cc159. The manager passes the home page to
     * [DeviceLocalsStorage.update], which before that commit had nowhere to put it.
     */
    @Test
    fun editingAStationRewritesItsHomePage() {
        val manager = manager(RecordingValidator(Verdict.SUCCESS))
        manager.addRadioStation(mContext, candidate(), { }, { })
        val mediaId = storedStations().single().id
        val messages = mutableListOf<String>()

        manager.editRadioStation(
            mContext, mediaId, candidate(homePage = EDITED_HOME_PAGE), { messages.add(it) }, { }
        )

        assertEquals(listOf("Radio Station updated successfully"), messages)
        val edited = storedStations().single()
        assertEquals("The edit did not rewrite the home page", EDITED_HOME_PAGE, edited.homePage)
        assertEquals(
            "The edit added a second station instead of changing the one it was given",
            mediaId,
            edited.id
        )
    }

    /**
     * An edit is not the place a station comes into existence: the store answers "not found" and
     * the caller is told so, rather than silently gaining a station.
     */
    @Test
    fun editingAnUnknownStationIsReportedAsFailure() {
        val failures = mutableListOf<String>()
        val successes = mutableListOf<String>()

        manager(RecordingValidator(Verdict.SUCCESS)).editRadioStation(
            mContext, UNKNOWN_MEDIA_ID, candidate(), { successes.add(it) }, { failures.add(it) }
        )

        assertEquals(listOf("Can not update Radio Station"), failures)
        assertTrue(successes.isEmpty())
        assertTrue("A failed edit created a station", storedStations().isEmpty())
    }

    /**
     * A station that is already gone is not an error: the caller is told the removal is done so the
     * dialog closes and the browse tree is refreshed either way.
     *
     * The path taken when the station *is* found is not driven from here. It reaches
     * `context.contentResolver` to delete the artwork, which no JVM context can answer for, so it
     * belongs to `DeviceLocalsStorageTest` and the local-station journey.
     */
    @Test
    fun removingAnUnknownStationIsStillReportedAsDone() {
        var removals = 0

        manager(RecordingValidator(Verdict.SUCCESS))
            .removeRadioStation(mContext, UNKNOWN_MEDIA_ID) { removals++ }

        assertEquals(1, removals)
    }

    @Test
    fun removingWithoutAContextDoesNothing() {
        var removals = 0

        manager(RecordingValidator(Verdict.SUCCESS)).removeRadioStation(null, UNKNOWN_MEDIA_ID) { removals++ }

        assertEquals("A removal without a context was reported as done", 0, removals)
    }

    @Test
    fun removingWithoutAMediaIdDoesNothing() {
        val manager = manager(RecordingValidator(Verdict.SUCCESS))
        manager.addRadioStation(mContext, candidate(), { }, { })
        var removals = 0

        manager.removeRadioStation(mContext, "") { removals++ }

        assertEquals("A removal without a media id was reported as done", 0, removals)
        assertFalse("A removal without a media id emptied the store", storedStations().isEmpty())
    }

    /**
     * The dispatchers are the only thing the tests above hand the manager that production does
     * not, so this builds one the way `DependencyRegistryCommon` does and drives the add path
     * through it. The add path answers on the calling thread, which is why it is the one path that
     * works without a main looper and can say that the defaults are still sound.
     */
    @Test
    fun theManagerProductionBuildsStillStoresWhatItIsGiven() {
        val messages = mutableListOf<String>()

        RadioStationManagerLayerImpl(
            RecordingValidator(Verdict.SUCCESS), mLocals, mFavorites, SilentImagesPersistenceLayer()
        ).addRadioStation(mContext, candidate(), { messages.add(it) }, { })

        assertEquals(listOf("Radio Station added successfully"), messages)
        assertEquals(HOME_PAGE, storedStations().single().homePage)
    }

    private fun manager(validator: RadioStationValidator): RadioStationManagerLayerImpl {
        return RadioStationManagerLayerImpl(
            validator,
            mLocals,
            mFavorites,
            SilentImagesPersistenceLayer(),
            Dispatchers.Unconfined,
            Dispatchers.Unconfined
        )
    }

    /**
     * @return a reader that has cached nothing, so what it answers came out of the preference
     *         store rather than out of the storage the manager wrote through.
     */
    private fun freshLocals(): DeviceLocalsStorage {
        val favorites = FavoritesStorage(WeakReference(mContext))
        return DeviceLocalsStorage(
            WeakReference(mContext), favorites, LatestRadioStationStorage(WeakReference(mContext))
        )
    }

    private fun storedStations() = freshLocals().getAll()

    private fun candidate(
        homePage: String = HOME_PAGE,
        addToFav: Boolean = false
    ): RadioStationToAdd {
        return RadioStationToAdd(NAME, URL, IMAGE_URL, homePage, GENRE, COUNTRY, addToFav)
    }

    private enum class Verdict {

        SUCCESS,

        WARNING,

        FAILURE
    }

    /**
     * Answers with a fixed verdict and remembers what it was asked about, so a test can tell the
     * difference between a manager that consults its validator and one that ignores it.
     */
    private class RecordingValidator(private val mVerdict: Verdict) : RadioStationValidator {

        val mCandidates = mutableListOf<RadioStationToAdd>()

        override fun validate(
            context: Context, rsToAdd: RadioStationToAdd,
            onSuccess: (msg: String) -> Unit,
            onWarning: (msg: String) -> Unit,
            onFailure: (msg: String) -> Unit
        ) {
            mCandidates.add(rsToAdd)
            when (mVerdict) {
                Verdict.SUCCESS -> onSuccess("Radio Station validated successfully")

                Verdict.WARNING -> {
                    onWarning(WARNED)
                    onSuccess("Radio Station validated successfully")
                }

                Verdict.FAILURE -> onFailure(REJECTED)
            }
        }
    }

    private class SilentImagesPersistenceLayer : ImagesPersistenceLayer {

        override fun open(uri: Uri): ParcelFileDescriptor? = null

        override fun delete(uri: Uri) = Unit

        override fun delete(mediaId: String) = Unit

        override fun deleteAll() = Unit
    }

    private companion object {

        const val NAME = "Test Station"

        const val URL = "http://localhost/stream.mp3"

        const val IMAGE_URL = "http://localhost/cover.png"

        const val HOME_PAGE = "http://localhost/home"

        const val EDITED_HOME_PAGE = "http://localhost/moved"

        const val GENRE = "Jazz"

        const val COUNTRY = "Poland"

        /**
         * [DeviceLocalsStorage.get] matches on `endsWith`, so this has to be a media id no stored
         * id can end with rather than merely one nothing was stored under.
         */
        const val UNKNOWN_MEDIA_ID = "no-such-station"

        const val REJECTED = "Radio Station's stream is invalid"

        const val WARNED = "Radio Station's home page is invalid"
    }
}
