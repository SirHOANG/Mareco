package com.mareco.app.core

import com.mareco.app.data.DelayMode
import com.mareco.app.data.ExecutionSettings
import com.mareco.app.data.MacroAction
import kotlin.math.max
import kotlin.math.min

/** Timing limits that Android's input system can execute reliably. */
object Limits {
    const val MIN_TAP_MS = 20L
    const val FAST_TAP_MS = 30L
    const val MIN_SWIPE_MS = 40L
    const val FAST_SWIPE_MAX_MS = 250L
    const val LONG_PRESS_MIN_MS = 450L
    const val MAX_STROKE_MS = 59_000L
    const val MIN_LOOP_MS = 50L
}

/** Turns recorded delays / durations into the real ones, using speed, custom and random delay. */
class TimingPolicy(
    private val s: ExecutionSettings,
    private val rnd: java.util.Random = java.util.Random()
) {
    val speed: Float = s.speed.coerceIn(0.1f, 20f)

    fun scaleMs(ms: Long): Long = max(0L, Math.round(ms / speed.toDouble()))

    /** Wait before a step. The very first step of a loop never gets the custom delay. */
    fun gapMs(a: MacroAction, first: Boolean): Long {
        if (first) return scaleMs(a.delayMs)
        val base = if (s.delayMode == DelayMode.CUSTOM) s.customDelayMs else a.delayMs
        return scaleMs(jitter(base))
    }

    fun waitMs(a: MacroAction): Long = scaleMs(jitter(a.durationMs))

    /** Duration of a finger stroke, kept inside what Android can execute. */
    fun strokeMs(ms: Long, minMs: Long): Long =
        min(Limits.MAX_STROKE_MS, max(minMs, Math.round(ms / speed.toDouble())))

    private fun jitter(d: Long): Long {
        if (!s.randomDelay || s.randomRangeMs <= 0L) return d
        val r = min(s.randomRangeMs, 3_600_000L).toInt()
        return max(0L, d + (rnd.nextInt(2 * r + 1) - r))
    }
}
