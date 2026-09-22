/*
 * Copyright 2017-2021 The "Open Radio" Project. Author: Chernyshov Yuriy
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
package com.yuriy.openradio.shared.model.net

import android.content.Context
import android.net.Uri
import androidx.core.util.Pair
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.NetUtils
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/15/14
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 *
 * [HTTPDownloaderImpl] allows to download data from the
 * resource over HTTP protocol.
 */
class HTTPDownloaderImpl(private val mResolver: ConnectionUrlResolver) : DownloaderLayer {

    private val mExecutor = Executors.newFixedThreadPool(8)

    override fun downloadDataFromUri(
        context: Context, uri: Uri,
        parameters: List<Pair<String, String>>,
        contentTypeFilter: String?,
        maxBytes: Int
    ): ByteArray {
        val task = BytesDownloader(
            context, mResolver, uri, parameters, contentTypeFilter ?: AppUtils.EMPTY_STRING, maxBytes
        )
        return mExecutor.submit(task).get()
    }

    class BytesDownloader(
        private val mContext: Context,
        private val mResolver: ConnectionUrlResolver,
        private val mUri: Uri,
        private val mParameters: List<Pair<String, String>>,
        private val mContentTypeFilter: String,
        private val mMaxBytes: Int = DownloaderLayer.NO_LIMIT
    ) : Callable<ByteArray> {

        override fun call(): ByteArray {

            data class Response(
                val url: URL? = null,
                val responseCode: Int = -1,
                val connection: HttpURLConnection? = null
            )

            fun openConnection(): Response {
                val url = mResolver.resolve(mUri, mParameters) ?: return Response()
                AppLogger.i("$CLASS_NAME Request URL:$url")
                val connection = NetUtils.getHttpURLConnection(
                    mContext,
                    url,
                    if (mParameters.isEmpty()) NetUtils.HTTP_METHOD_GET else NetUtils.HTTP_METHOD_POST,
                    mParameters
                ) ?: return Response()
                var responseCode = 0
                try {
                    responseCode = connection.responseCode
                } catch (exception: IOException) {
                    AppLogger.e(
                        "$CLASS_NAME getResponse ${
                            NetUtils.createExceptionMessage(
                                url.toString(),
                                mParameters
                            )
                        }", exception
                    )
                }
                AppLogger.d("$CLASS_NAME response code $responseCode for $url")
                return Response(url, responseCode, connection)
            }

            var response = ByteArray(0)

            var responseObj = openConnection()
            var attempt = 5
            while (true) {
                if (attempt < 0) {
                    AppLogger.e("$CLASS_NAME can not get connection for $responseObj")
                    break
                }
                val responseCode = responseObj.responseCode
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode > HttpURLConnection.HTTP_MULT_CHOICE - 1) {
                    NetUtils.closeHttpURLConnection(responseObj.connection)
                    responseObj = openConnection()
                    attempt--
                    continue
                }
                break
            }

            val connection = responseObj.connection ?: return response

            val contentType = connection.getHeaderField("Content-Type") ?: AppUtils.EMPTY_STRING
            if (mContentTypeFilter != AppUtils.EMPTY_STRING && contentType.startsWith(mContentTypeFilter).not()) {
                NetUtils.closeHttpURLConnection(responseObj.connection)
                AppLogger.w("$CLASS_NAME filtered out $contentType for ${responseObj.url}")
                return response
            }

            try {
                val inputStream = BufferedInputStream(connection.inputStream)
                response = toByteArray(inputStream, mMaxBytes)
                    ?: ByteArray(0).also {
                        AppLogger.e("$CLASS_NAME more than $mMaxBytes bytes at ${responseObj.url}")
                    }
            } catch (exception: IOException) {
                AppLogger.e(
                    "$CLASS_NAME getStream ${
                        NetUtils.createExceptionMessage(
                            responseObj.url.toString(),
                            mParameters
                        )
                    }", exception
                )
            } finally {
                NetUtils.closeHttpURLConnection(responseObj.connection)
            }
            AppLogger.d("$CLASS_NAME return ${response.size} bytes for ${responseObj.url}")
            return response
        }
    }

    companion object {
        /**
         * Tag to use in logging message.
         */
        private const val CLASS_NAME = "HTTPDI"

        private const val DEFAULT_BUFFER_SIZE = 1024 * 4

        /**
         * Represents the end-of-file (or stream).
         */
        private const val EOF = -1

        const val CONTENT_TYPE_IMG = "image/"

        /**
         * Reads [input] whole.
         *
         * @param maxBytes Most that may be read, or [DownloaderLayer.NO_LIMIT].
         * @return The content, or null when there is more of it than [maxBytes]. A response that
         * long is not the document the caller asked for, so it is refused rather than cut short.
         */
        @Throws(IOException::class)
        private fun toByteArray(input: InputStream, maxBytes: Int): ByteArray? {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == EOF) {
                    return output.toByteArray()
                }
                if (maxBytes != DownloaderLayer.NO_LIMIT && output.size() + read > maxBytes) {
                    return null
                }
                output.write(buffer, 0, read)
            }
        }
    }
}
