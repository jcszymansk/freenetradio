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

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentUtilsTest {

    @Test
    fun bundleToStringListsEveryKeyWithItsValueAndNullAsNULL() {
        val bundle = Bundle()
        bundle.putString("name", "Station")
        bundle.putInt("bitrate", 128)
        bundle.putString("genre", null)

        assertEquals("Bundle[name:Station|bitrate:128|genre:NULL]", IntentUtils.bundleToString(bundle))
    }

    @Test
    fun bundleToStringNamesAnEmptyAndAMissingBundle() {
        assertEquals("Bundle[]", IntentUtils.bundleToString(Bundle()))
        assertEquals("Bundle[null]", IntentUtils.bundleToString(null))
    }
}
