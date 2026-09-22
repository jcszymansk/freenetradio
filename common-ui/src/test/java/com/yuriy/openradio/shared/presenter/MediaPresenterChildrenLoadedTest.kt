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

package com.yuriy.openradio.shared.presenter

import android.content.Context
import android.content.ContextWrapper
import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.net.NetworkLayer
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.timer.SleepTimerListener
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.utils.MediaItemBuilder
import com.yuriy.openradio.shared.view.list.TestMediaItemsAdapter
import com.yuriy.openradio.shared.view.list.TestMediaItemsAdapter.Companion.mediaItem
import java.lang.ref.WeakReference
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the decision [MediaPresenterImpl.handleChildrenLoaded] makes between replacing the rows of
 * the browsed node and appending to them. A refresh of a node the user is already looking at is the
 * only case where the two differ, and it is the case the paginated nodes depend on: the service
 * says `replace`, the catalogue has not changed, and the rows must still be thrown away.
 */
class MediaPresenterChildrenLoadedTest {

    private val mAdapter = TestMediaItemsAdapter()
    private val mPresenter = presenter(mAdapter)

    @Test
    fun refreshOfTheSameCatalogueReplacesItsRows() {
        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a"), mediaItem("b")), false)

        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("c")), true)

        assertEquals(listOf("c"), mAdapter.mediaIds())
        assertEquals(COUNTRY_STATIONS, mAdapter.parentId)
    }

    @Test
    fun nextPageOfTheSameCatalogueAppendsToItsRows() {
        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a"), mediaItem("b")), false)

        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("c")), false)

        assertEquals(listOf("a", "b", "c"), mAdapter.mediaIds())
        assertEquals(COUNTRY_STATIONS, mAdapter.parentId)
    }

    @Test
    fun anotherCatalogueReplacesTheRowsOfThePreviousOne() {
        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a"), mediaItem("b")), false)

        mPresenter.handleChildrenLoaded(MediaId.MEDIA_ID_COUNTRIES_LIST, listOf(mediaItem("pl")), false)

        assertEquals(listOf("pl"), mAdapter.mediaIds())
        assertEquals(MediaId.MEDIA_ID_COUNTRIES_LIST, mAdapter.parentId)
    }

    @Test
    fun endOfListPageLeavesTheLoadedRowsAndTheirNodeAlone() {
        mPresenter.handleChildrenLoaded(COUNTRY_STATIONS, listOf(mediaItem("a"), mediaItem("b")), false)

        mPresenter.handleChildrenLoaded(
            MediaId.MEDIA_ID_COUNTRIES_LIST, listOf(MediaItemBuilder.buildMediaItemListEnded()), true
        )

        assertEquals(listOf("a", "b"), mAdapter.mediaIds())
        assertEquals(COUNTRY_STATIONS, mAdapter.parentId)
    }

    private companion object {

        /**
         * A node [com.yuriy.openradio.shared.utils.AppUtils.isSameCatalogue] does report as
         * unchanged on a second visit, unlike root, favorites and the local stations. Only such a
         * node can tell the two halves of the decision apart.
         */
        const val COUNTRY_STATIONS = MediaId.MEDIA_ID_COUNTRY_STATIONS

        /**
         * A presenter holding nothing but the adapter. The children-loaded path reads no other
         * collaborator, and it tolerates the absent list, so none of them has to work here.
         */
        fun presenter(adapter: TestMediaItemsAdapter): MediaPresenterImpl {
            val context: Context = object : ContextWrapper(null) {}
            val presenter = MediaPresenterImpl(
                context,
                UnusedNetworkLayer(),
                LocationStorage(WeakReference(context)),
                UnusedSleepTimerModel(),
                UnusedSourcesLayer(),
                FavoritesStorage(WeakReference(context))
            )
            presenter.attachList(null, adapter)
            return presenter
        }

        fun unexpected(): Nothing {
            throw AssertionError("The children loaded path reached a collaborator it has no business calling")
        }
    }

    private class UnusedNetworkLayer : NetworkLayer {

        override fun startMonitor(context: Context, listener: NetworkMonitorListener) = unexpected()

        override fun stopMonitor(context: Context) = unexpected()

        override fun checkConnectivityAndNotify(context: Context) = unexpected()

        override fun isMobileNetwork() = unexpected()
    }

    private class UnusedSourcesLayer : SourcesLayer {

        override fun getAllSources(): Set<Source> = unexpected()

        override fun getActiveSource(): Source = unexpected()

        override fun setActiveSource(source: Source) = unexpected()
    }

    private class UnusedSleepTimerModel : SleepTimerModel {

        override fun init() = unexpected()

        override fun isEnabled() = unexpected()

        override fun setEnabled(value: Boolean) = unexpected()

        override fun updateTime(enabled: Boolean) = unexpected()

        override fun updateTimer(time: Long, enabled: Boolean) = unexpected()

        override fun setDate(year: Int, month: Int, day: Int) = unexpected()

        override fun setTime(hourOfDay: Int, minute: Int) = unexpected()

        override fun getTime(): Date = unexpected()

        override fun getTimestamp() = unexpected()

        override fun isTimestampNotValid(value: Long) = unexpected()

        override fun addSleepTimerListener(listener: SleepTimerListener) = unexpected()

        override fun removeSleepTimerListener(listener: SleepTimerListener) = unexpected()
    }
}
