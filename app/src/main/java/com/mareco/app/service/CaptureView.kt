package com.mareco.app.service

import android.content.Context
import android.view.MotionEvent

/**
 * Full-screen touch catcher used while recording. It converts MotionEvents into screen-coordinate
 * pointer events for the recorder. Android never lets an app watch touches that go to other apps,
 * so Mareco puts this transparent layer on top and (in "Live" mode) replays each finished gesture
 * to the app underneath right away.
 */
class CaptureView(ctx: Context, private val sink: Sink) : MarkerView(ctx) {

    interface Sink {
        fun down(id: Int, x: Float, y: Float, t: Long)
        fun move(id: Int, x: Float, y: Float, t: Long)
        fun up(id: Int, x: Float, y: Float, t: Long)
        fun cancel(t: Long)
    }

    var markersEnabled: Boolean = true

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Offset between this window and the screen (works on every Android version).
        val ox = e.rawX - e.x
        val oy = e.rawY - e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val x = e.getX(i) + ox
                val y = e.getY(i) + oy
                sink.down(id, x, y, e.eventTime)
                if (markersEnabled) fingerDown(id, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until e.historySize) {
                    for (i in 0 until e.pointerCount) {
                        sink.move(
                            e.getPointerId(i),
                            e.getHistoricalX(i, h) + ox,
                            e.getHistoricalY(i, h) + oy,
                            e.getHistoricalEventTime(h)
                        )
                    }
                }
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val x = e.getX(i) + ox
                    val y = e.getY(i) + oy
                    sink.move(id, x, y, e.eventTime)
                    if (markersEnabled) fingerMove(id, x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val x = e.getX(i) + ox
                val y = e.getY(i) + oy
                sink.up(id, x, y, e.eventTime)
                if (markersEnabled) fingerUp(id, x, y)
            }
            MotionEvent.ACTION_CANCEL -> {
                sink.cancel(e.eventTime)
                clearFingers()
            }
        }
        return true
    }
}
