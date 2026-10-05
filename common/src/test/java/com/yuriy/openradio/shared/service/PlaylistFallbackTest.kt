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
import com.yuriy.openradio.shared.service.PlaylistFallback.Resolution
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
 * named failing never leads to reading it again, and a read only answers for the request that
 * asked for it.
 */
class PlaylistFallbackTest {

    @Test
    fun idleStartsResolvingTheStationThePlayerCouldNotRecognise() {
        val step = Idle().onUnrecognized(STATION)

        assertEquals(Step(Resolving(FIRST_READ), Action.Resolve(FIRST_READ)), step)
    }

    @Test
    fun idleIgnoresAResolutionNoRequestAskedFor() {
        val step = Idle().onResolved(FIRST_READ, listOf(URL_A))

        assertEquals(Step(Idle(), Action.Ignore), step)
    }

    @Test
    fun eachReadGetsTheNumberAfterTheLastOneAskedFor() {
        val step = Idle(resolutions = 41).onUnrecognized(STATION)

        val read = Resolution(STATION, 42)
        assertEquals(Step(Resolving(read), Action.Resolve(read)), step)
    }

    @Test
    fun resolvingPlaysTheFirstResolvedUrlAndKeepsTheRestInOrder() {
        val step = Resolving(FIRST_READ).onResolved(FIRST_READ, listOf(URL_A, URL_B, URL_C))

        assertEquals(Step(Trying(STATION, listOf(URL_B, URL_C), 1), Action.Play(STATION, URL_A)), step)
    }

    @Test
    fun resolvingASingleUrlPlaysItWithNothingLeft() {
        val step = Resolving(FIRST_READ).onResolved(FIRST_READ, listOf(URL_A))

        assertEquals(Step(Trying(STATION, emptyList(), 1), Action.Play(STATION, URL_A)), step)
    }

    @Test
    fun resolvingAnEmptyPlaylistGivesUp() {
        val step = Resolving(FIRST_READ).onResolved(FIRST_READ, emptyList())

        assertEquals(Step(Exhausted(STATION, 1), Action.GiveUp(STATION)), step)
    }

    @Test
    fun resolvingIgnoresTheReadOfAnotherStation() {
        val step = Resolving(FIRST_READ).onResolved(Resolution(OTHER_STATION, 1), listOf(URL_A))

        assertEquals(Step(Resolving(FIRST_READ), Action.Ignore), step)
    }

    @Test
    fun resolvingIgnoresAnotherReadOfTheSameStation() {
        val waiting = Resolving(Resolution(STATION, 2))

        val step = waiting.onResolved(FIRST_READ, listOf(URL_A))

        assertEquals(Step(waiting, Action.Ignore), step)
    }

    @Test
    fun resolvingIgnoresARepeatedFailureOfTheSameStation() {
        val step = Resolving(FIRST_READ).onUnrecognized(STATION)

        assertEquals(Step(Resolving(FIRST_READ), Action.Ignore), step)
    }

    @Test
    fun resolvingIgnoresEveryRepeatedFailureOfTheSameStation() {
        val (state, actions) = fold(
            Resolving(FIRST_READ),
            Unrecognized(STATION),
            Unrecognized(STATION),
            Unrecognized(STATION),
        )

        assertEquals(Resolving(FIRST_READ), state)
        assertEquals(listOf(Action.Ignore, Action.Ignore, Action.Ignore), actions)
    }

    @Test
    fun resolvingStartsOverForAFailureOfAnotherStation() {
        val step = Resolving(FIRST_READ).onUnrecognized(OTHER_STATION)

        val read = Resolution(OTHER_STATION, 2)
        assertEquals(Step(Resolving(read), Action.Resolve(read)), step)
    }

