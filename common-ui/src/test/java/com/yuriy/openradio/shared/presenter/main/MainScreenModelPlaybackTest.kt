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

package com.yuriy.openradio.shared.presenter.main

import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.service.OpenRadioService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenModelPlaybackTest {

    @Test
    fun theMiniPlayerIsWhatTheTrackerShows() {
        val m = ModelUnderTest().startAndConnect().play()

        assertEquals(MiniPlayerState("Arrow Classic Rock", "AC", "Iggy Pop - Candy", isPlaying = true), m.state.miniPlayer)
        assertEquals("s1", m.state.playingId)
    }

    @Test
    fun thePlayingStationsRowIsMarked() {
        val m = ModelUnderTest().startAndConnect().play()
        m.model.selectTab(Tab.MINE)

        m.client.answer(MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST, listOf(station("s1"), station("s2")))

        assertEquals(listOf(true, false), m.rows().map { (it as RowState.Station).isCurrent })
    }

    @Test
    fun aSearchResultRowOfThePlayingStationIsMarked() {
        val m = ModelUnderTest().startAndConnect().play()
        m.model.search("rock")

        m.client.answer(MediaId.MEDIA_ID_SEARCH_FROM_APP, listOf(station(MediaId.makeSearchId("s1"))))

        assertTrue((m.rows().single() as RowState.Station).isCurrent)
    }

    @Test
    fun closeResetsEveryTabAndAsksTheActivityToFinish() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.GENRE)
        m.model.search("rock")

        m.model.close()

        assertTrue(m.state.isClosed)
        assertEquals(Tab.FAV, m.model.snapshot().selectedTab)
        assertTrue(m.model.snapshot().stacks.values.all { it.size == 1 })
    }

    @Test
    fun aStartAfterCloseIsOpenAgain() {
        val m = ModelUnderTest().startAndConnect()
        m.model.close()
        m.model.stop()

        m.model.start()

        assertFalse(m.state.isClosed)
    }

    @Test
    fun leavingStopsTheService() {
        val m = ModelUnderTest().startAndConnect()

        m.model.leave()

        assertEquals(OpenRadioService.CMD_STOP_SERVICE, m.client.commands.last().first)
    }

    @Test
    fun theServiceCommanderAnswersFalseWithoutAClient() {
        val m = ModelUnderTest()

        assertFalse(runBlocking { m.model.serviceCommander.sendCommand(OpenRadioService.CMD_UPDATE_TREE) })
        m.startAndConnect()
        assertTrue(runBlocking { m.model.serviceCommander.sendCommand(OpenRadioService.CMD_UPDATE_TREE) })
        assertEquals(OpenRadioService.CMD_UPDATE_TREE, m.client.commands.last().first)
    }
}
