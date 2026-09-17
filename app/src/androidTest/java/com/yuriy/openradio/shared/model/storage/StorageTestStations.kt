package com.yuriy.openradio.shared.model.storage

import com.yuriy.openradio.shared.dependencies.DependencyRegistryCommon
import com.yuriy.openradio.shared.model.media.MediaStream.Companion.BIT_RATE_DEFAULT
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.setVariant
import com.yuriy.openradio.shared.model.translation.RadioStationJsonSerializer

/**
 * Builders shared by the persistence tests. Every station carries a non empty media stream because
 * [AbstractRadioStationsStorage.getAll] drops the ones that do not.
 */
internal fun makeStation(
    id: String,
    name: String = "Station $id",
    url: String = "https://example.test/$id",
    sortId: Int = DependencyRegistryCommon.UNKNOWN_ID,
    bitrate: Int = BIT_RATE_DEFAULT,
    isLocal: Boolean = false
): RadioStation {
    val station = RadioStation.makeDefaultInstance(id)
    station.name = name
    station.setVariant(bitrate, url)
    station.sortId = sortId
    station.isLocal = isLocal
    return station
}

internal fun serialize(station: RadioStation): String {
    return RadioStationJsonSerializer().serialize(station)
}

/**
 * Joins already formed segments the way [AbstractRadioStationsStorage.getAllAsString] does, so that
 * the demarshalling side can be driven with hand written input.
 */
internal fun marshall(vararg segments: String): String {
    return segments.joinToString(PAIR_DELIMITER)
}

internal fun entry(key: String, value: String): String {
    return "$key$KEY_VALUE_DELIMITER$value"
}

internal fun entry(station: RadioStation): String {
    return entry(station.id, serialize(station))
}

/**
 * Mirrors the private delimiters of [AbstractRadioStationsStorage].
 */
internal const val KEY_VALUE_DELIMITER = "<:>"

internal const val PAIR_DELIMITER = "<<::>>"
