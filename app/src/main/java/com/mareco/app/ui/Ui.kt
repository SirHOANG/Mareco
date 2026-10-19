package com.mareco.app.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.data.AppSettings
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToLong

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Colours. [dark] follows the theme setting (System / Light / Dark). */
object Pal {
    var dark = false
    var version = 0

    private fun pick(d: Long, l: Long): Int = (if (dark) d else l).toInt()

    val bg: Int get() = pick(0xFF0E1116, 0xFFF2F4F8)
    val surface: Int get() = pick(0xFF1A1F27, 0xFFFFFFFF)
    val surface2: Int get() = pick(0xFF262C36, 0xFFE8ECF2)
    val stroke: Int get() = pick(0xFF323A47, 0xFFD3DAE4)
    val text: Int get() = pick(0xFFF1F4F9, 0xFF14181F)
    val textDim: Int get() = pick(0xFF9AA5B5, 0xFF5A6575)
    val ripple: Int get() = pick(0x33FFFFFF, 0x22000000)
    val scrim: Int = 0x99000000.toInt()
    val accent: Int = 0xFF00B4FF.toInt()
    val onAccent: Int = 0xFF03222F.toInt()
    val danger: Int = 0xFFE5484D.toInt()
    val ok: Int = 0xFF30A46C.toInt()
    val warn: Int = 0xFFF5A524.toInt()

    /** Re-reads the theme setting. Returns true when the colours changed. */
    fun apply(ctx: Context): Boolean {
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val d = when (Mareco.settings.theme) {
            AppSettings.Theme.LIGHT -> false
            AppSettings.Theme.DARK -> true
            AppSettings.Theme.SYSTEM -> night
        }
        if (d == dark) return false
        dark = d
        version++
        return true
    }
}

// ------------------------------------------------------------------------------------ basics

fun Context.dp(v: Int): Int = Math.round(v * resources.displayMetrics.density)
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/** A text from the string resources, in the language this context is set to. Numbers are passed as text. */
fun Context.str(id: Int): String = getString(id)

fun Context.str(id: Int, vararg args: Any): String = getString(id, *args)

/** True when this context shows a right-to-left language (Arabic, Hebrew, Persian ...). */
fun Context.isRtl(): Boolean = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun shape(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radiusPx
    if (strokePx > 0) g.setStroke(strokePx, strokeColor)
    return g
}

fun Context.ripple(fill: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Int = 0): Drawable {
    val r = dpf(radiusDp)
    val content = shape(fill, r, strokeColor, if (strokeDp > 0) dp(strokeDp).coerceAtLeast(1) else 0)
    val mask = shape(Color.BLACK, r)
    return RippleDrawable(ColorStateList.valueOf(Pal.ripple), content, mask)
}

fun Context.text(s: CharSequence, sizeSp: Float = 15f, color: Int = Pal.text, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = s
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    t.setTextColor(color)
    if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    return t
}

/** Same, with the text taken from the string resources. */
fun Context.text(id: Int, sizeSp: Float = 15f, color: Int = Pal.text, bold: Boolean = false): TextView =
    text(getString(id), sizeSp, color, bold)

fun Context.vbox(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    return l
}

fun Context.hbox(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.HORIZONTAL
    l.gravity = Gravity.CENTER_VERTICAL
    return l
}

/**
 * Adds [v] with margins given in dp. [l] is the margin at the START edge and [r] at the END edge, so in
 * right-to-left languages (Arabic, Hebrew ...) the spacing flips together with the layout.
 */
fun LinearLayout.add(
    v: View, w: Int = MATCH, h: Int = WRAP, weight: Float = 0f,
    l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0
): View {
    val lp = LinearLayout.LayoutParams(w, h, weight)
    lp.topMargin = context.dp(t)
    lp.bottomMargin = context.dp(b)
    lp.marginStart = context.dp(l)
    lp.marginEnd = context.dp(r)
    addView(v, lp)
    return v
}

/**
 * Fixed-height spacer for a vertical LinearLayout. Add it with addView(space(..)), NOT with
 * LinearLayout.add(): add() would replace these layout params with WRAP_CONTENT, and a plain
 * View with WRAP_CONTENT is either 0 px tall or fills all remaining space.
 */
