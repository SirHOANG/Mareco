package com.mareco.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.mareco.app.EngineState
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.core.Fmt
import com.mareco.app.core.Platform

/** Main screen: the four big buttons from the spec plus the live status. */
class HomeScreen(act: MainActivity) : Screen(act) {
    override val title: String get() = act.getString(R.string.app_name)
    private var statusBox: LinearLayout? = null
    private var buttonsBox: LinearLayout? = null

    override fun build(): View {
        val c: Context = act
        val scroll = ScrollView(c)
        scroll.isFillViewport = true
        val col = c.vbox()
        col.setPadding(c.dp(20), c.dp(6), c.dp(20), c.dp(28))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))

        col.add(c.text("MARECO", 34f, Pal.accent, true))
        col.add(c.text(R.string.home_tagline, 15f, Pal.textDim), t = 2)
        col.add(c.text(R.string.home_flow, 12.5f, Pal.textDim), t = 6)

        val status = c.vbox()
        statusBox = status
        col.add(status, t = 18)

        val buttons = c.vbox()
        buttonsBox = buttons
        col.add(buttons, t = 14)

        col.add(
            c.button(R.string.home_help, Btn.GHOST) { act.push(HelpScreen(act)) },
            t = 18
        )
        fill()
        return scroll
    }

    override fun onShow() {
        fill()
    }

    override fun onStateChanged() {
        fill()
    }

    override fun onDataChanged() {
        fill()
    }

    private fun bigButton(c: Context, icon: Int, label: String, sub: String?, primary: Boolean, onClick: () -> Unit): View {
        val row = c.hbox()
        row.background = c.ripple(if (primary) Pal.accent else Pal.surface, 18f, Pal.stroke, if (primary) 0 else 1)
        row.setPadding(c.dp(16), c.dp(14), c.dp(16), c.dp(14))
        val fg = if (primary) Pal.onAccent else Pal.text
        val badge = c.iconBtn(icon, if (primary) Pal.accent else Pal.onAccent, 24, 46, if (primary) Pal.onAccent else Pal.accent) {}
        badge.isClickable = false
        row.add(badge, WRAP, WRAP, r = 14)
        val col = c.vbox()
        col.add(c.text(label, 18f, fg, true))
        if (sub != null) col.add(c.text(sub, 12.5f, if (primary) Pal.onAccent else Pal.textDim), t = 2)
        row.add(col, 0, WRAP, 1f)
        row.isClickable = true
        row.setOnClickListener { onClick() }
        return row
    }

    private fun fill() {
        val c: Context = act
        val status = statusBox ?: return
        val buttons = buttonsBox ?: return
        status.removeAllViews()
        buttons.removeAllViews()
        val s = Mareco.state
        val repo = Mareco.repo
        val settings = Mareco.settings
        val enabled = Platform.isServiceEnabled(c)
        val connected = Platform.isServiceConnected
        val ctl = Mareco.controller

        // ---- permission banner
        if (!enabled) {
            val card = c.card(16f, 14, Pal.surface)
            card.add(c.text("⚠ " + c.getString(R.string.home_setup_title), 16f, Pal.warn, true))
            card.add(c.text(R.string.home_setup_body, 13.5f, Pal.textDim), t = 4)
            card.add(c.button(R.string.home_open_setup) { act.push(SetupScreen(act)) }, t = 10)
            status.add(card)
            status.addView(c.space(12))
        } else if (!connected) {
            status.add(c.text(R.string.home_service_starting, 13.5f, Pal.textDim))
            status.addView(c.space(8))
        }

        // ---- status pill
        val label: String
        val color: Int
        when (s.engine) {
            EngineState.IDLE -> { label = c.getString(R.string.state_idle); color = Pal.textDim }
            EngineState.RECORDING -> {
                label = c.getString(if (s.recordingPaused) R.string.state_recording_paused else R.string.state_recording)
                color = Pal.danger
            }
            EngineState.READY -> { label = c.getString(R.string.state_ready); color = Pal.accent }
            EngineState.RUNNING -> { label = c.getString(R.string.state_running); color = Pal.ok }
            EngineState.PAUSED -> { label = c.getString(R.string.state_paused); color = Pal.warn }
            EngineState.STOPPED -> { label = c.getString(R.string.state_stopped); color = Pal.textDim }
        }
        val pill = c.hbox()
        pill.background = shape(Pal.surface, c.dpf(16f), Pal.stroke, 1)
        pill.setPadding(c.dp(14), c.dp(12), c.dp(14), c.dp(12))
        val dot = View(c)
        dot.background = shape(color, c.dpf(6f))
        pill.add(dot, c.dp(12), c.dp(12), r = 10)
        val info = c.vbox()
        info.add(c.text(label, 15f, color, true))
        val detail = when (s.engine) {
            EngineState.RECORDING ->
                c.getString(R.string.home_detail_recording, Fmt.clock(s.recordMs), s.recordActions.toString())
            EngineState.RUNNING, EngineState.PAUSED ->
                c.getString(
                    R.string.home_detail_running,
                    s.macroName,
                    s.loops.toString() + "/" + (if (s.totalLoops > 0) s.totalLoops.toString() else "∞"),
                    UiFmt.speed(c, s.speed)
                )
            EngineState.STOPPED -> s.message
            EngineState.READY -> repo.get(settings.defaultMacroId)?.name ?: ""
            EngineState.IDLE -> c.getString(R.string.home_idle_hint)
        }
        if (s.countdown > 0) {
            info.add(c.text(c.getString(R.string.home_countdown, s.countdown.toString()), 13f, Pal.warn, true), t = 2)
        } else if (detail.isNotEmpty()) {
            info.add(c.text(detail, 13f, Pal.textDim), t = 2)
        }
        pill.add(info, 0, WRAP, 1f)
        status.add(pill)

        // ---- controls while busy
        val busy = s.engine == EngineState.RECORDING || s.engine == EngineState.RUNNING || s.engine == EngineState.PAUSED || s.countdown > 0
        if (busy && ctl != null) {
            val row = c.hbox()
            if (s.engine == EngineState.RUNNING || s.engine == EngineState.PAUSED) {
                row.add(
                    c.button(if (s.engine == EngineState.RUNNING) R.string.btn_pause else R.string.btn_resume, Btn.SECONDARY) {
                        if (s.engine == EngineState.RUNNING) ctl.pauseRun() else ctl.resumeRun()
                    },
                    0, WRAP, 1f, r = 6
                )
            }
            row.add(
                c.button(if (s.engine == EngineState.RECORDING) R.string.btn_stop_save else R.string.btn_stop, Btn.DANGER) { ctl.emergencyStop() },
                0, WRAP, 1f, l = if (row.childCount > 0) 6 else 0
            )
            status.add(row, t = 10)
        }

        // ---- big buttons
        val defaultMacro = repo.get(settings.defaultMacroId)
        buttons.add(
            bigButton(c, R.drawable.ic_videocam, c.getString(R.string.home_start_rec), c.getString(R.string.home_start_rec_sub), true) { act.recordFromApp() }
        )
        buttons.addView(c.space(12))
        buttons.add(
            bigButton(
                c, R.drawable.ic_play, c.getString(R.string.home_run),
                if (defaultMacro != null) UiFmt.quoted(c, defaultMacro.name) else c.getString(R.string.home_run_none),
                false
            ) {
                val m = repo.get(settings.defaultMacroId) ?: repo.list().firstOrNull()
                if (m == null) {
                    act.toast(c.getString(R.string.toast_record_first))
                } else {
                    act.runFromApp(m.id)
                }
            }
        )
        buttons.addView(c.space(12))
        buttons.add(
            bigButton(
                c, R.drawable.ic_folder, c.getString(R.string.home_macros),
                c.getString(R.string.home_macros_count, repo.count().toString()), false
            ) { act.push(MacrosScreen(act)) }
        )
        buttons.addView(c.space(12))
        buttons.add(
            bigButton(c, R.drawable.ic_settings, c.getString(R.string.home_settings), c.getString(R.string.home_settings_sub), false) {
                act.push(SettingsScreen(act))
            }
        )
        buttons.addView(c.space(12))
        val shown = ctl?.overlay?.isToolbarShown == true
        buttons.add(
            bigButton(
                c, R.drawable.ic_move,
                c.getString(if (shown) R.string.home_bar_hide else R.string.home_bar_show),
                c.getString(R.string.home_bar_sub), false
            ) {
                if (ctl == null) {
                    act.push(SetupScreen(act))
                } else if (shown) {
                    ctl.hideToolbar()
                    fill()
                } else {
                    ctl.showToolbar()
                    fill()
                }
            }
        )
    }
}
