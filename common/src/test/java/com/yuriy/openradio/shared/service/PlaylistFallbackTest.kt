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

import androidx.media3.common.Player
import com.yuriy.openradio.shared.service.PlaylistFallback.Action
import com.yuriy.openradio.shared.service.PlaylistFallback.Exhausted
import com.yuriy.openradio.shared.service.PlaylistFallback.Idle
import com.yuriy.openradio.shared.service.PlaylistFallback.Resolving
import com.yuriy.openradio.shared.service.PlaylistFallback.Step
import com.yuriy.openradio.shared.service.PlaylistFallback.Trying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state machine that decides, for one play request, when a station playlist is read and which
 * of the streams it names is played next. A playlist is read at most once per request: a stream it
 * named failing never leads to reading it again.
 */
class PlaylistFallbackTest {

    @Test
    fun idleStartsResolvingTheStationThePlayerCouldNotRecognise() {
        val step = Idle.onUnrecognized(STATION)

        assertEquals(Step(Resolving(STATION), Action.Resolve(STATION)), step)
    }

    @Test
    fun idleIgnoresAResolutionNoRequestAskedFor() {
        val step = Idle.onResolved(STATION, listOf(URL_A))

        assertEquals(Step(Idle, Action.Ignore), step)
    }

    @Test
    fun resolvingPlaysTheFirstResolvedUrlAndKeepsTheRestInOrder() {
        val step = Resolving(STATION).onResolved(STATION, listOf(URL_A, URL_B, URL_C))

        assertEquals(Step(Trying(STATION, listOf(URL_B, URL_C)), Action.Play(STATION, URL_A)), step)
    }

    @Test
    fun resolvingASingleUrlPlaysItWithNothingLeft() {
        val step = Resolving(STATION).onResolved(STATION, listOf(URL_A))

        assertEquals(Step(Trying(STATION, emptyList()), Action.Play(STATION, URL_A)), step)
    }

    @Test
    fun resolvingAnEmptyPlaylistGivesUp() {
        val step = Resolving(STATION).onResolved(STATION, emptyList())

        assertEquals(Step(Exhausted(STATION), Action.GiveUp(STATION)), step)
    }

    @Test
    fun resolvingIgnoresTheResolutionOfAnotherStation() {
        val step = Resolving(STATION).onResolved(OTHER_STATION, listOf(URL_A))

        assertEquals(Step(Resolving(STATION), Action.Ignore), step)
    }

    @Test
    fun resolvingIgnoresARepeatedFailureOfTheSameStation() {
        val step = Resolving(STATION).onUnrecognized(STATION)

        assertEquals(Step(Resolving(STATION), Action.Ignore), step)
    }

    @Test
    fun resolvingIgnoresEveryRepeatedFailureOfTheSameStation() {
        val (state, actions) = fold(
            Resolving(STATION),
            Unrecognized(STATION),
            Unrecognized(STATION),
            Unrecognized(STATION),
        )

        assertEquals(Resolving(STATION), state)
        assertEquals(listOf(Action.Ignore, Action.Ignore, Action.Ignore), actions)
    }

    @Test
    fun resolvingStartsOverForAFailureOfAnotherStation() {
        val step = Resolving(STATION).onUnrecognized(OTHER_STATION)

        assertEquals(Step(Resolving(OTHER_STATION), Action.Resolve(OTHER_STATION)), step)
    }

    @Test
    fun resolvingAbandonedForAnotherStationIgnoresTheLateResolutionOfTheFirst() {
        val (state, actions) = fold(
            Idle,
            Unrecognized(STATION),
            Unrecognized(OTHER_STATION),
            Resolved(STATION, listOf(URL_A)),
        )

        assertEquals(Resolving(OTHER_STATION), state)
        assertEquals(
            listOf(Action.Resolve(STATION), Action.Resolve(OTHER_STATION), Action.Ignore),
            actions,
        )
    }

