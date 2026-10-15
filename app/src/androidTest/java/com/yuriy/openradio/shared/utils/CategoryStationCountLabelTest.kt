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

package com.yuriy.openradio.shared.utils

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.Category
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The station count under a category in the browse tree, as [MediaItemBuilder.buildChildCategory]
 * labels it from the app's real resources.
 *
 * The label is a plurals resource, so the form is picked by the plural rules of the locale the
 * resources are read in. On the JVM every resource resolves to the same placeholder, which cannot
 * tell one form from another. The counts 1, 2 and 5 fall in three different plural categories in a
 * language such as Polish (one, few, many), so they are the counts a translation has to get right.
 * The context is pinned to English, the only language the app ships strings for, so the expected
 * words do not move with the language of the device.
 */
@RunWith(AndroidJUnit4::class)
class CategoryStationCountLabelTest {

    private lateinit var mContext: Context

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration)
        configuration.setLocale(Locale.US)
        mContext = targetContext.createConfigurationContext(configuration)
    }

    @Test
    fun oneStationIsLabelledInTheSingular() {
        assertEquals("1 radio station", subtitleFor(1))
    }

    @Test
    fun twoStationsAreLabelledInThePlural() {
        assertEquals("2 radio stations", subtitleFor(2))
    }

    @Test
    fun fiveStationsAreLabelledInThePlural() {
        assertEquals("5 radio stations", subtitleFor(5))
    }

    private fun subtitleFor(stationsCount: Int): String {
        val category = Category("jazz", "Jazz", stationsCount)
        return MediaItemBuilder.buildChildCategory(mContext, category).mediaMetadata.subtitle.toString()
    }
}
