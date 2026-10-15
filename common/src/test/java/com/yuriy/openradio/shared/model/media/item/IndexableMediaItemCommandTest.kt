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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexableMediaItemCommandTest {

    @Test
    fun pageAdvancesAndResetsForNewCatalogue() {
        val command = object : IndexableMediaItemCommand() {}
        val presenter = RecordingPresenter()
        val listener = RecordingCommandListener()

        assertEquals(UrlLayer.FIRST_PAGE_INDEX, command.pageNumber)
        assertFalse(command.doLoadNoDataReceived())

        command.nextPageNumber()

        assertEquals(UrlLayer.FIRST_PAGE_INDEX + 1, command.pageNumber)
        assertTrue(command.doLoadNoDataReceived())
        command.execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = "parent", isSameCatalogue = true)
        )
        assertEquals(UrlLayer.FIRST_PAGE_INDEX + 1, command.pageNumber)

        command.execute(
            listener.playbackStateListener,
            dependencies(presenter, listener, parentId = "parent", isSameCatalogue = false)
        )

        assertEquals(UrlLayer.FIRST_PAGE_INDEX, command.pageNumber)
        assertFalse(command.doLoadNoDataReceived())
        listener.assertNoError()
        assertEquals("The page counter delivered a result of its own", 0, listener.results)
    }
}
