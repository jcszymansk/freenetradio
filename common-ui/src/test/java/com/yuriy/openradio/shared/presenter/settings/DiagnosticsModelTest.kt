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

import com.yuriy.openradio.shared.presenter.screen.Destination
import com.yuriy.openradio.shared.presenter.screen.ScreenStack
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticsModelTest {

    private val mScope = CoroutineScope(Dispatchers.Unconfined)
    private val mLogs = FakeLogs()
    private val mNotices = RecordingNotices()
    private val mScreens = ScreenStack()
    private val mModel = DiagnosticsModel(mLogs, mNotices, mScreens, mScope)
    private val mZip = File("diagnostic-report.zip")

    @After
    fun tearDown() {
        mScope.cancel()
    }

    @Test
    fun openShowsTheWarningSubScreenIdle() {
        mScreens.push(Destination.Settings)
        mModel.open()
        assertEquals(Destination.Diagnostics, mScreens.top.value)
        assertEquals(DiagnosticsState(collecting = false, report = null), mModel.state.value)
    }

    @Test
    fun createStartsCollectingAndTheButtonIsDisabled() {
        mModel.create()
        assertEquals(1, mLogs.collections)
        assertEquals(DiagnosticsState(collecting = true, report = null), mModel.state.value)
        assertFalse(mModel.state.value.canCreate)
    }

    @Test
    fun aSecondCreateWhileCollectingIsIgnored() {
        mModel.create()
        mModel.create()
        assertEquals(1, mLogs.collections)
    }

    @Test
    fun aCollectedReportIsHandedToThePlatform() {
        mModel.create()
        mLogs.succeed(mZip)
        assertEquals(DiagnosticsState(collecting = false, report = mZip), mModel.state.value)
        assertFalse(mModel.state.value.canCreate)
    }

    @Test
    fun aSharedReportLeavesTheScreenIdle() {
        mModel.create()
        mLogs.succeed(mZip)
        mModel.reportHandled(shared = true)
        assertEquals(DiagnosticsState(collecting = false, report = null), mModel.state.value)
        assertEquals(emptyList<Notice>(), mNotices.shown)
    }

    @Test
    fun aReportThatCouldNotBeSharedIsAFailure() {
        mModel.create()
        mLogs.succeed(mZip)
        mModel.reportHandled(shared = false)
        assertEquals(listOf(Notice.FAILURE), mNotices.shown)
        assertEquals(true, mModel.state.value.canCreate)
    }

    @Test
    fun aFailedCollectionShowsTheFailureAndEnablesTheButtonAgain() {
        mModel.create()
        mLogs.fail("logcat refused")
        assertEquals(listOf(Notice.FAILURE), mNotices.shown)
        assertEquals(DiagnosticsState(collecting = false, report = null), mModel.state.value)
        assertEquals(true, mModel.state.value.canCreate)
    }

    @Test
    fun anotherReportCanBeCreatedAfterOneWasShared() {
        mModel.create()
        mLogs.succeed(mZip)
        mModel.reportHandled(shared = true)
        mModel.create()
        assertEquals(2, mLogs.collections)
    }
}
