package com.mareco.app

import com.mareco.app.data.ActionType
import com.mareco.app.data.DelayMode
import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.ExecutionSettings
import com.mareco.app.data.Macro
import com.mareco.app.data.MacroAction
import com.mareco.app.data.MacroCodec
import com.mareco.app.data.MacroFormatException
import com.mareco.app.data.MacroStats
import com.mareco.app.data.PathPoint
import com.mareco.app.data.ScaleMode
import com.mareco.app.data.StopMode
import com.mareco.app.data.StrokeData
import com.mareco.app.data.SystemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MacroCodecTest {
    private fun sample() = Macro(
        id = "abc-123", name = "Farming Loop", description = "grind", createdAt = 1_700_000_000_000, modifiedAt = 1_700_000_100_000,
        device = DeviceInfo(1080, 2400, 420, 0, "Pixel", 37),
        appPackage = "com.game", appLabel = "Game",
        actions = listOf(
            MacroAction.tap(500f, 800f, 60, 0),
            MacroAction.longPress(10.5f, 20.5f, 900, 250),
            MacroAction.swipe(300f, 700f, 700f, 700f, 400, 1_000),
            MacroAction(ActionType.DRAG, 100, 300, 1f, 2f, 3f, 4f, path = listOf(PathPoint(1f, 2f, 0), PathPoint(2f, 3f, 150), PathPoint(3f, 4f, 300))),
            MacroAction(ActionType.MULTI, 5, 0, strokes = listOf(StrokeData(0, listOf(PathPoint(1f, 1f, 0), PathPoint(2f, 2f, 100))), StrokeData(20, listOf(PathPoint(5f, 5f, 0), PathPoint(6f, 6f, 80))))),
            MacroAction.key(SystemKey.HOME, 40),
            MacroAction.wait(2_000)
        ),
        execution = ExecutionSettings(
            stopMode = StopMode.TIME, count = 7, timeMs = 120_000, loopDelayMs = 2_000, speed = 1.5f, simulate = false, autoExecute = true,
            delayMode = DelayMode.CUSTOM, customDelayMs = 700, randomDelay = true, randomRangeMs = 150, humanize = true, humanizePx = 4,
            scaleMode = ScaleMode.ASPECT, lockToApp = true
        ),
        stats = MacroStats(12, 90_000, 1_700_000_200_000)
    )

    @Test
    fun everythingSurvivesAnEncodeDecodeRoundTrip() {
        val m = sample()
        assertEquals(m, MacroCodec.decode(MacroCodec.encode(m)))
        assertEquals(m, MacroCodec.decode(MacroCodec.encode(m, pretty = true)))
    }

    @Test
    fun spec_exampleFileLoads() {
        val json = """{"name":"Farming Macro","version":1,"actions":[{"type":"tap","x":500,"y":800,"delay":500}],
            "execution":{"mode":"infinite","speed":1.0,"simulate":true}}"""
        val m = MacroCodec.decode(json)
        assertEquals("Farming Macro", m.name)
        assertEquals(500L, m.actions[0].delayMs)
        assertEquals(StopMode.INFINITE, m.execution.stopMode)
        assertTrue(m.execution.simulate)
    }

    @Test
    fun garbageIsRejectedWithAFriendlyError() {
        for (bad in listOf("not json", "{}", """{"actions":[]}""", """{"format":"other","actions":[{"type":"tap"}]}""", """{"version":99,"actions":[{"type":"tap"}]}""")) {
            try {
                MacroCodec.decode(bad)
                fail("accepted: $bad")
            } catch (e: MacroFormatException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun unknownStepsAreSkippedAndValuesAreClamped() {
        val json = """{"actions":[{"type":"teleport"},{"type":"tap","x":1e30,"y":-5,"delay":-10,"duration":0},
            {"type":"wait","delay":999,"duration":999999999999},{"type":"key","key":"nonsense"}],
            "execution":{"speed":500,"count":-4,"mode":"weird"}}"""
        val m = MacroCodec.decode(json)
        assertEquals(2, m.actions.size)
        assertEquals(0L, m.actions[0].delayMs)
        assertEquals(1L, m.actions[0].durationMs)
        assertEquals(20_000f, m.actions[0].x, 0.001f)
        assertEquals(0L, m.actions[1].delayMs)
        assertEquals(86_400_000L, m.actions[1].durationMs)
        assertEquals(20f, m.execution.speed, 0.001f)
        assertEquals(1, m.execution.count)
        assertEquals(StopMode.INFINITE, m.execution.stopMode)
    }
}
