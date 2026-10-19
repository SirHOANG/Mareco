package com.mareco.app.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.SystemClock
import android.view.View

/**
 * Transparent full-screen view that draws touch feedback: a ripple for every tap, a trail and a dot
 * for every finger that is down, and (optionally) a red frame while recording.
 * All coordinates are SCREEN coordinates.
 */
open class MarkerView(ctx: Context) : View(ctx) {
    private class Ripple(val x: Float, val y: Float, val start: Long)

    private val ripples = ArrayList<Ripple>()
    private val fingers = HashMap<Int, PointF>()
    private val trails = HashMap<Int, Path>()
    private val density = ctx.resources.displayMetrics.density
    private val loc = IntArray(2)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val framePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        color = 0xB3FF3B30.toInt()
    }

    var showFrame: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    fun ripple(x: Float, y: Float) {
        ripples.add(Ripple(x, y, SystemClock.uptimeMillis()))
        postInvalidateOnAnimation()
    }

    fun fingerDown(id: Int, x: Float, y: Float) {
        fingers[id] = PointF(x, y)
        val p = Path()
        p.moveTo(x, y)
        trails[id] = p
        invalidate()
    }

    fun fingerMove(id: Int, x: Float, y: Float) {
        fingers[id]?.set(x, y)
        trails[id]?.lineTo(x, y)
        invalidate()
    }

    fun fingerUp(id: Int, x: Float, y: Float) {
        fingers.remove(id)
        trails.remove(id)
        ripple(x, y)
    }

    fun clearFingers() {
        fingers.clear()
        trails.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (showFrame) {
            val half = framePaint.strokeWidth / 2f
            canvas.drawRect(half, half, width - half, height - half, framePaint)
        }
        getLocationOnScreen(loc)
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())
        val now = SystemClock.uptimeMillis()
        val iter = ripples.iterator()
        while (iter.hasNext()) {
            val r = iter.next()
            val t = (now - r.start) / 450f
            if (t >= 1f) {
                iter.remove()
                continue
            }
            val radius = (14f + 26f * t) * density
            val alpha = ((1f - t) * 200f).toInt().coerceIn(0, 255)
            ringPaint.color = (alpha shl 24) or 0x0000B4FF
            canvas.drawCircle(r.x, r.y, radius, ringPaint)
            fillPaint.color = ((alpha / 3) shl 24) or 0x0000B4FF
            canvas.drawCircle(r.x, r.y, radius, fillPaint)
        }
        for ((id, p) in fingers) {
            trails[id]?.let {
                trailPaint.color = 0x9900B4FF.toInt()
                canvas.drawPath(it, trailPaint)
            }
            fillPaint.color = 0xCC00B4FF.toInt()
            canvas.drawCircle(p.x, p.y, 16f * density, fillPaint)
        }
        if (ripples.isNotEmpty()) postInvalidateOnAnimation()
    }
}
