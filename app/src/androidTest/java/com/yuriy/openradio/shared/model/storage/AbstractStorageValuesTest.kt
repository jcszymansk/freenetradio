package com.yuriy.openradio.shared.model.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

/**
 * Covers the typed accessors every preference backed store inherits from [AbstractStorage],
 * including what they do for values that were written under an incompatible type and for a
 * context that is no longer reachable.
 */
@RunWith(AndroidJUnit4::class)
class AbstractStorageValuesTest {

    private lateinit var mContext: Context
    private lateinit var mStorage: TestStorage

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mStorage = newStorage()
        mStorage.clear()
    }

    @After
    fun tearDown() {
        mStorage.clear()
    }

    @Test
    fun absentKeysReportTheProvidedDefaults() {
        assertEquals(7, mStorage.getIntValue("int", 7))
        assertEquals(7L, mStorage.getLongValue("long", 7L))
        assertEquals("default", mStorage.getStringValue("string", "default"))
        assertTrue(mStorage.getBooleanValue("boolean", true))
        assertFalse(mStorage.getBooleanValue("boolean", false))
    }

    @Test
    fun writtenValuesAreReadBackByANewInstance() {
        mStorage.putIntValue("int", 11)
        mStorage.putLongValue("long", 12L)
        mStorage.putStringValue("string", "value")
        mStorage.putBooleanValue("boolean", true)

        val reloaded = newStorage()
        assertEquals(11, reloaded.getIntValue("int", 0))
        assertEquals(12L, reloaded.getLongValue("long", 0L))
        assertEquals("value", reloaded.getStringValue("string", "default"))
        assertTrue(reloaded.getBooleanValue("boolean", false))
    }

    @Test
    fun clearDropsEveryStoredValue() {
        mStorage.putIntValue("int", 11)
        mStorage.putStringValue("string", "value")

        mStorage.clear()

        assertEquals(0, mStorage.getIntValue("int", 0))
        assertEquals("default", newStorage().getStringValue("string", "default"))
    }

    @Test
    fun readingAValueUnderAnIncompatibleTypeFails() {
        mStorage.putStringValue("key", "not a number")

        assertThrows(ClassCastException::class.java) { mStorage.getIntValue("key", 0) }
        assertThrows(ClassCastException::class.java) { mStorage.getLongValue("key", 0L) }
        assertThrows(ClassCastException::class.java) { mStorage.getBooleanValue("key", false) }
    }

    @Test
    fun aCollectedContextYieldsDefaultsAndSwallowsWrites() {
        val detached = TestStorage(WeakReference<Context>(null))

        detached.putStringValue("string", "value")

        assertEquals("default", detached.getStringValue("string", "default"))
        assertEquals(5, detached.getIntValue("int", 5))
        assertEquals(5L, detached.getLongValue("long", 5L))
        assertTrue(detached.getBooleanValue("boolean", true))
        assertEquals("default", newStorage().getStringValue("string", "default"))
    }

    private fun newStorage(): TestStorage {
        return TestStorage(WeakReference(mContext))
    }

    private class TestStorage(contextRef: WeakReference<Context>) :
        AbstractStorage(contextRef, "AbstractStorageValuesTestPreferences")
}
