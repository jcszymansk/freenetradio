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
 * A disconnect only ends what a connection has already started; one made before it connects is
 * lost, and the connection would go on to connect. Starting a connection therefore goes through
 * [connect], which [revoke] waits for: a revocation either comes first and the connection never
 * starts, or comes after it has started and its disconnect takes effect. Since [revoke] can wait
 * for as long as a connect takes, it is not for the main thread.
 *
 * ```
 *   reader thread                        revoking thread
 *   connect(c) { c.connect() }  ──┐      revoke(): waits for a connect under way,
 *   hold(c) { read ... blocked }  └────>   then revoked, disconnect every held c
 *             read throws  <───────────────────┘
 * ```
 *
 * @param mayUseNetwork Whether the network may be used now. Not asked again after a refusal.
 */
class NetworkPermit(private val mayUseNetwork: () -> Boolean) {

    /**
     * Guards [mIsRevoked] and [mHeld] together, so that a connection is never held after the
     * revocation that should have disconnected it. Never held for longer than it takes to read or
     * change them.
     */
    private val mLock = Any()

    /**
     * Held while a connection starts and while the permit is revoked, so that the two never
     * overlap. Taken before [mLock] whenever both are.
     */
    private val mConnectLock = Any()

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
     * Ends the permit and disconnects every connection held under it. Waits for a connection that
     * is starting to have started, so call it off the main thread.
     */
    fun revoke() {
        val held = synchronized(mConnectLock) {
            synchronized(mLock) {
                mIsRevoked = true
                val held = mHeld.toList()
                mHeld.clear()
                held
            }
        }
        AppLogger.i("$CLASS_NAME revoked, disconnect ${held.size} connections")
        for (connection in held) {
            connection.disconnect()
        }
    }

    /**
     * Starts [connection] by running [start], unless the permit is revoked or the network refuses
     * it now, in which case [start] never runs. The network is asked here, under the same lock,
     * rather than relied on from an earlier [mayContinue], because the service learns of a policy
     * change and revokes some time after the network turned. A revocation cannot land while
     * [start] runs; it waits, and the [hold] that reads the response then finds the permit
     * revoked and disconnects the connection that started.
     */
    fun connect(connection: HttpURLConnection, start: () -> Unit): Held<Unit> {
        synchronized(mConnectLock) {
            if (mayContinue().not()) {
                AppLogger.i("$CLASS_NAME ${connection.url} not started, the network may not be used")
                return Held.Revoked
            }
            start()
            return Held.Answered(Unit)
        }
    }

    /**
     * Runs [read] with [connection] held, so that revoking the permit meanwhile disconnects it.
     *
     * A disconnect only ends what a connection has already started, and one made before the
     * connection connects is lost. [read] therefore does not run at all once the permit is
     * revoked, and a revocation that lands while it runs is answered with [Held.Revoked] and a
     * second disconnect, which the connection honours once [read] has returned. A connection is
     * started through [connect], never inside [read].
     */
    fun <T> hold(connection: HttpURLConnection, read: () -> T): Held<T> {
        val isHeld = synchronized(mLock) {
            if (mIsRevoked) {
                false
            } else {
                mHeld.add(connection)
                true
            }
        }
        if (isHeld.not()) {
            AppLogger.i("$CLASS_NAME already revoked, ${connection.url} not read")
            connection.disconnect()
            return Held.Revoked
        }
        try {
            val value = read()
            if (isRevoked) {
                AppLogger.i("$CLASS_NAME revoked while ${connection.url} was read")
                connection.disconnect()
                return Held.Revoked
            }
            return Held.Answered(value)
        } finally {
            synchronized(mLock) { mHeld.remove(connection) }
        }
    }

    /**
     * What a [hold] came to.
     */
    sealed class Held<out T> {

        /**
         * The read ran to its end with the permit standing, and answered [value].
         */
        data class Answered<out T>(val value: T) : Held<T>()

        /**
         * The permit was revoked before or while the read ran, so whatever it read is not used.
         */
        object Revoked : Held<Nothing>() {

            override fun toString() = "Revoked"
        }
    }

    private companion object {

        const val CLASS_NAME = "NetworkPermit"
    }
}
