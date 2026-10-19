package com.mareco.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import com.mareco.app.Lang
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.data.ActionType
import com.mareco.app.data.Macro
import com.mareco.app.data.MacroAction
import com.mareco.app.data.SystemKey
import java.util.Locale

/**
 * The macro timeline: every recorded step in order. Select a step to edit, duplicate, delete or move it;
 * add waits and steps; clear everything. Every change is saved immediately.
 */
class EditorScreen(act: MainActivity, private val macroId: String) : Screen(act) {
    override val title: String get() = act.getString(R.string.editor_title)
    override val key: String get() = MainActivity.KEY_EDITOR + macroId
    private val repo = Mareco.repo
    private var actions: List<MacroAction> = emptyList()
    private var selected = -1
    private var macro: Macro? = null
    private var adapter: RowAdapter? = null
    private var header: LinearLayout? = null
    private val tools = ArrayList<View>()

    override fun build(): View {
        val c: Context = act
        val m = repo.get(macroId)
        if (m == null) {
            act.window.decorView.post { act.pop() }
            return c.text(R.string.editor_gone, 15f, Pal.textDim)
        }
        macro = m
        actions = m.actions
        if (selected >= actions.size) selected = -1

        val root = c.vbox()
        root.setPadding(c.dp(16), c.dp(2), c.dp(16), c.dp(8))
        val h = c.vbox()
        header = h
        root.add(h)
        fillHeader()
        root.add(toolRow(c), t = 10)

        val lv = ListView(c)
        lv.divider = null
        lv.dividerHeight = c.dp(6)
        lv.selector = ColorDrawable(Color.TRANSPARENT)
        val ad = RowAdapter(c)
        adapter = ad
        lv.adapter = ad
        lv.setOnItemClickListener { _, _, pos, _ ->
            selected = if (selected == pos) -1 else pos
            ad.notifyDataSetChanged()
            updateTools()
        }
        lv.setOnItemLongClickListener { _, _, pos, _ ->
            selected = pos
            ad.notifyDataSetChanged()
            updateTools()
            edit()
            true
        }
        root.add(lv, MATCH, 0, 1f, t = 8)
        updateTools()
        return root
    }

    /** The macro changed (we saved something): refresh without losing the scroll position. */
    override fun onDataChanged() {
        val m = repo.get(macroId)
        if (m == null) {
            act.pop()
            return
        }
        macro = m
        actions = m.actions
        if (selected >= actions.size) selected = actions.size - 1
        adapter?.notifyDataSetChanged()
        fillHeader()
        updateTools()
    }

