/*
 * Copyright 2020-2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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

import android.content.Context
import android.net.Uri
import androidx.core.util.Pair
import com.yuriy.openradio.R
import com.yuriy.openradio.shared.model.net.DownloaderLayer
import wseemann.media.jplaylistparser.parser.AutoDetectParser
import wseemann.media.jplaylistparser.playlist.Playlist
import wseemann.media.jplaylistparser.playlist.PlaylistEntry
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ExecutionException

object NetUtils {

    const val HTTP_METHOD_GET = "GET"
    const val HTTP_METHOD_POST = "POST"

    private val CLASS_NAME = NetUtils::class.java.simpleName
    private const val USER_AGENT_PARAMETER_KEY = "User-Agent"
    private const val HEADER_FIELD_LOCATION = "Location"

    fun isWebUrl(url: String): Boolean {
        return if (url.isEmpty()) {
            false
        } else url.lowercase(Locale.ROOT).startsWith("www")
                || url.lowercase(Locale.ROOT).startsWith("http")
    }

    private fun getHttpURLConnection(
        context: Context, urlString: String, requestMethod: String
    ): HttpURLConnection? {
        return try {
            getHttpURLConnection(context, URL(urlString), requestMethod, null)
        } catch (exception: MalformedURLException) {
            AppLogger.e("Can not get http connection (1) from $urlString", exception)
            null
        }
    }

    fun getHttpURLConnection(
        context: Context, url: URL, requestMethod: String, parameters: List<Pair<String, String>>?
    ): HttpURLConnection? {
        var connection: HttpURLConnection? = null
        var isRedirect = false
        var connectUrl = url
        var maxAttempt = 3

        do {
            try {
                isRedirect = false
                connection = connectUrl.openConnection() as HttpURLConnection
                connection.readTimeout = AppUtils.TIME_OUT
                connection.connectTimeout = AppUtils.TIME_OUT
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                connection.defaultUseCaches = false
                connection.requestMethod = requestMethod
                val userAgent = AppUtils.getUserAgent(context)
                try {
                    connection.setRequestProperty(USER_AGENT_PARAMETER_KEY, userAgent)
                } catch (e: Exception) {
                    SafeToast.showAnyThread(context, context.getString(R.string.user_agent_can_not_apply))
                }
                AppLogger.d("$CLASS_NAME UserAgent:$userAgent")

                // If there are http request parameters:
                if (!parameters.isNullOrEmpty()) {
                    connection.setRequestProperty("enctype", "application/x-www-form-urlencoded")
                    try {
                        connection.outputStream.use { outputStream ->
                            BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
                                writer.write(getPostParametersQuery(parameters))
                                writer.flush()
                            }
                        }
                    } catch (exception: IOException) {
                        AppLogger.e(
                            " ${createExceptionMessage(url.toString(), parameters)}", exception

                        )
                    }
                }
                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                ) {
                    if (maxAttempt-- <= 0) {
                        AppLogger.e("$CLASS_NAME redirect reached max attempts number")
                        break
                    }
                    val newUrl = connection.getHeaderField(HEADER_FIELD_LOCATION)
                    connection.disconnect()
                    AppLogger.i("$CLASS_NAME redirect from $connectUrl to $newUrl")
                    connectUrl = URL(newUrl)
                    isRedirect = true
                }
            } catch (exception: Exception) {
                // TODO: If SocketTimeoutException - retry with increased timeout.
                AppLogger.e("Can not get http connection (2) from $url", exception)
            }
        } while (isRedirect)

        return connection
    }

    fun closeHttpURLConnection(connection: HttpURLConnection?) {
        if (connection == null) {
            return
        }
        connection.disconnect()
    }

    fun checkResource(context: Context, url: String): Boolean {
        val connection = getHttpURLConnection(context, url, HTTP_METHOD_GET) ?: return false
        val responseCode = try {
            connection.responseCode
            // Make it Throwable to prevent any new type of RuntimeExceptions, such as "java.net.URISyntaxException"
        } catch (throwable: Throwable) {
            closeHttpURLConnection(connection)
            return false
        }
        if (responseCode < HttpURLConnection.HTTP_OK || responseCode > HttpURLConnection.HTTP_MULT_CHOICE - 1) {
            closeHttpURLConnection(connection)
            return false
        }
        closeHttpURLConnection(connection)
        return true
    }

    /**
     * Creates and returns a query of http connection parameters.
     *
     * @param params List of the parameters (keys and values).
     * @return String representation of query.
     * @throws UnsupportedEncodingException
     */
    @Throws(UnsupportedEncodingException::class)
    fun getPostParametersQuery(params: List<Pair<String, String>>): String {
        val result = StringBuilder()
        var first = true
        for (pair in params) {
            if (first) {
                first = false
            } else {
                result.append("&")
            }
            result.append(URLEncoder.encode(pair.first, AppUtils.UTF8))
            result.append("=")
            result.append(URLEncoder.encode(pair.second, AppUtils.UTF8))
        }
        AppLogger.i("$CLASS_NAME post query:$result")
        return result.toString()
    }

    /**
     * Resolves a station url that turned out to be a playlist into the streams it names.
     *
     * The station url itself is opened here, because its declared content type is part of how its
     * format is recognised. Every playlist it names in turn is read through [downloader].
     *
     * @param downloader Reads the playlists that [playlistUrl] names.
     * @return The stream urls, none when the playlist names none, or one empty string when
     * [playlistUrl] cannot be opened.
     */
    fun extractUrlsFromPlaylist(
        context: Context, downloader: DownloaderLayer, playlistUrl: String
    ): Array<String> {
        val connection =
            getHttpURLConnection(context, playlistUrl, HTTP_METHOD_GET) ?: return Array(1) { AppUtils.EMPTY_STRING }
        var inputStream: InputStream? = null
        var result = Array(1) { AppUtils.EMPTY_STRING }
        val parser = AutoDetectParser { url -> fetchPlaylist(context, downloader, url) }
        val playlist = Playlist()
        val contentType = connection.contentType
        try {
            inputStream = connection.inputStream
            parser.parse(playlistUrl, contentType, inputStream, playlist)
            val length = playlist.playlistEntries.size
            result = Array(length) { AppUtils.EMPTY_STRING }
            AppLogger.d("Found $length streams associated with $playlistUrl:")
            for ((i, entry) in playlist.playlistEntries.withIndex()) {
                result[i] = entry[PlaylistEntry.URI]
                AppLogger.d(" - ${result[i]}")
            }
        } catch (e: Exception) {
            val errorMessage = "Can not get urls from playlist at $playlistUrl"
            AppLogger.e(errorMessage, e)
        } finally {
            closeHttpURLConnection(connection)
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    /**/
                }
            }
        }
        return result
    }

    /**
     * Adapts [downloader] to the parsers' [wseemann.media.jplaylistparser.parser.PlaylistFetcher],
     * which answers a failed read with no content.
     */
    private fun fetchPlaylist(context: Context, downloader: DownloaderLayer, url: String): ByteArray {
        AppLogger.d("$CLASS_NAME reading playlist $url")
        return try {
            downloader.downloadDataFromUri(context, Uri.parse(url))
        } catch (exception: ExecutionException) {
            AppLogger.e("$CLASS_NAME can not read playlist $url", exception)
            ByteArray(0)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            AppLogger.e("$CLASS_NAME interrupted while reading playlist $url", exception)
            ByteArray(0)
        }
    }

    /**
     * @param uri
     * @param parameters
     * @return
     */
    fun createExceptionMessage(uri: Uri,
                               parameters: List<Pair<String, String>>): String {
        return createExceptionMessage(uri.toString(), parameters)
    }

    /**
     * @param uriStr
     * @param parameters
     * @return
     */
    fun createExceptionMessage(uriStr: String,
                               parameters: List<Pair<String, String>>): String {
        val builder = StringBuilder(uriStr)
        for (pair in parameters) {
            builder.append(" ")
            builder.append(pair.toString())
        }
        return builder.toString()
    }
}
