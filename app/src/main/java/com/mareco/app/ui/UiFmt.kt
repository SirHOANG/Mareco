package com.mareco.app.ui

import android.content.Context
import com.mareco.app.Lang
import com.mareco.app.R
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Number and time formatting for the screens, in the language of the screen.
 * (core/Fmt.kt is the plain English version used by the tests.) Digits stay 0-9 everywhere so that
 * the numbers in the app always look the same, whatever the language; only the units and the
 * decimal separator follow the language.
 */
object UiFmt {
    private fun separator(c: Context): Char =
        try {
            DecimalFormatSymbols.getInstance(Lang.locale(c)).decimalSeparator
        } catch (e: Exception) {
            '.'
        }

    /** [v] with [decimals] digits after the separator of the current language, e.g. "1,5" in Vietnamese. */
    fun decimal(c: Context, v: Double, decimals: Int): String {
        val plain = String.format(Locale.US, "%.${decimals}f", v)
        val sep = separator(c)
        return if (sep == '.') plain else plain.replace('.', sep)
    }

    fun duration(c: Context, ms: Long): String {
        if (ms < 1_000L) return "$ms ms"
        if (ms < 60_000L) {
            val n = if (ms % 1_000L == 0L) (ms / 1_000L).toString() else decimal(c, ms / 1_000.0, 1)
            return c.getString(R.string.fmt_s, n)
        }
        val total = ms / 1_000L
        val h = total / 3_600L
        val m = (total % 3_600L) / 60L
        val s = total % 60L
        return when {
            h > 0L -> c.getString(R.string.fmt_h_min, h.toString(), m.toString())
            s > 0L -> c.getString(R.string.fmt_min_s, m.toString(), s.toString())
            else -> c.getString(R.string.fmt_min, m.toString())
        }
    }

    /** "1.5x" (10x and above without decimals). */
    fun speed(c: Context, v: Float): String =
        if (v >= 10f) "${Math.round(v)}x" else decimal(c, v.toDouble(), 1) + "x"

    /** A whole number of seconds, e.g. "3 s". */
    fun seconds(c: Context, n: Int): String = c.getString(R.string.fmt_s, n.toString())

    /** "“Name”" with the quote marks of the current language. */
    fun quoted(c: Context, name: String): String = c.getString(R.string.fmt_quoted, name)

    /**
     * Wraps text that must always read left-to-right (coordinates, sizes) so it stays in order inside
     * right-to-left sentences. The wrapper characters are invisible.
     */
    fun ltr(s: String): String = "⁦" + s + "⁩"
}
