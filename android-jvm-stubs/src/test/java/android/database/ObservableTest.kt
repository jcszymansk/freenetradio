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

package android.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ObservableTest {

    private val mObservable = StringObservable()

    @Test
    fun registeredObserversAreVisibleToSubclassesInOrder() {
        mObservable.registerObserver("first")
        mObservable.registerObserver("second")

        assertEquals(listOf("first", "second"), mObservable.observers())
    }

    @Test
    fun unregisterDropsOnlyTheNamedObserver() {
        mObservable.registerObserver("first")
        mObservable.registerObserver("second")

        mObservable.unregisterObserver("first")

        assertEquals(listOf("second"), mObservable.observers())
    }

    @Test
    fun unregisterAllEmptiesTheList() {
        mObservable.registerObserver("first")
        mObservable.registerObserver("second")

        mObservable.unregisterAll()

        assertEquals(emptyList<String>(), mObservable.observers())
    }

    @Test
    fun registeringTheSameObserverTwiceIsRejected() {
        mObservable.registerObserver("first")

        assertThrows(IllegalStateException::class.java) {
            mObservable.registerObserver("first")
        }
        assertEquals(listOf("first"), mObservable.observers())
    }

    @Test
    fun unregisteringAnUnknownObserverIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            mObservable.unregisterObserver("first")
        }
    }

    @Test
    fun aNullObserverIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            mObservable.registerObserver(null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            mObservable.unregisterObserver(null)
        }
    }

    /**
     * Reads the list through the protected field, which is how the framework's own subclasses,
     * `RecyclerView.AdapterDataObservable` among them, reach it.
     */
    private class StringObservable : Observable<String?>() {

        fun observers(): List<String?> = mObservers.toList()
    }
}
