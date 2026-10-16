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

package com.yuriy.openradio.shared.model.media.item

import com.yuriy.openradio.shared.model.net.UrlLayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexableMediaItemCommandTest {

    @Test
    fun onlyAnEmptySecondPageIsReportedAsNoData() {
        val command = object : IndexableMediaItemCommand() {}

        assertFalse(command.doLoadNoDataReceived(UrlLayer.FIRST_PAGE_INDEX))
        assertTrue(command.doLoadNoDataReceived(UrlLayer.FIRST_PAGE_INDEX + 1))
        assertFalse(command.doLoadNoDataReceived(UrlLayer.FIRST_PAGE_INDEX + 2))
    }
}
