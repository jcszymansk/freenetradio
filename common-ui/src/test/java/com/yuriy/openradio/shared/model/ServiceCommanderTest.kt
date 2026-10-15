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

package com.yuriy.openradio.shared.model

import android.os.Bundle
import com.yuriy.openradio.shared.service.OpenRadioService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCommanderTest {

    @Test
    fun updateBrowseTreeSendsServiceCommand() = runBlocking {
        val commander = RecordingServiceCommander()

        assertTrue(commander.updateBrowseTree())
        assertEquals(OpenRadioService.CMD_UPDATE_TREE, commander.command)
    }

    private class RecordingServiceCommander : ServiceCommander {
        var command = ""

        override suspend fun sendCommand(command: String, parameters: Bundle): Boolean {
            this.command = command
            return true
        }

        override suspend fun sendCommand(
            command: String,
            parameters: Bundle,
            resultCallback: (Int, Bundle?) -> Unit
        ): Boolean {
            return false
        }
    }
}
