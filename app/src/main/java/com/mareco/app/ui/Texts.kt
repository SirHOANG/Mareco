package com.mareco.app.ui

import android.content.Context
import com.mareco.app.R
import com.mareco.app.core.ScaleWarning
import com.mareco.app.core.Scaler
import com.mareco.app.data.ActionType
import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.Macro
import com.mareco.app.data.MacroAction
import com.mareco.app.data.SystemKey

/** Names, one-line summaries and icons for macro steps, in the language of the screen. */
object Texts {
    fun typeName(c: Context, t: ActionType): String = c.getString(
        when (t) {
            ActionType.TAP -> R.string.step_tap
            ActionType.LONG_PRESS -> R.string.step_long_press
            ActionType.SWIPE -> R.string.step_swipe
            ActionType.DRAG -> R.string.step_drag
            ActionType.MULTI -> R.string.step_multi
            ActionType.KEY -> R.string.step_key
            ActionType.WAIT -> R.string.step_wait
        }
    )

    fun keyLabel(c: Context, k: SystemKey?): String = c.getString(
        when (k) {
            SystemKey.BACK -> R.string.key_back
            SystemKey.HOME -> R.string.key_home
            SystemKey.RECENTS -> R.string.key_recents
            SystemKey.NOTIFICATIONS -> R.string.key_notifications
            SystemKey.QUICK_SETTINGS -> R.string.key_quick_settings
            null -> R.string.key_generic
        }
    )

    private fun p(x: Float, y: Float) = UiFmt.ltr("(${Math.round(x)}, ${Math.round(y)})")

    fun summary(c: Context, a: MacroAction): String = when (a.type) {
        ActionType.TAP -> c.getString(R.string.sum_tap, p(a.x, a.y))
        ActionType.LONG_PRESS -> c.getString(R.string.sum_long_press, p(a.x, a.y), UiFmt.duration(c, a.durationMs))
        ActionType.SWIPE -> c.getString(R.string.sum_swipe, p(a.x, a.y), p(a.x2, a.y2))
        ActionType.DRAG -> c.getString(R.string.sum_drag, p(a.x, a.y), p(a.x2, a.y2), a.path.size.toString())
        ActionType.MULTI -> c.getString(R.string.sum_multi, a.strokes.size.toString(), UiFmt.duration(c, a.busyMs))
        ActionType.KEY -> c.getString(R.string.sum_key, keyLabel(c, a.key))
        ActionType.WAIT -> c.getString(R.string.sum_wait, UiFmt.duration(c, a.durationMs))
    }

    fun icon(a: MacroAction): Int = when (a.type) {
        ActionType.TAP -> R.drawable.ic_tap
        ActionType.LONG_PRESS -> R.drawable.ic_hold
        ActionType.SWIPE, ActionType.DRAG -> R.drawable.ic_swipe
        ActionType.MULTI -> R.drawable.ic_multi
        ActionType.KEY -> R.drawable.ic_key
        ActionType.WAIT -> R.drawable.ic_timer
    }

    fun executed(c: Context, m: Macro): String =
        if (m.stats.runs <= 0) c.getString(R.string.row_never)
        else c.getString(R.string.row_executed, m.stats.runs.toString(), UiFmt.duration(c, m.stats.totalRuntimeMs))

    fun detail(c: Context, m: Macro): String {
        val base = c.getString(R.string.row_detail, m.actionCount.toString(), UiFmt.duration(c, m.loopLengthMs))
        return if (m.appLabel.isNotEmpty()) base + " · " + m.appLabel else base
    }

    /** "1080 × 2400", kept left-to-right inside right-to-left text. */
    fun size(d: DeviceInfo): String = UiFmt.ltr(d.label)

    /** Plain-language warnings about running a macro on a different screen than it was recorded on. */
    fun scaleWarnings(c: Context, rec: DeviceInfo, cur: DeviceInfo): List<String> =
        Scaler.warningList(rec, cur).map { w -> scaleWarning(c, w) }

    private fun scaleWarning(c: Context, w: ScaleWarning): String = when (w.kind) {
        ScaleWarning.Kind.ORIENTATION -> c.getString(
            R.string.warn_orientation,
            orientation(c, w.rec.isLandscape), orientation(c, w.cur.isLandscape)
        )
        ScaleWarning.Kind.SIZE -> c.getString(R.string.warn_size, size(w.rec), size(w.cur))
        ScaleWarning.Kind.ASPECT -> c.getString(R.string.warn_aspect)
    }

    private fun orientation(c: Context, landscape: Boolean): String =
        c.getString(if (landscape) R.string.orient_landscape else R.string.orient_portrait)
}
