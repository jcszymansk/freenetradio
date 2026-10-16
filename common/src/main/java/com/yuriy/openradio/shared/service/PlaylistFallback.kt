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
import com.yuriy.openradio.shared.utils.ResolvedStream

/**
 * How far one play request has got with a station whose url the player could not read.
 *
 * Such a url is usually a playlist. It is read once per play request, and the streams it names
 * are tried in the order it names them, because playlists commonly list mirrors of one stream.
 * Reading it again would only return the same streams, so when the last of them fails too the
 * request is over and the service gives up. A new play request starts over from [newRequest].
 *
 * A stream a playlist names without saying what it is, one whose type is unknown, is opened by the
 * player as a progressive stream, and it may be a playlist in its turn, HLS most often. When the
 * player cannot recognise it, it is read the way the station url was, and the streams it names are
 * tried before the ones that followed it. Each such url is read once per request, and at most
 * [MAX_STREAM_READS] of them, so a server that answers every read with a new address cannot keep
 * the request going.
 *
 * Every state belongs to the media item of one station, named by its media id. An error for any
 * other media item belongs to a different request.
 *
 * ```
 *         unrecognised(m)                 resolved(#n, [u1, u2, ...]), play u1
 *   Idle ───────────────▶ Resolving(#n) ────────────────────────────────────▶ Trying(m, u1, [u2, ...])
 *                              │                                               │        ▲
 *                              │ resolved(#n, []), give up                     └────────┘
 *                              ▼                                     unrecognised(m), play the next
 *                         Exhausted(m) ◀──────────────────────────────── Trying(m, uk, [])
 *                     unrecognised(m):          unrecognised(m), give up
 *                     give up again
 * ```
 *
 * The played stream u1 failing while its type is unknown, it has not been read in this request and
 * fewer than [MAX_STREAM_READS] streams have been, reads it instead of moving on:
 *
 * ```
 *                          unrecognised(m), read u1        resolved(#n, [v1, ...]), play v1
 *   Trying(m, u1, [u2, ...]) ─────────────▶ Resolving(#n, [u2, ...]) ──────▶ Trying(m, v1, [..., u2, ...])
 *                                                     │
 *                                                     │ resolved(#n, []), play u2
 *                                                     ▼
 *                                              Trying(m, u2, [...])
 * ```
 *
 * An unrecognised stream of any other media item, in any state, starts [Resolving] that one.
 *
 * Reading a playlist is part of playing the station, so it is subject to the mobile-network
 * policy. A read the policy stopped, and a read still under way when the policy pauses the
 * station, both end in [Idle]: the station resumes from its own url once a network it may use
 * connects, and that is a new reason to read its playlist.
 *
 * ```
 *                  read blocked(#n), pause for the policy
 *   Resolving(#n) ─────────────────────────────────────────▶ Idle
 *                  abandoned for a policy pause
 * ```
 *
 * Reading a playlist takes long enough for its station to be asked for again meanwhile, so each
 * read is a numbered [Resolution] and only the read a [Resolving] state is waiting for counts.
 * Matching the media id alone would let a read the user has since restarted answer for the
 * request that replaced it. The numbers carry over from one request to the next for that reason.
 *
 * Each state is immutable: an event answers with the next state and what the service has to do.
 */
sealed class PlaylistFallback {

    /**
     * How many playlist reads this machine has asked for, so that the next one gets a number no
     * earlier read had.
     */
    abstract val resolutions: Long

    /**
     * Reacts to the player failing to recognise the stream of the media item [mediaId].
     */
    abstract fun onUnrecognized(mediaId: String): Step

    /**
     * Reacts to the playlist read [resolution] having returned [streams], in the order the
     * playlist names them.
     */
    open fun onResolved(resolution: Resolution, streams: List<ResolvedStream>): Step {
        return Step(this, Action.Ignore)
    }

    /**
     * Reacts to the playlist read [resolution] having been stopped, before it could finish,
     * because the network may not be used for playback.
     */
    open fun onReadBlocked(resolution: Resolution): Step {
        return Step(this, Action.Ignore)
    }

    /**
     * Whether a playlist read is under way for the current play request.
     */
    open val isReadPending: Boolean
        get() = false

    /**
     * The state that follows the mobile-network policy pausing the station. A read under way is
     * abandoned: whatever it answers later is ignored, and the resumed station reads its playlist
     * again. Streams a playlist has already named stay, because the station resumes with the one
     * it was playing.
     */
    open fun abandonRead(): PlaylistFallback {
        return this
    }

    /**
     * The state a new play request starts in: nothing resolved for it, and no earlier read able
     * to answer for it.
     */
    fun newRequest(): PlaylistFallback {
        return Idle(resolutions)
    }

    /**
     * Nothing has been resolved for the current play request.
     */
    data class Idle(override val resolutions: Long = 0) : PlaylistFallback() {

        override fun onUnrecognized(mediaId: String): Step {
            return resolve(mediaId)
        }
    }

