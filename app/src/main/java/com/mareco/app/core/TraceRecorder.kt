package com.mareco.app.core

import com.mareco.app.data.ActionType
import com.mareco.app.data.MacroAction
import com.mareco.app.data.PathPoint
import com.mareco.app.data.StrokeData
import com.mareco.app.data.SystemKey
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.max

/**
 * Turns raw finger events into structured macro steps (tap / long press / swipe / drag / multi-touch).
 * Pure logic: the Android layer only feeds it pointer events and key presses.
 *
 * [slopPx] is how far a finger may wander and still count as "not moved".
 * [onAction] is called as soon as a gesture is finished (used for Live pass-through).
 */
class TraceRecorder(
    private val slopPx: Float,
    private val onAction: (MacroAction) -> Unit = {}
) {
    private class Pt(val x: Float, val y: Float, val t: Long)
    private class Trace {
        val pts = ArrayList<Pt>()
    }

    private val out = ArrayList<MacroAction>()
    private val active = LinkedHashMap<Int, Trace>()
    private val done = ArrayList<Trace>()
    private var cursorT = 0L
    private var lastT = 0L

    val actions: List<MacroAction> get() = out
    val isTouching: Boolean get() = active.isNotEmpty()

    fun begin(nowMs: Long) {
        out.clear()
        active.clear()
        done.clear()
        cursorT = nowMs
        lastT = nowMs
    }

    /** Call when recording continues after a pause, so the paused time does not become a delay. */
    fun resume(nowMs: Long) {
        if (active.isEmpty() && done.isEmpty()) cursorT = nowMs
        lastT = nowMs
    }

    fun down(id: Int, x: Float, y: Float, t: Long) {
        val tr = Trace()
        tr.pts.add(Pt(x, y, t))
        active[id] = tr
        lastT = t
    }

    fun move(id: Int, x: Float, y: Float, t: Long) {
        val tr = active[id] ?: return
        val last = tr.pts.last()
        if (abs(x - last.x) >= 0.5f || abs(y - last.y) >= 0.5f) tr.pts.add(Pt(x, y, t))
        lastT = t
    }

    fun up(id: Int, x: Float, y: Float, t: Long) {
        val tr = active.remove(id) ?: return
        tr.pts.add(Pt(x, y, max(t, tr.pts.last().t)))
        done.add(tr)
        lastT = t
        if (active.isEmpty()) finalizeGesture()
    }

    /** Ends every finger that is still down (screen rotated, recording stopped mid-touch ...). */
    fun cancelAll(t: Long) {
        if (active.isEmpty()) return
        for (tr in active.values) {
            val last = tr.pts.last()
            tr.pts.add(Pt(last.x, last.y, max(t, last.t)))
            done.add(tr)
        }
        active.clear()
        finalizeGesture()
    }

    /** A hardware / system button press. Ignored while a finger is down. */
    fun key(key: SystemKey, t: Long) {
        if (active.isNotEmpty()) return
        push(MacroAction.key(key, max(0L, t - cursorT)))
        cursorT = t
        lastT = t
    }

    /** Finishes the recording. The first step never waits (the timeline starts at the first touch). */
    fun finish(): List<MacroAction> {
        if (active.isNotEmpty()) cancelAll(lastT)
        val list = ArrayList(out)
        if (list.isNotEmpty()) list[0] = list[0].copy(delayMs = 0L)
        return list
    }

    fun removeLast() {
        if (out.isNotEmpty()) out.removeAt(out.size - 1)
    }

    // ---------------------------------------------------------------- internals

    private fun push(a: MacroAction) {
        out.add(a)
        onAction(a)
    }

    private fun finalizeGesture() {
        if (done.isEmpty()) return
        val traces = done.toList()
        done.clear()
        val startT = traces.minOf { it.pts.first().t }
        val endT = traces.maxOf { it.pts.last().t }
        val base = if (traces.size == 1) classifySingle(traces[0]) else classifyMulti(traces)
        push(base.copy(delayMs = max(0L, startT - cursorT)))
        cursorT = endT
    }

    private fun classifySingle(tr: Trace): MacroAction {
        val first = tr.pts.first()
        val last = tr.pts.last()
        val dur = max(1L, last.t - first.t)
        var maxD = 0f
        for (p in tr.pts) maxD = max(maxD, hypot(p.x - first.x, p.y - first.y))
        if (maxD <= slopPx) {
            return if (dur >= LONG_PRESS_MS) MacroAction.longPress(first.x, first.y, dur)
            else MacroAction.tap(first.x, first.y, dur)
        }
        val len = hypot(last.x - first.x, last.y - first.y)
        var dev = 0f
        for (p in tr.pts) dev = max(dev, distToLine(p.x, p.y, first.x, first.y, last.x, last.y))
        if (len > 0f && dev <= max(slopPx * 1.5f, 0.06f * len)) {
            return MacroAction.swipe(first.x, first.y, last.x, last.y, dur)
        }
        val rel = tr.pts.map { PathPoint(it.x, it.y, it.t - first.t) }
        return MacroAction(
            type = ActionType.DRAG,
            durationMs = dur,
            x = first.x, y = first.y, x2 = last.x, y2 = last.y,
            path = reduce(rel)
        )
    }

    private fun classifyMulti(traces: List<Trace>): MacroAction {
        val t0 = traces.minOf { it.pts.first().t }
        val strokes = traces.sortedBy { it.pts.first().t }.take(MAX_STROKES).map { tr ->
            val f = tr.pts.first()
            StrokeData(f.t - t0, reduce(tr.pts.map { PathPoint(it.x, it.y, it.t - f.t) }))
        }
        return MacroAction(ActionType.MULTI, 0L, 0L, strokes = strokes)
    }

    private fun reduce(points: List<PathPoint>): List<PathPoint> {
        var eps = max(1.2f, slopPx * 0.2f)
        var res = simplify(points, eps)
        while (res.size > MAX_PATH_POINTS) {
            eps *= 1.6f
            res = simplify(points, eps)
        }
        return res
    }

    companion object {
        const val LONG_PRESS_MS = 400L
        private const val MAX_PATH_POINTS = 300
        private const val MAX_STROKES = 10

        /** Perpendicular distance of a point from the line a-b (distance to a when a == b). */
        internal fun distToLine(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax
            val dy = by - ay
            val len = hypot(dx, dy)
            if (len < 1e-3f) return hypot(px - ax, py - ay)
            return abs(dx * (ay - py) - (ax - px) * dy) / len
        }

        /** Ramer-Douglas-Peucker, iterative (no recursion depth problems). */
        internal fun simplify(pts: List<PathPoint>, eps: Float): List<PathPoint> {
            if (pts.size <= 2) return pts
            val keep = BooleanArray(pts.size)
            keep[0] = true
            keep[pts.size - 1] = true
            val stackS = IntArray(pts.size)
            val stackE = IntArray(pts.size)
            var sp = 0
            stackS[sp] = 0
            stackE[sp] = pts.size - 1
            sp++
            while (sp > 0) {
                sp--
                val s = stackS[sp]
                val e = stackE[sp]
                var maxD = -1f
                var idx = -1
                for (i in s + 1 until e) {
                    val d = distToLine(pts[i].x, pts[i].y, pts[s].x, pts[s].y, pts[e].x, pts[e].y)
                    if (d > maxD) {
                        maxD = d
                        idx = i
                    }
                }
                if (idx != -1 && maxD > eps && sp + 2 <= stackS.size) {
                    keep[idx] = true
                    stackS[sp] = s
                    stackE[sp] = idx
                    sp++
                    stackS[sp] = idx
                    stackE[sp] = e
                    sp++
                }
            }
            val res = ArrayList<PathPoint>()
            for (i in pts.indices) if (keep[i]) res.add(pts[i])
            return res
        }
    }
}
