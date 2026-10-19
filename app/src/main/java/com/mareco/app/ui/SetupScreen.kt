package com.mareco.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.core.Platform

/** Beginner-friendly permission setup (spec section 12). Nothing is bypassed or requested silently. */
class SetupScreen(act: MainActivity) : Screen(act) {
    override val title: String get() = act.getString(R.string.setup_title)
    override val key: String get() = MainActivity.KEY_SETUP

    override fun build(): View {
        val c: Context = act
        val scroll = ScrollView(c)
        val col = c.vbox()
        col.setPadding(c.dp(20), c.dp(6), c.dp(20), c.dp(28))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))
        val enabled = Platform.isServiceEnabled(c)
        val connected = Platform.isServiceConnected

        col.add(c.text("✓ " + c.getString(R.string.setup_installed), 16f, Pal.ok, true))
        if (enabled && connected) {
            col.add(c.text("✓ " + c.getString(R.string.setup_service_on), 16f, Pal.ok, true), t = 10)
            col.add(c.text(R.string.setup_all_set, 14f, Pal.textDim), t = 6)
            col.add(
                c.button(R.string.home_bar_show) {
                    Mareco.controller?.showToolbar()
                    act.pop()
                },
                t = 14
            )
        } else if (enabled) {
            col.add(c.text("✓ " + c.getString(R.string.setup_switched_on), 16f, Pal.ok, true), t = 10)
            col.add(c.text("⏳ " + c.getString(R.string.setup_waiting), 14f, Pal.textDim), t = 6)
            col.add(c.button(R.string.setup_check_again) { recheck() }, t = 14)
        } else {
            col.add(c.text("⚠ " + c.getString(R.string.setup_required), 16f, Pal.warn, true), t = 10)
            col.add(c.text(R.string.setup_steps, 14f, Pal.text), t = 6)
            col.add(c.button(R.string.setup_open_settings) { Platform.openAccessibilitySettings(c) }, t = 14)
            col.add(c.text(R.string.setup_after, 13.5f, Pal.textDim), t = 14)
            col.add(c.button(R.string.setup_check_again, Btn.SECONDARY) { recheck() }, t = 6)
        }

        col.add(c.sectionTitle(c.getString(R.string.setup_why_title)), t = 26)
        col.add(c.text(R.string.setup_why_a11y, 13.5f, Pal.textDim), t = 6)
        col.add(c.text(R.string.setup_why_keys, 13.5f, Pal.textDim), t = 8)
        col.add(c.text(R.string.setup_why_app, 13.5f, Pal.textDim), t = 8)
        col.add(c.text(R.string.setup_no_internet, 13.5f, Pal.ok, true), t = 8)

        col.add(c.sectionTitle(c.getString(R.string.setup_grey_title)), t = 26)
        col.add(c.text(R.string.setup_grey_body, 13.5f, Pal.text), t = 6)
        col.add(c.button(R.string.setup_open_app_info, Btn.SECONDARY) { Platform.openAppInfo(c) }, t = 10)

        col.add(c.sectionTitle(c.getString(R.string.setup_keep_title)), t = 26)
        col.add(c.text(R.string.setup_keep_body, 13.5f, Pal.textDim), t = 6)
        return scroll
    }

    private fun recheck() {
        act.refreshScreen()
        val ok = Platform.isServiceEnabled(act) && Platform.isServiceConnected
        act.toast(act.getString(if (ok) R.string.toast_perm_on else R.string.toast_perm_off))
    }

    override val refreshOnResume: Boolean = true

    /** The service connected or disconnected: show the new status. */
    override fun onStateChanged() {
        act.refreshScreen()
    }
}
