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
        rs.name = "F/M Rainbow Vijayawada"
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter2() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.name = "Radio MIrchi Telugu"
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter3() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.homePage = "radiosindia.com"
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter4() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.setVariant(125, "stream.radiojar.com/z4qyckhr9druv")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter5() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.homePage = "radiosindia.com"
        rs.setVariant(125, "stream.radiojar.com/z4qyckhr9druv")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter6() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.name = "Radio MIrchi Telugu"
        rs.homePage = "radiosindia.com"
        rs.setVariant(125, "stream.radiojar.com/z4qyckhr9druv")
        Assert.assertTrue(mFilter.filter(rs))
    }

    @Test
    fun testFilter7() {
        val rs = RadioStation.makeDefaultInstance("123")
        Assert.assertFalse(mFilter.filter(rs))
    }

    @Test
    fun testFilter8() {
        val rs = RadioStation.makeDefaultInstance("123")
        rs.name = "Radio"
        rs.homePage = "radio.com"
        rs.setVariant(125, "stream.radiojar.com")
        Assert.assertTrue(mFilter.filter(rs))
    }
}
