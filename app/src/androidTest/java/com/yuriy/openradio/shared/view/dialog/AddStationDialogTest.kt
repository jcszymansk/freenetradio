package com.yuriy.openradio.shared.view.dialog

import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.R
import com.yuriy.openradio.shared.model.ServiceCommander
import com.yuriy.openradio.shared.model.media.RadioStationToAdd
import com.yuriy.openradio.shared.service.OpenRadioService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AddStationDialogTest {

    @Test
    fun successfulAddRequestsBrowseTreeUpdate() {
        val commander = RecordingServiceCommander()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = AddStationDialog()
                dialog.showNow(activity.supportFragmentManager, AddStationDialog.DIALOG_TAG)
                dialog.configureWith(SuccessfulAddPresenter())
                dialog.configureWith(commander)

                dialog.requireView()
                    .findViewById<android.view.View>(R.id.add_edit_station_dialog_add_btn_view)
                    .performClick()
            }

            assertTrue(
                "Successful add did not request a browse-tree update",
                commander.commandSent.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )
            assertEquals(OpenRadioService.CMD_UPDATE_TREE, commander.command)
        }
    }

    private class SuccessfulAddPresenter : AddEditStationDialogPresenter {
        override fun addRadioStation(
            radioStation: RadioStationToAdd,
            onSuccess: (String) -> Unit,
            onFailure: (String) -> Unit
        ) {
            onSuccess("Radio Station added successfully")
        }

        override fun editRadioStation(
            mediaId: String,
            radioStation: RadioStationToAdd,
            onSuccess: (String) -> Unit,
            onFailure: (String) -> Unit
        ) = Unit
    }

    private class RecordingServiceCommander : ServiceCommander {
        val commandSent = CountDownLatch(1)
        var command = ""

        override suspend fun sendCommand(command: String, parameters: Bundle): Boolean {
            this.command = command
            commandSent.countDown()
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

    private companion object {
        const val TIMEOUT_SECONDS = 5L
    }
}
