package com.mareco.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.mareco.app.Lang
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.core.GesturePlan
import com.mareco.app.core.Platform
import com.mareco.app.data.DeviceInfo
import com.mareco.app.ui.DialogHandle
import com.mareco.app.ui.DialogHost
import com.mareco.app.ui.MATCH
import com.mareco.app.ui.WRAP
import com.mareco.app.ui.dialogFrame
import com.mareco.app.ui.dp
import com.mareco.app.ui.isRtl

/**
 * Owns every floating window Mareco draws. All of them are TYPE_ACCESSIBILITY_OVERLAY windows created
 * through the accessibility service, so the "Display over other apps" permission is not required.
 *
 *  - toolbar : the small vertical control bar (never takes keyboard focus, touches outside pass through)
 *  - capture : transparent full-screen layer that records touches
 *  - markers : transparent, untouchable layer that shows where macro taps land (optional)
 *  - panel   : full-screen dim layer that hosts dialogs (settings, execution settings ...)
 */
class OverlayManager(private val service: AccessibilityService, private val ctl: MarecoController) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /**
     * Context for everything drawn here: the Mareco theme plus the language chosen in Settings
     * (the service itself only knows the phone's language). Replaced by [refreshLanguage].
     */
    var ui: Context = buildUi()
        private set
    private val settings = Mareco.settings

    var toolbar: ToolbarView? = null
        private set
    private var toolbarLp: WindowManager.LayoutParams? = null
    private var capture: CaptureView? = null
    private var captureLp: WindowManager.LayoutParams? = null
    private var markers: MarkerView? = null
    private var panel: PanelLayer? = null
    private var screen: DeviceInfo = Platform.display(service)

    val dialogHost: DialogHost = OverlayDialogHost()

    val isToolbarShown: Boolean get() = toolbar != null
    val hasPanel: Boolean get() = panel != null

    private fun buildUi(): Context = ContextThemeWrapper(Lang.wrap(service), R.style.Theme_Mareco)

    /**
     * The language changed: build the windows again so their texts follow. Dialogs are closed (their texts
     * are fixed when they are built); the bar keeps its position and state.
     */
    fun refreshLanguage() {
        ui = buildUi()
        closePanels()
        val hadToolbar = toolbar != null
        if (hadToolbar) {
            saveToolbarPosition()
            hideToolbar()
            showToolbar()
        }
        ctl.render()
    }

    // ------------------------------------------------------------------ window helpers

    private fun lp(w: Int, h: Int, flags: Int): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT
        )
        // LEFT (not START): positions are absolute screen coordinates, they must not flip in right-to-left languages.
        p.gravity = Gravity.TOP or Gravity.LEFT
        p.title = "Mareco"
        if (Build.VERSION.SDK_INT >= 30) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return p
    }

    private fun addWindow(v: View, p: WindowManager.LayoutParams): Boolean =
        try {
            wm.addView(v, p)
            true
        } catch (e: Exception) {
            false
        }

    private fun removeWindow(v: View?) {
        if (v == null) return
        try {
            wm.removeViewImmediate(v)
        } catch (e: Exception) {
            // already gone
        }
    }

    private fun update(v: View?, p: WindowManager.LayoutParams?) {
        if (v == null || p == null) return
        try {
            wm.updateViewLayout(v, p)
        } catch (e: Exception) {
            // window was removed meanwhile
        }
    }

    private val screenFlags =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    // ------------------------------------------------------------------------ toolbar

    fun showToolbar(): Boolean {
        if (toolbar != null) return true
        screen = Platform.display(service)
        val tb = ToolbarView(ui, ctl)
        val p = lp(WRAP, WRAP, screenFlags)
        tb.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val sx = settings.overlayX
        val sy = settings.overlayY
        p.x = if (sx >= 0) sx else screen.width - tb.measuredWidth - ui.dp(10)
        p.y = if (sy >= 0) sy else (screen.height * 0.30f).toInt()
        clamp(p, tb.measuredWidth, tb.measuredHeight)
        if (!addWindow(tb, p)) return false
        toolbar = tb
        toolbarLp = p
        return true
    }

    fun hideToolbar() {
        removeWindow(toolbar)
        toolbar = null
        toolbarLp = null
    }

    private fun clamp(p: WindowManager.LayoutParams, w: Int, h: Int) {
        p.x = p.x.coerceIn(0, maxOf(0, screen.width - w))
        p.y = p.y.coerceIn(0, maxOf(0, screen.height - h))
    }

    fun moveToolbarBy(dx: Int, dy: Int) {
        val tb = toolbar ?: return
        val p = toolbarLp ?: return
        p.x += dx
        p.y += dy
        clamp(p, tb.width, tb.height)
        update(tb, p)
    }

    fun saveToolbarPosition() {
        toolbarLp?.let {
            settings.overlayX = it.x
            settings.overlayY = it.y
        }
    }

    fun resetToolbarPosition() {
        settings.overlayX = -1
        settings.overlayY = -1
        if (toolbar != null) {
            hideToolbar()
            showToolbar()
            ctl.render()
        }
    }

    /** Re-creates the buttons (size / theme changed) and keeps the position inside the screen. */
    fun rebuildToolbar() {
        val tb = toolbar ?: return
        tb.rebuild()
        tb.post {
            toolbarLp?.let {
                clamp(it, tb.width, tb.height)
                update(tb, it)
            }
        }
        ctl.render()
    }

    fun applyAppearance() {
        toolbar?.applyAppearance()
    }

    /** While a macro runs the screen must not time out. */
    fun keepScreenOn(on: Boolean) {
        val p = toolbarLp ?: return
        val has = (p.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (on == has) return
        p.flags = if (on) p.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        else p.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        update(toolbar, p)
    }

    fun toolbarRect(): Rect? {
        val tb = toolbar ?: return null
        val p = toolbarLp ?: return null
        return Rect(p.x, p.y, p.x + tb.width, p.y + tb.height)
    }

    /** Lets a gesture pass through the toolbar when it would otherwise land on it. */
    fun setToolbarTouchable(on: Boolean) {
        val p = toolbarLp ?: return
        val has = (p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0
        if (on == has) return
        p.flags = if (on) p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        update(toolbar, p)
    }

    private fun raiseToolbar() {
        val tb = toolbar ?: return
        val p = toolbarLp ?: return
        removeWindow(tb)
        addWindow(tb, p)
    }

    // ------------------------------------------------------------------------- capture

    fun showCapture(sink: CaptureView.Sink, markersOn: Boolean): Boolean {
        hideCapture()
        val cv = CaptureView(ui, sink)
        cv.markersEnabled = markersOn
        cv.showFrame = true
        val p = lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, screenFlags)
        if (!addWindow(cv, p)) return false
        capture = cv
        captureLp = p
        raiseToolbar()
        return true
    }

    fun hideCapture() {
        removeWindow(capture)
        capture = null
        captureLp = null
    }

    /** false = touches go straight to the app underneath (used while replaying a gesture, and while paused). */
    fun setCaptureTouchable(on: Boolean) {
        val p = captureLp ?: return
        val has = (p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0
        if (on == has) return
        p.flags = if (on) p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        update(capture, p)
    }

    fun setCaptureFrame(on: Boolean) {
        capture?.showFrame = on
    }

    // -------------------------------------------------------------------------- markers

    fun showMarkers() {
        if (markers != null) return
        val mv = MarkerView(ui)
        val p = lp(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            screenFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        if (addWindow(mv, p)) {
            markers = mv
            raiseToolbar()
        }
    }

    fun hideMarkers() {
        removeWindow(markers)
        markers = null
    }

    fun markPlan(plan: GesturePlan) {
        val mv = markers ?: return
        for (s in plan.strokes) {
            if (s.xy.size >= 2) mv.ripple(s.xy[0], s.xy[1])
        }
    }

    // ---------------------------------------------------------------------------- panel

    @Suppress("DEPRECATION")
    private fun ensurePanel(): PanelLayer? {
        panel?.let { return it }
        val layer = PanelLayer(ui) {
            removeWindow(panel)
            panel = null
        }
        // The window itself only knows the phone's direction; follow the language chosen for Mareco.
        layer.layoutDirection = if (ui.isRtl()) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val p = lp(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (!addWindow(layer, p)) return null
        panel = layer
        return layer
    }

    fun closePanels() {
        panel?.clear()
        removeWindow(panel)
        panel = null
    }

    private inner class OverlayDialogHost : DialogHost {
        override val context: Context get() = ui

        override fun show(content: View, cancelable: Boolean, scroll: Boolean, onCancel: (() -> Unit)?): DialogHandle {
            val layer = ensurePanel()
            if (layer == null) {
                Platform.toast(ui, ui.getString(R.string.toast_panel_failed), true)
                return object : DialogHandle {
                    override fun dismiss() {}
                }
            }
            return layer.push(content, cancelable, scroll, onCancel)
        }

        override fun toast(message: String) {
            Platform.toast(ui, message)
        }
    }

    // ---------------------------------------------------------------- lifecycle / rotation

    fun onConfigurationChanged() {
        screen = Platform.display(service)
        val tb = toolbar
        val p = toolbarLp
        if (tb != null && p != null) {
            clamp(p, tb.width, tb.height)
            update(tb, p)
        }
    }

    fun removeAll() {
        closePanels()
        hideMarkers()
        hideCapture()
        hideToolbar()
    }
}

/** Stack of dialog frames inside one focusable overlay window. Back closes the top one. */
class PanelLayer(ctx: Context, private val onEmpty: () -> Unit) : FrameLayout(ctx) {
    private class Entry(val frame: View, val cancelable: Boolean, val onCancel: (() -> Unit)?)

    private val entries = ArrayList<Entry>()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun push(content: View, cancelable: Boolean, scroll: Boolean, onCancel: (() -> Unit)?): DialogHandle {
        var entry: Entry? = null
        val frame = dialogFrame(context, content, 440, scroll) {
            entry?.let { if (it.cancelable) close(it, true) }
        }
        entry = Entry(frame, cancelable, onCancel)
        entries.add(entry)
        addView(frame, LayoutParams(MATCH, MATCH))
        requestFocus()
        val e = entry
        return object : DialogHandle {
            override fun dismiss() {
                close(e, false)
            }
        }
    }

    private fun close(e: Entry, byUser: Boolean) {
        if (!entries.remove(e)) return
        removeView(e.frame)
        if (byUser) e.onCancel?.invoke()
        if (entries.isEmpty()) onEmpty()
    }

    fun clear() {
        entries.clear()
        removeAllViews()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && entries.isNotEmpty()) {
                val top = entries[entries.size - 1]
                if (top.cancelable) close(top, true)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
