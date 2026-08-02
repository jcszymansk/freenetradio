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
