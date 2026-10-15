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

package com.yuriy.openradio.shared.model.media.item

import android.os.Bundle
import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.AppUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the deadline every browse command that asks a provider answers within (TASK-072), run
 * the way the service runs it, through [MediaItemCommandDependencies.execute].
 *
 * Providers block rather than suspend, so a timeout wrapped around them can only fire once they
 * have returned. Each case holds its provider until the command has answered, which is what a
 * provider stuck on the network looks like from the command: a command that waits for the
 * provider never answers, and one that lets the provider's late answer through answers twice.
 * Every provider holds a station back, so a late answer that got through is visible in the items.
 */
class MediaItemCommandDeadlineTest {

    @Test
    fun allCategoriesAnswerWhileTheProviderIsStuck() {
        assertAnsweredWhileStuck(MediaItemAllCategories()) { hold ->
            RecordingPresenter(mCategories = setOf(Category("rock", "Rock", 42)), mBeforeAnswer = hold)
        }
    }

    @Test
    fun childCategoriesAnswerWhileTheProviderIsStuck() {
        assertAnsweredWhileStuck(
            MediaItemChildCategories(),
            parentId = MediaId.MEDIA_ID_CHILD_CATEGORIES + "rock"
        ) { hold ->
            RecordingPresenter(mCategoryStations = stations("late"), mBeforeAnswer = hold)
        }
    }

    @Test
    fun countriesAnswerWhileTheProviderIsStuck() {
        assertAnsweredWhileStuck(MediaItemCountriesList()) { hold ->
            RecordingPresenter(mCountries = setOf(Country("Poland", "PL")), mBeforeAnswer = hold)
        }
    }

    /**
     * The command makes sure the countries are cached before it asks for the stations, and the
     * deadline covers that first call as well: it reaches the same provider.
     */
    @Test
    fun countryStationsAnswerWhileTheProviderIsStuck() {
        assertAnsweredWhileStuck(MediaItemCountryStations()) { hold ->
            RecordingPresenter(
                mCountries = setOf(Country("Poland", "PL")),
                mCountryStations = stations("late"),
                mBeforeAnswer = hold
            )
        }
    }

    @Test
    fun favoritesAnswerWhileTheStorageIsStuck() {
        assertAnsweredWhileStuck(MediaItemFavoritesList()) { hold ->
            RecordingPresenter(mFavorites = stations("late"), mBeforeAnswer = hold)
        }
    }

    @Test
    fun localStationsAnswerWhileTheStorageIsStuck() {
        assertAnsweredWhileStuck(MediaItemLocalsList()) { hold ->
            RecordingPresenter(mDeviceLocals = stations("late"), mBeforeAnswer = hold)
        }
    }

    @Test
    fun newStationsAnswerWhileTheProviderIsStuck() {
        assertAnsweredWhileStuck(MediaItemNewStations()) { hold ->
            RecordingPresenter(mNewStations = stations("late"), mBeforeAnswer = hold)
        }
    }

    @Test
    fun popularStationsAnswerWhileTheProviderIsStuck() {
        assertAnsweredWhileStuck(MediaItemPopularStations()) { hold ->
            RecordingPresenter(mPopularStations = stations("late"), mBeforeAnswer = hold)
        }
    }

    @Test
    fun searchAnswersWhileTheProviderIsStuck() {
        assertAnsweredWhileStuck(
            MediaItemSearchFromService(),
            options = AppUtils.makeSearchQueryBundle("jazz")
        ) { hold ->
            RecordingPresenter(mSearchStations = stations("late"), mBeforeAnswer = hold)
        }
    }

    /**
     * Runs [command] against the presenter [presenterWith] builds around a hold that keeps every
     * provider call from answering until the command has delivered a result, and asserts that the
     * result was the empty one the deadline sends, delivered once.
     */
    private fun assertAnsweredWhileStuck(
        command: MediaItemCommand,
        parentId: String = AppUtils.EMPTY_STRING,
        options: Bundle = Bundle(),
        presenterWith: (hold: () -> Unit) -> RecordingPresenter
    ) {
        val listener = RecordingCommandListener()
        val asked = CountDownLatch(1)
        val presenter = presenterWith {
            asked.countDown()
            listener.awaitDelivery()
        }

        dependencies(presenter, listener, parentId = parentId, options = options, timeoutMillis = DEADLINE_MILLIS)
            .execute(command, listener.playbackStateListener)

        assertTrue(
            "The command never asked its provider, so its answer says nothing about the deadline",
            asked.await(RecordingCommandListener.AWAIT_MILLIS, TimeUnit.MILLISECONDS)
        )
        listener.awaitResult()
        assertEquals("The command answered more than once", 1, listener.results)
        assertEquals(
            "The provider's late answer reached the listener after the deadline had answered",
            emptyList<String>(),
            listener.mediaIds
        )
        listener.assertNoError()
    }

    companion object {

        /** Far below [RecordingCommandListener.AWAIT_MILLIS], so the deadline expires first. */
        private const val DEADLINE_MILLIS = 50L
    }
}
