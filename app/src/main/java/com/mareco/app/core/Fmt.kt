package com.mareco.app.core

import java.util.Locale

/** Small text formatters shared by the screens, the floating controls and the tests. */
object Fmt {
    fun duration(ms: Long): String {
        if (ms < 1_000L) return "$ms ms"
        if (ms < 60_000L) {
            return if (ms % 1_000L == 0L) "${ms / 1_000L} s" else String.format(Locale.US, "%.1f s", ms / 1_000.0)
        }
        val total = ms / 1_000L
        val h = total / 3_600L
        val m = (total % 3_600L) / 60L
        val s = total % 60L
        return when {
            h > 0L -> "$h h $m min"
            s > 0L -> "$m min $s s"
            else -> "$m min"
        }
    }

    fun clock(ms: Long): String {
        val t = ms / 1_000L
        return String.format(Locale.US, "%02d:%02d", t / 60L, t % 60L)
    }

    fun speed(v: Float): String = when {
        v >= 10f -> "${Math.round(v)}x"
        else -> String.format(Locale.US, "%.1fx", v)
    }

    fun times(n: Int): String = if (n == 1) "1 time" else "$n times"
}
