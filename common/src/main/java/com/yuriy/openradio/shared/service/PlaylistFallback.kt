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
 * request is over and the service gives up. A new play request starts from [Idle].
 *
 * Every state belongs to the media item of one station, named by its media id. An error or a
 * resolution for any other media item belongs to a different request.
 *
 * ```
 *         unrecognised(m)                 resolved(m, [u1, u2, ...]), play u1
 *   Idle ───────────────▶ Resolving(m) ─────────────────────────────────────▶ Trying(m, [u2, ...])
 *                              │                                               │        ▲
 *                              │ resolved(m, []), give up                      └────────┘
 *                              ▼                                     unrecognised(m), play the next
 *                         Exhausted(m) ◀──────────────────────────────── Trying(m, [])
 *                     unrecognised(m):          unrecognised(m), give up
 *                     give up again
 * ```
 *
 * An unrecognised stream of any other media item, in any state, starts [Resolving] that one.
 *
 * Each state is immutable: an event answers with the next state and what the service has to do.
 */
sealed class PlaylistFallback {

    /**
     * Reacts to the player failing to recognise the stream of the media item [mediaId].
     */
    abstract fun onUnrecognized(mediaId: String): Step

    /**
     * Reacts to the playlist of the media item [mediaId] having been read into [urls], in the
     * order the playlist names them.
     */
    open fun onResolved(mediaId: String, urls: List<String>): Step {
        return Step(this, Action.Ignore)
    }

    /**
     * Nothing has been resolved for the current play request.
     */
    object Idle : PlaylistFallback() {

        override fun onUnrecognized(mediaId: String): Step {
            return resolve(mediaId)
        }

        override fun toString() = "Idle"
    }

    /**
     * The playlist of [mediaId] is being read.
     */
    data class Resolving(val mediaId: String) : PlaylistFallback() {

        override fun onUnrecognized(mediaId: String): Step {
            if (mediaId == this.mediaId) {
                return Step(this, Action.Ignore)
            }
            return resolve(mediaId)
        }

        override fun onResolved(mediaId: String, urls: List<String>): Step {
            if (mediaId != this.mediaId) {
                return Step(this, Action.Ignore)
            }
            return tryFirst(mediaId, urls)
        }
    }

    /**
     * The streams the playlist of [mediaId] named are being tried; [remaining] have not been yet.
     */
    data class Trying(val mediaId: String, val remaining: List<String>) : PlaylistFallback() {

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
    data class Exhausted(val mediaId: String) : PlaylistFallback() {

        override fun onUnrecognized(mediaId: String): Step {
            if (mediaId != this.mediaId) {
                return resolve(mediaId)
            }
            return Step(this, Action.GiveUp(mediaId))
        }
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
         * Read the playlist at the station url of [mediaId].
         */
        data class Resolve(val mediaId: String) : Action()

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
        return Step(Resolving(mediaId), Action.Resolve(mediaId))
    }

    protected fun tryFirst(mediaId: String, urls: List<String>): Step {
        if (urls.isEmpty()) {
            return Step(Exhausted(mediaId), Action.GiveUp(mediaId))
        }
        return Step(Trying(mediaId, urls.drop(1)), Action.Play(mediaId, urls.first()))
    }
}
