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

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * An HTTP server bound to the loopback address, for the recovery paths where raw HTTP is the
 * subject rather than an accident of how the fixture is delivered.
 *
 * Two things cannot be tested without one. A playlist url is resolved by
 * [com.yuriy.openradio.shared.utils.NetUtils.extractUrlsFromPlaylist], which opens the url as an
 * `HttpURLConnection` and can therefore only ever succeed over HTTP. And a stream the server
 * refuses is classified from the status code it refused with, which needs a server willing to
 * refuse. Everything else the playback tests do is a local file, per the testing roadmap.
 *
 * The socket is bound to the address literal `127.0.0.1`, the same one every url it hands out
 * names, so it is reachable with the device's networking disabled and unreachable from anywhere
 * else.
 */
internal class LoopbackHttpFixture {

    private val mRoutes = ConcurrentHashMap<String, Response>()

    private val mBroadcasts = ConcurrentHashMap<String, Broadcast>()

    private val mRequests = CopyOnWriteArrayList<String>()

    private val mExecutor = Executors.newCachedThreadPool()

    private var mServer: ServerSocket? = null

    @Volatile
    private var mRunning = false

    fun start() {
        val server = ServerSocket(0, BACKLOG, InetAddress.getByName(HOST))
        mServer = server
        mRunning = true
        mExecutor.execute { accept(server) }
    }

    fun stop() {
        mRunning = false
        try {
            mServer?.close()
        } catch (exception: IOException) {
            /* Closing a socket nobody is reading from is not a failure. */
        }
        mServer = null
        mExecutor.shutdownNow()
    }

    /**
     * Serves [body] at [path] and returns the url that reaches it.
     */
    fun serve(path: String, contentType: String, body: ByteArray): String {
        mRoutes[path] = Response(HTTP_OK, "OK", contentType, body)
        return url(path)
    }

    fun serve(path: String, contentType: String, body: String): String {
        return serve(path, contentType, body.toByteArray(Charsets.UTF_8))
    }

    /**
     * Broadcasts at [path] the way a radio station does: [head] first, then zeroed bytes at
     * [bytesPerSecond] for as long as the client keeps the connection open, with no length
     * declared. The returned [Broadcast] counts what was sent and how many connections are open.
     */
    fun broadcast(path: String, contentType: String, head: ByteArray, bytesPerSecond: Int): Broadcast {
        val broadcast = Broadcast(url(path), contentType, head, bytesPerSecond)
        mBroadcasts[path] = broadcast
        return broadcast
    }

    /**
     * Refuses [path] with [status] and returns the url that reaches it.
     */
    fun refuse(path: String, status: Int, reason: String): String {
        mRoutes[path] = Response(status, reason, TEXT_PLAIN, ByteArray(0))
        return url(path)
    }

    /**
     * Redirects [path] to [location] with [status], and returns the url that reaches it.
     * [location] is sent as given, so it can be relative.
     */
    fun redirect(path: String, status: Int, location: String): String {
        mRoutes[path] = Response(status, "Redirect", TEXT_PLAIN, ByteArray(0), mapOf("Location" to location))
        return url(path)
    }

    /**
     * @return the paths that were asked for, in the order they arrived.
     */
    fun requestedPaths(): List<String> {
        return ArrayList(mRequests)
    }

    fun url(path: String): String {
        val server = mServer ?: throw IllegalStateException("The server is not started")
        return "http://$HOST:${server.localPort}$path"
    }

    private fun accept(server: ServerSocket) {
        while (mRunning) {
            val socket = try {
                server.accept()
            } catch (exception: IOException) {
                // The socket was closed by stop(), or the run is over.
                return
            }
            mExecutor.execute { answer(socket) }
        }
    }

