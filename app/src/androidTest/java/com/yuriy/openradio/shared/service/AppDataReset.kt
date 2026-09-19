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

package com.yuriy.openradio.shared.service

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionResult
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals

/**
 * Android's clear-data scenario, as close as a test that shares a process with the service can
 * get to it.
 *
 * `pm clear` would take the instrumentation process with it, so this does what `pm clear` does
 * short of the process kill: it empties every preference file the app owns, then lets
 * [OpenRadioService.CMD_CLEAR_CACHE] take both API caches, the stored images and the latest
 * station. Whether the suite should instead run under Test Orchestrator, which would get a real
 * `pm clear` per test, is TASK-031.
 */
@UnstableApi
internal class AppDataReset(private val mContext: Context) {

    /**
     * Empties every preference file the app has written, found by listing `shared_prefs` rather
     * than by naming the stores, so a store added later is covered without touching this helper.
     *
     * The files are cleared through [android.content.SharedPreferences] instead of being deleted:
     * Android caches one instance per file per process, and the service holds several of them, so
     * deleting the file on disk would leave the service reading the values it already has. That is
     * the one thing `pm clear` gets for free by killing the process.
     *
     * The ExoPlayer media cache under the external files directory is left alone. It is not part
     * of the browse profile and the player holds it open.
     *
     * @return the names of the files that were cleared.
     */
    fun clearEveryPreferenceFile(): List<String> {
        val directory = File(mContext.applicationInfo.dataDir, PREFERENCE_DIRECTORY)
        val names = (directory.listFiles() ?: emptyArray())
            .map { it.name }
            .filter { it.endsWith(PREFERENCE_FILE_SUFFIX) }
            .map { it.removeSuffix(PREFERENCE_FILE_SUFFIX) }
        for (name in names) {
            mContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        return names
    }

    /**
     * @return true when every file [clearEveryPreferenceFile] emptied is still empty.
     */
    fun everyPreferenceFileIsEmpty(names: List<String>): Boolean {
        return names.all {
            mContext.getSharedPreferences(it, Context.MODE_PRIVATE).all.isEmpty()
        }
    }

    /**
     * Waits for the preference file of the store [name] to hold [needle] on disk.
     *
     * The stores write with [android.content.SharedPreferences.Editor.apply], so a value is
     * readable through the preferences of this process before it has been written anywhere a
     * restart could find it. Reading the file rather than the preferences is the difference
     * between the two, which is why this looks in the same directory [clearEveryPreferenceFile]
     * empties rather than asking a storage.
     *
     * The store is named rather than searched for, because the same station can be in more than
     * one of them and finding it in any file would not say it reached the right one. A failure
     * names the files that do hold it, so writing to the wrong store reads as that rather than as
     * not writing at all.
     */
    fun awaitPreferenceFileContaining(name: String, needle: String) {
        val directory = File(mContext.applicationInfo.dataDir, PREFERENCE_DIRECTORY)
        val file = File(directory, name + PREFERENCE_FILE_SUFFIX)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WRITE_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (file.isFile && file.readText().contains(needle)) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        val elsewhere = (directory.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(PREFERENCE_FILE_SUFFIX) && it.readText().contains(needle) }
            .map { it.name }
        throw AssertionError(
            "'$needle' did not reach $file within $WRITE_TIMEOUT_SECONDS seconds, so nothing a " +
                "restart reads in that store holds it. It is in $elsewhere."
        )
    }

    /**
     * Hands the caches to the service and waits until [cleared] holds.
     *
     * `CMD_CLEAR_CACHE` hands the work to a coroutine and answers immediately, so its success code
     * says nothing about whether anything has been emptied yet.
     *
     * `OpenRadioServicePresenterImpl.clear` runs its four steps in order: the persistent API cache,
     * the in-memory one, the stored images, then the latest station. A [cleared] that reads the
     * latest station is therefore a signal that the whole operation finished rather than only the
     * step being watched, and each of the other three can then be asserted on its own rather than
     * inferred from that ordering.
     */
    fun clearCaches(browser: ServiceBrowser, cleared: () -> Boolean) {
        assertEquals(
            "The service refused the clear command",
            SessionResult.RESULT_SUCCESS,
            browser.command(OpenRadioService.CMD_CLEAR_CACHE).resultCode
        )
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CLEAR_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (cleared()) {
                return
            }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("CMD_CLEAR_CACHE did not finish within $CLEAR_TIMEOUT_SECONDS seconds")
    }

    private companion object {

        const val PREFERENCE_DIRECTORY = "shared_prefs"

        const val PREFERENCE_FILE_SUFFIX = ".xml"

        const val CLEAR_TIMEOUT_SECONDS = 10L

        /**
         * Covers the hop onto the thread `apply` writes from, and the write itself.
         */
        const val WRITE_TIMEOUT_SECONDS = 20L

        const val POLL_MILLIS = 50L
    }
}
