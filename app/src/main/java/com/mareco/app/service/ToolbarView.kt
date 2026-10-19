package com.mareco.app.service

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mareco.app.EngineState
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.core.BarSymbols
import com.mareco.app.core.MainSymbol
import com.mareco.app.core.Platform
import com.mareco.app.data.AppSettings
import com.mareco.app.ui.WRAP
import com.mareco.app.ui.dp
import com.mareco.app.ui.dpf
import com.mareco.app.ui.shape
import com.mareco.app.ui.text

/** What the status block under the buttons shows. */
class Hud(
    val engine: EngineState,
    val recPaused: Boolean,
    val countdown: Int,
    val line1: String,
    val line2: String,
    val color: Int
)

/**
 * The floating vertical bar. Four things only:
 *
 *  - X        closes the bar (open it again from the app)
 *  - blue ●/▶/■ the one main button. In Record mode it is the record button ● (press to start recording);
 *             in Start mode it is ▶ (press to run the macro). While recording or running it becomes ■:
 *             press it to stop (a recording is saved). Hold it to pause. See [BarSymbols] for the exact rules.
 *  - gear     the settings panel (mode, default macro, every setting)
 *  - arrows   drag handle
 */
class ToolbarView(ctx: Context, private val ctl: MarecoController) : LinearLayout(ctx) {
    private val btnClose = ImageView(ctx)
    private val btnMain = ImageView(ctx)
    private val btnSettings = ImageView(ctx)
    private val btnMove = ImageView(ctx)
    private val status1: TextView = ctx.text("", 9.5f, 0xFFFFFFFF.toInt(), true)
    private val status2: TextView = ctx.text("", 9.5f, 0xFFB8C0CC.toInt(), false)
    private var lastX = 0f
    private var lastY = 0f

