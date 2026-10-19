package com.mareco.app.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import com.mareco.app.R
import com.mareco.app.data.Macro

/**
 * One macro in a list, laid out like the reference "Operation Recorder" row:
 * name + "Executed N times | W x H", then the tools (execution settings, edit, duplicate, share,
 * delete) and the big play button. A button only appears when its callback is given.
 */
object MacroRow {
    class Callbacks(
        val onSelect: ((Macro) -> Unit)? = null,
        val onSettings: ((Macro) -> Unit)? = null,
        val onEdit: ((Macro) -> Unit)? = null,
        val onDuplicate: ((Macro) -> Unit)? = null,
        val onShare: ((Macro) -> Unit)? = null,
        val onDelete: ((Macro) -> Unit)? = null,
        val onPlay: ((Macro) -> Unit)? = null
    )

    fun build(ctx: Context, m: Macro, isDefault: Boolean, cb: Callbacks): View {
        val card = ctx.vbox()
        card.background = ctx.ripple(Pal.surface, 16f, if (isDefault) Pal.accent else Pal.stroke, if (isDefault) 2 else 1)
        card.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(12), ctx.dp(10))

        val top = ctx.hbox()
        val info = ctx.vbox()
        info.add(ctx.text(m.name, 17f, bold = true).also { it.setSingleLine(true); it.ellipsize = android.text.TextUtils.TruncateAt.END })
        info.add(ctx.text(Texts.executed(ctx, m) + "  |  " + Texts.size(m.device), 12.5f, Pal.textDim), t = 3)
        info.add(ctx.text(Texts.detail(ctx, m), 12.5f, Pal.textDim), t = 1)
        if (cb.onSelect != null) {
            info.isClickable = true
            info.setOnClickListener { cb.onSelect.invoke(m) }
        }
        top.add(info, 0, WRAP, 1f, r = 8)
        cb.onPlay?.let { play ->
            top.add(ctx.iconBtn(R.drawable.ic_play, Pal.onAccent, 26, 50, Pal.accent, ctx.getString(R.string.cd_run)) { play(m) }, WRAP, WRAP)
        }
        card.add(top)

        val tools = ctx.hbox()
        tools.gravity = Gravity.CENTER_VERTICAL
        fun tool(res: Int, tint: Int, descId: Int, f: ((Macro) -> Unit)?) {
            if (f != null) tools.add(ctx.iconBtn(res, tint, 20, 38, 0, ctx.getString(descId)) { f(m) }, WRAP, WRAP, r = 2)
        }
        tool(R.drawable.ic_tune, Pal.text, R.string.cd_settings, cb.onSettings)
        tool(R.drawable.ic_edit, Pal.text, R.string.cd_edit, cb.onEdit)
        tool(R.drawable.ic_copy, Pal.text, R.string.cd_duplicate, cb.onDuplicate)
        tool(R.drawable.ic_share, Pal.text, R.string.share, cb.onShare)
        tool(R.drawable.ic_delete, Pal.danger, R.string.delete, cb.onDelete)
        val spacer = View(ctx)
        tools.add(spacer, 0, 1, 1f)
        if (isDefault) {
            val badge = ctx.text(ctx.getString(R.string.row_default), 11f, Pal.onAccent, true)
            badge.setPadding(ctx.dp(8), ctx.dp(3), ctx.dp(8), ctx.dp(3))
            badge.background = shape(Pal.accent, ctx.dpf(8f))
            tools.add(badge, WRAP, WRAP)
        }
        card.add(tools, t = 6)
        return card
    }
}
