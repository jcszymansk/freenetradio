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

import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.shared.presenter.screen.ScreenStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentModelTest {

    private val mStore = FakeUserAgentStore(isCustom = true, storedCustom = "Previous/2.0")
    private val mScreens = ScreenStack()
    private val mModel = UserAgentModel(mStore, mScreens)

    @Test
    fun openShowsTheFlagAndTheUserAgentInUse() {
        openFromSettings()
        assertEquals(Destination.UserAgent, mScreens.top.value)
        assertEquals(UserAgentState(custom = true, text = "Previous/2.0"), mModel.state.value)
    }

    @Test
    fun withTheFlagOffTheFieldHoldsTheDefaultUserAgent() {
        mStore.isCustom = false
        openFromSettings()
        assertEquals(
            UserAgentState(custom = false, text = FakeUserAgentStore.DEFAULT_AGENT),
            mModel.state.value,
        )
    }

    @Test
    fun theSwitchIsStoredWhenToggled() {
        openFromSettings()
        mModel.setCustom(false)
        mModel.setCustom(true)
        assertEquals(listOf("isCustom=false", "isCustom=true"), mStore.writes)
        assertTrue(mModel.state.value.custom)
    }

    @Test
    fun theTrimmedTextIsStoredWhenTheScreenIsLeft() {
        openFromSettings()
        mModel.edit("MyAgent/1.0 ")
        mScreens.pop()
        assertEquals(listOf("custom=MyAgent/1.0"), mStore.writes)
        assertEquals(Destination.Settings, mScreens.top.value)
    }

    @Test
    fun anEmptyTextIsNotStoredAndThePreviousOneIsKept() {
        openFromSettings()
        mModel.edit("   ")
        mScreens.pop()
        assertEquals(emptyList<String>(), mStore.writes)
        assertEquals("Previous/2.0", mStore.storedCustom)
        assertTrue(mStore.isCustom)
    }

    @Test
    fun theWarningShowsWhileTheSwitchIsOnAndTheFieldIsEmpty() {
        openFromSettings()
        mModel.edit(" ")
        assertTrue(mModel.state.value.emptyWarning)
        mModel.setCustom(false)
        assertFalse(mModel.state.value.emptyWarning)
        mModel.setCustom(true)
        mModel.edit("x")
        assertFalse(mModel.state.value.emptyWarning)
    }

    @Test
    fun leavingIsNeverBlocked() {
        openFromSettings()
        mModel.edit("")
        assertTrue(mScreens.pop())
        assertEquals(Destination.Settings, mScreens.top.value)
    }

    @Test
    fun clearingTheStackIsALeaveToo() {
        openFromSettings()
        mModel.edit("Closing/1.0")
        mScreens.clear()
        assertEquals(listOf("custom=Closing/1.0"), mStore.writes)
    }

    @Test
    fun leavingAnotherScreenStoresNothing() {
        openFromSettings()
        mModel.edit("Typed/1.0")
        mScreens.push(Destination.About)
        mScreens.pop()
        assertEquals(emptyList<String>(), mStore.writes)
        assertEquals("Typed/1.0", mModel.state.value.text)
    }

    @Test
    fun aRecreationKeepsTheTypedTextAndStoresNothing() {
        openFromSettings()
        mModel.edit("Typed/1.0")
        // A recreation pops nothing, so the draft held by the model is what the new screen shows.
        assertEquals("Typed/1.0", mModel.state.value.text)
        assertEquals(emptyList<String>(), mStore.writes)
    }

    /** Opens the sub-screen from Settings and forgets the writes made so far. */
    private fun openFromSettings() {
        mScreens.push(Destination.Settings)
        mModel.open()
        mStore.writes.clear()
    }
}
