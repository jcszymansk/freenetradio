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

import android.content.Context
import com.yuriy.openradio.shared.model.storage.AppPreferencesManager
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.SleepTimerStorage
import java.lang.ref.WeakReference

/**
 * The user owned stores the browse tree is built from.
 *
 * Preference files are process wide, so writing through these instances is visible to the service,
 * which holds its own. Two of the service's instances memoize, though: [FavoritesStorage.isFavorite]
 * caches its answer per station and [LatestRadioStationStorage.get] caches the station itself. Read
 * back through a freshly built storage, never through the one that seeded the value.
 */
internal class ServiceStorages(context: Context) {

    private val mContext = context

    private val mContextRef = WeakReference(context)

    val favorites = FavoritesStorage(mContextRef)

    val latest = LatestRadioStationStorage(mContextRef)

    val locals = DeviceLocalsStorage(mContextRef, favorites, latest)

    val sleepTimer = SleepTimerStorage(mContextRef)

    /**
     * Wipes every store this suite touches.
     *
     * Clearing the latest station matters beyond its own tests: the service reads it in `onCreate`
     * and, once it has an active station, a root browse posts `maybeCreateInitialPlaylist`, which
     * asks the provider for new stations. That is a network call reached from an otherwise offline
     * node, and the instrumented suite runs with networking disabled.
     */
    fun clear() {
        favorites.clear()
        locals.clear()
        latest.clear()
        sleepTimer.clear()
        AppPreferencesManager.lastKnownRadioStationEnabled(mContext, false)
    }

    /**
     * @return a storage that has not yet cached any answer, for reading back what a test wrote.
     */
    fun freshFavorites(): FavoritesStorage {
        return FavoritesStorage(mContextRef)
    }

    fun freshLocals(): DeviceLocalsStorage {
        return DeviceLocalsStorage(mContextRef, freshFavorites(), LatestRadioStationStorage(mContextRef))
    }
}
