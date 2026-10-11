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

package com.yuriy.openradio.shared.utils

import java.net.HttpURLConnection

/**
 * Permission for one read made of several requests to use the network, which ends for good the
 * first time [mayUseNetwork] refuses it or someone [revoke]s it.
 *
 * Every request and every transfer of the read asks [mayContinue], from whichever thread it runs
 * on. A refusal is remembered rather than asked again, so the read as a whole knows it was cut
 * short even when the network becomes usable again before it looks, and a part of it that was
 * stopped is never mistaken for one that found nothing.
 *
 * Asking only stops a transfer at its next read, and a read can wait on its socket for as long as
 * the server holds the response back. Connections are therefore [hold]ed for as long as they are
 * read, and revoking the permit disconnects them, which ends a waiting read at once.
 *
 * ```
 *   reader thread                    revoking thread
 *   hold(c) { read ... blocked }     revoke(): revoked, disconnect every held c
 *             read throws  <──────────────┘
 * ```
 *
 * @param mayUseNetwork Whether the network may be used now. Not asked again after a refusal.
 */
class NetworkPermit(private val mayUseNetwork: () -> Boolean) {

    private val mLock = Any()

    /**
     * Guarded by [mLock], like [mHeld], so that a connection is never held after the revocation
     * that should have disconnected it.
     */
    private var mIsRevoked = false

    private val mHeld = mutableSetOf<HttpURLConnection>()

    /**
     * Whether the network refused this read at any point, or it was revoked.
     */
    val isRevoked: Boolean
        get() = synchronized(mLock) { mIsRevoked }

    /**
     * Whether the read may go on using the network.
     */
    fun mayContinue(): Boolean {
        if (isRevoked) {
            return false
        }
        if (mayUseNetwork()) {
            return true
        }
        AppLogger.i("$CLASS_NAME refused by the network")
        synchronized(mLock) { mIsRevoked = true }
        return false
    }

    /**
     * Ends the permit and disconnects every connection held under it, from any thread.
     */
    fun revoke() {
        val held = synchronized(mLock) {
            mIsRevoked = true
            val held = mHeld.toList()
            mHeld.clear()
            held
        }
        AppLogger.i("$CLASS_NAME revoked, disconnect ${held.size} connections")
        for (connection in held) {
            connection.disconnect()
        }
    }

    /**
     * Runs [read] with [connection] held, so that revoking the permit meanwhile disconnects it.
     * A connection held after the permit was revoked is disconnected before [read] runs, so its
     * read fails at once.
     */
    fun <T> hold(connection: HttpURLConnection, read: () -> T): T {
        val isHeld = synchronized(mLock) {
            if (mIsRevoked) {
                false
            } else {
                mHeld.add(connection)
                true
            }
        }
        if (isHeld.not()) {
            AppLogger.i("$CLASS_NAME already revoked, disconnect ${connection.url}")
            connection.disconnect()
        }
        try {
            return read()
        } finally {
            synchronized(mLock) { mHeld.remove(connection) }
        }
    }

    private companion object {

        const val CLASS_NAME = "NetworkPermit"
    }
}
