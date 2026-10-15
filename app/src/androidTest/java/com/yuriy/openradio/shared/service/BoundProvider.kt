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

import com.yuriy.openradio.shared.model.media.MediaId
import org.junit.Assert.assertTrue

/**
 * Asserts that this process bound the Radio Browser provider, given the ids the service offered
 * at the root.
 *
 * Fixtures are Radio Browser payloads keyed to Radio Browser URLs, and the journeys expect the
 * Radio Browser root menu, so a run against any other provider would answer nothing and say
 * nothing about why. Reading `SourcePreferences` would not tell: `ImagesProvider.onCreate` calls
 * `DependencyRegistryCommon.init`, which reads the selection once, and Android creates content
 * providers before the Application and before the instrumentation, so the URL layer, the parser
 * and the root command are bound before any test code has run at all. The preference only decides
 * what a future process will use. What was actually bound does show in the root menu, where the
 * two Radio Browser only nodes are added by the same `Source` value that chose the URL layer.
 *
 * An install left on WebRadio therefore stays on WebRadio for the whole run, and all a test can do
 * is assert what was bound. Clearing the app's data before the run is what avoids it, which is
 * why `AGENTS.md` asks for that. Getting a fresh process per test is TASK-031.
 */
internal fun assertRadioBrowserIsBound(rootIds: List<String>) {
    assertTrue(
        "This process bound a provider other than Radio Browser, so fixtures keyed to Radio " +
            "Browser URLs would silently answer nothing. It read that from SourcePreferences " +
            "before any test ran, and nothing here can change it, so the device was carrying a " +
            "stored selection into the run. Clear the app's data first, as AGENTS.md describes. " +
            "Root offered $rootIds",
        rootIds.containsAll(listOf(MediaId.MEDIA_ID_NEW_STATIONS, MediaId.MEDIA_ID_POPULAR_STATIONS))
    )
}