fun Context.space(hDp: Int): View {
    val v = View(this)
    v.layoutParams = LinearLayout.LayoutParams(1, dp(hDp))
    return v
}

fun Context.divider(): View {
    val v = View(this)
    v.setBackgroundColor(Pal.stroke)
    v.layoutParams = LinearLayout.LayoutParams(MATCH, 1)
    return v
}

fun Context.card(radiusDp: Float = 16f, padDp: Int = 14, fill: Int = Pal.surface): LinearLayout {
    val c = vbox()
    c.background = shape(fill, dpf(radiusDp), Pal.stroke, 1)
    c.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp))
    return c
}

fun Context.sectionTitle(s: String): TextView {
    // Capital letters in the language of the screen (Turkish i -> İ ...). Scripts without capitals are unchanged.
    val t = text(s.uppercase(resources.configuration.locales[0]), 12f, Pal.accent, true)
    // Letter spacing breaks scripts whose letters join up (Arabic, Indic scripts, Thai ...):
    // only add it for Latin, Greek, Cyrillic and Armenian text (code points below Hebrew).
    t.letterSpacing = if (s.any { it.isLetter() && it.code >= 0x590 }) 0f else 0.08f
    return t
}

// ---------------------------------------------------------------------------------- buttons

enum class Btn { PRIMARY, SECONDARY, DANGER, GHOST }

fun Context.button(id: Int, kind: Btn = Btn.PRIMARY, onClick: () -> Unit): TextView = button(getString(id), kind, onClick)

fun Context.button(label: String, kind: Btn = Btn.PRIMARY, onClick: () -> Unit): TextView {
    val fill: Int
    val fg: Int
    when (kind) {
        Btn.PRIMARY -> { fill = Pal.accent; fg = Pal.onAccent }
        Btn.SECONDARY -> { fill = Pal.surface2; fg = Pal.text }
        Btn.DANGER -> { fill = Pal.danger; fg = Color.WHITE }
        Btn.GHOST -> { fill = 0; fg = Pal.accent }
    }
    val t = text(label, 15f, fg, true)
    t.gravity = Gravity.CENTER
    t.minHeight = dp(46)
    t.setPadding(dp(16), dp(10), dp(16), dp(10))
    t.background = ripple(fill, 14f)
    t.isClickable = true
    t.setOnClickListener { onClick() }
    return t
}

/** Round icon button. [res] is a white vector that gets tinted. [desc] is the name read out by screen readers. */
fun Context.iconBtn(
    res: Int, tint: Int = Pal.text, sizeDp: Int = 22, boxDp: Int = 40, fill: Int = 0,
    desc: String? = null, onClick: () -> Unit
): ImageView {
    val iv = ImageView(this)
    iv.setImageResource(res)
    iv.setColorFilter(tint)
    if (desc != null) iv.contentDescription = desc
    iv.scaleType = ImageView.ScaleType.FIT_CENTER
    val pad = dp((boxDp - sizeDp) / 2)
    iv.setPadding(pad, pad, pad, pad)
    iv.background = ripple(fill, boxDp / 2f)
    iv.isClickable = true
    iv.setOnClickListener { onClick() }
    iv.minimumWidth = dp(boxDp)
    iv.minimumHeight = dp(boxDp)
    iv.layoutParams = LinearLayout.LayoutParams(dp(boxDp), dp(boxDp))
    return iv
}

fun Context.dropdown(label: String, onClick: () -> Unit): TextView {
    val t = text(label, 14.5f, Pal.text)
    t.gravity = Gravity.CENTER_VERTICAL
    t.setPadding(dp(12), dp(10), dp(8), dp(10))
    t.background = ripple(Pal.surface2, 10f)
    val d = getDrawable(R.drawable.ic_dropdown)?.mutate()
    d?.setTint(Pal.textDim)
    t.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, d, null)
    t.compoundDrawablePadding = dp(4)
    t.isClickable = true
    t.setOnClickListener { onClick() }
    return t
}

private fun stateList(on: Int, off: Int): ColorStateList =
    ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(on, off))

