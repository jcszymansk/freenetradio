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

package android.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleTest {

    @Test
    fun valuesRoundTripPerType() {
        val bundle = Bundle()

        bundle.putString("name", "Station")
        bundle.putInt("bitrate", 128)
        bundle.putBoolean("favorite", true)

        assertEquals("Station", bundle.getString("name"))
        assertEquals(128, bundle.getInt("bitrate"))
        assertTrue(bundle.getBoolean("favorite"))
        assertEquals(3, bundle.size())
        assertEquals(setOf("name", "bitrate", "favorite"), bundle.keySet())
        assertFalse(bundle.isEmpty())
    }

    @Test
    fun missingKeysFallBackToTheGivenDefault() {
        val bundle = Bundle()

        assertNull(bundle.getString("name"))
        assertEquals("fallback", bundle.getString("name", "fallback"))
        assertEquals(0, bundle.getInt("bitrate"))
        assertEquals(-1, bundle.getInt("bitrate", -1))
        assertFalse(bundle.getBoolean("favorite"))
        assertTrue(bundle.getBoolean("favorite", true))
        assertFalse(bundle.containsKey("name"))
        assertTrue(bundle.isEmpty())
    }

    @Test
    fun readingAKeyAsTheWrongTypeYieldsTheDefault() {
        val bundle = Bundle()

        bundle.putString("bitrate", "128")

        assertEquals(-1, bundle.getInt("bitrate", -1))
        assertTrue(bundle.containsKey("bitrate"))
    }

    @Test
    fun writingTheSameKeyTwiceKeepsTheLastValue() {
        val bundle = Bundle()

        bundle.putInt("sortId", 1)
        bundle.putInt("sortId", 2)

        assertEquals(2, bundle.getInt("sortId"))
        assertEquals(1, bundle.size())
    }

    @Test
    fun nullStringsAreStoredAndReadBackAsNull() {
        val bundle = Bundle()

        bundle.putString("name", null)

        assertTrue(bundle.containsKey("name"))
        assertNull(bundle.getString("name"))
        assertEquals("fallback", bundle.getString("name", "fallback"))
    }
}
