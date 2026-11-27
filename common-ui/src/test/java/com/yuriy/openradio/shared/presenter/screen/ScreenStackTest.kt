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

package com.yuriy.openradio.shared.presenter.screen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStackTest {

    private val mScope = CoroutineScope(Dispatchers.Unconfined)
    private val mStack = ScreenStack()

    @After
    fun tearDown() {
        mScope.cancel()
    }

    @Test
    fun aNewStackIsEmptyAndShowsTheMainScreen() {
        assertNull(mStack.top.value)
        assertEquals(emptyList<Destination>(), mStack.snapshot())
    }

    @Test
    fun pushShowsTheDestinationOnTop() {
        mStack.push(Destination.Settings)
        mStack.push(Destination.SettingsChoice(ChoiceKind.SOURCE))

        assertEquals(Destination.SettingsChoice(ChoiceKind.SOURCE), mStack.top.value)
        assertEquals(
            listOf(Destination.Settings, Destination.SettingsChoice(ChoiceKind.SOURCE)),
            mStack.snapshot()
        )
    }

    @Test
    fun popReturnsToTheDestinationBelow() {
        mStack.push(Destination.Settings)
        mStack.push(Destination.UserAgent)

        assertTrue(mStack.pop())
        assertEquals(Destination.Settings, mStack.top.value)
    }

    @Test
    fun popOnAnEmptyStackDoesNothingAndSaysSo() {
        assertFalse(mStack.pop())
        assertNull(mStack.top.value)
    }

    @Test
    fun aSecondPushOfTheDestinationOnTopIsIgnored() {
        mStack.push(Destination.Settings)
        mStack.push(Destination.Settings)

        assertEquals(listOf(Destination.Settings), mStack.snapshot())
    }

    @Test
    fun theSameDestinationMayBePushedAgainWhenItIsNotOnTop() {
        mStack.push(Destination.About)
        mStack.push(Destination.Settings)
        mStack.push(Destination.About)

        assertEquals(
            listOf(Destination.About, Destination.Settings, Destination.About),
            mStack.snapshot()
        )
    }

    @Test
    fun aboutPushedAloneReturnsToTheMainScreen() {
        mStack.push(Destination.About)

        assertTrue(mStack.pop())
        assertNull(mStack.top.value)
    }

    @Test
    fun backPopsTheStackBeforeAskingTheMainScreen() {
        mStack.push(Destination.Settings)
        var calls = 0

        assertTrue(mStack.backThen { calls++; true })

        assertEquals(0, calls)
        assertNull(mStack.top.value)
    }

    @Test
    fun backOnAnEmptyStackAsksTheMainScreenAndPassesItsAnswerOn() {
        var calls = 0

        assertFalse(mStack.backThen { calls++; false })
        assertTrue(mStack.backThen { calls++; true })

        assertEquals(2, calls)
    }

    @Test
    fun everyListenerHearsWhichDestinationLeft() {
        val first = mutableListOf<Destination>()
        val second = mutableListOf<Destination>()
        mStack.addLeaveListener { first.add(it) }
        mStack.addLeaveListener { second.add(it) }
        mStack.push(Destination.Settings)
        mStack.push(Destination.StreamBuffering)

        mStack.pop()

        assertEquals(listOf(Destination.StreamBuffering), first)
        assertEquals(listOf(Destination.StreamBuffering), second)
    }

    @Test
    fun aPushIsNotALeave() {
        val left = mutableListOf<Destination>()
        mStack.addLeaveListener { left.add(it) }

        mStack.push(Destination.Settings)
        mStack.push(Destination.UserAgent)

        assertEquals(emptyList<Destination>(), left)
    }

    /**
     * A listener that puts a setting back, the source, must have done so before the screen below
     * reads the stores, and that screen reads them when it sees the new top.
     */
    @Test
    fun theListenersRunBeforeTheNewTopIsPublished() {
        val events = mutableListOf<String>()
        mScope.launch {
            mStack.top.collect { events.add("top:$it") }
        }
        mStack.addLeaveListener { events.add("leave:$it") }

        mStack.push(Destination.Settings)
        mStack.push(Destination.About)
        mStack.pop()

        assertEquals(
            listOf("top:null", "top:Settings", "top:About", "leave:About", "top:Settings"),
            events
        )
    }

    @Test
    fun clearPopsEveryDestinationTopFirstAndTellsTheListeners() {
        val left = mutableListOf<Destination>()
        mStack.addLeaveListener { left.add(it) }
        mStack.push(Destination.Settings)
        mStack.push(Destination.UserAgent)

        mStack.clear()

        assertEquals(listOf(Destination.UserAgent, Destination.Settings), left)
        assertNull(mStack.top.value)
        assertEquals(emptyList<Destination>(), mStack.snapshot())
    }

    @Test
    fun clearOnAnEmptyStackTellsNobody() {
        val left = mutableListOf<Destination>()
        mStack.addLeaveListener { left.add(it) }

        mStack.clear()

        assertEquals(emptyList<Destination>(), left)
    }
}
