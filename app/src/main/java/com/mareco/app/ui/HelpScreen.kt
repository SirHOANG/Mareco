package com.mareco.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.mareco.app.R

/** How Mareco works and, just as important, what Android does NOT allow (spec section 17). */
class HelpScreen(act: MainActivity) : Screen(act) {
    override val title: String get() = act.getString(R.string.help_title)
    override val key: String get() = MainActivity.KEY_HELP

    override fun build(): View {
        val c: Context = act
        val scroll = ScrollView(c)
        val col = c.vbox()
        col.setPadding(c.dp(20), c.dp(0), c.dp(20), c.dp(32))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))

        fun h(id: Int) = col.add(c.sectionTitle(c.getString(id)), t = 22, b = 4)
        fun p(id: Int) = col.add(c.text(id, 14f, Pal.text), t = 4)

        h(R.string.help_bar_title)
        p(R.string.help_bar_play)
        p(R.string.help_bar_hold)
        p(R.string.help_bar_gear)
        p(R.string.help_bar_move)

        h(R.string.help_flow_title)
        p(R.string.help_flow_body)

        h(R.string.help_live_title)
        p(R.string.help_live_body)
        p(R.string.help_plan_body)

        h(R.string.help_limits_title)
        p(R.string.help_limit_1)
        p(R.string.help_limit_2)
        p(R.string.help_limit_3)
        p(R.string.help_limit_4)
        p(R.string.help_limit_5)
        p(R.string.help_limit_6)

        h(R.string.help_privacy_title)
        p(R.string.help_privacy_body)
        return scroll
    }
}