    /** Name of what the main button does right now (screen readers, and shown when held while idle). */
    private var mainLabel = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        build()
    }

    /** Rebuilds the buttons (after a size or theme change). */
    fun rebuild() {
        removeAllViews()
        build()
    }

    private fun build() {
        val ctx = context
        val settings = Mareco.settings
        background = shape(0xEB111318.toInt(), ctx.dpf(28f), 0x40FFFFFF, 1)
        setPadding(ctx.dp(4), ctx.dp(6), ctx.dp(4), ctx.dp(6))
        alpha = settings.overlayAlpha
        val icon = ctx.dp(settings.barSize.iconDp)
        val pad = ctx.dp(8)
        val box = icon + pad * 2

        // X: smaller than the other buttons, so it is not hit by accident
        val closeIcon = (icon * 0.62f).toInt()
        val closePad = ctx.dp(6)
        val closeBox = closeIcon + closePad * 2
        btnClose.setImageResource(R.drawable.ic_tb_close)
        btnClose.scaleType = ImageView.ScaleType.FIT_CENTER
        btnClose.setPadding(closePad, closePad, closePad, closePad)
        btnClose.contentDescription = ctx.getString(R.string.bar_cd_close)
        btnClose.isClickable = true
        btnClose.setOnClickListener { ctl.onClose() }
        btnClose.setOnLongClickListener {
            Platform.toast(ctx, ctx.getString(R.string.bar_cd_close))
            true
        }
        addView(btnClose, LayoutParams(closeBox, closeBox))

        // the main button: ● (Record mode), ▶ (Start mode) or ■; render() keeps it up to date
        btnMain.setImageResource(
            if (settings.mode == AppSettings.Mode.RECORD) R.drawable.ic_tb_record else R.drawable.ic_tb_play
        )
        btnMain.scaleType = ImageView.ScaleType.FIT_CENTER
        btnMain.setPadding(pad, pad, pad, pad)
        btnMain.isClickable = true
        btnMain.setOnClickListener { ctl.onPlay() }
        btnMain.setOnLongClickListener {
            // Pauses / resumes / stops when that makes sense; otherwise just shows the button's name.
            if (!ctl.onPlayLong()) Platform.toast(ctx, mainLabel)
            true
        }
        addView(btnMain, LayoutParams(box, box))

        // the gear
        btnSettings.setImageResource(R.drawable.ic_tb_settings)
        btnSettings.scaleType = ImageView.ScaleType.FIT_CENTER
        btnSettings.setPadding(pad, pad, pad, pad)
        btnSettings.contentDescription = ctx.getString(R.string.bar_cd_settings)
        btnSettings.isClickable = true
        btnSettings.setOnClickListener { ctl.onSettings() }
        btnSettings.setOnLongClickListener {
            Platform.toast(ctx, ctx.getString(R.string.bar_cd_settings))
            true
        }
        addView(btnSettings, LayoutParams(box, box))

        // the drag handle
        btnMove.setImageResource(R.drawable.ic_tb_move)
        btnMove.scaleType = ImageView.ScaleType.FIT_CENTER
        btnMove.setPadding(pad, pad, pad, pad)
        btnMove.contentDescription = ctx.getString(R.string.bar_cd_move)
        btnMove.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX
                    lastY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    ctl.overlay.moveToolbarBy((e.rawX - lastX).toInt(), (e.rawY - lastY).toInt())
                    lastX = e.rawX
                    lastY = e.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ctl.overlay.saveToolbarPosition()
                    true
                }
                else -> false
            }
        }
        addView(btnMove, LayoutParams(box, box))

        // the two tiny status lines: text shrinks to fit, whatever the language
        fitLine(status1)
        fitLine(status2)
        status1.gravity = Gravity.CENTER
        status2.gravity = Gravity.CENTER
        status1.setOnClickListener { ctl.toast(ctl.statusText()) }
        status2.setOnClickListener { ctl.toast(ctl.statusText()) }
        (status1.parent as? ViewGroup)?.removeView(status1)
        (status2.parent as? ViewGroup)?.removeView(status2)
        addView(status1, LayoutParams(box, WRAP))
        addView(status2, LayoutParams(box, WRAP))
    }

    private fun fitLine(t: TextView) {
        t.setSingleLine(true)
        t.ellipsize = TextUtils.TruncateAt.END
        t.setAutoSizeTextTypeUniformWithConfiguration(6, 10, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    fun render(h: Hud) {
        val ctx = context
        val counting = h.countdown > 0
        val recording = h.engine == EngineState.RECORDING
        val recordMode = Mareco.settings.mode == AppSettings.Mode.RECORD
        btnMain.setImageResource(
            when (BarSymbols.main(h.engine, h.recPaused, counting, recordMode)) {
                MainSymbol.STOP -> R.drawable.ic_tb_stop
                MainSymbol.RECORD -> R.drawable.ic_tb_record
                MainSymbol.PLAY -> R.drawable.ic_tb_play
            }
        )
        mainLabel = ctx.getString(
            when {
                counting -> R.string.bar_cd_cancel
                recording && h.recPaused -> R.string.bar_cd_resume
                recording -> R.string.bar_cd_stop_save
                h.engine == EngineState.RUNNING -> R.string.bar_cd_stop
                h.engine == EngineState.PAUSED -> R.string.bar_cd_resume
                recordMode -> R.string.bar_cd_record
                else -> R.string.bar_cd_run
            }
        )
        btnMain.contentDescription = mainLabel
        val busy = recording || h.engine == EngineState.RUNNING || h.engine == EngineState.PAUSED || counting
        btnClose.alpha = if (busy) 0.35f else 1f
        status1.text = h.line1
        status1.setTextColor(h.color)
        status2.text = h.line2
    }

    fun applyAppearance() {
        alpha = Mareco.settings.overlayAlpha
    }

    companion object {
        fun modeColor(mode: AppSettings.Mode): Int =
            if (mode == AppSettings.Mode.RECORD) 0xFFFF6B6B.toInt() else 0xFF4ADE80.toInt()
    }
}
