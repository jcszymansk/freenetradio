/*
 * Copyright 2024 The "Open Radio" Project. Author: Chernyshov Yuriy
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

package com.yuriy.openradio.shared.model.filter

import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.setVariant
import org.junit.Assert
import org.junit.Test

class FilterImplTest {

    private val mFilter = FilterImpl()

    @Test
    fun testFilter1() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.setVariant(125, "stream.radiojar.com/z4qyckhr9druv")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter2() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.setVariant(125, "air.pc.cdn.bitgravity.com/air/live/pbaudio174")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter3() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.setVariant(125, "air.pc.cdn.bitgravity.com/air/live/pbaudio175")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter4() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.setVariant(125, "playerservices.streamtheworld.com/api/livestream-redirect/HYD_TEL_GSTAAC")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun emptyStreamIsRejected() {
        val rs = RadioStation.makeDefaultInstance("123")
        Assert.assertFalse(mFilter.filter(rs))
    }

    @Test
    fun testFilter8() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.name = "Radio"
        rs.homePage = "radio.com"
        rs.setVariant(125, "stream.radiojar.com")
        Assert.assertFalse(mFilter.filter(rs))
    }

    @Test
    fun testFilter9() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.name = "exclusiu digital (3cat)"
        rs.homePage = ""
        rs.setVariant(96, "https://directes-radio-int.ccma.cat/live-content/radio-oca-hls/master.m3u8")
        Assert.assertFalse(mFilter.filter(rs))
    }

    @Test
    fun testFilter10() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.name = "Radio Rainbow"
        rs.homePage = ""
        rs.setVariant(96, "directes-radio-int.ccma.cat")
        Assert.assertFalse(mFilter.filter(rs))
    }

    @Test
    fun matchesHomePageCaseInsensitively() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.homePage = "HTTPS://RADIOSINDIA.COM/station"
        rs.setVariant(125, "https://unrelated.example/stream")

        Assert.assertTrue(mFilter.filter(rs))
    }
}