    @Test
    fun resolvingAbandonedForAnotherStationIgnoresTheLateReadOfTheFirst() {
        val (state, actions) = fold(
            Idle(),
            Unrecognized(STATION),
            Unrecognized(OTHER_STATION),
            Resolved(FIRST_READ, listOf(URL_A)),
        )

        val otherRead = Resolution(OTHER_STATION, 2)
        assertEquals(Resolving(otherRead), state)
        assertEquals(
            listOf(Action.Resolve(FIRST_READ), Action.Resolve(otherRead), Action.Ignore),
            actions,
        )
    }

    /**
     * A station left for another and returned to within one request is read again, and the read
     * it was abandoned with must not answer for the new one.
     */
    @Test
    fun aStationReturnedToHearsOnlyItsLatestRead() {
        val latest = Resolution(STATION, 3)
        val (state, actions) = fold(
            Idle(),
            Unrecognized(STATION),
            Unrecognized(OTHER_STATION),
            Unrecognized(STATION),
            Resolved(FIRST_READ, listOf(URL_A)),
            Resolved(latest, listOf(URL_B)),
        )

        assertEquals(Trying(STATION, emptyList(), 3), state)
        assertEquals(
            listOf(
                Action.Resolve(FIRST_READ),
                Action.Resolve(Resolution(OTHER_STATION, 2)),
                Action.Resolve(latest),
                Action.Ignore,
                Action.Play(STATION, URL_B),
            ),
            actions,
        )
    }

    @Test
    fun tryingPlaysTheNextUrlWhenThePlayedOneFails() {
        val step = Trying(STATION, listOf(URL_B, URL_C), 1).onUnrecognized(STATION)

        assertEquals(Step(Trying(STATION, listOf(URL_C), 1), Action.Play(STATION, URL_B)), step)
    }

    @Test
    fun tryingGivesUpWhenNoUrlIsLeft() {
        val step = Trying(STATION, emptyList(), 1).onUnrecognized(STATION)

        assertEquals(Step(Exhausted(STATION, 1), Action.GiveUp(STATION)), step)
    }

