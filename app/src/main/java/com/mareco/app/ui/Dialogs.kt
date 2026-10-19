package com.mareco.app.ui

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.mareco.app.Lang
import com.mareco.app.Languages
import com.mareco.app.R
import com.mareco.app.core.Platform
import com.mareco.app.data.ActionType
import com.mareco.app.data.DelayMode
import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.ExecutionSettings
import com.mareco.app.data.MacroAction
import com.mareco.app.data.ScaleMode
import com.mareco.app.data.StopMode
import com.mareco.app.data.SystemKey
import kotlin.math.abs

internal fun Context.editField(initial: String, hint: String = ""): EditText {
    val e = EditText(this)
    e.setText(initial)
    e.hint = hint
    e.setTextColor(Pal.text)
    e.setHintTextColor(Pal.textDim)
    e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    e.setSingleLine(true)
    e.imeOptions = EditorInfo.IME_ACTION_DONE
    e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    e.background = shape(Pal.surface2, dpf(12f))
    e.setPadding(dp(14), 0, dp(14), 0)
    return e
}

/** Label above, control below (works on narrow phones). */
internal fun Context.stackRow(label: String, control: View): LinearLayout {
    val col = vbox()
    col.setPadding(0, dp(6), 0, dp(6))
    col.add(text(label, 13.5f, Pal.textDim))
    col.add(control, WRAP, WRAP, t = 4)
    return col
}

/** Dialog bodies that work both in the app and inside the floating overlay. */
object Dialogs {

    fun message(host: DialogHost, title: String, text: String, ok: String = host.context.getString(R.string.ok)) {
        val c = host.context
        val box = c.vbox()
        box.add(c.text(title, 19f, bold = true))
        box.add(c.text(text, 14.5f, Pal.textDim), t = 10)
        var h: DialogHandle? = null
        box.add(c.button(ok) { h?.dismiss() }, t = 16)
        h = host.show(box, scroll = true)
    }

    fun confirm(
        host: DialogHost, title: String, message: String,
        positive: String = host.context.getString(R.string.ok),
        negative: String = host.context.getString(R.string.cancel),
        danger: Boolean = false,
        onResult: (Boolean) -> Unit
    ) {
        val c = host.context
        val box = c.vbox()
        box.add(c.text(title, 19f, bold = true))
        box.add(c.text(message, 14.5f, Pal.textDim), t = 10)
        var h: DialogHandle? = null
        val row = c.hbox()
        row.add(c.button(negative, Btn.SECONDARY) { h?.dismiss(); onResult(false) }, 0, WRAP, 1f, r = 6)
        row.add(c.button(positive, if (danger) Btn.DANGER else Btn.PRIMARY) { h?.dismiss(); onResult(true) }, 0, WRAP, 1f, l = 6)
        box.add(row, t = 18)
        h = host.show(box, scroll = true, onCancel = { onResult(false) })
    }

    fun choice(host: DialogHost, title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val c = host.context
        val box = c.vbox()
        box.add(c.text(title, 19f, bold = true))
        box.addView(c.space(8))
        var h: DialogHandle? = null
        options.forEachIndexed { i, label ->
            val row = c.hbox()
            row.setPadding(c.dp(10), c.dp(12), c.dp(10), c.dp(12))
            row.background = c.ripple(if (i == selected) Pal.surface2 else 0, 12f)
            row.add(c.text(label, 15.5f, if (i == selected) Pal.accent else Pal.text, i == selected), 0, WRAP, 1f)
            if (i == selected) row.add(c.iconBtn(R.drawable.ic_check, Pal.accent, 20, 24) {}, WRAP, WRAP)
            row.isClickable = true
            row.setOnClickListener { h?.dismiss(); onPick(i) }
            box.add(row, t = 2)
        }
        h = host.show(box, scroll = true)
    }

