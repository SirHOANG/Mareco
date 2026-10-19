package com.mareco.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.mareco.app.Mareco
import com.mareco.app.R

/** "My Macros": every saved macro as a row (execution settings, edit, duplicate, share, delete, play). */
class MacrosScreen(act: MainActivity) : Screen(act) {
    override val title: String get() = act.getString(R.string.macros_title)
    override val key: String get() = MainActivity.KEY_MACROS

    override fun build(): View {
        val c: Context = act
        val repo = Mareco.repo
        val settings = Mareco.settings
        val scroll = ScrollView(c)
        val col = c.vbox()
        col.setPadding(c.dp(16), c.dp(4), c.dp(16), c.dp(28))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))

        val top = c.hbox()
        top.add(c.button(R.string.macros_new) { act.recordFromApp() }, 0, WRAP, 1f, r = 6)
        top.add(c.button(R.string.macros_import, Btn.SECONDARY) { act.importMacro() }, 0, WRAP, 1f, l = 6)
        col.add(top)

        val macros = repo.list()
        if (macros.isEmpty()) {
            col.add(c.text(R.string.macros_empty, 14.5f, Pal.textDim), t = 24)
        }
        val cb = callbacks()
        for (m in macros) {
            col.add(MacroRow.build(c, m, m.id == settings.defaultMacroId, cb), t = 12)
        }
        return scroll
    }

    private fun callbacks(): MacroRow.Callbacks {
        val repo = Mareco.repo
        val settings = Mareco.settings
        return MacroRow.Callbacks(
            onSelect = { m ->
                settings.defaultMacroId = m.id
                act.toast(act.getString(R.string.toast_default_set, m.name))
                act.refreshScreen()
            },
            onSettings = { m ->
                Dialogs.executionSettings(act.dialogs, m.name, m.execution, m.device, m.appLabel) { saved ->
                    repo.update(m.id) { it.copy(execution = saved) }
                }
            },
            onEdit = { m -> act.push(EditorScreen(act, m.id)) },
            onDuplicate = { m ->
                repo.duplicate(m.id, act.getString(R.string.copy_suffix))
                act.toast(act.getString(R.string.toast_duplicated))
            },
            onShare = { m ->
                Dialogs.choice(
                    act.dialogs, act.getString(R.string.share_title, m.name),
                    listOf(act.getString(R.string.share_send_app), act.getString(R.string.share_save_file)), -1
                ) { i -> if (i == 0) act.shareMacro(m) else act.exportMacro(m) }
            },
            onDelete = { m ->
                Dialogs.confirm(
                    act.dialogs, act.getString(R.string.delete_title), act.getString(R.string.delete_msg, m.name),
                    act.getString(R.string.delete), act.getString(R.string.cancel), danger = true
                ) { ok ->
                    if (ok) {
                        repo.delete(m.id)
                        if (settings.defaultMacroId == m.id) settings.defaultMacroId = ""
                    }
                }
            },
            onPlay = { m -> act.runFromApp(m.id) }
        )
    }
}
