package com.mareco.app.core

import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.ScaleMode
import kotlin.math.abs
import kotlin.math.min

/**
 * Maps recorded coordinates onto the current screen.
 * ORIGINAL keeps the numbers, SCALE stretches each axis, ASPECT keeps the aspect ratio (letterboxed).
 */
class Scaler(rec: DeviceInfo, cur: DeviceInfo, mode: ScaleMode) {
    private val sx: Float
    private val sy: Float
    private val ox: Float
    private val oy: Float

    init {
        val valid = rec.width > 0 && rec.height > 0 && cur.width > 0 && cur.height > 0
        if (!valid || mode == ScaleMode.ORIGINAL) {
            sx = 1f
            sy = 1f
            ox = 0f
            oy = 0f
        } else if (mode == ScaleMode.SCALE) {
            sx = cur.width.toFloat() / rec.width
            sy = cur.height.toFloat() / rec.height
            ox = 0f
            oy = 0f
        } else {
            val s = min(cur.width.toFloat() / rec.width, cur.height.toFloat() / rec.height)
            sx = s
            sy = s
            ox = (cur.width - rec.width * s) / 2f
            oy = (cur.height - rec.height * s) / 2f
        }
    }

    fun x(v: Float): Float = v * sx + ox
    fun y(v: Float): Float = v * sy + oy

    companion object {
        val IDENTITY = Scaler(DeviceInfo(0, 0), DeviceInfo(0, 0), ScaleMode.ORIGINAL)

        /** What is different between the screen a macro was recorded on and the one it runs on. */
        fun warningList(rec: DeviceInfo, cur: DeviceInfo): List<ScaleWarning> {
            val out = ArrayList<ScaleWarning>()
            if (rec.width <= 0 || rec.height <= 0 || cur.width <= 0 || cur.height <= 0) return out
            if (rec.isLandscape != cur.isLandscape) {
                out.add(ScaleWarning(ScaleWarning.Kind.ORIENTATION, rec, cur))
                return out
            }
            val dw = abs(cur.width - rec.width) / rec.width.toFloat()
            val dh = abs(cur.height - rec.height) / rec.height.toFloat()
            if (dw > 0.10f || dh > 0.10f) out.add(ScaleWarning(ScaleWarning.Kind.SIZE, rec, cur))
            val a1 = rec.width.toFloat() / rec.height
            val a2 = cur.width.toFloat() / cur.height
            if (abs(a1 - a2) / a1 > 0.05f) out.add(ScaleWarning(ScaleWarning.Kind.ASPECT, rec, cur))
            return out
        }

        /** The same warnings as plain English sentences (the app shows translated versions, see ui/Texts.kt). */
        fun warnings(rec: DeviceInfo, cur: DeviceInfo): List<String> = warningList(rec, cur).map { it.english() }
    }
}

/** One thing worth warning about before running a macro on a different screen. */
class ScaleWarning(val kind: Kind, val rec: DeviceInfo, val cur: DeviceInfo) {
    enum class Kind { ORIENTATION, SIZE, ASPECT }

    fun english(): String = when (kind) {
        Kind.ORIENTATION -> {
            val was = if (rec.isLandscape) "landscape" else "portrait"
            val now = if (cur.isLandscape) "landscape" else "portrait"
            "Recorded in $was but the screen is $now now. Rotate the phone back or taps will land in the wrong places."
        }
        Kind.SIZE -> "Recorded on ${rec.label}, this screen is ${cur.label}. Coordinates will be scaled, so test it before leaving it unattended."
        Kind.ASPECT -> "The screen shape (aspect ratio) is different, so scaled taps can be slightly off."
    }
}
