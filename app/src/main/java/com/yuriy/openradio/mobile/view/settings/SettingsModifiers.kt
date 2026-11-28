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

package com.yuriy.openradio.mobile.view.settings

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

private const val DISABLED_ALPHA = 0.38f

/**
 * Draws a console-design control that has no enabled state as disabled and says so to
 * accessibility and tests. The caller's action must ignore taps itself.
 */
internal fun Modifier.disabledLook(disabled: Boolean): Modifier = if (disabled) {
    alpha(DISABLED_ALPHA).semantics { disabled() }
} else {
    this
}
