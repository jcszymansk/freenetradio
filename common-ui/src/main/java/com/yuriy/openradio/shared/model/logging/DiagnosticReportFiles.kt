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

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * The diagnostic report files kept in [mDirectory] for sharing.
 *
 * A shared report is read by another application at a time this one does not learn: an email
 * client may only open the attachment when its draft is sent. So every report gets a file of its
 * own, which a later report never overwrites, and a report is deleted only once it is older than
 * [RETENTION_MS].
 *
 * @param mNow the current time in milliseconds, compared with the files' modification times.
 */
class DiagnosticReportFiles(
    private val mDirectory: File,
    private val mNow: () -> Long = System::currentTimeMillis
) {

    /**
     * Writes a new report file with [content] and returns it. On failure no part of the report is
     * left behind: an [IOException] becomes the failed [Result], and anything else [content]
     * throws is rethrown once the partial file is gone.
     */
    fun write(content: (OutputStream) -> Unit): Result<File> {
        val report = try {
            createReportFile()
        } catch (e: IOException) {
            return Result.failure(e)
        }
        return try {
            FileOutputStream(report).use(content)
            Result.success(report)
        } catch (e: IOException) {
            report.delete()
            Result.failure(e)
        } catch (e: Throwable) {
            report.delete()
            throw e
        }
    }

    /**
     * Deletes the reports older than [RETENTION_MS].
     */
    fun deleteExpired() {
        val oldestKept = mNow() - RETENTION_MS
        mDirectory.listFiles()
            ?.filter { it.isFile && it.lastModified() < oldestKept }
            ?.forEach { it.delete() }
    }

    private fun createReportFile(): File {
        if (!mDirectory.isDirectory && !mDirectory.mkdirs()) {
            throw IOException("Cannot create $mDirectory")
        }
        return File.createTempFile(REPORT_PREFIX, REPORT_SUFFIX, mDirectory)
    }

    companion object {

        /**
         * How long a shared report is kept. Long enough for a draft that holds the report to be
         * sent the same day.
         */
        const val RETENTION_MS = 24L * 60 * 60 * 1000

        private const val REPORT_PREFIX = "diagnostic-report-"
        private const val REPORT_SUFFIX = ".zip"
    }
}
