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

package com.yuriy.openradio.shared.model.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.getStreamUrlFixed
import com.yuriy.openradio.shared.model.media.isInvalid
import com.yuriy.openradio.testing.UNREACHABLE_ORIGIN
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference
import java.util.TreeSet

/**
 * Created by Chernyshov Yurii
 * At Android Studio
 * On 14/07/17
 * E-Mail: chernyshov.yuriy@gmail.com
 *
 * Covers the marshalling, demarshalling and ordering that every radio station store inherits from
 * [AbstractRadioStationsStorage]. [FavoritesStorage] stands in as a concrete subclass.
 */
@RunWith(AndroidJUnit4::class)
class AbstractRadioStationsStorageTest {

    private lateinit var mContext: Context
    private lateinit var mStorage: FavoritesStorage

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mStorage = newStorage()
        mStorage.clear()
    }

    @After
    fun tearDown() {
        mStorage.clear()
    }

    @Test
    fun fromStringToList() {
        val list = mStorage.getAllFromString(DIRBLE_EXPORT)

        assertEquals(8, list.size)
        for (radioStation in list) {
            assertFalse(radioStation.mediaStream.isEmpty)
        }
    }

    @Test
    fun getAllAddAll() {
        val num = 10
        val list = TreeSet<RadioStation>()
        for (i in 0..num) {
            list.add(makeStation("id-$i", url = "$UNREACHABLE_ORIGIN/stream-$i", sortId = i))
        }

        mStorage.addAll(list)

        val newList = mStorage.getAll()

        assertEquals(num + 1, newList.size)
        for ((i, radioStation) in newList.withIndex()) {
            assertEquals("id-$i", radioStation.id)
        }
    }

    @Test
    fun demarshallingAnEmptyStringYieldsNoStations() {
        assertTrue(mStorage.getAllFromString("").isEmpty())
    }

    @Test
    fun demarshallingKeepsTheStoredSortIdsAsTheyAre() {
        val value = marshall(
            entry(makeStation("a", sortId = 5)),
            entry(makeStation("b", sortId = 2))
        )

        val list = mStorage.getAllFromString(value).toList()

        assertEquals(listOf("b", "a"), list.map { it.id })
        assertEquals(listOf(2, 5), list.map { it.sortId })
    }

    @Test
    fun demarshallingSkipsRecordsThatAreNotWellFormed() {
        val value = marshall(
            "no-delimiter-at-all",
            entry("empty", ""),
            entry("not-json", "{this is not json"),
            entry("boolean", "true"),
            entry("too", "many") + KEY_VALUE_DELIMITER + "parts",
            entry(makeStation("good", sortId = 1))
        )

        val list = mStorage.getAllFromString(value)

        assertEquals(listOf("good"), list.map { it.id })
    }

    @Test
    fun demarshallingSkipsRecordsWithoutAnIdentifier() {
        val value = marshall(
            entry("blank", "{\"Name\":\"No id\",\"StreamUrl\":\"$UNREACHABLE_ORIGIN/x\"}"),
            entry(makeStation("good", sortId = 1))
        )

        val list = mStorage.getAllFromString(value)

        assertEquals(listOf("good"), list.map { it.id })
    }

    @Test
    fun marshallingRoundTripsThroughDemarshalling() {
        mStorage.addAll(setOf(makeStation("a", sortId = 1), makeStation("b", sortId = 2)))

        val restored = mStorage.getAllFromString(mStorage.getAllAsString())

        assertEquals(setOf("a", "b"), restored.map { it.id }.toSet())
        assertEquals(
            "$UNREACHABLE_ORIGIN/a", restored.first { it.id == "a" }.getStreamUrlFixed()
        )
    }

    @Test
    fun readingBackIgnoresValuesThatAreNotStations() {
        mStorage.addAll(setOf(makeStation("a", sortId = 1)))
        mStorage.putBooleanValue("a-flag", true)
        mStorage.putStringValue("junk", "{this is not json")

        assertEquals(listOf("a"), mStorage.getAll().map { it.id })
    }

    @Test
    fun readingBackRenumbersSortIdsFromZero() {
        mStorage.addAll(
            setOf(
                makeStation("a", sortId = 30),
                makeStation("b", sortId = 10),
                makeStation("c", sortId = 20)
            )
        )

        val all = mStorage.getAll().toList()

        assertEquals(listOf("b", "c", "a"), all.map { it.id })
        assertEquals(listOf(0, 1, 2), all.map { it.sortId })
    }

    @Test
    fun readingBackAnUnknownKeyYieldsTheInvalidInstance() {
        assertTrue(mStorage.get("absent").isInvalid())
    }

    @Test
    fun theStorageKeyOfAStationIsItsMediaId() {
        assertEquals("a", mStorage.createKeyForRadioStation(makeStation("a")))
    }

    private fun newStorage(): FavoritesStorage {
        return FavoritesStorage(WeakReference(mContext))
    }

    private companion object {

        /**
         * An export produced by an earlier release, kept as the canonical demarshalling fixture.
         * Every url keeps its path but has had its origin replaced with [UNREACHABLE_ORIGIN], spelled
         * out because the export escapes its slashes.
         */
        const val DIRBLE_EXPORT = "56911<:>{\"Id\":56911,\"Name\":\"Radio El Alfarero\",\"Bitrate\":\"0\",\"Country\":\"CA\",\"Genre\":\"\",\"ImgUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56911\\/1ca6cde6-47ba-4b6e-a784-8c2f91103477.jpg\",\"StreamUrl\":\"http:\\/\\/127.0.0.1:9\\/index.html?sid=1\",\"Status\":0,\"ThumbUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56911\\/thumb_1ca6cde6-47ba-4b6e-a784-8c2f91103477.jpg\",\"Website\":\"http:\\/\\/127.0.0.1:9\",\"IsLocal\":false,\"SortId\":4}<<::>>57028<:>{\"Id\":57028,\"Name\":\"Radio Cafe Turc\",\"Bitrate\":\"320\",\"Country\":\"TR\",\"Genre\":\"\",\"ImgUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/57028\\/6a3255fc-8d55-46e3-ac07-75712fe752e7.jpg\",\"StreamUrl\":\"http:\\/\\/127.0.0.1:9\\/rct.mp3\",\"Status\":0,\"ThumbUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/57028\\/thumb_6a3255fc-8d55-46e3-ac07-75712fe752e7.jpg\",\"Website\":\"http:\\/\\/127.0.0.1:9\",\"IsLocal\":false,\"SortId\":2}<<::>>56722<:>{\"Id\":56722,\"Name\":\"Radiowaves\",\"Bitrate\":\"192\",\"Country\":\"CA\",\"Genre\":\"\",\"ImgUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56722\\/3b1f2b68-7f17-4158-96df-976ec41c5f66.jpg\",\"StreamUrl\":\"http:\\/\\/127.0.0.1:9\\/8897\",\"Status\":0,\"ThumbUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56722\\/thumb_3b1f2b68-7f17-4158-96df-976ec41c5f66.jpg\",\"Website\":\"\",\"IsLocal\":false,\"SortId\":7}<<::>>56973<:>{\"Id\":56973,\"Name\":\"House Nation Toronto Radio\",\"Bitrate\":\"128\",\"Country\":\"CA\",\"Genre\":\"\",\"ImgUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56973\\/bf422823-a224-4436-abb1-e6ee6dad9e08.jpg\",\"StreamUrl\":\"http:\\/\\/127.0.0.1:9\",\"Status\":0,\"ThumbUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56973\\/thumb_bf422823-a224-4436-abb1-e6ee6dad9e08.jpg\",\"Website\":\"http:\\/\\/127.0.0.1:9\\/sand-drift\",\"IsLocal\":false,\"SortId\":0}<<::>>56907<:>{\"Id\":56907,\"Name\":\"Rock 103\",\"Bitrate\":\"128\",\"Country\":\"CA\",\"Genre\":\"\",\"ImgUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56907\\/9e83203b-b5ed-4332-9e1a-2bbf627eda20.jpg\",\"StreamUrl\":\"http:\\/\\/127.0.0.1:9\\/9697\",\"Status\":0,\"ThumbUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56907\\/thumb_9e83203b-b5ed-4332-9e1a-2bbf627eda20.jpg\",\"Website\":\"http:\\/\\/127.0.0.1:9\",\"IsLocal\":false,\"SortId\":5}<<::>>57078<:>{\"Id\":57078,\"Name\":\"Xenxay's 70s & 80s Hits\",\"Bitrate\":\"0\",\"Country\":\"CA\",\"Genre\":\"\",\"ImgUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/57078\\/57d61792-f061-486d-b0f4-8c5440fad7d1.jpg\",\"StreamUrl\":\"http:\\/\\/127.0.0.1:9\\/xenxay-smusicmix\",\"Status\":0,\"ThumbUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/57078\\/thumb_57d61792-f061-486d-b0f4-8c5440fad7d1.jpg\",\"Website\":\"http:\\/\\/127.0.0.1:9\",\"IsLocal\":false,\"SortId\":3}<<::>>56721<:>{\"Id\":56721,\"Name\":\"Hot105 Non Stop\",\"Bitrate\":\"80\",\"Country\":\"CA\",\"Genre\":\"\",\"ImgUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56721\\/Hot105_cover.jpg\",\"StreamUrl\":\"http:\\/\\/127.0.0.1:9\\/stream\",\"Status\":0,\"ThumbUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56721\\/thumb_Hot105_cover.jpg\",\"Website\":\"http:\\/\\/127.0.0.1:9\\/radio\\/HOT105-Non-Stop-s275831\\/\",\"IsLocal\":false,\"SortId\":8}<<::>>56670<:>{\"Id\":56670,\"Name\":\"Hot105 Non Stop\",\"Bitrate\":\"24\",\"Country\":\"CA\",\"Genre\":\"\",\"ImgUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56670\\/HOT105_LOGO_NEW_3.jpeg\",\"StreamUrl\":\"http:\\/\\/127.0.0.1:9\\/stream\",\"Status\":0,\"ThumbUrl\":\"http:\\/\\/127.0.0.1:9\\/station\\/56670\\/thumb_HOT105_LOGO_NEW_3.jpeg\",\"Website\":\"http:\\/\\/127.0.0.1:9\\/hot105\\/hot105-non-stop\",\"IsLocal\":false,\"SortId\":6}"

    }
}
