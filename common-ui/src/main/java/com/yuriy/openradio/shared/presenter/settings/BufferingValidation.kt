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

/** The stream buffering values a user can change, in the order the dialog showed them. */
enum class BufferField { MIN, MAX, PLAY, REBUFFER }

/**
 * The rule a buffering value broke.
 *
 * [INVALID_VALUE] stands for `invalid_buffer_desc`; it covers an empty value, one that is
 * not all digits, and one outside the bounds. [MAX_BELOW_MIN] stands for
 * `min_buffer_greater_max_buffer` and is shown under max. [PLAY_ABOVE_MIN] stands for
 * `play_buffer_greater_min_buffer` and [REBUFFER_ABOVE_MIN] for
 * `play_re_buffer_greater_min_buffer`, each shown under its own field.
 */
enum class BufferRule { INVALID_VALUE, MAX_BELOW_MIN, PLAY_ABOVE_MIN, REBUFFER_ABOVE_MIN }

/** A rule broken by the value in [field]. A cross-field rule belongs to the field that is not min. */
data class BufferError(val field: BufferField, val rule: BufferRule)

/**
 * The inclusive bounds a buffering value must fall in: the `min_buffer_val` and
 * `max_buffer_val` resources, passed in so that the rules read no resources.
 */
data class BufferBounds(val min: Int, val max: Int)

/** The four stream buffering values, in milliseconds. */
data class BufferSettings(val min: Int, val max: Int, val play: Int, val rebuffer: Int) {

    companion object {

        /** The buffering this app shipped with, from [AppPreferencesManager]'s defaults. */
        val DEFAULTS = BufferSettings(
            AppPreferencesManager.DEFAULT_MIN_BUFFER_MS,
            AppPreferencesManager.DEFAULT_MAX_BUFFER_MS,
            AppPreferencesManager.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            AppPreferencesManager.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
    }
}

/** The buffering fields as the user typed them, before validation. */
data class BufferTexts(val min: String, val max: String, val play: String, val rebuffer: String) {

    /** Returns the text of [field]. */
    operator fun get(field: BufferField): String = when (field) {
        BufferField.MIN -> min
        BufferField.MAX -> max
        BufferField.PLAY -> play
        BufferField.REBUFFER -> rebuffer
    }

    /** Returns these texts with the text of [field] replaced by [text]. */
    fun with(field: BufferField, text: String): BufferTexts = when (field) {
        BufferField.MIN -> copy(min = text)
        BufferField.MAX -> copy(max = text)
        BufferField.PLAY -> copy(play = text)
        BufferField.REBUFFER -> copy(rebuffer = text)
    }

    companion object {

        /** Returns [values] in their decimal form. */
        fun of(values: BufferSettings): BufferTexts = BufferTexts(
            values.min.toString(),
            values.max.toString(),
            values.play.toString(),
            values.rebuffer.toString()
        )
    }
}

/** The outcome of [validateBuffering]: the parsed values, or every rule that failed. */
sealed interface BufferValidation {

    /** Every field parsed and every rule held. */
    data class Valid(val values: BufferSettings) : BufferValidation

    /** At least one rule failed; [errors] is never empty. */
    data class Invalid(val errors: List<BufferError>) : BufferValidation
}

/**
 * Applies the stream buffering rules to [texts], using [bounds], and reports every failed rule
 * at once instead of stopping at the first.
 *
 * A field that cannot be read produces [BufferRule.INVALID_VALUE] under its own field. The
 * cross-field rules run only when min is readable, and each also needs the other field it
 * compares: max must not be below min, and min must not be below play or rebuffer.
 */
fun validateBuffering(texts: BufferTexts, bounds: BufferBounds): BufferValidation {
    val min = parseBuffer(texts[BufferField.MIN], bounds)
    val max = parseBuffer(texts[BufferField.MAX], bounds)
    val play = parseBuffer(texts[BufferField.PLAY], bounds)
    val rebuffer = parseBuffer(texts[BufferField.REBUFFER], bounds)

    val errors = mutableListOf<BufferError>()
    if (min == null) {
        errors += BufferError(BufferField.MIN, BufferRule.INVALID_VALUE)
    }
    if (max == null) {
        errors += BufferError(BufferField.MAX, BufferRule.INVALID_VALUE)
    }
    if (play == null) {
        errors += BufferError(BufferField.PLAY, BufferRule.INVALID_VALUE)
    }
    if (rebuffer == null) {
        errors += BufferError(BufferField.REBUFFER, BufferRule.INVALID_VALUE)
    }

    if (min != null) {
        if (max != null && max < min) {
            errors += BufferError(BufferField.MAX, BufferRule.MAX_BELOW_MIN)
        }
        if (play != null && min < play) {
            errors += BufferError(BufferField.PLAY, BufferRule.PLAY_ABOVE_MIN)
        }
        if (rebuffer != null && min < rebuffer) {
            errors += BufferError(BufferField.REBUFFER, BufferRule.REBUFFER_ABOVE_MIN)
        }
    }

    if (min != null && max != null && play != null && rebuffer != null && errors.isEmpty()) {
        return BufferValidation.Valid(BufferSettings(min, max, play, rebuffer))
    }
    return BufferValidation.Invalid(errors)
}

/**
 * Parses [text] as the dialog did, returning null when it is empty after trimming, has a
 * character that is not a digit, does not fit an [Int], or falls outside [bounds].
 */
private fun parseBuffer(text: String, bounds: BufferBounds): Int? {
    val trimmed = text.trim { it <= ' ' }
    if (trimmed.isEmpty() || !trimmed.all { it.isDigit() }) {
        return null
    }
    val value = trimmed.toIntOrNull() ?: return null
    return if (value in bounds.min..bounds.max) value else null
}
