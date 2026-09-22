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

package com.yuriy.openradio.shared.permission

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/**
 * Grants the permission the add and edit dialogs ask for when they resume, so that the request
 * does not put a system prompt over the Activity a test is reading. Picking an image for a station
 * is not what those tests are about, and neither dialog reaches the picker in them.
 *
 * Nothing is done below API 28, where `grantRuntimePermission` is not available.
 */
internal fun grantImageReadPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        return
    }
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName, permission
    )
    assertTrue(
        "The image read permission could not be granted, so the dialogs will ask for it and put " +
            "a system prompt over the Activity under test",
        PermissionChecker.isExternalStorageGranted(context)
    )
}
