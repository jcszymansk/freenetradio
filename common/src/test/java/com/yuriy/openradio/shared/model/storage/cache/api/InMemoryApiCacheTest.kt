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

package com.yuriy.openradio.shared.model.storage.cache.api

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Every [InMemoryApiCache] shares one process-wide map, so each test starts and ends with it
 * empty: a test that failed halfway must not hand its entries to the next one.
 */
class InMemoryApiCacheTest {

    private val mCache = InMemoryApiCache()

    @Before
    fun setUp() {
        mCache.clear()
    }

    @After
    fun tearDown() {
        mCache.clear()
    }

    @Test
    fun storesReplacesRemovesAndClearsEntries() {
        assertEquals("", mCache["missing"])

        mCache.put("key", "first")
        assertEquals("first", mCache["key"])

        mCache.put("key", "second")
        assertEquals("second", mCache["key"])

        mCache.remove("key")
        assertEquals("", mCache["key"])

        mCache.put("one", "1")
        mCache.put("two", "2")
        mCache.clear()
        assertEquals("", mCache["one"])
        assertEquals("", mCache["two"])
    }

    @Test
    fun everyInstanceSeesTheSameEntries() {
        mCache.put("key", "shared")

        assertEquals("shared", InMemoryApiCache()["key"])
    }

    /**
     * What the cache answers for the empty key is the whole observable contract: the map is
     * private and has no size or iteration, so whether `put` and `remove` refuse the empty key or
     * `get` refuses to answer it cannot be told apart from outside, and dropping one of those
     * guards would leave this test green. The other entries are here to show that touching the
     * empty key leaves the rest of the cache alone.
     */
    @Test
    fun theEmptyKeyIsNeverAnsweredAndDisturbsNothing() {
        mCache.put("kept", "data")

        mCache.put("", "data")
        assertEquals("", mCache[""])
        assertEquals("data", mCache["kept"])

        mCache.remove("")
        assertEquals("", mCache[""])
        assertEquals("data", mCache["kept"])
    }
}
