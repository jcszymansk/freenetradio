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
import com.yuriy.openradio.shared.model.net.NetworkLayer
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LocationStorage
import com.yuriy.openradio.shared.model.timer.SleepTimerListener
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.view.list.TestMediaItemsAdapter
import java.lang.ref.WeakReference
import java.util.Date

/**
 * A presenter holding nothing but the adapter. The browse paths these tests drive read no other
 * collaborator, and they tolerate the absent list, media browser and activity, so none of them has
 * to work here.
 */
internal fun listOnlyPresenter(adapter: TestMediaItemsAdapter): MediaPresenterImpl {
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

private fun unexpected(): Nothing {
    throw AssertionError("The browse path reached a collaborator it has no business calling")
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