fun Context.switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
    val row = hbox()
    row.setPadding(0, dp(8), 0, dp(8))
    val col = vbox()
    col.add(text(title, 15f))
    if (subtitle != null) col.add(text(subtitle, 12.5f, Pal.textDim), t = 2)
    row.add(col, 0, WRAP, 1f)
    val sw = Switch(this)
    sw.isChecked = checked
    sw.thumbTintList = stateList(Pal.accent, Pal.textDim)
    sw.trackTintList = stateList((Pal.accent and 0x00FFFFFF) or 0x66000000, Pal.stroke)
    sw.setOnCheckedChangeListener { _, v -> onChange(v) }
    row.add(sw, WRAP, WRAP, l = 12)
    row.isClickable = true
    row.setOnClickListener { sw.toggle() }
    return row
}

/** Check box row in the style of the reference "Execution Settings" dialog. */
fun Context.checkRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
    val row = hbox()
    row.setPadding(0, dp(6), 0, dp(6))
    val cb = CheckBox(this)
    cb.isChecked = checked
    cb.buttonTintList = stateList(Pal.accent, Pal.textDim)
    cb.setOnCheckedChangeListener { _, v -> onChange(v) }
    row.add(cb, WRAP, WRAP, r = 4)
    val col = vbox()
    col.add(text(title, 14.5f))
    if (subtitle != null) col.add(text(subtitle, 12.5f, Pal.textDim), t = 2)
    row.add(col, 0, WRAP, 1f)
    row.isClickable = true
    row.setOnClickListener { cb.toggle() }
    return row
}

fun Context.formRow(label: String, control: View): LinearLayout {
    val row = hbox()
    row.setPadding(0, dp(6), 0, dp(6))
    row.add(text(label, 14.5f), 0, WRAP, 1f, r = 10)
    row.add(control, WRAP, WRAP)
    return row
}

// ---------------------------------------------------------------------------- compound views

class Segmented(
    ctx: Context,
    options: List<String>,
    selected: Int,
    private val onSelect: (Int) -> Unit
) : LinearLayout(ctx) {
    private val tabs = ArrayList<TextView>()
    var index: Int = selected
        private set

    init {
        orientation = HORIZONTAL
        background = shape(Pal.surface2, ctx.dpf(12f))
        setPadding(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
        options.forEachIndexed { i, label ->
            val t = ctx.text(label, 13.5f, Pal.text, true)
            t.gravity = Gravity.CENTER
            t.setPadding(ctx.dp(6), ctx.dp(9), ctx.dp(6), ctx.dp(9))
            t.setOnClickListener { select(i, true) }
            tabs.add(t)
            addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        paint()
    }

    fun select(i: Int, notify: Boolean = false) {
        index = i
        paint()
        if (notify) onSelect(i)
    }

    private fun paint() {
        tabs.forEachIndexed { i, t ->
            t.background = if (i == index) shape(Pal.accent, context.dpf(10f)) else null
            t.setTextColor(if (i == index) Pal.onAccent else Pal.text)
        }
    }
}

/** [-] 123 [+] unit. Calls [onChange] whenever the value changes (typed or stepped). */
class NumberField(
    ctx: Context,
    initial: Double,
    private val min: Double,
    private val max: Double,
    private val step: Double,
    private val decimals: Int,
    unit: String? = null,
    private val onChange: (Double) -> Unit
) : LinearLayout(ctx) {
    var value: Double = initial.coerceIn(min, max)
        private set
    private val edit = EditText(ctx)
    private var internal = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ctx.iconBtn(R.drawable.ic_remove, Pal.text, 18, 38, Pal.surface2) { bump(-step) })
        edit.setTextColor(Pal.text)
        edit.setHintTextColor(Pal.textDim)
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        edit.gravity = Gravity.CENTER
        edit.setSingleLine(true)
        edit.imeOptions = EditorInfo.IME_ACTION_DONE
        edit.inputType = InputType.TYPE_CLASS_NUMBER or (if (decimals > 0) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
        edit.background = shape(Pal.surface2, ctx.dpf(10f))
        edit.setPadding(ctx.dp(4), 0, ctx.dp(4), 0)
        edit.setText(format(value))
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (internal) return
                val v = asciiNumber(s?.toString() ?: "").toDoubleOrNull() ?: return
                value = v.coerceIn(min, max)
                onChange(value)
            }
        })
        edit.setOnFocusChangeListener { _, has -> if (!has) normalize() }
        edit.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) {
                normalize()
                edit.clearFocus()
            }
            false
        }
        val lp = LinearLayout.LayoutParams(ctx.dp(76), ctx.dp(38))
        lp.setMargins(ctx.dp(6), 0, ctx.dp(6), 0)
        addView(edit, lp)
        addView(ctx.iconBtn(R.drawable.ic_add, Pal.text, 18, 38, Pal.surface2) { bump(step) })
        if (unit != null) {
            val u = ctx.text(unit, 13f, Pal.textDim)
            val ulp = LinearLayout.LayoutParams(WRAP, WRAP)
            ulp.marginStart = ctx.dp(8)
            addView(u, ulp)
        }
    }

    private fun format(v: Double): String =
        if (decimals <= 0) v.roundToLong().toString() else String.format(Locale.US, "%.${decimals}f", v)

    /**
     * Turns what the keyboard produced into a plain number: digits of any script (Arabic-Indic, Devanagari,
     * Bengali ...) become 0-9, and the comma / Arabic decimal separators become a dot.
     */
    private fun asciiNumber(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val d = Character.digit(ch, 10)
            when {
                d >= 0 -> sb.append(('0'.code + d).toChar())
                ch == ',' || ch == '٫' || ch == '،' -> sb.append('.')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun normalize() {
        internal = true
        edit.setText(format(value))
        internal = false
    }

    /** Sets the value from code (does not call [onChange]). */
    fun setValue(v: Double) {
        value = v.coerceIn(min, max)
        normalize()
    }

    private fun bump(delta: Double) {
        val raw = ((value + delta) * 1000.0).roundToLong() / 1000.0
        value = raw.coerceIn(min, max)
        normalize()
        onChange(value)
    }
}

class MaxHeightScrollView(ctx: Context, private val maxPx: Int) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST))
    }
}

