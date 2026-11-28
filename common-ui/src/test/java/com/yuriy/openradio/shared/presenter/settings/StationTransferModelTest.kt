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

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationTransferModelTest {

    private val mScope = CoroutineScope(Dispatchers.Unconfined)
    private val mFiles = FakeStationFiles()
    private val mNotices = RecordingNotices()
    private val mModel = StationTransferModel(mFiles, mNotices, mScope)
    private val mDocument = Uri.parse("content://com.example.documents/document/42")

    @After
    fun tearDown() {
        mScope.cancel()
    }

    @Test
    fun anExportWritesTheChosenDocumentAndReportsSuccess() {
        mModel.exportTo(mDocument)
        assertEquals(listOf(mDocument), mFiles.exports)
        assertEquals(listOf(Notice.SUCCESS), mNotices.shown)
        assertFalse(mModel.state.value.running)
    }

    @Test
    fun anImportReadsTheChosenDocumentAndReportsSuccess() {
        mModel.importFrom(mDocument)
        assertEquals(listOf(mDocument), mFiles.imports)
        assertEquals(listOf(Notice.SUCCESS), mNotices.shown)
    }

    @Test
    fun theProgressShowsWhileTheFileIsWrittenOrRead() {
        mFiles.gate = CompletableDeferred()
        mModel.exportTo(mDocument)
        assertTrue(mModel.state.value.running)
        assertEquals(emptyList<Notice>(), mNotices.shown)
        mFiles.gate?.complete(Unit)
        assertFalse(mModel.state.value.running)
        assertEquals(listOf(Notice.SUCCESS), mNotices.shown)
    }

    @Test
    fun aFailedTransferIsReportedAsAFailure() {
        mFiles.answer = false
        mModel.exportTo(mDocument)
        mModel.importFrom(mDocument)
        assertEquals(listOf(Notice.FAILURE, Notice.FAILURE), mNotices.shown)
        assertFalse(mModel.state.value.running)
    }

    @Test
    fun noDocumentIsAFailureAndTouchesNoFile() {
        // Null stands for a cancelled document UI, a result that is not OK, or a result without a URI.
        mModel.exportTo(null)
        mModel.importFrom(null)
        assertEquals(listOf(Notice.FAILURE, Notice.FAILURE), mNotices.shown)
        assertEquals(emptyList<Uri>(), mFiles.exports)
        assertEquals(emptyList<Uri>(), mFiles.imports)
        assertFalse(mModel.state.value.running)
    }

    @Test
    fun aDocumentUiThatCouldNotStartIsAFailure() {
        mModel.failedToStart()
        assertEquals(listOf(Notice.FAILURE), mNotices.shown)
    }

    @Test
    fun theProgressStaysUntilTheLastOfTwoTransfersEnds() {
        val first = CompletableDeferred<Unit>()
        mFiles.gate = first
        mModel.exportTo(mDocument)
        assertTrue(mModel.state.value.running)
        val second = CompletableDeferred<Unit>()
        mFiles.gate = second
        mModel.importFrom(mDocument)
        first.complete(Unit)
        assertTrue(mModel.state.value.running)
        second.complete(Unit)
        assertFalse(mModel.state.value.running)
    }
}
