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
import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.dependencies.DeviceLocalsStorageDependency
import com.yuriy.openradio.shared.dependencies.FavoritesStorageDependency
import com.yuriy.openradio.shared.dependencies.LatestRadioStationStorageDependency
import com.yuriy.openradio.shared.model.storage.DeviceLocalsStorage
import com.yuriy.openradio.shared.model.storage.FavoritesStorage
import com.yuriy.openradio.shared.model.storage.LatestRadioStationStorage
import com.yuriy.openradio.shared.model.storage.SleepTimerStorage
import com.yuriy.openradio.shared.model.storage.SourceStorage
import java.lang.ref.WeakReference

/**
 * The user owned stores the browse tree is built from, as the service itself sees them.
 *
 * These are the registry's own instances, obtained through the single-method `*Dependency` hooks
 * that are the supported way to reach them. A test-built parallel instance would share the
 * preference file but not the memory in front of it, and two of these storages keep some:
 * [FavoritesStorage] caches every answer it has given about a station, and
 * [LatestRadioStationStorage] caches the station itself. Seeding through a parallel instance
 * therefore leaves the service answering from a cache the test never touched.
 */
internal class ServiceStorages(context: Context) {

    private val mContextRef = WeakReference(context)

    val favorites = registryFavorites()

    val latest = registryLatest()

    val locals = registryLocals()

    val sleepTimer = SleepTimerStorage(mContextRef)

    private val mSource = SourceStorage(mContextRef)

    /**
     * Wipes every store this suite touches, leaving the profile the way a fresh install finds it.
     *
     * Favorites are removed one by one before the file is wiped, because the inherited `clear()`
     * empties the file and leaves [FavoritesStorage]'s answer cache saying those stations are
     * still favorites, while `remove` drops both.
     *
     * Clearing the latest station matters beyond its own tests. The service reads it in `onCreate`
     * and keeps it as the active station; once it has one, a root browse posts
     * `maybeCreateInitialPlaylist`, which asks the provider for new stations. With no stored
     * station `setActiveRS` rejects the invalid instance and that path stays shut. The wipe drops
     * the registry instance's cached copy along with the file, so a service created after it reads
     * nothing. A service already running keeps the station it adopted, whatever is stored; that is
     * what [ServiceBrowser.connect] checks for.
     */
    fun clear() {
        for (station in favorites.getAll()) {
            favorites.remove(station)
        }
        favorites.clear()
        locals.clear()
        latest.clear()
        sleepTimer.clear()
        // The provider selection decides which URL a fixture has to be keyed to and which nodes
        // the root offers. Note that only a future process binds it: the registry reads it once,
        // at start up, so a test that depends on the provider has to assert what was bound rather
        // than what is stored. See OpenRadioServiceSearchTest.
        mSource.clear()
    }

    /**
     * @return a storage that has not yet cached any answer, for reading back what a test wrote.
     */
    fun freshFavorites(): FavoritesStorage {
        return FavoritesStorage(mContextRef)
    }

    fun freshLocals(): DeviceLocalsStorage {
        return DeviceLocalsStorage(mContextRef, freshFavorites(), freshLatest())
    }

    /**
     * A latest-station storage that has not cached a station yet, so `get` reads the file. The
     * long-lived instances never re-read it once they have one.
     */
    fun freshLatest(): LatestRadioStationStorage {
        return LatestRadioStationStorage(mContextRef)
    }

    private fun registryFavorites(): FavoritesStorage {
        lateinit var result: FavoritesStorage
        DependencyRegistryCommon.injectFavoritesStorage(
            object : FavoritesStorageDependency {

                override fun configureWith(favoritesStorage: FavoritesStorage) {
                    result = favoritesStorage
                }
            }
        )
        return result
    }

    private fun registryLatest(): LatestRadioStationStorage {
        lateinit var result: LatestRadioStationStorage
        DependencyRegistryCommon.injectLatestRadioStationStorage(
            object : LatestRadioStationStorageDependency {

                override fun configureWith(latestRadioStationStorage: LatestRadioStationStorage) {
                    result = latestRadioStationStorage
                }
            }
        )
        return result
    }

    private fun registryLocals(): DeviceLocalsStorage {
        lateinit var result: DeviceLocalsStorage
        DependencyRegistryCommon.injectDeviceLocalsStorage(
            object : DeviceLocalsStorageDependency {

                override fun configureWith(deviceLocalsStorage: DeviceLocalsStorage) {
                    result = deviceLocalsStorage
                }
            }
        )
        return result
    }
}