    /** "Record completed, saved as: [name] [Save] [Don't save]" body. */
    fun nameContent(
        ctx: Context, title: String, initial: String, positive: String, negative: String?,
        onPositive: (String) -> Unit, onNegative: () -> Unit
    ): View {
        val box = ctx.vbox()
        box.add(ctx.text(title, 18f, bold = true))
        val input = ctx.editField(initial, ctx.getString(R.string.name_hint))
        box.add(input, MATCH, ctx.dp(52), t = 14)
        val row = ctx.hbox()
        row.add(ctx.button(positive) { onPositive(input.text.toString()) }, 0, WRAP, 1f, r = 6)
        if (negative != null) row.add(ctx.button(negative, Btn.SECONDARY) { onNegative() }, 0, WRAP, 1f, l = 6)
        box.add(row, t = 18)
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) {
                onPositive(input.text.toString())
                true
            } else {
                false
            }
        }
        input.selectAll()
        input.post { input.requestFocus() }
        return box
    }

    fun name(
        host: DialogHost, title: String, initial: String, positive: String, negative: String?,
        onResult: (String?) -> Unit
    ) {
        var h: DialogHandle? = null
        val body = nameContent(
            host.context, title, initial, positive, negative,
            { text -> h?.dismiss(); onResult(text) },
            { h?.dismiss(); onResult(null) }
        )
        h = host.show(body, scroll = true, onCancel = { onResult(null) })
    }

    /**
     * The language list: "System default" first, then every language in its own name.
     * [onPick] gets the language tag, or "" for "follow the phone".
     */
    fun languagePicker(host: DialogHost, onPick: (String) -> Unit) {
        val c = host.context
        val saved = Lang.saved(c)
        val phone = Lang.phoneLanguage(c)
        val tags = ArrayList<String>()
        val labels = ArrayList<String>()
        tags.add("")
        labels.add(c.getString(R.string.lang_system) + " (" + phone.name + ")")
        for (l in Languages.all) {
            tags.add(l.tag)
            labels.add(l.name)
        }
        val selected = tags.indexOf(saved).let { if (it < 0) 0 else it }
        choice(host, c.getString(R.string.set_language), labels, selected) { i -> onPick(tags[i]) }
    }

    // ------------------------------------------------------------------ execution settings

    fun executionSettings(
        host: DialogHost, title: String, initial: ExecutionSettings,
        device: DeviceInfo?, appLabel: String, onSave: (ExecutionSettings) -> Unit
    ) {
        val c = host.context
        var s = initial
        var handle: DialogHandle? = null
        val box = c.vbox()
        box.add(c.text(title, 20f, bold = true))
        box.addView(c.space(6))

        // ---- stop condition
        val stopLabels = listOf(
            c.getString(R.string.exec_stop_infinite),
            c.getString(R.string.exec_stop_count),
            c.getString(R.string.exec_stop_time)
        )
        val countRow = c.stackRow(
            c.getString(R.string.exec_executions),
            NumberField(c, s.count.toDouble(), 1.0, 1_000_000.0, 1.0, 0) { s = s.copy(count = it.toInt()) }
        )
        val timeRow = c.stackRow(
            c.getString(R.string.exec_run_for),
            NumberField(c, s.timeMs / 60_000.0, 1.0, 10_080.0, 1.0, 0, c.getString(R.string.unit_min)) { s = s.copy(timeMs = Math.round(it * 60_000.0)) }
        )
        fun refreshStop() {
            countRow.show(s.stopMode == StopMode.COUNT)
            timeRow.show(s.stopMode == StopMode.TIME)
        }
        val stopBtn = c.dropdown(stopLabels[s.stopMode.ordinal]) {}
        stopBtn.setOnClickListener {
            choice(host, c.getString(R.string.exec_stop_condition), stopLabels, s.stopMode.ordinal) { i ->
                s = s.copy(stopMode = StopMode.entries[i])
                stopBtn.text = stopLabels[i]
                refreshStop()
            }
        }
        box.add(c.stackRow(c.getString(R.string.exec_stop_condition), stopBtn))
        box.add(countRow)
        box.add(timeRow)
        refreshStop()

        // ---- loop interval
        box.add(
            c.stackRow(
                c.getString(R.string.exec_loop_interval),
                NumberField(c, s.loopDelayMs / 1000.0, 0.0, 86_400.0, 0.5, 1, c.getString(R.string.unit_s)) { s = s.copy(loopDelayMs = Math.round(it * 1000.0)) }
            )
        )

        // ---- speed
        val speedValues = floatArrayOf(0.5f, 1f, 1.5f, 2f, 5f, 10f)
        val speedOptions = speedValues.map { UiFmt.speed(c, it) } + c.getString(R.string.speed_custom_dots)
        fun presetIndex(v: Float): Int = when {
            abs(v - 0.5f) < 0.01f -> 0
            abs(v - 1f) < 0.01f -> 1
            abs(v - 2f) < 0.01f -> 2
            else -> 3
        }
        val speedBtn = c.dropdown(UiFmt.speed(c, s.speed)) {}
        val customField = NumberField(c, s.speed.toDouble(), 0.1, 20.0, 0.1, 1, "x") { v ->
            s = s.copy(speed = v.toFloat())
            speedBtn.text = UiFmt.speed(c, s.speed)
        }
        val customRow = c.stackRow(c.getString(R.string.exec_custom_speed), customField)
        var presets: Segmented? = null
        fun setSpeed(v: Float) {
            s = s.copy(speed = v.coerceIn(0.1f, 20f))
            speedBtn.text = UiFmt.speed(c, s.speed)
            val idx = presetIndex(s.speed)
            presets?.select(idx)
            customField.setValue(s.speed.toDouble())
            customRow.show(idx == 3)
        }
        presets = Segmented(
            c,
            listOf(c.getString(R.string.speed_slow), c.getString(R.string.speed_normal), c.getString(R.string.speed_fast), c.getString(R.string.custom)),
            presetIndex(s.speed)
        ) { i ->
            when (i) {
                0 -> setSpeed(0.5f)
                1 -> setSpeed(1f)
                2 -> setSpeed(2f)
                else -> customRow.show(true)
            }
        }
        customRow.show(presets.index == 3)
        speedBtn.setOnClickListener {
            val cur = speedValues.indexOfFirst { abs(it - s.speed) < 0.01f }
            choice(host, c.getString(R.string.exec_speed), speedOptions, if (cur >= 0) cur else speedValues.size) { i ->
                if (i < speedValues.size) setSpeed(speedValues[i]) else {
                    presets.select(3)
                    customRow.show(true)
                }
            }
        }
        box.add(c.stackRow(c.getString(R.string.exec_speed), speedBtn))
        box.add(presets, t = 2)
        box.add(customRow)

        // ---- other
        box.add(c.text(c.getString(R.string.exec_other), 14.5f, Pal.textDim), t = 10)
        box.add(
            c.checkRow(
                c.getString(R.string.exec_auto_title),
                c.getString(R.string.exec_auto_sub),
                s.autoExecute
            ) { s = s.copy(autoExecute = it) }
        )
        box.add(
            c.checkRow(
                c.getString(R.string.exec_sim_title),
                c.getString(R.string.exec_sim_sub),
                s.simulate
            ) { s = s.copy(simulate = it) }
        )
        if (appLabel.isNotEmpty()) {
            box.add(
                c.checkRow(
                    c.getString(R.string.exec_lock_title, appLabel),
                    c.getString(R.string.exec_lock_sub),
                    s.lockToApp
                ) { s = s.copy(lockToApp = it) }
            )
        }

        // ---- more options
        val more = c.vbox()
        more.show(false)
        val closedArrow = if (c.isRtl()) "\u25C2" else "\u25B8"
        val moreWord = c.getString(R.string.exec_more)
        val moreToggle = c.text("$moreWord $closedArrow", 14.5f, Pal.accent, true)
        moreToggle.setPadding(0, c.dp(10), 0, c.dp(10))
        moreToggle.isClickable = true
        moreToggle.setOnClickListener {
            val open = more.visibility != View.VISIBLE
            more.show(open)
            moreToggle.text = if (open) "$moreWord \u25BE" else "$moreWord $closedArrow"
        }
        box.add(moreToggle, t = 4)
        box.add(more)

        val delayLabels = listOf(c.getString(R.string.delay_original), c.getString(R.string.custom))
        val customDelayRow = c.stackRow(
            c.getString(R.string.exec_delay_custom),
            NumberField(c, s.customDelayMs.toDouble(), 0.0, 600_000.0, 50.0, 0, "ms") { s = s.copy(customDelayMs = it.toLong()) }
        )
        val delayBtn = c.dropdown(delayLabels[s.delayMode.ordinal]) {}
        delayBtn.setOnClickListener {
            choice(host, c.getString(R.string.exec_delay_mode), delayLabels, s.delayMode.ordinal) { i ->
                s = s.copy(delayMode = DelayMode.entries[i])
                delayBtn.text = delayLabels[i]
                customDelayRow.show(i == 1)
            }
        }
        more.add(c.stackRow(c.getString(R.string.exec_delay_mode), delayBtn))
        more.add(customDelayRow)
        customDelayRow.show(s.delayMode == DelayMode.CUSTOM)

        val randRow = c.stackRow(
            c.getString(R.string.exec_random_range),
            NumberField(c, s.randomRangeMs.toDouble(), 0.0, 60_000.0, 50.0, 0, "ms") { s = s.copy(randomRangeMs = it.toLong()) }
        )
        more.add(
            c.switchRow(c.getString(R.string.exec_random), c.getString(R.string.exec_random_sub), s.randomDelay) {
                s = s.copy(randomDelay = it)
                randRow.show(it)
            }
        )
        more.add(randRow)
        randRow.show(s.randomDelay)

        val humanRow = c.stackRow(
            c.getString(R.string.exec_humanize_px),
            NumberField(c, s.humanizePx.toDouble(), 0.0, 50.0, 1.0, 0, "px") { s = s.copy(humanizePx = it.toInt()) }
        )
        more.add(
            c.switchRow(c.getString(R.string.exec_humanize), c.getString(R.string.exec_humanize_sub), s.humanize) {
                s = s.copy(humanize = it)
                humanRow.show(it)
            }
        )
        more.add(humanRow)
        humanRow.show(s.humanize)

        val scaleLabels = listOf(
            c.getString(R.string.scale_original),
            c.getString(R.string.scale_scale),
            c.getString(R.string.scale_aspect)
        )
        val scaleBtn = c.dropdown(scaleLabels[s.scaleMode.ordinal]) {}
        scaleBtn.setOnClickListener {
            choice(host, c.getString(R.string.exec_scaling), scaleLabels, s.scaleMode.ordinal) { i ->
                s = s.copy(scaleMode = ScaleMode.entries[i])
                scaleBtn.text = scaleLabels[i]
            }
        }
        more.add(c.stackRow(c.getString(R.string.exec_scaling), scaleBtn))
        if (device != null && device.width > 0) {
            val cur = Platform.display(c)
            val warnings = Texts.scaleWarnings(c, device, cur)
            val info = StringBuilder(c.getString(R.string.exec_device_info, Texts.size(device), Texts.size(cur)))
            for (w in warnings) info.append("\n\u26A0 ").append(w)
            more.add(c.text(info.toString(), 12.5f, if (warnings.isEmpty()) Pal.textDim else Pal.warn), t = 4)
        }

        // ---- buttons
        val row = c.hbox()
        row.add(c.button(R.string.save) { handle?.dismiss(); onSave(s) }, 0, WRAP, 1f, r = 6)
        row.add(c.button(R.string.cancel, Btn.SECONDARY) { handle?.dismiss() }, 0, WRAP, 1f, l = 6)
        box.add(row, t = 18)
        handle = host.show(box, scroll = true)
    }

    // ------------------------------------------------------------------------ action editor

    fun actionEditor(host: DialogHost, action: MacroAction, isNew: Boolean, onSave: (MacroAction) -> Unit) {
        val c = host.context
        var a = action
        var handle: DialogHandle? = null
        val box = c.vbox()
        box.add(c.text(c.getString(if (isNew) R.string.ae_add else R.string.ae_edit, Texts.typeName(c, a.type)), 19f, bold = true))
        box.addView(c.space(4))

        fun num(label: String, v: Double, min: Double, max: Double, step: Double, unit: String?, set: (Double) -> Unit) {
            box.add(c.stackRow(label, NumberField(c, v, min, max, step, 0, unit) { set(it) }))
        }

        when (a.type) {
            ActionType.TAP, ActionType.LONG_PRESS -> {
                num(c.getString(R.string.ae_x), a.x.toDouble(), 0.0, 20_000.0, 1.0, "px") { a = a.copy(x = it.toFloat()) }
                num(c.getString(R.string.ae_y), a.y.toDouble(), 0.0, 20_000.0, 1.0, "px") { a = a.copy(y = it.toFloat()) }
                num(c.getString(R.string.ae_press_time), a.durationMs.toDouble(), 1.0, 60_000.0, 10.0, "ms") { a = a.copy(durationMs = it.toLong()) }
            }
            ActionType.SWIPE -> {
                num(c.getString(R.string.ae_start_x), a.x.toDouble(), 0.0, 20_000.0, 1.0, "px") { a = a.copy(x = it.toFloat()) }
                num(c.getString(R.string.ae_start_y), a.y.toDouble(), 0.0, 20_000.0, 1.0, "px") { a = a.copy(y = it.toFloat()) }
                num(c.getString(R.string.ae_end_x), a.x2.toDouble(), 0.0, 20_000.0, 1.0, "px") { a = a.copy(x2 = it.toFloat()) }
                num(c.getString(R.string.ae_end_y), a.y2.toDouble(), 0.0, 20_000.0, 1.0, "px") { a = a.copy(y2 = it.toFloat()) }
                num(c.getString(R.string.ae_swipe_time), a.durationMs.toDouble(), 1.0, 60_000.0, 10.0, "ms") { a = a.copy(durationMs = it.toLong()) }
            }
            ActionType.DRAG -> {
                box.add(c.text(c.getString(R.string.ae_drag_info, a.path.size.toString()), 13f, Pal.textDim), t = 4)
                num(c.getString(R.string.ae_drag_time), a.durationMs.toDouble(), 1.0, 60_000.0, 10.0, "ms") { a = a.copy(durationMs = it.toLong()) }
            }
            ActionType.MULTI -> {
                box.add(c.text(c.getString(R.string.ae_multi_info, a.strokes.size.toString()), 13f, Pal.textDim), t = 4)
            }
            ActionType.KEY -> {
                val keys = SystemKey.entries
                val keyBtn = c.dropdown(Texts.keyLabel(c, a.key ?: SystemKey.BACK)) {}
                keyBtn.setOnClickListener {
                    choice(host, c.getString(R.string.step_key), keys.map { Texts.keyLabel(c, it) }, keys.indexOf(a.key ?: SystemKey.BACK)) { i ->
                        a = a.copy(key = keys[i])
                        keyBtn.text = Texts.keyLabel(c, keys[i])
                    }
                }
                box.add(c.stackRow(c.getString(R.string.ae_key_label), keyBtn))
            }
            ActionType.WAIT -> {
                num(c.getString(R.string.step_wait), a.durationMs.toDouble(), 0.0, 86_400_000.0, 100.0, "ms") { a = a.copy(durationMs = it.toLong()) }
            }
        }
        if (a.type != ActionType.WAIT) {
            num(c.getString(R.string.ae_delay_before), a.delayMs.toDouble(), 0.0, 3_600_000.0, 50.0, "ms") { a = a.copy(delayMs = it.toLong()) }
        }

        val row = c.hbox()
        row.add(c.button(R.string.save) { handle?.dismiss(); onSave(a) }, 0, WRAP, 1f, r = 6)
        row.add(c.button(R.string.cancel, Btn.SECONDARY) { handle?.dismiss() }, 0, WRAP, 1f, l = 6)
        box.add(row, t = 18)
        handle = host.show(box, scroll = true)
    }
}
