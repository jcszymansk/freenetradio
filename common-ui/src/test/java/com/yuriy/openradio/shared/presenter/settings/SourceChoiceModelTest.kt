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

package com.yuriy.openradio.shared.presenter.settings

import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.presenter.screen.ChoiceKind
import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.shared.presenter.screen.ScreenStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceChoiceModelTest {

    private val mSources = FakeSources(Source.RADIO_BROWSER)
    private val mRestart = RecordingRestart()
    private val mScreens = ScreenStack()
    private val mModel = SourceChoiceModel(mSources, mRestart, mScreens)

    @Test
    fun openShowsTheChoiceWithEverySourceAndTheActiveOneSelected() {
        mScreens.push(Destination.Settings)
        mModel.open()
        assertEquals(
            Destination.SettingsChoice(ChoiceKind.SOURCE),
            mScreens.top.value,
        )
        assertEquals(
            SourceChoiceState(Source.entries.toList(), Source.RADIO_BROWSER, restartPending = false),
            mModel.state.value,
        )
    }

    @Test
    fun choosingAnotherSourceSetsItAtOnceAndOffersTheRestart() {
        mModel.open()
        mModel.choose(Source.WEB_RADIO)
        assertEquals(listOf(Source.WEB_RADIO), mSources.writes)
        assertEquals(Source.WEB_RADIO, mModel.state.value.active)
        assertTrue(mModel.state.value.restartPending)
        assertEquals(
            Destination.SettingsChoice(ChoiceKind.SOURCE),
            mScreens.top.value,
        )
    }

    @Test
    fun choosingTheOriginalSourceAgainHidesTheRestart() {
        mModel.open()
        mModel.choose(Source.WEB_RADIO)
        mModel.choose(Source.RADIO_BROWSER)
        assertFalse(mModel.state.value.restartPending)
        assertEquals(Source.RADIO_BROWSER, mSources.active)
    }

    @Test
    fun restartRestartsWithTheChosenSourceActive() {
        mModel.open()
        mModel.choose(Source.WEB_RADIO)
        mModel.restart()
        assertEquals(1, mRestart.restarts)
        assertEquals(Source.WEB_RADIO, mSources.active)
    }

    @Test
    fun leavingWithoutRestartingPutsTheOriginalSourceBack() {
        mScreens.push(Destination.Settings)
        mModel.open()
        mModel.choose(Source.WEB_RADIO)
        mScreens.pop()
        assertEquals(Source.RADIO_BROWSER, mSources.active)
        assertEquals(listOf(Source.WEB_RADIO, Source.RADIO_BROWSER), mSources.writes)
        assertEquals(Destination.Settings, mScreens.top.value)
    }

    @Test
    fun leavingAfterChoosingTheOriginalAgainChangesNothing() {
        mModel.open()
        mModel.choose(Source.WEB_RADIO)
        mModel.choose(Source.RADIO_BROWSER)
        mSources.writes.clear()
        mScreens.pop()
        assertTrue(mSources.writes.isEmpty())
    }

    @Test
    fun leavingWithoutChoosingChangesNothing() {
        mModel.open()
        mScreens.pop()
        assertTrue(mSources.writes.isEmpty())
    }

    @Test
    fun clearingTheStackAlsoPutsTheOriginalSourceBack() {
        mScreens.push(Destination.Settings)
        mModel.open()
        mModel.choose(Source.WEB_RADIO)
        mScreens.clear()
        assertEquals(Source.RADIO_BROWSER, mSources.active)
    }

    @Test
    fun leavingAnotherScreenDoesNotTouchTheSource() {
        mModel.open()
        mModel.choose(Source.WEB_RADIO)
        mScreens.push(Destination.About)
        mScreens.pop()
        assertEquals(Source.WEB_RADIO, mSources.active)
        assertTrue(mModel.state.value.restartPending)
    }

    @Test
    fun aRecreationIsNotALeaveSoThePendingChoiceStays() {
        mModel.open()
        mModel.choose(Source.WEB_RADIO)
        // Recreating the activity pops nothing: the stack and this model outlive it.
        assertEquals(Source.WEB_RADIO, mModel.state.value.active)
        assertTrue(mModel.state.value.restartPending)
        assertEquals(listOf(Source.WEB_RADIO), mSources.writes)
    }

    @Test
    fun openingAgainAfterLeavingStartsFromTheSourceThenActive() {
        mModel.open()
        mModel.choose(Source.WEB_RADIO)
        mScreens.pop()
        mSources.active = Source.WEB_RADIO
        mModel.open()
        assertFalse(mModel.state.value.restartPending)
        assertEquals(Source.WEB_RADIO, mModel.state.value.active)
    }
}