    /**
     * Answers one request and closes the connection.
     *
     * Every response says `Connection: close` and carries a `Content-Length`, so neither the
     * player's data source nor the playlist resolver has to guess where a body ends. Keeping one
     * connection per request is what makes that safe to do. A [Broadcast] is the exception: it
     * has no end to declare, and the connection closing is the end of its body.
     */
    private fun answer(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) {
                    break
                }
            }
            val path = pathOf(requestLine)
            mRequests.add(path)
            val broadcast = mBroadcasts[path]
            if (broadcast != null) {
                broadcast.transmit(it)
                return
            }
            val response = mRoutes[path] ?: Response(HTTP_NOT_FOUND, "Not Found", TEXT_PLAIN, ByteArray(0))
            val head = buildString {
                append("HTTP/1.1 ${response.status} ${response.reason}\r\n")
                append("Content-Type: ${response.contentType}\r\n")
                append("Content-Length: ${response.body.size}\r\n")
                for ((name, value) in response.headers) {
                    append("$name: $value\r\n")
                }
                append("Accept-Ranges: none\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            val output = it.getOutputStream()
            output.write(head.toByteArray(Charsets.US_ASCII))
            output.write(response.body)
            output.flush()
        }
    }

    private fun pathOf(requestLine: String): String {
        val parts = requestLine.split(' ')
        return if (parts.size < 2) requestLine else parts[1]
    }

    /**
     * One endless stream, and what has been sent of it over every connection that asked for it.
     */
    class Broadcast internal constructor(
        val url: String,
        private val mContentType: String,
        private val mHead: ByteArray,
        private val mBytesPerSecond: Int
    ) {

        private val mBytesSent = AtomicLong()

        private val mOpenConnections = AtomicInteger()

        private val mConnections = AtomicInteger()

        /**
         * Bytes written to every connection so far, head included. A write the client never read
         * still counts once the socket accepted it.
         */
        fun bytesSent(): Long {
            return mBytesSent.get()
        }

        /**
         * Connections that are still being written to.
         */
        fun openConnections(): Int {
            return mOpenConnections.get()
        }

        /**
         * Connections ever made, open or closed.
         */
        fun connections(): Int {
            return mConnections.get()
        }

        /**
         * Writes the stream to [socket] until the client closes the connection or the thread is
         * interrupted by [LoopbackHttpFixture.stop]. Writing into a socket the client closed fails
         * once the client's reset arrives, which is how the end of the connection is noticed.
         */
        internal fun transmit(socket: Socket) {
            mConnections.incrementAndGet()
            mOpenConnections.incrementAndGet()
            try {
                val output = socket.getOutputStream()
                val head = buildString {
                    append("HTTP/1.1 $HTTP_OK OK\r\n")
                    append("Content-Type: $mContentType\r\n")
                    append("Accept-Ranges: none\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                write(output, head.toByteArray(Charsets.US_ASCII))
                write(output, mHead)
                val chunk = ByteArray(maxOf(1, mBytesPerSecond / CHUNKS_PER_SECOND))
                while (Thread.currentThread().isInterrupted.not()) {
                    write(output, chunk)
                    Thread.sleep(MILLIS_PER_SECOND / CHUNKS_PER_SECOND)
                }
            } catch (exception: IOException) {
                /* The client closed the connection, which is the only way a broadcast ends. */
            } catch (exception: InterruptedException) {
                /* The fixture is stopping. */
            } finally {
                mOpenConnections.decrementAndGet()
            }
        }

        private fun write(output: OutputStream, bytes: ByteArray) {
            output.write(bytes)
            output.flush()
            mBytesSent.addAndGet(bytes.size.toLong())
        }

        private companion object {

            const val CHUNKS_PER_SECOND = 50

            const val MILLIS_PER_SECOND = 1_000L
        }
    }

    private class Response(
        val status: Int,
        val reason: String,
        val contentType: String,
        val body: ByteArray,
        val headers: Map<String, String> = emptyMap()
    )

    companion object {

        const val HTTP_OK = 200

        const val HTTP_FORBIDDEN = 403

        const val HTTP_NOT_FOUND = 404

        const val HTTP_FOUND = 302

        const val HTTP_TEMPORARY_REDIRECT = 307

        const val AUDIO_WAV = "audio/wav"

        const val AUDIO_PLS = "audio/x-scpls"

        const val TEXT_PLAIN = "text/plain"

        /**
         * Spelled out as IPv4 rather than taken from [InetAddress.getLoopbackAddress], which on a
         * dual-stack device answers `::1`. That address needs square brackets inside a url and
         * every url here is assembled as a string, so the unbracketed form reaches the player as
         * a malformed port and the request never leaves.
         */
        private const val HOST = "127.0.0.1"

        private const val BACKLOG = 16
    }
}
