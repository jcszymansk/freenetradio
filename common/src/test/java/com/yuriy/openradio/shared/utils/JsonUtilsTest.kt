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

package com.yuriy.openradio.shared.utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [JsonUtils], the accessors every translator in the project reads untrusted JSON through.
 * Each one is exercised for a value that is there, a key that is not, and a value of the wrong
 * shape, plus the edge cases where the accessors disagree with what their names promise.
 *
 * Two conventions here need saying out loud.
 *
 * The wrong-type value is always a nested [JSONArray], never a number or a boolean. Two different
 * `org.json` implementations run this code: the reference one on the JVM test classpath, which
 * refuses to hand a non-String to `getString`, and Android's, which coerces a number or a boolean
 * to its text. A structured value is the one shape both refuse, so asserting on it pins
 * [JsonUtils] rather than the classpath the test happens to run on. It is also the realistic
 * hazard: a provider that answers with an object or a list where a station field is expected.
 *
 * Some of the behaviour pinned below is wrong, and is pinned because it is what ships.
 * [JsonUtils.getShortArray] and [JsonUtils.getIntArray] drop a single-entry array on the floor
 * and return nothing, which loses the band levels of a one-band equalizer; TASK-077 carries the
 * fix. The assertions that record it say so where they stand.
 */
class JsonUtilsTest {

    private val mStructured = JSONArray(listOf(1, 2))

    @Test
    fun stringValueReadsWhatIsThereAndFallsBackWhenItIsNot() {
        val jsonObject = JSONObject().put(KEY, "Radio Nowhere")

        assertEquals("Radio Nowhere", JsonUtils.getStringValue(jsonObject, KEY))
        assertEquals(AppUtils.EMPTY_STRING, JsonUtils.getStringValue(jsonObject, ABSENT_KEY))
    }

    @Test
    fun stringValueDefaultOverloadCoversBothTheKeyBeingThereAndNot() {
        val jsonObject = JSONObject().put(KEY, "Radio Nowhere")

        assertEquals("Radio Nowhere", JsonUtils.getStringValue(jsonObject, KEY, FALLBACK))
        assertEquals(FALLBACK, JsonUtils.getStringValue(jsonObject, ABSENT_KEY, FALLBACK))
    }

    @Test
    fun stringValueRefusesAStructuredValue() {
        val jsonObject = JSONObject().put(KEY, mStructured)

        assertThrows(JSONException::class.java) { JsonUtils.getStringValue(jsonObject, KEY) }
        assertThrows(JSONException::class.java) {
            JsonUtils.getStringValue(jsonObject, KEY, FALLBACK)
        }
    }

    @Test
    fun intValueReadsNumbersWrittenAsNumbersAndAsText() {
        assertEquals(128, JsonUtils.getIntValue(JSONObject().put(KEY, 128), KEY))
        assertEquals(128, JsonUtils.getIntValue(JSONObject().put(KEY, "128"), KEY))
        assertEquals(128, JsonUtils.getIntValue(JSONObject().put(KEY, 128.9), KEY))
    }

    @Test
    fun intValueFallsBackForAMissingKeyAndForAValueItCannotRead() {
        val jsonObject = JSONObject()
            .put(KEY, mStructured)
            .put(TEXT_KEY, "not a number")

        assertEquals(0, JsonUtils.getIntValue(jsonObject, ABSENT_KEY))
        assertEquals(0, JsonUtils.getIntValue(jsonObject, KEY))
        assertEquals(0, JsonUtils.getIntValue(jsonObject, TEXT_KEY))
    }

    @Test
    fun intValueDefaultOverloadCoversBothTheKeyBeingThereAndNot() {
        val jsonObject = JSONObject()
            .put(KEY, 128)
            .put(TEXT_KEY, "not a number")
            .put(STRUCTURED_KEY, mStructured)

        assertEquals(128, JsonUtils.getIntValue(jsonObject, KEY, INT_FALLBACK))
        assertEquals(INT_FALLBACK, JsonUtils.getIntValue(jsonObject, ABSENT_KEY, INT_FALLBACK))
        assertEquals(INT_FALLBACK, JsonUtils.getIntValue(jsonObject, TEXT_KEY, INT_FALLBACK))
        assertEquals(INT_FALLBACK, JsonUtils.getIntValue(jsonObject, STRUCTURED_KEY, INT_FALLBACK))
    }

    @Test
    fun booleanValueReadsBooleansWrittenAsBooleansAndAsText() {
        assertTrue(JsonUtils.getBooleanValue(JSONObject().put(KEY, true), KEY))
        assertFalse(JsonUtils.getBooleanValue(JSONObject().put(KEY, false), KEY))
        assertTrue(JsonUtils.getBooleanValue(JSONObject().put(KEY, "true"), KEY))
        assertFalse(JsonUtils.getBooleanValue(JSONObject().put(KEY, "false"), KEY))
    }

    @Test
    fun booleanValueIsFalseForAMissingKeyAndRefusesAStructuredValue() {
        val jsonObject = JSONObject().put(KEY, mStructured)

        assertFalse(JsonUtils.getBooleanValue(jsonObject, ABSENT_KEY))
        assertThrows(JSONException::class.java) { JsonUtils.getBooleanValue(jsonObject, KEY) }
    }

