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
import org.junit.Test

class StreamBufferingModelTest {

    private val mStored = BufferSettings(30000, 60000, 2000, 3000)
    private val mBuffers = FakeBufferStore(mStored)
    private val mScreens = ScreenStack()
    private val mModel = StreamBufferingModel(mBuffers, BufferBounds(1000, 300000), mScreens)

    private fun openFromSettings() {
        mScreens.push(Destination.Settings)
        mModel.open()
    }

    @Test
    fun openFillsTheFieldsFromTheStoredValues() {
        openFromSettings()
        assertEquals(Destination.StreamBuffering, mScreens.top.value)
        assertEquals(StreamBufferingState(BufferTexts.of(mStored), emptyMap()), mModel.state.value)
    }

    @Test
    fun editingChangesOnlyThatField() {
        openFromSettings()
        mModel.edit(BufferField.PLAY, "2500")
        assertEquals(BufferTexts("30000", "60000", "2500", "3000"), mModel.state.value.texts)
        assertEquals(emptyList<BufferSettings>(), mBuffers.writes)
    }

    @Test
    fun restoreDefaultsFillsTheDefaultsWithoutStoringThem() {
        openFromSettings()
        mModel.restoreDefaults()
        assertEquals(BufferTexts.of(BufferSettings.DEFAULTS), mModel.state.value.texts)
        assertEquals(emptyList<BufferSettings>(), mBuffers.writes)
        assertEquals(Destination.StreamBuffering, mScreens.top.value)
    }

    @Test
    fun saveWithValidValuesStoresAllFourAndReturnsToSettings() {
        openFromSettings()
        mModel.edit(BufferField.MIN, "40000")
        mModel.save()
        assertEquals(listOf(BufferSettings(40000, 60000, 2000, 3000)), mBuffers.writes)
        assertEquals(Destination.Settings, mScreens.top.value)
    }

    @Test
    fun restoreDefaultsThenSaveStoresTheDefaults() {
        openFromSettings()
        mModel.restoreDefaults()
        mModel.save()
        assertEquals(listOf(BufferSettings.DEFAULTS), mBuffers.writes)
    }

    @Test
    fun saveWithAnInvalidValueStoresNothingStaysAndShowsTheErrorUnderItsField() {
        openFromSettings()
        mModel.edit(BufferField.MAX, "20000")
        mModel.save()
        assertEquals(emptyList<BufferSettings>(), mBuffers.writes)
        assertEquals(Destination.StreamBuffering, mScreens.top.value)
        assertEquals(mapOf(BufferField.MAX to BufferRule.MAX_BELOW_MIN), mModel.state.value.errors)
    }

    @Test
    fun saveShowsEveryErrorAtOnce() {
        openFromSettings()
        mModel.edit(BufferField.MAX, "20000")
        mModel.edit(BufferField.PLAY, "")
        mModel.edit(BufferField.REBUFFER, "40000")
        mModel.save()
        assertEquals(
            mapOf(
                BufferField.PLAY to BufferRule.INVALID_VALUE,
                BufferField.MAX to BufferRule.MAX_BELOW_MIN,
                BufferField.REBUFFER to BufferRule.REBUFFER_ABOVE_MIN,
            ),
            mModel.state.value.errors,
        )
    }

    @Test
    fun theErrorsStayWhileTheUserEditsAndGoOnTheNextSave() {
        openFromSettings()
        mModel.edit(BufferField.MAX, "20000")
        mModel.save()
        mModel.edit(BufferField.MAX, "70000")
        assertEquals(mapOf(BufferField.MAX to BufferRule.MAX_BELOW_MIN), mModel.state.value.errors)
        mModel.save()
        assertEquals(listOf(BufferSettings(30000, 70000, 2000, 3000)), mBuffers.writes)
        assertEquals(Destination.Settings, mScreens.top.value)
    }

    @Test
    fun closingStoresNothingWhateverTheFieldsHold() {
        openFromSettings()
        mModel.edit(BufferField.MIN, "40000")
        mModel.close()
        assertEquals(emptyList<BufferSettings>(), mBuffers.writes)
        assertEquals(Destination.Settings, mScreens.top.value)
    }

    @Test
    fun systemBackStoresNothingEither() {
        openFromSettings()
        mModel.edit(BufferField.MIN, "40000")
        mScreens.pop()
        assertEquals(emptyList<BufferSettings>(), mBuffers.writes)
    }

    @Test
    fun aRecreationKeepsTheTypedValuesAndTheShownErrors() {
        openFromSettings()
        mModel.edit(BufferField.MAX, "20000")
        mModel.save()
        // Recreating the activity pops nothing, so this state is what the new composition reads.
        assertEquals(
            StreamBufferingState(
                BufferTexts("30000", "20000", "2000", "3000"),
                mapOf(BufferField.MAX to BufferRule.MAX_BELOW_MIN),
            ),
            mModel.state.value,
        )
    }

    @Test
    fun openingAgainStartsFromTheStoredValuesWithNoErrors() {
        openFromSettings()
        mModel.edit(BufferField.MAX, "20000")
        mModel.save()
        mModel.close()
        mModel.open()
        assertEquals(StreamBufferingState(BufferTexts.of(mStored), emptyMap()), mModel.state.value)
    }

    @Test
    fun theBoundsArePassedIn() {
        assertEquals(BufferBounds(1000, 300000), mModel.bounds)
    }
}
