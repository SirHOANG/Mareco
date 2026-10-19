package com.mareco.app

import com.mareco.app.core.Fmt
import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {
    @Test
    fun durationsReadNaturally() {
        assertEquals("250 ms", Fmt.duration(250))
        assertEquals("1 s", Fmt.duration(1_000))
        assertEquals("1.5 s", Fmt.duration(1_500))
        assertEquals("2 min 5 s", Fmt.duration(125_000))
        assertEquals("3 min", Fmt.duration(180_000))
        assertEquals("1 h 3 min", Fmt.duration(3_780_000))
    }

    @Test
    fun clockAndSpeedAndTimes() {
        assertEquals("00:13", Fmt.clock(13_400))
        assertEquals("12:05", Fmt.clock(725_000))
        assertEquals("1.0x", Fmt.speed(1f))
        assertEquals("0.5x", Fmt.speed(0.5f))
        assertEquals("10x", Fmt.speed(10f))
        assertEquals("1 time", Fmt.times(1))
        assertEquals("3 times", Fmt.times(3))
    }
}
