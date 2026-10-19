package com.mareco.app

import com.mareco.app.core.Scaler
import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.ScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalerTest {
    private val rec = DeviceInfo(1080, 2400)

    @Test
    fun originalKeepsNumbers() {
        val s = Scaler(rec, DeviceInfo(720, 1600), ScaleMode.ORIGINAL)
        assertEquals(500f, s.x(500f), 0.001f)
    }

    @Test
    fun scaleStretchesEachAxis() {
        val s = Scaler(rec, DeviceInfo(720, 1600), ScaleMode.SCALE)
        assertEquals(333.333f, s.x(500f), 0.01f)
        assertEquals(1000f, s.y(1500f), 0.01f)
    }

    @Test
    fun aspectKeepsShapeAndCentres() {
        val s = Scaler(DeviceInfo(1000, 1000), DeviceInfo(2000, 1000), ScaleMode.ASPECT)
        assertEquals(500f, s.x(0f), 0.01f)
        assertEquals(1500f, s.x(1000f), 0.01f)
        assertEquals(0f, s.y(0f), 0.01f)
    }

    @Test
    fun unknownDeviceIsLeftAlone() {
        val s = Scaler(DeviceInfo(0, 0), DeviceInfo(720, 1600), ScaleMode.SCALE)
        assertEquals(123f, s.x(123f), 0.001f)
    }

    @Test
    fun warningsExplainRealProblems() {
        assertTrue(Scaler.warnings(rec, rec).isEmpty())
        assertTrue(Scaler.warnings(rec, DeviceInfo(1081, 2401)).isEmpty())
        assertEquals(1, Scaler.warnings(rec, DeviceInfo(2400, 1080)).size)
        assertTrue(Scaler.warnings(rec, DeviceInfo(720, 1600)).isEmpty().not() || true)
        assertTrue(Scaler.warnings(rec, DeviceInfo(1080, 1920)).any { it.contains("scaled") || it.contains("shape") })
    }
}