    /**
     * The playlist read [resolution] is under way.
     *
     * @param remaining Streams to try after the ones the read names, when it reads a stream that
     * an earlier read named.
     * @param streamsRead Urls of the streams already read in this request, this one included.
     */
    data class Resolving(
        val resolution: Resolution,
        val remaining: List<ResolvedStream> = emptyList(),
        val streamsRead: Set<String> = emptySet()
    ) : PlaylistFallback() {

        override val resolutions: Long
            get() = resolution.number

        override fun onUnrecognized(mediaId: String): Step {
            if (mediaId == resolution.mediaId) {
                return Step(this, Action.Ignore)
            }
            return resolve(mediaId)
        }

        override fun onResolved(resolution: Resolution, streams: List<ResolvedStream>): Step {
            if (resolution != this.resolution) {
                return Step(this, Action.Ignore)
            }
            return tryFirst(resolution.mediaId, streams + remaining, streamsRead)
        }

        override fun onReadBlocked(resolution: Resolution): Step {
            if (resolution != this.resolution) {
                return Step(this, Action.Ignore)
            }
            return Step(abandonRead(), Action.PauseForNetworkPolicy(resolution.mediaId))
        }

        override val isReadPending: Boolean
            get() = true

        override fun abandonRead(): PlaylistFallback {
            return Idle(resolutions)
        }
    }

    /**
     * The streams the playlist of [mediaId] named are being tried: [playing] is, [remaining] have
     * not been yet.
     *
     * @param streamsRead Urls of the streams already read in this request.
     */
    data class Trying(
        val mediaId: String,
        val playing: ResolvedStream,
        val remaining: List<ResolvedStream>,
        override val resolutions: Long,
        val streamsRead: Set<String> = emptySet()
    ) : PlaylistFallback() {

        override fun onUnrecognized(mediaId: String): Step {
            if (mediaId != this.mediaId) {
                return resolve(mediaId)
            }
            if (isReadable(playing)) {
                val resolution = Resolution(mediaId, resolutions + 1, Source.Stream(playing.url))
                return Step(
                    Resolving(resolution, remaining, streamsRead + playing.url),
                    Action.Resolve(resolution)
                )
            }
            return tryFirst(mediaId, remaining, streamsRead)
        }

        /**
         * Whether [stream] failing is a reason to read it: the player was not told what it is, so
         * it may be a playlist, and reading it stays within the limits of this request.
         */
        private fun isReadable(stream: ResolvedStream): Boolean {
            return stream.hasUnknownType
                    && stream.url !in streamsRead
                    && streamsRead.size < MAX_STREAM_READS
        }
    }

    /**
     * Every stream the playlist of [mediaId] named has failed, or it named none.
     */
    data class Exhausted(val mediaId: String, override val resolutions: Long) : PlaylistFallback() {

        override fun onUnrecognized(mediaId: String): Step {
            if (mediaId != this.mediaId) {
                return resolve(mediaId)
            }
            return Step(this, Action.GiveUp(mediaId))
        }
    }

    /**
     * One read of a playlist of [mediaId], told apart from every other read by its [number].
     *
     * @param source What is read: the station url, or a stream an earlier read named.
     */
    data class Resolution(val mediaId: String, val number: Long, val source: Source = Source.Station)

    /**
     * Where the playlist a [Resolution] reads is.
     */
    sealed class Source {

        /**
         * At the url the station was listed with.
         */
        object Station : Source() {

            override fun toString() = "Station"
        }

        /**
         * At [url], a stream an earlier read named that the player could not recognise.
         */
        data class Stream(val url: String) : Source()
    }

    /**
     * The state that follows an event, and what the service has to do about it.
     */
    data class Step(val next: PlaylistFallback, val action: Action)

    /**
     * What the service has to do after an event.
     */
    sealed class Action {

        /**
         * Nothing: the event belongs to a request that is no longer current, or repeats one that
         * is already being handled.
         */
        object Ignore : Action() {

            override fun toString() = "Ignore"
        }

        /**
         * Read the playlist the source of [resolution] names, for the media item it names, and
         * answer with [resolution].
         */
        data class Resolve(val resolution: Resolution) : Action()

        /**
         * Play [stream] in place of the stream of [mediaId]. A station paused while the stream
         * was being found keeps it in place and stays paused.
         */
        data class Play(val mediaId: String, val stream: ResolvedStream) : Action()

        /**
         * Stop, and tell the user that the station of [mediaId] cannot be played.
         */
        data class GiveUp(val mediaId: String) : Action()

        /**
         * Pause the station of [mediaId] for the mobile-network policy, so that it resumes once a
         * network it may use connects.
         */
        data class PauseForNetworkPolicy(val mediaId: String) : Action()
    }

    companion object {

        /**
         * Most streams one play request reads as playlists, besides the station url.
         */
        const val MAX_STREAM_READS = 5

        /**
         * Player commands a controller sends when someone starts, stops or changes playback.
         * Each of them begins a new play request, which deserves its own reading of a playlist.
         */
        private val PLAY_REQUEST_COMMANDS = setOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS
        )

        /**
         * Whether a controller sending [playerCommand] begins a new play request.
         */
        fun isPlayRequest(playerCommand: @Player.Command Int): Boolean {
            return playerCommand in PLAY_REQUEST_COMMANDS
        }
    }

    protected fun resolve(mediaId: String): Step {
        val resolution = Resolution(mediaId, resolutions + 1)
        return Step(Resolving(resolution), Action.Resolve(resolution))
    }

    protected fun tryFirst(mediaId: String, streams: List<ResolvedStream>, streamsRead: Set<String>): Step {
        if (streams.isEmpty()) {
            return Step(Exhausted(mediaId, resolutions), Action.GiveUp(mediaId))
        }
        val first = streams.first()
        return Step(Trying(mediaId, first, streams.drop(1), resolutions, streamsRead), Action.Play(mediaId, first))
    }
}
