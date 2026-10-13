/*
 * Copyright 2017-2022 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.model

import android.content.Context
import android.net.Uri
import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.net.DownloaderLayer
import com.yuriy.openradio.shared.model.net.NetworkLayer
import com.yuriy.openradio.shared.model.parser.ParserLayer
import com.yuriy.openradio.shared.model.storage.cache.api.ApiCache
import com.yuriy.openradio.shared.model.translation.MediaIdBuilder
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils

/**
 * Created by Yuriy Chernyshov
 * At Android Studio
 * On 12/15/14
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * [ModelLayerImpl] is the main implementation of the [ModelLayer] interface.
 */
class ModelLayerImpl(
    private val mContext: Context,
    private val mDataParser: ParserLayer,
    private val mNetworkLayer: NetworkLayer,
    private val mDownloaderLayer: DownloaderLayer,
    private val mApiCachePersistent: ApiCache,
    private val mApiCacheInMemory: ApiCache
) : ModelLayer {

    override fun getAllCategories(uri: Uri): Set<Category> {
        val data = downloadData(uri)
        return mDataParser.getAllCategories(data)
    }

    override fun getAllCountries(uri: Uri): Set<Country> {
        val data = downloadData(uri)
        return mDataParser.getAllCountries(data)
    }

    override fun getStations(uri: Uri, mediaIdBuilder: MediaIdBuilder): Set<RadioStation> {
        val data = downloadData(uri)
        return mDataParser.getRadioStations(data, mediaIdBuilder, uri)
    }

    /**
     * Download data as [String].
     *
     * @param uri Uri to download from.
     * @return [String]
     */
    private fun downloadData(uri: Uri): String {
        // Create key to associate response with.
        val responsesMapKey = uri.toString()

        // Fetch RAM memory first.
        var response = mApiCacheInMemory[responsesMapKey]
        if (response != AppUtils.EMPTY_STRING && response != "[]") {
            return response
        }

        // Then look up data in the DB.
        response = mApiCachePersistent[responsesMapKey]
        if (response != AppUtils.EMPTY_STRING && response != "[]") {
            mApiCacheInMemory.remove(responsesMapKey)
            mApiCacheInMemory.put(responsesMapKey, response)
            return response
        }

        // Both caches are local reads that cannot fail for lack of a network, so connectivity is
        // only asked about here, where a request would really go out. Asking any earlier threw
        // away a cached response in the one situation the cache exists for.
        if (!mNetworkLayer.checkConnectivityAndNotify(mContext)) {
            return AppUtils.EMPTY_STRING
        }
        // Finally, go to internet.

        // Declare and initialize variable for response.
        response = String(mDownloaderLayer.downloadDataFromUri(mContext, uri))
        // Ignore empty response finally.
        if (response == AppUtils.EMPTY_STRING || response == "[]") {
            response = AppUtils.EMPTY_STRING
            AppLogger.w("$CLASS_NAME can not parse data, response is empty")
            return response
        }
        // Remove previous record.
        mApiCachePersistent.remove(responsesMapKey)
        mApiCacheInMemory.remove(responsesMapKey)
        // Finally, cache new response.
        mApiCachePersistent.put(responsesMapKey, response)
        mApiCacheInMemory.put(responsesMapKey, response)
        return response
    }

    companion object {
        /**
         * Tag string to use in logging messages.
         */
        private const val CLASS_NAME = "ASPI"
    }
}
