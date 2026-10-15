/*
 * Copyright 2023 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Collects diagnostic reports into the application cache, from where they are shared through
 * `FileProvider` (see `res/xml/provider_paths.xml`, which must name the same directory).
 */
class LoggingLayerImpl(private val mContext: Context) : LoggingLayer {

    private val mReports = DiagnosticReportFiles(File(mContext.cacheDir, REPORTS_DIR_NAME))

    override fun collectAdbLogs(onSuccess: (file: File) -> Unit, onError: (msg: String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            deleteLegacyReports()
            mReports.deleteExpired()
            mReports.write(::writeReport).fold(onSuccess) { onError(it.message.toString()) }
        }
    }

    private fun writeReport(output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(LOG_ENTRY_NAME))
            val writer = zip.bufferedWriter()
            writer.writeLine(getDeviceInfo())
            writer.writeLine(getApplicationBuildInfo(mContext))
            val process = ProcessBuilder("logcat", "-d").start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { writer.writeLine(it) }
            }
            writer.flush()
        }
    }

    /**
     * Reports used to be written to persistent internal storage, where they outlived the share.
     */
    private fun deleteLegacyReports() {
        File(mContext.filesDir, LEGACY_LOGS_DIR_NAME).deleteRecursively()
    }

    private fun BufferedWriter.writeLine(line: String) {
        write(line)
        newLine()
    }

    private fun getDeviceInfo(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val versionName = Build.VERSION.RELEASE
        val sdkVersion = Build.VERSION.SDK_INT
        val buildType = Build.TYPE
        val buildId = Build.ID
        val buildTime = Build.TIME
        val fingerprint = Build.FINGERPRINT
        val hardware = Build.HARDWARE
        val bootloader = Build.BOOTLOADER
        val board = Build.BOARD
        val radioVersion = Build.getRadioVersion()
        val brand = Build.BRAND
        val cpuAbi = Build.CPU_ABI
        val cpuAbi2 = Build.CPU_ABI2
        val device = Build.DEVICE
        val display = Build.DISPLAY
        val host = Build.HOST
        val tags = Build.TAGS

        return """
        Device Information:
        Manufacturer: $manufacturer
        Model: $model
        Android Version: $versionName (SDK $sdkVersion)
        Build Type: $buildType
        Build ID: $buildId
        Build Time: $buildTime
        Fingerprint: $fingerprint
        Hardware: $hardware
        Bootloader: $bootloader
        Board: $board
        Radio Version: $radioVersion
        Brand: $brand
        CPU ABI: $cpuAbi
        CPU ABI 2: $cpuAbi2
        Device: $device
        Display: $display
        Host: $host
        Tags: $tags
        """.trimIndent()
    }

    private fun getApplicationBuildInfo(context: Context): String {
        val packageManager: PackageManager = context.packageManager
        val packageName: String = context.packageName
        try {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName: String = packageInfo.versionName
            val versionCode: Int = packageInfo.versionCode
            return """
            Application Build Information:
            Package Name: $packageName
            Version Name: $versionName
            Version Code: $versionCode
            """.trimIndent()
        } catch (e: PackageManager.NameNotFoundException) {
            // Ignore
        }
        return "Application Build Information: Not available"
    }

    companion object {

        private const val LOG_ENTRY_NAME = "adb_logs.txt"
        private const val LEGACY_LOGS_DIR_NAME = "logs"

        /**
         * Directory under the cache that holds the reports.
         */
        private const val REPORTS_DIR_NAME = "diagnostic_reports"
    }
}
