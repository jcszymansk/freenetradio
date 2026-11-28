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

import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Test

class BufferingValidationTest {

    private val mBounds = BufferBounds(min = 1000, max = 300000)
    private val mValid = BufferTexts(min = "30000", max = "60000", play = "2000", rebuffer = "3000")

    private fun assertErrors(texts: BufferTexts, vararg expected: BufferError) {
        assertEquals(BufferValidation.Invalid(expected.toList()), validateBuffering(texts, mBounds))
    }

    @Test
    fun validValuesAreReturnedAsNumbers() {
        assertEquals(BufferValidation.Valid(BufferSettings(30000, 60000, 2000, 3000)), validateBuffering(mValid, mBounds))
    }

    @Test
    fun theDefaultsAreValid() {
        assertEquals(
            BufferValidation.Valid(BufferSettings.DEFAULTS),
            validateBuffering(BufferTexts.of(BufferSettings.DEFAULTS), mBounds)
        )
    }

    @Test
    fun theDefaultsAreTheOnesThePreferencesFallBackTo() {
        assertEquals(
            BufferSettings(
                AppPreferencesManager.DEFAULT_MIN_BUFFER_MS,
                AppPreferencesManager.DEFAULT_MAX_BUFFER_MS,
                AppPreferencesManager.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                AppPreferencesManager.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            ),
            BufferSettings.DEFAULTS
        )
    }

    @Test
    fun surroundingSpacesAreTrimmedAsTheDialogTrimmedThem() {
        val texts = BufferTexts(min = " 30000", max = "60000 ", play = "\t2000", rebuffer = "3000\n")

        assertEquals(BufferValidation.Valid(BufferSettings(30000, 60000, 2000, 3000)), validateBuffering(texts, mBounds))
    }

    @Test
    fun bothBoundsAreAllowed() {
        val texts = BufferTexts(min = "1000", max = "300000", play = "1000", rebuffer = "1000")

        assertEquals(BufferValidation.Valid(BufferSettings(1000, 300000, 1000, 1000)), validateBuffering(texts, mBounds))
    }

    @Test
    fun anEmptyValueIsInvalid() {
        assertErrors(mValid.with(BufferField.PLAY, ""), BufferError(BufferField.PLAY, BufferRule.INVALID_VALUE))
    }

    @Test
    fun aValueOfOnlySpacesIsInvalid() {
        assertErrors(mValid.with(BufferField.MAX, "   "), BufferError(BufferField.MAX, BufferRule.INVALID_VALUE))
    }

    @Test
    fun aValueThatIsNotAllDigitsIsInvalid() {
        for (text in listOf("12a00", "-5000", "2000.5", "2 000", "+2000")) {
            assertErrors(
                mValid.with(BufferField.REBUFFER, text),
                BufferError(BufferField.REBUFFER, BufferRule.INVALID_VALUE)
            )
        }
    }

    @Test
    fun aValueTooLargeForAnIntIsInvalid() {
        assertErrors(mValid.with(BufferField.MAX, "99999999999"), BufferError(BufferField.MAX, BufferRule.INVALID_VALUE))
    }

    @Test
    fun aValueAboveTheUpperBoundIsInvalid() {
        assertErrors(mValid.with(BufferField.MAX, "300001"), BufferError(BufferField.MAX, BufferRule.INVALID_VALUE))
    }

    @Test
    fun aValueBelowTheLowerBoundIsInvalid() {
        assertErrors(mValid.with(BufferField.PLAY, "999"), BufferError(BufferField.PLAY, BufferRule.INVALID_VALUE))
    }

    @Test
    fun maxBelowMinIsShownUnderMax() {
        assertErrors(mValid.with(BufferField.MAX, "29999"), BufferError(BufferField.MAX, BufferRule.MAX_BELOW_MIN))
    }

    @Test
    fun maxEqualToMinIsAllowed() {
        assertEquals(
            BufferValidation.Valid(BufferSettings(30000, 30000, 2000, 3000)),
            validateBuffering(mValid.with(BufferField.MAX, "30000"), mBounds)
        )
    }

    @Test
    fun playAboveMinIsShownUnderPlay() {
        assertErrors(mValid.with(BufferField.PLAY, "30001"), BufferError(BufferField.PLAY, BufferRule.PLAY_ABOVE_MIN))
    }

    @Test
    fun playEqualToMinIsAllowed() {
        assertEquals(
            BufferValidation.Valid(BufferSettings(30000, 60000, 30000, 3000)),
            validateBuffering(mValid.with(BufferField.PLAY, "30000"), mBounds)
        )
    }

    @Test
    fun rebufferAboveMinIsShownUnderRebuffer() {
        assertErrors(
            mValid.with(BufferField.REBUFFER, "30001"),
            BufferError(BufferField.REBUFFER, BufferRule.REBUFFER_ABOVE_MIN)
        )
    }

    @Test
    fun everyFailedRuleIsReportedAtOnce() {
        val texts = BufferTexts(min = "30000", max = "20000", play = "40000", rebuffer = "")

        assertErrors(
            texts,
            BufferError(BufferField.REBUFFER, BufferRule.INVALID_VALUE),
            BufferError(BufferField.MAX, BufferRule.MAX_BELOW_MIN),
            BufferError(BufferField.PLAY, BufferRule.PLAY_ABOVE_MIN)
        )
    }

    @Test
    fun anInvalidMinIsTheOnlyErrorEvenIfTheOthersWouldFailAgainstIt() {
        // With min unreadable, no rule between it and another field can be judged.
        val texts = BufferTexts(min = "abc", max = "2000", play = "250000", rebuffer = "250000")

        assertErrors(texts, BufferError(BufferField.MIN, BufferRule.INVALID_VALUE))
    }

    @Test
    fun anInvalidOtherFieldSkipsOnlyItsOwnCrossFieldRule() {
        val texts = BufferTexts(min = "30000", max = "x", play = "40000", rebuffer = "3000")

        assertErrors(
            texts,
            BufferError(BufferField.MAX, BufferRule.INVALID_VALUE),
            BufferError(BufferField.PLAY, BufferRule.PLAY_ABOVE_MIN)
        )
    }

    @Test
    fun textsReadAndReplaceEachField() {
        val texts = BufferTexts("1", "2", "3", "4")

        assertEquals(listOf("1", "2", "3", "4"), BufferField.entries.map { texts[it] })
        for (field in BufferField.entries) {
            val replaced = texts.with(field, "9")
            assertEquals(
                BufferField.entries.map { if (it == field) "9" else texts[it] },
                BufferField.entries.map { replaced[it] }
            )
        }
        assertEquals(BufferTexts("5", "6", "7", "8"), BufferTexts.of(BufferSettings(5, 6, 7, 8)))
    }
}
