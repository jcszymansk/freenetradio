/*
 * Copyright 2020 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.model.translation

import com.yuriy.openradio.shared.model.eq.EqualizerState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerSerializationTest {

    @Test
    fun serializationRoundTrip() {
        val expected = EqualizerState().apply {
            isEnabled = false
            currentPreset = 2
            numOfBands = 3
            presets = listOf("One", "Two", "Three")
            bandLevelRange = shortArrayOf(-1500, 1500)
            centerFrequencies = intArrayOf(60_000, 1_000_000, 14_000_000)
            bandLevels = shortArrayOf(100, 200, 300)
        }

        val serialized = EqualizerJsonStateSerializer().serialize(expected)
        val actual = EqualizerStateJsonDeserializer().deserialize(serialized)

        assertEquals(expected.isEnabled, actual.isEnabled)
        assertEquals(expected.currentPreset, actual.currentPreset)
        assertEquals(expected.numOfBands, actual.numOfBands)
        assertEquals(expected.presets, actual.presets)
        assertArrayEquals(expected.bandLevelRange, actual.bandLevelRange)
        assertArrayEquals(expected.centerFrequencies, actual.centerFrequencies)
        assertArrayEquals(expected.bandLevels, actual.bandLevels)
    }

    @Test
    fun emptyStateRoundTripKeepsEmptyCollections() {
        val actual = EqualizerStateJsonDeserializer().deserialize(
            EqualizerJsonStateSerializer().serialize(EqualizerState())
        )

        assertTrue(actual.presets.isEmpty())
        assertTrue(actual.centerFrequencies.isEmpty())
        assertTrue(actual.bandLevels.isEmpty())
    }

    @Test
    fun malformedDataReturnsDefaultState() {
        val deserializer = EqualizerStateJsonDeserializer()

        listOf("{", """{"BandLevels":"1,not-a-number"}""").forEach { value ->
            val state = deserializer.deserialize(value)

            assertTrue(state.isEnabled)
            assertEquals(0.toShort(), state.currentPreset)
            assertEquals(0.toShort(), state.numOfBands)
            assertTrue(state.presets.isEmpty())
        }
    }

    @Test
    fun invalidPresetAndBandRangeUseSafeDefaults() {
        val state = EqualizerState().apply {
            currentPreset = -1
            bandLevelRange = shortArrayOf(100)
        }

        assertEquals(0.toShort(), state.currentPreset)
        assertArrayEquals(shortArrayOf(-1500, 1500), state.bandLevelRange)
    }

    @Test
    fun mutableStateIsDefensivelyCopied() {
        val bandRange = shortArrayOf(-100, 100)
        val frequencies = intArrayOf(1_000, 2_000)
        val levels = shortArrayOf(10, 20)
        val presets = mutableListOf("One", "Two")
        val state = EqualizerState().apply {
            bandLevelRange = bandRange
            centerFrequencies = frequencies
            bandLevels = levels
            this.presets = presets
        }

        bandRange[0] = 0
        frequencies[0] = 0
        levels[0] = 0
        presets.clear()

        assertArrayEquals(shortArrayOf(-100, 100), state.bandLevelRange)
        assertArrayEquals(intArrayOf(1_000, 2_000), state.centerFrequencies)
        assertArrayEquals(shortArrayOf(10, 20), state.bandLevels)
        assertEquals(listOf("One", "Two"), state.presets)

        state.bandLevelRange[0] = 0
        state.centerFrequencies[0] = 0
        state.bandLevels[0] = 0
        (state.presets as MutableList).clear()

        assertArrayEquals(shortArrayOf(-100, 100), state.bandLevelRange)
        assertArrayEquals(intArrayOf(1_000, 2_000), state.centerFrequencies)
        assertArrayEquals(shortArrayOf(10, 20), state.bandLevels)
        assertEquals(listOf("One", "Two"), state.presets)
    }
}