    private fun fillHeader() {
        val c: Context = act
        val h = header ?: return
        val m = repo.get(macroId) ?: return
        h.removeAllViews()
        val nameRow = c.hbox()
        val name = c.text(m.name, 21f, Pal.text, true)
        name.setSingleLine(true)
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        nameRow.add(name, 0, WRAP, 1f)
        nameRow.add(c.iconBtn(R.drawable.ic_edit, Pal.text, 20, 40) { rename() }, WRAP, WRAP)
        h.add(nameRow)
        if (m.description.isNotBlank()) h.add(c.text(m.description, 13.5f, Pal.textDim), t = 2)
        h.add(
            c.text(
                c.getString(R.string.editor_stats, m.actionCount.toString(), UiFmt.duration(c, m.loopLengthMs), Texts.executed(c, m)),
                12.5f, Pal.textDim
            ),
            t = 4
        )
        h.add(
            c.text(
                if (m.appLabel.isNotEmpty()) c.getString(R.string.editor_recorded_in, Texts.size(m.device), m.appLabel)
                else c.getString(R.string.editor_recorded, Texts.size(m.device)),
                12.5f, Pal.textDim
            ),
            t = 1
        )
        val df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, Lang.locale(c))
        h.add(
            c.text(
                c.getString(R.string.editor_dates, df.format(java.util.Date(m.createdAt)), df.format(java.util.Date(m.modifiedAt))),
                12.5f, Pal.textDim
            ),
            t = 1
        )
        h.add(c.text(R.string.editor_hint, 12.5f, Pal.textDim), t = 4)
        h.add(
            c.text(R.string.editor_edit_desc, 12.5f, Pal.accent, true).also {
                it.setPadding(0, c.dp(4), 0, c.dp(2))
                it.isClickable = true
                it.setOnClickListener { editDescription() }
            },
            t = 0
        )
    }

    private fun toolRow(c: Context): View {
        val row = c.hbox()
        tools.clear()
        fun tool(res: Int, tint: Int, needsSelection: Boolean, descId: Int, onClick: () -> Unit) {
            val b = c.iconBtn(res, tint, 20, 42, Pal.surface2, c.getString(descId)) { onClick() }
            b.tag = needsSelection
            tools.add(b)
            row.add(b, c.dp(42), c.dp(42), r = 6)
        }
        tool(R.drawable.ic_edit, Pal.text, true, R.string.cd_edit) { edit() }
        tool(R.drawable.ic_copy, Pal.text, true, R.string.cd_duplicate) { duplicate() }
        tool(R.drawable.ic_delete, Pal.danger, true, R.string.delete) { delete() }
        tool(R.drawable.ic_up, Pal.text, true, R.string.cd_up) { move(-1) }
        tool(R.drawable.ic_down, Pal.text, true, R.string.cd_down) { move(1) }
        tool(R.drawable.ic_timer, Pal.accent, false, R.string.cd_add_wait) { addWait() }
        tool(R.drawable.ic_add, Pal.accent, false, R.string.cd_add_step) { addStep() }
        row.add(View(c), 0, 1, 1f)
        row.add(c.button(R.string.editor_clear_all, Btn.GHOST) { clearAll() }, WRAP, WRAP)
        return row
    }

    private fun updateTools() {
        for (b in tools) b.alpha = if ((b.tag as? Boolean) == true && selected < 0) 0.35f else 1f
    }

    // ----------------------------------------------------------------------- operations

    private fun commit(list: List<MacroAction>, sel: Int) {
        actions = list
        selected = sel
        adapter?.notifyDataSetChanged()
        updateTools()
        repo.update(macroId) { it.copy(actions = list) }
    }

    private fun needSelection(): Int {
        if (selected < 0 || selected >= actions.size) {
            act.toast(act.getString(R.string.toast_select_step))
            return -1
        }
        return selected
    }

    private fun edit() {
        val i = needSelection()
        if (i < 0) return
        Dialogs.actionEditor(act.dialogs, actions[i], false) { edited ->
            val l = actions.toMutableList()
            l[i] = edited
            commit(l, i)
        }
    }

    private fun duplicate() {
        val i = needSelection()
        if (i < 0) return
        val l = actions.toMutableList()
        l.add(i + 1, actions[i])
        commit(l, i + 1)
    }

    private fun delete() {
        val i = needSelection()
        if (i < 0) return
        val l = actions.toMutableList()
        l.removeAt(i)
        commit(l, if (l.isEmpty()) -1 else minOf(i, l.size - 1))
    }

    private fun move(d: Int) {
        val i = needSelection()
        if (i < 0) return
        val j = i + d
        if (j < 0 || j >= actions.size) return
        val l = actions.toMutableList()
        val tmp = l[i]
        l[i] = l[j]
        l[j] = tmp
        commit(l, j)
    }

    private fun insert(a: MacroAction) {
        val at = if (selected >= 0) minOf(selected + 1, actions.size) else actions.size
        val l = actions.toMutableList()
        l.add(at, a)
        commit(l, at)
    }

    private fun addWait() {
        Dialogs.actionEditor(act.dialogs, MacroAction.wait(1000L), true) { insert(it) }
    }

    private fun addStep() {
        val m = macro
        val cx = if (m != null && m.device.width > 0) m.device.width / 2f else 500f
        val cy = if (m != null && m.device.height > 0) m.device.height / 2f else 800f
        val options = listOf(
            act.getString(R.string.step_tap), act.getString(R.string.step_long_press), act.getString(R.string.step_swipe),
            act.getString(R.string.step_back_btn), act.getString(R.string.step_home_btn), act.getString(R.string.step_recents_btn)
        )
        Dialogs.choice(act.dialogs, act.getString(R.string.editor_add_step), options, -1) { i ->
            val base = when (i) {
                0 -> MacroAction.tap(cx, cy, 60L, 500L)
                1 -> MacroAction.longPress(cx, cy, 800L, 500L)
                2 -> MacroAction.swipe(cx, cy * 1.2f, cx, cy * 0.6f, 300L, 500L)
                3 -> MacroAction.key(SystemKey.BACK, 500L)
                4 -> MacroAction.key(SystemKey.HOME, 500L)
                else -> MacroAction.key(SystemKey.RECENTS, 500L)
            }
            Dialogs.actionEditor(act.dialogs, base, true) { insert(it) }
        }
    }

    private fun clearAll() {
        if (actions.isEmpty()) return
        Dialogs.confirm(
            act.dialogs, act.getString(R.string.editor_clear_title), act.getString(R.string.editor_clear_msg),
            act.getString(R.string.editor_clear_all), act.getString(R.string.cancel), danger = true
        ) { ok -> if (ok) commit(emptyList(), -1) }
    }

    private fun rename() {
        val m = repo.get(macroId) ?: return
        Dialogs.name(act.dialogs, act.getString(R.string.dlg_rename_title), m.name, act.getString(R.string.save), act.getString(R.string.cancel)) { text ->
            if (text != null && text.isNotBlank()) {
                val clean = text.trim()
                repo.rename(
                    macroId,
                    if (clean.equals(m.name, ignoreCase = true)) clean else repo.uniqueName(clean, act.getString(R.string.default_macro_name))
                )
            }
        }
    }

    private fun editDescription() {
        val m = repo.get(macroId) ?: return
        Dialogs.name(act.dialogs, act.getString(R.string.dlg_desc_title), m.description, act.getString(R.string.save), act.getString(R.string.cancel)) { text ->
            if (text != null) repo.rename(macroId, m.name, text.trim())
        }
    }

    // ------------------------------------------------------------------------- list rows

    private inner class RowAdapter(val c: Context) : BaseAdapter() {
        override fun getCount(): Int = actions.size
        override fun getItem(position: Int): Any = actions[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val a = actions[position]
            val sel = position == selected
            val row = c.hbox()
            row.background = shape(
                if (sel) 0x2600B4FF else Pal.surface, c.dpf(12f),
                if (sel) Pal.accent else Pal.stroke, if (sel) 2 else 1
            )
            row.setPadding(c.dp(10), c.dp(10), c.dp(10), c.dp(10))
            row.add(c.text(String.format(Locale.US, "%02d", position + 1), 13f, Pal.textDim, true), WRAP, WRAP, r = 8)
            val icon = c.iconBtn(Texts.icon(a), Pal.accent, 20, 30) {}
            icon.isClickable = false
            row.add(icon, c.dp(30), c.dp(30), r = 8)
            row.add(c.text(Texts.summary(c, a), 14.5f), 0, WRAP, 1f)
            if (a.type != ActionType.WAIT && a.delayMs > 0L) {
                val chip = c.text(UiFmt.ltr("+" + UiFmt.duration(c, a.delayMs)), 11.5f, Pal.textDim, true)
                chip.setPadding(c.dp(8), c.dp(3), c.dp(8), c.dp(3))
                chip.background = shape(Pal.surface2, c.dpf(8f))
                row.add(chip, WRAP, WRAP, l = 8)
            }
            return row
        }
    }
}
