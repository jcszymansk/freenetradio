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

/**
 * How far one play request has got with a station whose url the player could not read.
 *
 * Such a url is usually a playlist. It is read once per play request, and the streams it names
 * are tried in the order it names them, because playlists commonly list mirrors of one stream.
 * Reading it again would only return the same streams, so when the last of them fails too the
 * request is over and the service gives up. A new play request starts over from [newRequest].
 *
 * Every state belongs to the media item of one station, named by its media id. An error for any
 * other media item belongs to a different request.
 *
 * ```
 *         unrecognised(m)                 resolved(#n, [u1, u2, ...]), play u1
 *   Idle ───────────────▶ Resolving(#n) ────────────────────────────────────▶ Trying(m, [u2, ...])
 *                              │                                               │        ▲
 *                              │ resolved(#n, []), give up                     └────────┘
 *                              ▼                                     unrecognised(m), play the next
 *                         Exhausted(m) ◀──────────────────────────────── Trying(m, [])
 *                     unrecognised(m):          unrecognised(m), give up
 *                     give up again
 * ```
 *
 * An unrecognised stream of any other media item, in any state, starts [Resolving] that one.
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
     * Reacts to the playlist read [resolution] having returned [urls], in the order the playlist
     * names them.
     */
    open fun onResolved(resolution: Resolution, urls: List<String>): Step {
        return Step(this, Action.Ignore)
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
     */
    data class Resolving(val resolution: Resolution) : PlaylistFallback() {

        override val resolutions: Long
            get() = resolution.number

        override fun onUnrecognized(mediaId: String): Step {
            if (mediaId == resolution.mediaId) {
                return Step(this, Action.Ignore)
            }
            return resolve(mediaId)
        }

        override fun onResolved(resolution: Resolution, urls: List<String>): Step {
            if (resolution != this.resolution) {
                return Step(this, Action.Ignore)
            }
            return tryFirst(resolution.mediaId, urls)
        }
    }

    /**
     * The streams the playlist of [mediaId] named are being tried; [remaining] have not been yet.
     */
    data class Trying(
        val mediaId: String,
        val remaining: List<String>,
        override val resolutions: Long
    ) : PlaylistFallback() {

        override fun onUnrecognized(mediaId: String): Step {
            if (mediaId != this.mediaId) {
                return resolve(mediaId)
            }
            return tryFirst(mediaId, remaining)
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
     * One read of the playlist of [mediaId], told apart from every other read by its [number].
     */
    data class Resolution(val mediaId: String, val number: Long)

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
         * Read the playlist at the station url of the media item [resolution] names, and answer
         * with [resolution].
         */
        data class Resolve(val resolution: Resolution) : Action()

        /**
         * Play [streamUrl] in place of the stream of [mediaId].
         */
        data class Play(val mediaId: String, val streamUrl: String) : Action()

        /**
         * Stop, and tell the user that the station of [mediaId] cannot be played.
         */
        data class GiveUp(val mediaId: String) : Action()
    }

    companion object {

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

    protected fun tryFirst(mediaId: String, urls: List<String>): Step {
        if (urls.isEmpty()) {
            return Step(Exhausted(mediaId, resolutions), Action.GiveUp(mediaId))
        }
        return Step(Trying(mediaId, urls.drop(1), resolutions), Action.Play(mediaId, urls.first()))
    }
}
