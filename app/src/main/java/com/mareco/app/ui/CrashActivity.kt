package com.mareco.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mareco.app.CrashReporter
import com.mareco.app.R

/**
 * Shows the error text after a crash. Runs in its own process (":crash") and deliberately uses only
 * plain Android views, so it cannot be broken by whatever broke the app.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(CrashReporter.EXTRA_TRACE) ?: CrashReporter.lastCrash(this) ?: getString(R.string.crash_no_details)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0E1116"))
        root.setPadding(dp(16), dp(40), dp(16), dp(16))

        val title = TextView(this)
        title.text = getString(R.string.crash_title)
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        root.addView(title)

        val hint = TextView(this)
        hint.text = getString(R.string.crash_hint)
        hint.setTextColor(Color.parseColor("#9AA5B5"))
        hint.textSize = 14f
        hint.setPadding(0, dp(6), 0, dp(10))
        root.addView(hint)

        val scroll = ScrollView(this)
        val body = TextView(this)
        body.text = trace
        body.setTextColor(Color.parseColor("#E6EAF0"))
        body.textSize = 11f
        body.typeface = Typeface.MONOSPACE
        body.setTextIsSelectable(true)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val copy = Button(this)
        copy.text = getString(R.string.copy)
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Mareco crash", trace))
            Toast.makeText(this, getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
        }
        val share = Button(this)
        share.text = getString(R.string.share)
        share.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, trace)
            startActivity(Intent.createChooser(send, getString(R.string.crash_share_title)))
        }
        val close = Button(this)
        close.text = getString(R.string.close)
        close.setOnClickListener {
            CrashReporter.clear(this)
            finishAndRemoveTask()
        }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(copy, lp)
        row.addView(share, lp)
        row.addView(close, lp)
        root.addView(row)
        setContentView(root)
    }
}