    @Test
    fun tryingPlaysEveryResolvedUrlInOrderAndThenGivesUpOnce() {
        val (state, actions) = fold(
            Resolving(FIRST_READ),
            Resolved(FIRST_READ, listOf(URL_A, URL_B, URL_C)),
            Unrecognized(STATION),
            Unrecognized(STATION),
            Unrecognized(STATION),
        )

        assertEquals(Exhausted(STATION, 1), state)
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
    fun tryingIgnoresARepeatOfItsOwnRead() {
        val trying = Trying(STATION, listOf(URL_B), 1)

        val step = trying.onResolved(FIRST_READ, listOf(URL_C))

        assertEquals(Step(trying, Action.Ignore), step)
    }

    @Test
    fun tryingIgnoresTheReadOfAnotherStation() {
        val trying = Trying(STATION, listOf(URL_B), 1)

        val step = trying.onResolved(Resolution(OTHER_STATION, 2), listOf(URL_C))

        assertEquals(Step(trying, Action.Ignore), step)
    }

    @Test
    fun tryingStartsOverForAFailureOfAnotherStation() {
        val step = Trying(STATION, listOf(URL_B), 1).onUnrecognized(OTHER_STATION)

        val read = Resolution(OTHER_STATION, 2)
        assertEquals(Step(Resolving(read), Action.Resolve(read)), step)
    }

    @Test
    fun exhaustedGivesUpAgainWithoutResolvingTheSameStation() {
        val step = Exhausted(STATION, 1).onUnrecognized(STATION)

        assertEquals(Step(Exhausted(STATION, 1), Action.GiveUp(STATION)), step)
    }

    @Test
    fun exhaustedIgnoresARead() {
        val step = Exhausted(STATION, 1).onResolved(FIRST_READ, listOf(URL_A))

        assertEquals(Step(Exhausted(STATION, 1), Action.Ignore), step)
    }

    @Test
    fun exhaustedStartsOverForAFailureOfAnotherStation() {
        val step = Exhausted(STATION, 1).onUnrecognized(OTHER_STATION)

        val read = Resolution(OTHER_STATION, 2)
        assertEquals(Step(Resolving(read), Action.Resolve(read)), step)
    }

    @Test
    fun aNewRequestStartsIdleAndKeepsTheReadCount() {
        val states = listOf(
            Idle(4),
            Resolving(Resolution(STATION, 4)),
            Trying(STATION, listOf(URL_A), 4),
            Exhausted(STATION, 4),
        )

        for (state in states) {
            assertEquals(state.toString(), Idle(4), state.newRequest())
        }
    }

    @Test
    fun aNewRequestReadsAnExhaustedStationAgain() {
        val step = Exhausted(STATION, 1).newRequest().onUnrecognized(STATION)

        val read = Resolution(STATION, 2)
        assertEquals(Step(Resolving(read), Action.Resolve(read)), step)
    }

    /**
     * The race review found in the first version, which matched a read by its media id: a request
     * for the same station started while its first read was under way took that first read as its
     * own, and then ignored the read it had asked for.
     */
    @Test
    fun aReadStartedForAnEarlierRequestCannotAnswerForTheNextOne() {
        val (start, _) = fold(Idle(), Unrecognized(STATION))
        val secondRead = Resolution(STATION, 2)

        val (state, actions) = fold(
            start.newRequest(),
            Unrecognized(STATION),
            Resolved(FIRST_READ, listOf(URL_A)),
            Resolved(secondRead, listOf(URL_B)),
        )

        assertEquals(Trying(STATION, emptyList(), 2), state)
        assertEquals(
            listOf(Action.Resolve(secondRead), Action.Ignore, Action.Play(STATION, URL_B)),
            actions,
        )
    }

    @Test
    fun aPlaylistWhoseOnlyStreamKeepsFailingIsResolvedExactlyOnce() {
        val failures = List(10) { Unrecognized(STATION) }
        val events = listOf(failures.first(), Resolved(FIRST_READ, listOf(URL_A))) + failures.drop(1)

        val (state, actions) = fold(Idle(), *events.toTypedArray())

        assertEquals(Exhausted(STATION, 1), state)
        assertEquals(1, actions.count { it is Action.Resolve })
        assertEquals(1, actions.count { it is Action.Play })
        assertEquals(
            listOf(Action.Resolve(FIRST_READ), Action.Play(STATION, URL_A)) +
                List(9) { Action.GiveUp(STATION) },
            actions,
        )
    }

    @Test
    fun duplicateUrlsInAPlaylistAreEachTried() {
        val (state, actions) = fold(
            Idle(),
            Unrecognized(STATION),
            Resolved(FIRST_READ, listOf(URL_A, URL_A, URL_B)),
            Unrecognized(STATION),
            Unrecognized(STATION),
            Unrecognized(STATION),
        )

        assertEquals(Exhausted(STATION, 1), state)
        assertEquals(
            listOf(
                Action.Resolve(FIRST_READ),
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
        val otherRead = Resolution(OTHER_STATION, 2)
        val (state, actions) = fold(
            Idle(),
            Unrecognized(STATION),
            Resolved(FIRST_READ, emptyList()),
            Unrecognized(OTHER_STATION),
            Resolved(otherRead, listOf(URL_C)),
            Unrecognized(OTHER_STATION),
            Unrecognized(OTHER_STATION),
        )

        assertEquals(Exhausted(OTHER_STATION, 2), state)
        assertEquals(
            listOf(
                Action.Resolve(FIRST_READ),
                Action.GiveUp(STATION),
                Action.Resolve(otherRead),
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

    private data class Resolved(val resolution: Resolution, val urls: List<String>) : Event() {

        override fun applyTo(state: PlaylistFallback) = state.onResolved(resolution, urls)
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

        val FIRST_READ = Resolution(STATION, 1)
    }
}
