/*
 * Copyright 2017-2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import android.content.Context
import com.yuriy.openradio.shared.model.media.RadioStationToAdd

/**
 * Validator of a candidate Radio Station before it is added to the system.
 */
interface RadioStationValidator {

    /**
     * Validates [rsToAdd] and answers through exactly one of [onSuccess] or [onFailure], plus
     * [onWarning] for a defect that does not stop the station from being added. Validation may
     * reach the network, so the answer can arrive after this call returns.
     */
    fun validate(
        context: Context, rsToAdd: RadioStationToAdd,
        onSuccess: (msg: String) -> Unit,
        onWarning: (msg: String) -> Unit,
        onFailure: (msg: String) -> Unit
    )
}
