package com.mareco.app

import com.mareco.app.core.GesturePlanner
import com.mareco.app.core.Scaler
import com.mareco.app.core.TimingPolicy
import com.mareco.app.data.ActionType
import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.ExecutionSettings
import com.mareco.app.data.MacroAction
import com.mareco.app.data.PathPoint
import com.mareco.app.data.ScaleMode
import com.mareco.app.data.StrokeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class GesturePlannerTest {
    private val screen = DeviceInfo(1080, 2400)
    private fun timing(speed: Float = 1f) = TimingPolicy(ExecutionSettings(speed = speed))
    private fun plan(a: MacroAction, speed: Float = 1f, simulate: Boolean = true, scaler: Scaler = Scaler.IDENTITY, cur: DeviceInfo = screen, humanize: Int = 0) =
        GesturePlanner.plan(a, scaler, cur, timing(speed), simulate, humanize, Random(3))

    @Test
    fun tapUsesRecordedPressTimeWhenSimulating() {
        val p = plan(MacroAction.tap(500f, 800f, 80))!!
        assertEquals(80L, p.strokes[0].durationMs)
        assertEquals(500f, p.strokes[0].xy[0], 0.001f)
        assertEquals(800f, p.strokes[0].xy[1], 0.001f)
    }

    @Test
    fun tapIsQuickAndClampedWhenNotSimulatingOrVeryFast() {
        assertEquals(30L, plan(MacroAction.tap(5f, 5f, 80), simulate = false)!!.strokes[0].durationMs)
        assertEquals(20L, plan(MacroAction.tap(5f, 5f, 80), speed = 10f)!!.strokes[0].durationMs)
    }

    @Test
    fun longPressNeverBecomesShorterThanAHold() {
        assertEquals(450L, plan(MacroAction.longPress(5f, 5f, 500), speed = 5f)!!.strokes[0].durationMs)
        assertEquals(1_000L, plan(MacroAction.longPress(5f, 5f, 1_000))!!.strokes[0].durationMs)
    }

    @Test
    fun swipeDurationScalesAndFastModeCapsIt() {
        assertEquals(1_000L, plan(MacroAction.swipe(0f, 0f, 100f, 0f, 1_000))!!.strokes[0].durationMs)
        assertEquals(500L, plan(MacroAction.swipe(0f, 0f, 100f, 0f, 1_000), speed = 2f)!!.strokes[0].durationMs)
        assertEquals(250L, plan(MacroAction.swipe(0f, 0f, 100f, 0f, 1_000), simulate = false)!!.strokes[0].durationMs)
        assertEquals(40L, plan(MacroAction.swipe(0f, 0f, 100f, 0f, 100), speed = 10f)!!.strokes[0].durationMs)
    }

    @Test
    fun dragFollowsTheRecordedCurveOnlyWhenSimulating() {
        val path = listOf(PathPoint(0f, 0f, 0), PathPoint(50f, 80f, 100), PathPoint(100f, 0f, 200))
        val drag = MacroAction(ActionType.DRAG, 0, 200, 0f, 0f, 100f, 0f, path = path)
        assertEquals(6, plan(drag)!!.strokes[0].xy.size)
        assertEquals(4, plan(drag, simulate = false)!!.strokes[0].xy.size)
    }

    @Test
    fun coordinatesAreScaledAndKeptOnScreen() {
        val cur = DeviceInfo(720, 1600)
        val scaler = Scaler(screen, cur, ScaleMode.SCALE)
        val xy = plan(MacroAction.tap(540f, 1200f, 60), scaler = scaler, cur = cur)!!.strokes[0].xy
        assertEquals(360f, xy[0], 0.01f)
        assertEquals(800f, xy[1], 0.01f)
        val edge = plan(MacroAction.tap(5000f, -20f, 60))!!.strokes[0].xy
        assertEquals(1079f, edge[0], 0.001f)
        assertEquals(0f, edge[1], 0.001f)
    }

    @Test
    fun multiTouchKeepsEveryFinger() {
        val a = MacroAction(
            ActionType.MULTI, 0, 0,
            strokes = listOf(
                StrokeData(0, listOf(PathPoint(100f, 100f, 0), PathPoint(200f, 200f, 300))),
                StrokeData(50, listOf(PathPoint(900f, 900f, 0), PathPoint(800f, 800f, 250)))
            )
        )
        val p = plan(a, speed = 2f)!!
        assertEquals(2, p.strokes.size)
        assertEquals(25L, p.strokes[1].startOffsetMs)
        assertEquals(150L, p.strokes[0].durationMs)
    }

    @Test
    fun humanizeOnlyMovesTapsALittle() {
        repeat(30) {
            val xy = plan(MacroAction.tap(500f, 800f, 60), humanize = 4)!!.strokes[0].xy
            assertTrue(xy[0] in 496f..504f)
            assertTrue(xy[1] in 796f..804f)
        }
    }

    @Test
    fun keysAndWaitsHaveNoFingerStrokes() {
        assertNull(plan(MacroAction.wait(100)))
        assertNull(plan(MacroAction.key(com.mareco.app.data.SystemKey.BACK)))
        assertNotNull(plan(MacroAction.tap(1f, 1f)))
    }
}