    @Test
    fun tryingPlaysTheNextUrlWhenThePlayedOneFails() {
        val step = Trying(STATION, listOf(URL_B, URL_C)).onUnrecognized(STATION)

        assertEquals(Step(Trying(STATION, listOf(URL_C)), Action.Play(STATION, URL_B)), step)
    }

    @Test
    fun tryingGivesUpWhenNoUrlIsLeft() {
        val step = Trying(STATION, emptyList()).onUnrecognized(STATION)

        assertEquals(Step(Exhausted(STATION), Action.GiveUp(STATION)), step)
    }

    @Test
    fun tryingPlaysEveryResolvedUrlInOrderAndThenGivesUpOnce() {
        val (state, actions) = fold(
            Resolving(STATION),
            Resolved(STATION, listOf(URL_A, URL_B, URL_C)),
            Unrecognized(STATION),
            Unrecognized(STATION),
            Unrecognized(STATION),
        )

        assertEquals(Exhausted(STATION), state)
        assertEquals(
            listOf(
                Action.Play(STATION, URL_A),
                Action.Play(STATION, URL_B),
                Action.Play(STATION, URL_C),
                Action.GiveUp(STATION),
            ),
            actions,
        )
    }

    @Test
    fun tryingIgnoresAResolutionOfTheSameStation() {
        val trying = Trying(STATION, listOf(URL_B))

        val step = trying.onResolved(STATION, listOf(URL_C))

        assertEquals(Step(trying, Action.Ignore), step)
    }

    @Test
    fun tryingIgnoresAResolutionOfAnotherStation() {
        val trying = Trying(STATION, listOf(URL_B))

        val step = trying.onResolved(OTHER_STATION, listOf(URL_C))

        assertEquals(Step(trying, Action.Ignore), step)
    }

    @Test
    fun tryingStartsOverForAFailureOfAnotherStation() {
        val step = Trying(STATION, listOf(URL_B)).onUnrecognized(OTHER_STATION)

        assertEquals(Step(Resolving(OTHER_STATION), Action.Resolve(OTHER_STATION)), step)
    }

    @Test
    fun exhaustedGivesUpAgainWithoutResolvingTheSameStation() {
        val step = Exhausted(STATION).onUnrecognized(STATION)

        assertEquals(Step(Exhausted(STATION), Action.GiveUp(STATION)), step)
    }

    @Test
    fun exhaustedIgnoresAResolution() {
        val step = Exhausted(STATION).onResolved(STATION, listOf(URL_A))

        assertEquals(Step(Exhausted(STATION), Action.Ignore), step)
    }

    @Test
    fun exhaustedStartsOverForAFailureOfAnotherStation() {
        val step = Exhausted(STATION).onUnrecognized(OTHER_STATION)

        assertEquals(Step(Resolving(OTHER_STATION), Action.Resolve(OTHER_STATION)), step)
    }

    @Test
    fun aPlaylistWhoseOnlyStreamKeepsFailingIsResolvedExactlyOnce() {
        val failures = List(10) { Unrecognized(STATION) }
        val events = listOf(failures.first(), Resolved(STATION, listOf(URL_A))) + failures.drop(1)

        val (state, actions) = fold(Idle, *events.toTypedArray())

        assertEquals(Exhausted(STATION), state)
        assertEquals(1, actions.count { it is Action.Resolve })
        assertEquals(1, actions.count { it is Action.Play })
        assertEquals(
            listOf(Action.Resolve(STATION), Action.Play(STATION, URL_A)) +
                List(9) { Action.GiveUp(STATION) },
            actions,
        )
    }

    @Test
    fun duplicateUrlsInAPlaylistAreEachTried() {
        val (state, actions) = fold(
            Idle,
            Unrecognized(STATION),
            Resolved(STATION, listOf(URL_A, URL_A, URL_B)),
            Unrecognized(STATION),
            Unrecognized(STATION),
            Unrecognized(STATION),
        )

        assertEquals(Exhausted(STATION), state)
        assertEquals(
            listOf(
                Action.Resolve(STATION),
                Action.Play(STATION, URL_A),
                Action.Play(STATION, URL_A),
                Action.Play(STATION, URL_B),
                Action.GiveUp(STATION),
            ),
            actions,
        )
    }

