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

package com.yuriy.openradio.shared.presenter.settings

import android.net.Uri
import android.os.Bundle
import com.yuriy.openradio.shared.model.ServiceCommander
import com.yuriy.openradio.shared.model.logging.LoggingLayer
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.model.source.SourcesLayer
import com.yuriy.openradio.shared.model.storage.StartPlayback
import com.yuriy.openradio.shared.presenter.main.CountrySetting
import com.yuriy.openradio.shared.service.location.Country
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/** A [GeneralSettings] that records every write, in order. */
internal class FakeGeneralSettings(
    startPlayback: StartPlayback = StartPlayback.IF_PLAYING,
    masterVolume: Int = 100,
    btAutoPlay: Boolean = false,
    useMobileData: Boolean = true,
) : GeneralSettings {

    val writes = mutableListOf<String>()

    override var startPlayback: StartPlayback = startPlayback
        set(value) {
            field = value
            writes += "startPlayback=$value"
        }

    override var masterVolume: Int = masterVolume
        set(value) {
            field = value
            writes += "masterVolume=$value"
        }

    override var btAutoPlay: Boolean = btAutoPlay
        set(value) {
            field = value
            writes += "btAutoPlay=$value"
        }

    override var useMobileData: Boolean = useMobileData
        set(value) {
            field = value
            writes += "useMobileData=$value"
        }
}

/** A [BufferStore] that records every write, in order. */
internal class FakeBufferStore(values: BufferSettings = BufferSettings.DEFAULTS) : BufferStore {

    val writes = mutableListOf<BufferSettings>()

    override var values: BufferSettings = values
        set(value) {
            field = value
            writes += value
        }
}

/** A [UserAgentStore] that records every write, in order. */
internal class FakeUserAgentStore(
    isCustom: Boolean = false,
    var storedCustom: String? = null,
    private val mDefaultAgent: String = DEFAULT_AGENT,
) : UserAgentStore {

    val writes = mutableListOf<String>()

    private var mIsCustom = isCustom

    override var isCustom: Boolean
        get() = mIsCustom
        set(value) {
            mIsCustom = value
            writes += "isCustom=$value"
        }

    override val current: String
        get() = if (mIsCustom) storedCustom ?: mDefaultAgent else mDefaultAgent

    override fun storeCustom(value: String) {
        storedCustom = value
        writes += "custom=$value"
    }

    companion object {
        const val DEFAULT_AGENT = "FreeNetRadio/1.0 ExoPlayerLib"
    }
}

/** A [CountrySetting] that records every write, in order. */
internal class FakeCountrySetting(private var mCode: String = "CA") : CountrySetting {

    val writes = mutableListOf<String>()

    override fun countryCode(): String = mCode

    override fun setCountryCode(countryCode: String) {
        mCode = countryCode
        writes += countryCode
    }
}

/** A [SourcesLayer] that records every write, in order. */
internal class FakeSources(active: Source = Source.RADIO_BROWSER) : SourcesLayer {

    val writes = mutableListOf<Source>()

    var active: Source = active
        set(value) {
            field = value
            writes += value
        }

    override fun getAllSources(): Set<Source> = Source.entries.toSet()

    override fun getActiveSource(): Source = active

    override fun setActiveSource(source: Source) {
        active = source
    }
}

/** A command as [RecordingCommander] recorded it. */
internal data class SentCommand(val command: String, val parameters: Bundle)

/** A [ServiceCommander] that records every command and answers [connected]. */
internal class RecordingCommander(var connected: Boolean = true) : ServiceCommander {

    val sent = mutableListOf<SentCommand>()

    fun commands(): List<String> = sent.map { it.command }

    override suspend fun sendCommand(command: String, parameters: Bundle): Boolean {
        sent += SentCommand(command, parameters)
        return connected
    }

    override suspend fun sendCommand(
        command: String,
        parameters: Bundle,
        resultCallback: (Int, Bundle?) -> Unit,
    ): Boolean {
        sent += SentCommand(command, parameters)
        return connected
    }
}

/** A [BluetoothPermission] that answers [required]. */
internal class FakeBluetoothPermission(var required: Boolean = false) : BluetoothPermission {

    override fun isRequired(): Boolean = required
}

/** An [AppRestart] that counts its restarts. */
internal class RecordingRestart : AppRestart {

    var restarts = 0

    override fun restart() {
        restarts++
    }
}

/** A [SettingsNotices] that records every notice, in order. */
internal class RecordingNotices : SettingsNotices {

    val shown = mutableListOf<Notice>()

    override fun show(notice: Notice) {
        shown += notice
    }
}

/** A [StationFiles] that records every transfer and answers [answer]. */
internal class FakeStationFiles : StationFiles {

    val exports = mutableListOf<Uri>()

    val imports = mutableListOf<Uri>()

    var answer = true

    var gate: CompletableDeferred<Unit>? = null

    override suspend fun exportTo(document: Uri): Boolean {
        exports += document
        gate?.await()
        return answer
    }

    override suspend fun importFrom(document: Uri): Boolean {
        imports += document
        gate?.await()
        return answer
    }
}

/** A [LoggingLayer] that keeps the callbacks of its last collection for the test to fire. */
internal class FakeLogs : LoggingLayer {

    var collections = 0

    private var mOnSuccess: ((File) -> Unit)? = null

    private var mOnError: ((String) -> Unit)? = null

    override fun collectAdbLogs(onSuccess: (file: File) -> Unit, onError: (msg: String) -> Unit) {
        collections++
        mOnSuccess = onSuccess
        mOnError = onError
    }

    fun succeed(file: File) {
        checkNotNull(mOnSuccess) { "Nothing is being collected" }(file)
    }

    fun fail(message: String) {
        checkNotNull(mOnError) { "Nothing is being collected" }(message)
    }
}

/** The countries the settings screens offer, in screen order. */
internal val COUNTRIES: CountryList = CountryList {
    listOf(
        Country("Use Location", "Use Location"),
        Country("Canada", "CA"),
        Country("Poland", "PL"),
    )
}
