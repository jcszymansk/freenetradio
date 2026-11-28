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

package com.yuriy.openradio.mobile.view.settings

import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.settings.SourceChoiceState
import com.yuriy.openradio.shared.service.location.Country
import org.junit.Assert.assertEquals
import org.junit.Test

class ChoiceRowsTest {

    @Test
    fun startPlaybackOffersAlwaysIfPlayingAndNeverInThatOrder() {
        val rows = startPlaybackRows(StartPlayback.IF_PLAYING) { it.name.lowercase() }

        assertEquals(
            listOf(
                ChoiceRow("ALWAYS", "always", detail = null, selected = false),
                ChoiceRow("IF_PLAYING", "if_playing", detail = null, selected = true),
                ChoiceRow("NEVER", "never", detail = null, selected = false),
            ),
            rows,
        )
    }

    @Test
    fun sourcesAreLabelledByTheirNameAndTheActiveOneIsSelected() {
        val state = SourceChoiceState(Source.entries, Source.WEB_RADIO, restartPending = true)

        assertEquals(
            listOf(
                ChoiceRow("RADIO_BROWSER", "Radio Browser", detail = null, selected = false),
                ChoiceRow("WEB_RADIO", "Web Radio", detail = null, selected = true),
            ),
            sourceRows(state),
        )
    }

    @Test
    fun countriesShowTheirCodeExceptTheLocationEntry() {
        val countries = listOf(Country("Use Location", "Use Location"), Country("Poland", "PL"))

        assertEquals(
            listOf(
                ChoiceRow("Use Location", "Use Location", detail = null, selected = false),
                ChoiceRow("PL", "Poland", detail = "PL", selected = true),
            ),
            countryRows(countries, selectedCode = "PL"),
        )
    }

    @Test
    fun theListOpensAtTheSelectedRowOrAtTheTopWithoutOne() {
        val unselected = ChoiceRow("a", "A", detail = null, selected = false)
        val selected = ChoiceRow("b", "B", detail = null, selected = true)

        assertEquals(1, firstShown(listOf(unselected, selected)))
        assertEquals(0, firstShown(listOf(unselected, unselected)))
    }
}
