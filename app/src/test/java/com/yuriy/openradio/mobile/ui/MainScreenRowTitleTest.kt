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

package com.yuriy.openradio.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.jcszymansk.console.design.ConsoleTheme
import com.yuriy.openradio.mobile.view.main.MainScreen
import com.yuriy.openradio.mobile.view.main.MainScreenTags
import com.yuriy.openradio.shared.presenter.main.ListContent
import com.yuriy.openradio.shared.presenter.main.RowState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The title of every list row is findable on its own. A station row without artwork draws its
 * initials before its name, so the row's merged text starts with the initials; the instrumented
 * journeys read a row's title through [MainScreenTags.rowTitle] instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class MainScreenRowTitleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aStationRowWithoutArtworkTitlesItselfWithItsName() {
        val stations = show("mine_loaded").filterIsInstance<RowState.Station>()
        assertTrue("the fixture holds stations", stations.isNotEmpty())
        stations.forEach { station ->
            assertTrue("${station.id} has no artwork", station.artworkUri == null)
            composeRule.onNodeWithTag(MainScreenTags.rowTitle(station.id), useUnmergedTree = true)
                .assertTextEquals(station.name)
        }
    }

    @Test
    fun aCategoryRowTitlesItselfWithItsTitle() {
        val categories = show("genre_loaded").filterIsInstance<RowState.Category>()
        assertTrue("the fixture holds categories", categories.isNotEmpty())
        categories.forEach { category ->
            composeRule.onNodeWithTag(MainScreenTags.rowTitle(category.id), useUnmergedTree = true)
                .assertTextEquals(category.title)
        }
    }

    /**
     * The journeys find rows by [MainScreenTags.ROW_PREFIX]; a title tag with that prefix would
     * be read as one more row.
     */
    @Test
    fun aTitleTagIsNotARowTag() {
        assertFalse(MainScreenTags.rowTitle("m1").startsWith(MainScreenTags.ROW_PREFIX))
    }

    /**
     * Composes the fixture state [name] and returns its rows.
     */
    private fun show(name: String): List<RowState> {
        val state = MainScreenFixtures.states.getValue(name)
        composeRule.setContent {
            ConsoleTheme(darkTheme = true) {
                Box { MainScreen(state, MainScreenFixtures.noActions) }
            }
        }
        composeRule.waitForIdle()
        return (state.content as ListContent.Loaded).rows
    }
}