    @Test
    fun shortArrayReadsEveryEntryOfAMultipleEntryValue() {
        val jsonObject = JSONObject().put(KEY, "-1500,0,1500")

        assertArrayEquals(shortArrayOf(-1500, 0, 1500), JsonUtils.getShortArray(jsonObject, KEY))
    }

    /**
     * The single-entry case is the defect TASK-077 carries: the guard that turns the one element
     * an empty value splits into into an empty array cannot tell it from a real lone entry, so a
     * one-band equalizer comes back with no band levels at all.
     */
    @Test
    fun shortArrayLosesASingleEntryAndIsEmptyForAnEmptyValueOrAMissingKey() {
        val jsonObject = JSONObject()
            .put(KEY, "1500")
            .put(EMPTY_KEY, AppUtils.EMPTY_STRING)

        assertArrayEquals(shortArrayOf(), JsonUtils.getShortArray(jsonObject, KEY))
        assertArrayEquals(shortArrayOf(), JsonUtils.getShortArray(jsonObject, EMPTY_KEY))
        assertArrayEquals(shortArrayOf(), JsonUtils.getShortArray(jsonObject, ABSENT_KEY))
    }

    @Test
    fun shortArrayRefusesAStructuredValueAndAnEntryThatIsNotANumber() {
        val jsonObject = JSONObject()
            .put(KEY, mStructured)
            .put(TEXT_KEY, "-1500,not a number")

        assertThrows(JSONException::class.java) { JsonUtils.getShortArray(jsonObject, KEY) }
        assertThrows(NumberFormatException::class.java) {
            JsonUtils.getShortArray(jsonObject, TEXT_KEY)
        }
    }

    @Test
    fun intArrayReadsEveryEntryOfAMultipleEntryValue() {
        val jsonObject = JSONObject().put(KEY, "60000,1000000,14000000")

        assertArrayEquals(
            intArrayOf(60_000, 1_000_000, 14_000_000),
            JsonUtils.getIntArray(jsonObject, KEY)
        )
    }

    /**
     * The same defect as [shortArrayLosesASingleEntryAndIsEmptyForAnEmptyValueOrAMissingKey],
     * in the accessor that reads the equalizer's centre frequencies. TASK-077 carries the fix.
     */
    @Test
    fun intArrayLosesASingleEntryAndIsEmptyForAnEmptyValueOrAMissingKey() {
        val jsonObject = JSONObject()
            .put(KEY, "60000")
            .put(EMPTY_KEY, AppUtils.EMPTY_STRING)

        assertArrayEquals(intArrayOf(), JsonUtils.getIntArray(jsonObject, KEY))
        assertArrayEquals(intArrayOf(), JsonUtils.getIntArray(jsonObject, EMPTY_KEY))
        assertArrayEquals(intArrayOf(), JsonUtils.getIntArray(jsonObject, ABSENT_KEY))
    }

    @Test
    fun intArrayRefusesAStructuredValueAndAnEntryThatIsNotANumber() {
        val jsonObject = JSONObject()
            .put(KEY, mStructured)
            .put(TEXT_KEY, "60000,not a number")

        assertThrows(JSONException::class.java) { JsonUtils.getIntArray(jsonObject, KEY) }
        assertThrows(NumberFormatException::class.java) {
            JsonUtils.getIntArray(jsonObject, TEXT_KEY)
        }
    }

    @Test
    fun listValueKeepsASingleEntryAndEveryEntryOfAMultipleEntryValue() {
        val jsonObject = JSONObject()
            .put(KEY, "Normal,Classical,Dance")
            .put(TEXT_KEY, "Normal")

        assertEquals(
            listOf("Normal", "Classical", "Dance"),
            JsonUtils.getListValue<String>(jsonObject, KEY)
        )
        assertEquals(listOf("Normal"), JsonUtils.getListValue<String>(jsonObject, TEXT_KEY))
    }

    /**
     * An empty value used to come back as a list holding one empty string, because splitting
     * `""` yields one empty element; 0cb7b55 fixed it and nothing named [JsonUtils] asserted it
     * afterwards.
     */
    @Test
    fun listValueIsEmptyForAnEmptyValueAndForAMissingKey() {
        val jsonObject = JSONObject().put(EMPTY_KEY, AppUtils.EMPTY_STRING)

        assertTrue(JsonUtils.getListValue<String>(jsonObject, EMPTY_KEY).isEmpty())
        assertTrue(JsonUtils.getListValue<String>(jsonObject, ABSENT_KEY).isEmpty())
    }

    @Test
    fun listValueRefusesAStructuredValue() {
        val jsonObject = JSONObject().put(KEY, mStructured)

        assertThrows(JSONException::class.java) { JsonUtils.getListValue<String>(jsonObject, KEY) }
    }

    companion object {

        private const val KEY = "key"

        private const val TEXT_KEY = "textKey"

        private const val EMPTY_KEY = "emptyKey"

        private const val STRUCTURED_KEY = "structuredKey"

        private const val ABSENT_KEY = "absentKey"

        private const val FALLBACK = "fallback"

        private const val INT_FALLBACK = -1
    }
}
