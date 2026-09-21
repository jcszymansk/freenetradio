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

package com.yuriy.openradio.shared.model.net

import android.net.Uri
import androidx.core.util.Pair
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import com.yuriy.openradio.shared.utils.NetUtils
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.URL
import java.net.UnknownHostException
import java.util.Random

/**
 * Resolves the Radio Browser mirror to connect to.
 *
 * Radio Browser publishes its mirrors as A records rather than as a fixed host, so
 * [UrlLayerRadioBrowserImpl] stamps every address it builds with
 * [UrlLayerRadioBrowserImpl.BASE_URL_PREFIX] and this class substitutes a live mirror for it.
 * The lookup happens once and its result is reused, and a failed lookup falls back to mirrors
 * recorded by hand, which have to be re-checked from time to time to stay current.
 *
 * Addresses without the prefix are passed through untouched: station artwork and stream probes
 * go through the same downloader and must not be redirected at a Radio Browser mirror.
 */
class DnsMirrorUrlResolver : ConnectionUrlResolver {

    private val mRandom = Random()
    private var mUrlsSet = Array(0) { AppUtils.EMPTY_STRING }

    override fun resolve(uri: Uri, parameters: List<Pair<String, String>>): URL? {
        val uriStr = uri.toString()
        if (!uriStr.startsWith(UrlLayerRadioBrowserImpl.BASE_URL_PREFIX)) {
            return getUrl(uriStr, parameters)
        }

        synchronized(mRandom) {
            if (mUrlsSet.isNotEmpty()) {
                val i = mRandom.nextInt(mUrlsSet.size)
                return getUrlModified(uriStr, mUrlsSet[i], parameters)
            }
        }

        try {
            val list = InetAddress.getAllByName(LOOK_UP_DNS)
            synchronized(mRandom) {
                mUrlsSet = Array(list.size) { AppUtils.EMPTY_STRING }
                var i = 0
                for (item in list) {
                    mUrlsSet[i++] = "https://" + item.canonicalHostName
                    AppLogger.i("$TAG look up host:" + mUrlsSet[i - 1])
                }
            }
        } catch (exception: UnknownHostException) {
            AppLogger.e(
                "$TAG do lookup ${NetUtils.createExceptionMessage(uri, parameters)}", exception
            )
        }

        var url: URL? = null
        synchronized(mRandom) {
            if (mUrlsSet.isNotEmpty()) {
                val i = mRandom.nextInt(mUrlsSet.size)
                url = getUrlModified(uriStr, mUrlsSet[i], parameters)
            }
        }
        if (url != null) {
            return url
        }

        val i = mRandom.nextInt(RESERVED_URLS.size)
        return getUrlModified(uriStr, RESERVED_URLS[i], parameters)
    }

    private fun getUrlModified(
        uriOrigin: String, uri: String, parameters: List<Pair<String, String>>
    ): URL? {
        val uriModified = uriOrigin.replaceFirst(
            UrlLayerRadioBrowserImpl.BASE_URL_PREFIX.toRegex(), uri
        )
        return getUrl(uriModified, parameters)
    }

    private fun getUrl(uri: String, parameters: List<Pair<String, String>>): URL? {
        return try {
            URL(uri)
        } catch (exception: MalformedURLException) {
            AppLogger.e(
                "$TAG getUrl ${NetUtils.createExceptionMessage(uri, parameters)}", exception
            )
            null
        }
    }

    companion object {

        private const val TAG = "DnsURLRslv"
        private const val LOOK_UP_DNS = "all.api.radio-browser.info"
        private val RESERVED_URLS = arrayOf(
            "https://de1.api.radio-browser.info",
            "https://fr1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info"
        )
    }
}