/** Dim background + centred rounded card. Used for dialogs in the app and in the overlay. */
fun dialogFrame(
    ctx: Context,
    content: View,
    maxWidthDp: Int = 420,
    scroll: Boolean = false,
    onOutside: (() -> Unit)? = null
): FrameLayout {
    val dm = ctx.resources.displayMetrics
    val frame = FrameLayout(ctx)
    frame.setBackgroundColor(Pal.scrim)
    val card = FrameLayout(ctx)
    card.background = shape(Pal.surface, ctx.dpf(22f), Pal.stroke, 1)
    card.isClickable = true
    val pad = ctx.dp(18)
    card.setPadding(pad, pad, pad, pad)
    val inner: View = if (scroll) {
        val sv = MaxHeightScrollView(ctx, (dm.heightPixels * 0.82f).toInt())
        sv.addView(content)
        sv
    } else {
        content
    }
    card.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
    val w = min(ctx.dp(maxWidthDp), dm.widthPixels - ctx.dp(28))
    frame.addView(card, FrameLayout.LayoutParams(w, WRAP, Gravity.CENTER))
    if (onOutside != null) frame.setOnClickListener { onOutside() }
    return frame
}

// ------------------------------------------------------------------------------ system bars

/** Pads this view by the system bars / cutout / keyboard (needed because Android 15+ is edge-to-edge). */
fun View.applyBarInsets() {
    setOnApplyWindowInsetsListener { v, insets ->
        val p = if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
            )
            intArrayOf(i.left, i.top, i.right, i.bottom)
        } else {
            @Suppress("DEPRECATION")
            intArrayOf(
                insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom
            )
        }
        v.setPadding(p[0], p[1], p[2], p[3])
        insets
    }
    requestApplyInsets()
}

/** Dark icons on light backgrounds and the other way round. */
@Suppress("DEPRECATION")
fun Activity.styleSystemBars() {
    try {
        applySystemBarStyle()
    } catch (e: Exception) {
        // cosmetic only
    }
}

@Suppress("DEPRECATION")
private fun Activity.applySystemBarStyle() {
    val darkIcons = !Pal.dark
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 30) {
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (darkIcons) mask else 0, mask)
    } else {
        var f = window.decorView.systemUiVisibility
        val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        f = if (darkIcons) f or flags else f and flags.inv()
        window.decorView.systemUiVisibility = f
    }
}
