package com.mareco.app.service

import android.view.View
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.data.AppSettings
import com.mareco.app.data.Macro
import com.mareco.app.ui.Btn
import com.mareco.app.ui.DialogHandle
import com.mareco.app.ui.Dialogs
import com.mareco.app.ui.MacroRow
import com.mareco.app.ui.Pal
import com.mareco.app.ui.Segmented
import com.mareco.app.ui.SettingsView
import com.mareco.app.ui.WRAP
import com.mareco.app.ui.add
import com.mareco.app.ui.button
import com.mareco.app.ui.divider
import com.mareco.app.ui.hbox
import com.mareco.app.ui.iconBtn
import com.mareco.app.ui.space
import com.mareco.app.ui.text
import com.mareco.app.ui.vbox

/**
 * The panel behind the gear button of the floating controls:
 *  1. the two modes - "Record macro" (set a macro up) and "Start macro" (run a saved one),
 *  2. everything from the app's Settings screen.
 */
class OverlayPanels(private val ctl: MarecoController) {
    private val settings = Mareco.settings
    private val repo = Mareco.repo
    private val host get() = ctl.overlay.dialogHost

    fun openSettings() {
        val c = host.context
        val root = c.vbox()
        var handle: DialogHandle? = null

        val head = c.hbox()
        head.add(c.text(R.string.ov_title, 20f, bold = true), 0, WRAP, 1f)
        head.add(c.iconBtn(R.drawable.ic_close, Pal.text, 22, 40, 0, c.getString(R.string.close)) { handle?.dismiss() }, WRAP, WRAP)
        root.add(head)

        root.add(c.text(R.string.ov_mode, 13.5f, Pal.textDim), t = 8)
        val section = c.vbox()
        val seg = Segmented(
            c, listOf(c.getString(R.string.ov_mode_record), c.getString(R.string.ov_mode_start)),
            if (settings.mode == AppSettings.Mode.RECORD) 0 else 1
        ) { i ->
            settings.mode = if (i == 0) AppSettings.Mode.RECORD else AppSettings.Mode.START
            fill(section) { handle?.dismiss() }
        }
        root.add(seg, t = 6)
        root.add(section, t = 12)
        fill(section) { handle?.dismiss() }

        root.add(c.divider(), t = 14)
        root.add(SettingsView.build(host, inOverlay = true), t = 2)

        val row = c.hbox()
        row.add(
            c.button(R.string.home_bar_hide, Btn.SECONDARY) {
                handle?.dismiss()
                ctl.hideToolbar()
            },
            0, WRAP, 1f, r = 6
        )
        row.add(c.button(R.string.close) { handle?.dismiss() }, 0, WRAP, 1f, l = 6)
        root.add(row, t = 20)
        handle = host.show(root, scroll = true)
    }

    /** The part of the panel that changes with the mode. */
    private fun fill(section: android.widget.LinearLayout, closePanel: () -> Unit) {
        val c = host.context
        section.removeAllViews()
        if (settings.mode == AppSettings.Mode.RECORD) {
            section.add(c.text(R.string.ov_record_help, 13.5f, Pal.textDim))
            section.add(
                c.button(R.string.ov_start_rec) {
                    closePanel()
                    ctl.startRecording()
                },
                t = 10
            )
            return
        }
        val m = repo.get(settings.defaultMacroId)
        if (m == null) {
            section.add(c.text(R.string.ov_start_help_none, 13.5f, Pal.textDim))
            if (repo.count() > 0) {
                section.add(c.button(R.string.ov_choose, Btn.SECONDARY) { openChooser { fill(section, closePanel) } }, t = 10)
            }
            return
        }
        section.add(c.text(R.string.ov_default_label, 13.5f, Pal.textDim))
        section.add(
            MacroRow.build(c, m, true, callbacks({ fill(section, closePanel) }, closePanel)),
            t = 8
        )
        val row = c.hbox()
        row.add(c.button(R.string.ov_change, Btn.SECONDARY) { openChooser { fill(section, closePanel) } }, 0, WRAP, 1f, r = 6)
        row.add(
            c.button(R.string.ov_mode_start) {
                closePanel()
                ctl.startRun(m.id)
            },
            0, WRAP, 1f, l = 6
        )
        section.add(row, t = 10)
    }

    /** List of every macro with the same buttons as the reference design (settings, edit, copy, delete, play). */
    fun openChooser(after: () -> Unit) {
        val c = host.context
        val box = c.vbox()
        var handle: DialogHandle? = null
        box.add(c.text(R.string.ov_chooser_title, 20f, bold = true))
        box.add(c.text(R.string.ov_chooser_help, 13f, Pal.textDim), t = 4)
        val list = c.vbox()
        box.add(list, t = 10)

        fun refill() {
            list.removeAllViews()
            val macros = repo.list()
            if (macros.isEmpty()) {
                list.add(c.text(R.string.ov_none_yet, 14.5f, Pal.textDim))
            }
            for (m in macros) {
                val cb = callbacks({ refill(); after() }, { handle?.dismiss(); ctl.overlay.closePanels() }, selectCloses = { handle?.dismiss() })
                list.add(MacroRow.build(c, m, m.id == settings.defaultMacroId, cb))
                // A spacer must be added with addView: add() would give it WRAP_CONTENT, i.e. no height at all.
                list.addView(c.space(10))
            }
        }
        refill()
        box.add(c.button(R.string.close) { handle?.dismiss(); after() }, t = 10)
        handle = host.show(box, scroll = true, onCancel = { after() })
    }

    private fun callbacks(refresh: () -> Unit, closeAll: () -> Unit, selectCloses: (() -> Unit)? = null): MacroRow.Callbacks {
        val c = host.context
        return MacroRow.Callbacks(
            onSelect = { m: Macro ->
                settings.defaultMacroId = m.id
                host.toast(c.getString(R.string.toast_default_set, m.name))
                if (selectCloses != null) selectCloses() else refresh()
                refresh()
            },
            onSettings = { m: Macro -> ctl.editExecutionSettings(m.id, host) { refresh() } },
            onEdit = { m: Macro ->
                ctl.askText(
                    c.getString(R.string.dlg_rename_title), m.name, c.getString(R.string.save), c.getString(R.string.cancel),
                    dismissSaves = false
                ) { text ->
                    if (text != null && text.isNotBlank()) {
                        val clean = text.trim()
                        repo.rename(
                            m.id,
                            if (clean.equals(m.name, true)) clean else repo.uniqueName(clean, c.getString(R.string.default_macro_name))
                        )
                    }
                    refresh()
                }
            },
            onDuplicate = { m: Macro ->
                repo.duplicate(m.id, c.getString(R.string.copy_suffix))
                host.toast(c.getString(R.string.toast_duplicated))
                refresh()
            },
            onDelete = { m: Macro ->
                Dialogs.confirm(
                    host, c.getString(R.string.delete_title), c.getString(R.string.delete_msg, m.name),
                    c.getString(R.string.delete), c.getString(R.string.cancel), danger = true
                ) { ok ->
                    if (ok) {
                        repo.delete(m.id)
                        if (settings.defaultMacroId == m.id) settings.defaultMacroId = ""
                        refresh()
                    }
                }
            },
            onPlay = { m: Macro ->
                closeAll()
                ctl.startRun(m.id)
            }
        )
    }

    @Suppress("unused")
    private fun unusedView(): View? = null
}
