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

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.testing.withActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Selects [station] on its own rendered row on MINE, and puts the tabs back.
 *
 * The item handed over is the one MINE's row shows and the parent is the node MINE stands in,
 * both read from the screen's model the way its long-press reads them, so neither argument of
 * the selection is invented here. Offline no row tap gets past the connectivity gate, which is
 * why [JourneyPlayback] makes the call the tap would make.
 */
@UnstableApi
internal fun selectTheStationFromMine(
    scenario: ActivityScenario<MainActivity>,
    compose: ComposeTestRule,
    profile: JourneyProfile,
    playback: JourneyPlayback,
    station: RadioStation,
) {
    val view = MainScreenView(compose)
    val navigation = JourneyNavigation(scenario, view)
    try {
        awaitTheStationOnMine(view, profile, station)
        val model = registryModel()
        val target = scenario.withActivity { model.longPressTarget(station.id) }
        assertNotNull(
            "The station's row is on MINE but the model holds no item for it. " + view.describe(),
            target
        )
        playback.select(target!!.item, target.parentId)
    } finally {
        navigation.returnToRoots()
    }
}

/**
 * Selects MINE and waits for it to list the seeded station, under its own name.
 */
@UnstableApi
internal fun awaitTheStationOnMine(view: MainScreenView, profile: JourneyProfile, station: RadioStation) {
    view.awaitTabs(profile.cleanInstallTabs())
    assertEquals(
        "MINE does not show the seeded station",
        listOf(BrowseRow(station.id, station.name)),
        profile.awaitSeededMine(view, station)
    )
}
