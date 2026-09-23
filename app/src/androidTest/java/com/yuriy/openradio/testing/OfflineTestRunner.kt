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

package com.yuriy.openradio.testing

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * The instrumentation runner of the app's test APK: `AndroidJUnitRunner` with
 * [OfflineDeviceRunnerBuilder] always installed.
 *
 * The builder is added here rather than through `testInstrumentationRunnerArguments`, because
 * arguments are something a launcher passes and can leave out, while the runner class is part of
 * the test APK's manifest and holds however the run was started.
 */
class OfflineTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(withOfflineGuard(arguments ?: Bundle()))
    }

    internal companion object {

        /**
         * The runner argument `AndroidJUnitRunner` reads custom builders from, as a comma separated
         * list of class names.
         */
        const val RUNNER_BUILDER_ARGUMENT = "runnerBuilder"

        /**
         * @return a copy of [arguments] whose builder list starts with [OfflineDeviceRunnerBuilder],
         *   ahead of any builder the launcher passed, so that no other builder can claim a class
         *   before the guard has seen it.
         */
        fun withOfflineGuard(arguments: Bundle): Bundle {
            val builders = listOf(OfflineDeviceRunnerBuilder::class.java.name) +
                arguments.getString(RUNNER_BUILDER_ARGUMENT).orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != OfflineDeviceRunnerBuilder::class.java.name }
            return Bundle(arguments).apply {
                putString(RUNNER_BUILDER_ARGUMENT, builders.joinToString(","))
            }
        }
    }
}