    @Test
    fun anotherStationFailingAfterTheFirstWasExhaustedGetsItsOwnSingleResolution() {
        val (state, actions) = fold(
            Idle,
            Unrecognized(STATION),
            Resolved(STATION, emptyList()),
            Unrecognized(OTHER_STATION),
            Resolved(OTHER_STATION, listOf(URL_C)),
            Unrecognized(OTHER_STATION),
            Unrecognized(OTHER_STATION),
        )

        assertEquals(Exhausted(OTHER_STATION), state)
        assertEquals(
            listOf(
                Action.Resolve(STATION),
                Action.GiveUp(STATION),
                Action.Resolve(OTHER_STATION),
                Action.Play(OTHER_STATION, URL_C),
                Action.GiveUp(OTHER_STATION),
                Action.GiveUp(OTHER_STATION),
            ),
            actions,
        )
    }

    @Test
    fun everyCommandThatStartsStopsOrChangesPlaybackIsAPlayRequest() {
        val requests = mapOf(
            "COMMAND_PLAY_PAUSE" to Player.COMMAND_PLAY_PAUSE,
            "COMMAND_PREPARE" to Player.COMMAND_PREPARE,
            "COMMAND_STOP" to Player.COMMAND_STOP,
            "COMMAND_SEEK_TO_DEFAULT_POSITION" to Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            "COMMAND_SEEK_TO_PREVIOUS" to Player.COMMAND_SEEK_TO_PREVIOUS,
            "COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM" to Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            "COMMAND_SEEK_TO_NEXT" to Player.COMMAND_SEEK_TO_NEXT,
            "COMMAND_SEEK_TO_NEXT_MEDIA_ITEM" to Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            "COMMAND_SEEK_TO_MEDIA_ITEM" to Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            "COMMAND_SET_MEDIA_ITEM" to Player.COMMAND_SET_MEDIA_ITEM,
            "COMMAND_CHANGE_MEDIA_ITEMS" to Player.COMMAND_CHANGE_MEDIA_ITEMS,
        )

        for ((name, command) in requests) {
            assertTrue(name, PlaylistFallback.isPlayRequest(command))
        }
    }

    @Test
    fun commandsThatLeavePlaybackAloneAreNotPlayRequests() {
        val others = mapOf(
            "COMMAND_SET_VOLUME" to Player.COMMAND_SET_VOLUME,
            "COMMAND_GET_CURRENT_MEDIA_ITEM" to Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            "COMMAND_GET_METADATA" to Player.COMMAND_GET_METADATA,
            "COMMAND_SET_SHUFFLE_MODE" to Player.COMMAND_SET_SHUFFLE_MODE,
            "COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM" to Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            "COMMAND_INVALID" to Player.COMMAND_INVALID,
        )

        for ((name, command) in others) {
            assertFalse(name, PlaylistFallback.isPlayRequest(command))
        }
    }

    private sealed class Event {

        abstract fun applyTo(state: PlaylistFallback): Step
    }

    private data class Unrecognized(val mediaId: String) : Event() {

        override fun applyTo(state: PlaylistFallback) = state.onUnrecognized(mediaId)
    }

    private data class Resolved(val mediaId: String, val urls: List<String>) : Event() {

        override fun applyTo(state: PlaylistFallback) = state.onResolved(mediaId, urls)
    }

    /**
     * Feeds [events] to the machine starting at [start] and returns the final state together with
     * every action it asked for, in order.
     */
    private fun fold(start: PlaylistFallback, vararg events: Event): Pair<PlaylistFallback, List<Action>> {
        var state = start
        val actions = mutableListOf<Action>()
        for (event in events) {
            val step = event.applyTo(state)
            state = step.next
            actions.add(step.action)
        }
        return state to actions
    }

    private companion object {
        const val STATION = "station-1"
        const val OTHER_STATION = "station-2"
        const val URL_A = "http://stream.test/a"
        const val URL_B = "http://stream.test/b"
        const val URL_C = "http://stream.test/c"
    }
}
