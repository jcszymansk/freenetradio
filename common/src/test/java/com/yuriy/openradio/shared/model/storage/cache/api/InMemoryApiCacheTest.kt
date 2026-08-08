package com.yuriy.openradio.shared.model.storage.cache.api

import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryApiCacheTest {

    @Test
    fun storesReplacesRemovesAndClearsEntries() {
        val cache = InMemoryApiCache()
        cache.clear()

        assertEquals("", cache["missing"])

        cache.put("key", "first")
        assertEquals("first", cache["key"])

        cache.put("key", "second")
        assertEquals("second", cache["key"])

        cache.remove("key")
        assertEquals("", cache["key"])

        cache.put("one", "1")
        cache.put("two", "2")
        cache.clear()
        assertEquals("", cache["one"])
        assertEquals("", cache["two"])
    }

    @Test
    fun ignoresEmptyKeys() {
        val cache = InMemoryApiCache()
        cache.clear()

        cache.put("", "data")
        assertEquals("", cache[""])
        cache.remove("")
        assertEquals("", cache[""])
    }
}
