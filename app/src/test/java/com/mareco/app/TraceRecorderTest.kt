package com.mareco.app

import com.mareco.app.core.TraceRecorder
import com.mareco.app.data.ActionType
import com.mareco.app.data.MacroAction
import com.mareco.app.data.SystemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class TraceRecorderTest {
    private val seen = ArrayList<MacroAction>()
    private val rec = TraceRecorder(slopPx = 20f) { seen.add(it) }

    @Test
    fun tapIsRecordedWithHoldTimeAndDelay() {
        rec.begin(0)
        rec.down(0, 500f, 800f, 1_000)
        rec.up(0, 502f, 801f, 1_060)
        val a = rec.actions.single()
        assertEquals(ActionType.TAP, a.type)
        assertEquals(60L, a.durationMs)
        assertEquals(1_000L, a.delayMs)
        assertEquals(500f, a.x, 0.001f)
        assertEquals(1, seen.size)
        assertEquals(0L, rec.finish().first().delayMs)
    }

    @Test
    fun longPressNeedsToStayStill() {
        rec.begin(0)
        rec.down(0, 100f, 100f, 0)
        rec.move(0, 104f, 103f, 300)
        rec.up(0, 104f, 103f, 700)
        assertEquals(ActionType.LONG_PRESS, rec.actions.single().type)
        assertEquals(700L, rec.actions.single().durationMs)
    }

    @Test
    fun straightMovementIsASwipe() {
        rec.begin(0)
        rec.down(0, 100f, 500f, 0)
        for (i in 1..9) rec.move(0, 100f + i * 50f, 500f + (if (i % 2 == 0) 2f else -2f), i * 30L)
        rec.up(0, 600f, 500f, 300)
        val a = rec.actions.single()
        assertEquals(ActionType.SWIPE, a.type)
        assertEquals(600f, a.x2, 0.001f)
        assertEquals(300L, a.durationMs)
    }

    @Test
    fun curvedMovementIsADragWithAReducedPath() {
        rec.begin(0)
        rec.down(0, 500f, 500f, 0)
        for (i in 1..60) {
            val ang = PI * i / 60.0
            rec.move(0, 500f + 300f * cos(ang).toFloat(), 500f + 300f * sin(ang).toFloat(), i * 16L)
        }
        rec.up(0, 200f, 500f, 970)
        val a = rec.actions.single()
        assertEquals(ActionType.DRAG, a.type)
        assertTrue("path ${a.path.size}", a.path.size in 4..61)
        assertEquals(0L, a.path.first().t)
        assertEquals(970L, a.path.last().t)
    }

    @Test
    fun twoFingersBecomeOneMultiTouchStep() {
        rec.begin(0)
        rec.down(0, 300f, 800f, 100)
        rec.down(1, 700f, 800f, 130)
        rec.move(0, 400f, 800f, 300)
        rec.move(1, 600f, 800f, 300)
        rec.up(0, 400f, 800f, 400)
        assertTrue(rec.actions.isEmpty())
        rec.up(1, 600f, 800f, 420)
        val a = rec.actions.single()
        assertEquals(ActionType.MULTI, a.type)
        assertEquals(2, a.strokes.size)
        assertEquals(30L, a.strokes[1].startOffsetMs)
        assertEquals(100L, a.delayMs)
    }

    @Test
    fun delaysAreMeasuredFromTheEndOfThePreviousStep() {
        rec.begin(0)
        rec.down(0, 10f, 10f, 100); rec.up(0, 10f, 10f, 160)
        rec.down(0, 20f, 20f, 700); rec.up(0, 20f, 20f, 760)
        assertEquals(listOf(100L, 540L), rec.actions.map { it.delayMs })
        assertEquals(listOf(0L, 540L), rec.finish().map { it.delayMs })
    }

    @Test
    fun systemKeysAreRecordedBetweenTouches() {
        rec.begin(0)
        rec.down(0, 10f, 10f, 100); rec.up(0, 10f, 10f, 160)
        rec.key(SystemKey.BACK, 1_000)
        val k = rec.actions[1]
        assertEquals(ActionType.KEY, k.type)
        assertEquals(SystemKey.BACK, k.key)
        assertEquals(840L, k.delayMs)
    }

    @Test
    fun pausedTimeIsNotADelay() {
        rec.begin(0)
        rec.resume(5_000)
        rec.down(0, 10f, 10f, 5_100); rec.up(0, 10f, 10f, 5_150)
        assertEquals(100L, rec.actions.single().delayMs)
    }

    @Test
    fun stoppingMidTouchKeepsWhatWasDone() {
        rec.begin(0)
        rec.down(0, 10f, 10f, 100)
        val list = rec.finish()
        assertEquals(1, list.size)
    }
}
