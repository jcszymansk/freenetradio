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
import android.content.ContextWrapper
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.images.ImagesPersistenceLayer
import com.yuriy.openradio.shared.utils.RadioStationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * How the manager treats the verdict of the validator it is given. The injected validator is what
 * keeps these cases off the network: the production one probes the stream url over HTTP.
 */
class RadioStationManagerLayerImplTest {

    @Test
    fun theCandidateIsHandedToTheValidator() {
        val validator = RecordingValidator(Verdict.SUCCESS)
        val candidate = candidate()

        manager(validator).addRadioStation(CONTEXT, candidate, { }, { })

        assertSame(candidate, validator.mCandidates.single())
    }

    @Test
    fun anAcceptedCandidateIsReportedAsAdded() {
        val messages = mutableListOf<String>()

        manager(RecordingValidator(Verdict.SUCCESS))
            .addRadioStation(CONTEXT, candidate(), { messages.add(it) }, { fail -> messages.add(fail) })

        assertEquals(listOf("Radio Station added successfully"), messages)
    }

    @Test
    fun aRejectedCandidateIsReportedAsFailure() {
        val failures = mutableListOf<String>()
        val successes = mutableListOf<String>()

        manager(RecordingValidator(Verdict.FAILURE))
            .addRadioStation(CONTEXT, candidate(), { successes.add(it) }, { failures.add(it) })

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
            .addRadioStation(CONTEXT, candidate(), { successes.add(it) }, { failures.add(it) })

        assertEquals(listOf(WARNED), failures)
        assertEquals(listOf("Radio Station added successfully"), successes)
    }

    private fun manager(validator: RadioStationValidator): RadioStationManagerLayerImpl {
        val contextRef = WeakReference(CONTEXT)
        val favoritesStorage = FavoritesStorage(contextRef)
        return RadioStationManagerLayerImpl(
            validator,
            DeviceLocalsStorage(contextRef, favoritesStorage, LatestRadioStationStorage(contextRef)),
            favoritesStorage,
            SilentImagesPersistenceLayer()
        )
    }

    private fun candidate(): RadioStationToAdd {
        return RadioStationToAdd(
            "Test Station", "http://localhost/stream.mp3", "", "http://localhost/home", "Jazz",
            "Poland", false
        )
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

        /**
         * Storage reads fall back to their defaults and writes go nowhere without real preferences,
         * which is enough for the validator verdicts these cases are about.
         */
        val CONTEXT: Context = ContextWrapper(null)

        const val REJECTED = "Radio Station's stream is invalid"

        const val WARNED = "Radio Station's home page is invalid"
    }
}
