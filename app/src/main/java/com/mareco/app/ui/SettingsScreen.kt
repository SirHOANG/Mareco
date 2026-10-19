package com.mareco.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.mareco.app.R

class SettingsScreen(act: MainActivity) : Screen(act) {
    override val title: String get() = act.getString(R.string.home_settings)
    override val key: String get() = MainActivity.KEY_SETTINGS

    override fun build(): View {
        val c: Context = act
        val scroll = ScrollView(c)
        val col = c.vbox()
        col.setPadding(c.dp(20), c.dp(0), c.dp(20), c.dp(32))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))
        col.add(SettingsView.build(act.dialogs, false) { act.push(SetupScreen(act)) })
        return scroll
    }

    /** Settings write straight to storage, so nothing to rebuild when data changes. */
    override fun onDataChanged() {}
}
