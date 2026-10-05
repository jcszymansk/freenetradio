package com.yuriy.openradio.shared.view.dialog

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.mobile.view.activity.MainActivity
import com.yuriy.openradio.shared.R
import com.yuriy.openradio.shared.model.ServiceCommander
import com.yuriy.openradio.shared.model.media.RadioStationToAdd
import com.yuriy.openradio.shared.permission.grantImageReadPermission
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.ServiceBrowser
import com.yuriy.openradio.shared.service.ServiceStorages
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class AddStationDialogTest {

    /**
     * The service's own storages: the latest station is cleared so that no service this test
     * starts adopts one, and a service reads it through the registry instance, which keeps a copy
     * that clearing a parallel instance would leave behind.
     */
    private lateinit var mStorages: ServiceStorages

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        grantImageReadPermission(context)
        mStorages = ServiceStorages(context)
        clearState()
        ServiceBrowser.assertABrowseCannotStartPlayback()
    }

    @After
    fun tearDown() {
        clearState()
    }

    private fun clearState() {
        mStorages.sleepTimer.clear()
        mStorages.latest.clear()
    }

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
