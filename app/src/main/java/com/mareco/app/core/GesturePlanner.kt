package com.mareco.app.core

import com.mareco.app.data.ActionType
import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.MacroAction
import kotlin.math.max
import kotlin.math.min

/** One finger of a gesture, ready to hand to Android. [xy] holds x0,y0,x1,y1,... */
class StrokePlan(val startOffsetMs: Long, val durationMs: Long, val xy: FloatArray)

class GesturePlan(val strokes: List<StrokePlan>) {
    val totalMs: Long get() = strokes.maxOfOrNull { it.startOffsetMs + it.durationMs } ?: 0L
}

/** Pure logic: macro step + settings -> exact finger strokes. No Android classes involved. */
object GesturePlanner {

    fun plan(
        a: MacroAction,
        scaler: Scaler,
        cur: DeviceInfo,
        timing: TimingPolicy,
        simulate: Boolean,
        humanizePx: Int = 0,
        rnd: java.util.Random = java.util.Random()
    ): GesturePlan? {
        val maxX = if (cur.width > 0) cur.width - 1f else 100_000f
        val maxY = if (cur.height > 0) cur.height - 1f else 100_000f

        fun px(v: Float): Float = scaler.x(v).coerceIn(0f, maxX)
        fun py(v: Float): Float = scaler.y(v).coerceIn(0f, maxY)
        fun jitter(): Float =
            if (humanizePx > 0) (rnd.nextInt(2 * humanizePx + 1) - humanizePx).toFloat() else 0f

        fun single(xy: FloatArray, durationMs: Long) = GesturePlan(listOf(StrokePlan(0L, durationMs, xy)))

        when (a.type) {
            ActionType.TAP -> {
                val d = if (simulate) timing.strokeMs(max(1L, a.durationMs), Limits.MIN_TAP_MS) else Limits.FAST_TAP_MS
                val x = (px(a.x) + jitter()).coerceIn(0f, maxX)
                val y = (py(a.y) + jitter()).coerceIn(0f, maxY)
                return single(floatArrayOf(x, y), d)
            }
            ActionType.LONG_PRESS -> {
                val base = max(a.durationMs, Limits.LONG_PRESS_MIN_MS)
                val d = min(Limits.MAX_STROKE_MS, max(timing.strokeMs(base, Limits.MIN_TAP_MS), Limits.LONG_PRESS_MIN_MS))
                val x = (px(a.x) + jitter()).coerceIn(0f, maxX)
                val y = (py(a.y) + jitter()).coerceIn(0f, maxY)
                return single(floatArrayOf(x, y), d)
            }
            ActionType.SWIPE -> {
                var d = timing.strokeMs(max(1L, a.durationMs), Limits.MIN_SWIPE_MS)
                if (!simulate) d = min(d, Limits.FAST_SWIPE_MAX_MS)
                return single(straight(a, ::px, ::py, ::jitter), d)
            }
            ActionType.DRAG -> {
                var d = timing.strokeMs(max(1L, a.durationMs), Limits.MIN_SWIPE_MS)
                if (simulate && a.path.size >= 2) {
                    val xy = FloatArray(a.path.size * 2)
                    for (i in a.path.indices) {
                        xy[i * 2] = px(a.path[i].x)
                        xy[i * 2 + 1] = py(a.path[i].y)
                    }
                    return single(xy, d)
                }
                if (!simulate) d = min(d, Limits.FAST_SWIPE_MAX_MS)
                return single(straight(a, ::px, ::py, ::jitter), d)
            }
            ActionType.MULTI -> {
                val strokes = ArrayList<StrokePlan>()
                for (s in a.strokes.take(10)) {
                    if (s.points.isEmpty()) continue
                    val xy = FloatArray(s.points.size * 2)
                    for (i in s.points.indices) {
                        xy[i * 2] = px(s.points[i].x)
                        xy[i * 2 + 1] = py(s.points[i].y)
                    }
                    strokes.add(
                        StrokePlan(
                            timing.scaleMs(s.startOffsetMs),
                            timing.strokeMs(s.durationMs, Limits.MIN_TAP_MS),
                            xy
                        )
                    )
                }
                return if (strokes.isEmpty()) null else GesturePlan(strokes)
            }
            ActionType.KEY, ActionType.WAIT -> return null
        }
    }

    private fun straight(
        a: MacroAction,
        px: (Float) -> Float,
        py: (Float) -> Float,
        jitter: () -> Float
    ): FloatArray = floatArrayOf(
        px(a.x) + jitter(), py(a.y) + jitter(),
        px(a.x2) + jitter(), py(a.y2) + jitter()
    ).also { xy ->
        for (i in xy.indices) if (xy[i] < 0f) xy[i] = 0f
    }
}
