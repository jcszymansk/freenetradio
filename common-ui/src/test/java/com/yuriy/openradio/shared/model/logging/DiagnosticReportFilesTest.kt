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

package com.yuriy.openradio.shared.model.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Covers the lifetime of shared diagnostic reports: each report is a file of its own, and a report
 * is only deleted once it is old enough that no receiving application can still be reading it.
 */
class DiagnosticReportFilesTest {

    @get:Rule
    val mFolder = TemporaryFolder()

    private val mNow = 10 * DiagnosticReportFiles.RETENTION_MS

    @Test
    fun writeCreatesTheDirectoryAndReturnsTheWrittenReport() {
        val directory = File(mFolder.root, "reports")
        val files = DiagnosticReportFiles(directory) { mNow }

        val report = files.write { it.write(byteArrayOf(1, 2, 3)) }.getOrThrow()

        assertEquals(directory, report.parentFile)
        assertTrue(report.name.endsWith(".zip"))
        assertEquals(listOf<Byte>(1, 2, 3), report.readBytes().toList())
    }

    @Test
    fun aNewReportNeverReplacesAnEarlierOne() {
        val files = DiagnosticReportFiles(mFolder.root) { mNow }

        val first = files.write { it.write(1) }.getOrThrow()
        val second = files.write { it.write(2) }.getOrThrow()

        assertNotEquals(first, second)
        assertEquals(listOf<Byte>(1), first.readBytes().toList())
        assertEquals(listOf<Byte>(2), second.readBytes().toList())
    }

    @Test
    fun aFailedWriteLeavesNoPartialReport() {
        val files = DiagnosticReportFiles(mFolder.root) { mNow }
        val failure = IOException("logcat went away")

        val result = files.write {
            it.write(1)
            throw failure
        }

        assertEquals(failure, result.exceptionOrNull())
        assertEquals(emptyList<String>(), mFolder.root.list()!!.toList())
    }

    @Test
    fun aDirectoryThatCannotBeCreatedIsAFailedWrite() {
        val blocker = mFolder.newFile("blocker")
        val files = DiagnosticReportFiles(File(blocker, "reports")) { mNow }

        val result = files.write { it.write(1) }

        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun deleteExpiredRemovesOnlyReportsOlderThanTheRetention() {
        val files = DiagnosticReportFiles(mFolder.root) { mNow }
        val expired = reportAged(DiagnosticReportFiles.RETENTION_MS + 1)
        val atTheLimit = reportAged(DiagnosticReportFiles.RETENTION_MS)
        val recent = reportAged(1)

        files.deleteExpired()

        assertFalse(expired.exists())
        assertTrue(atTheLimit.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun deleteExpiredKeepsAReportJustWritten() {
        val files = DiagnosticReportFiles(mFolder.root) { System.currentTimeMillis() }
        val report = files.write { it.write(1) }.getOrThrow()

        files.deleteExpired()

        assertTrue(report.exists())
    }

    @Test
    fun deleteExpiredLeavesSubdirectoriesAlone() {
        val files = DiagnosticReportFiles(mFolder.root) { mNow }
        val directory = mFolder.newFolder("nested")
        directory.setLastModified(0)

        files.deleteExpired()

        assertTrue(directory.exists())
    }

    @Test
    fun deleteExpiredOnAMissingDirectoryDoesNothing() {
        val directory = File(mFolder.root, "never-created")
        val files = DiagnosticReportFiles(directory) { mNow }

        files.deleteExpired()

        assertFalse(directory.exists())
    }

    private fun reportAged(ageMs: Long): File {
        val report = mFolder.newFile("report-$ageMs.zip")
        assertTrue(report.setLastModified(mNow - ageMs))
        return report
    }
}
