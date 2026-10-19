package com.mareco.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import com.mareco.app.CrashReporter
import com.mareco.app.Lang
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.data.Macro
import com.mareco.app.data.MacroFormatException
import com.mareco.app.share.MacroShareProvider
import java.io.File
import java.io.IOException

/** One screen of the app (Home, My Macros, Editor ...). The activity shows one at a time. */
abstract class Screen(val act: MainActivity) {
    abstract val title: String
    abstract fun build(): View

    /**
     * Short name that lets the activity bring this screen back after it was recreated (for example when the
     * language changes). Null = the screen is not restored.
     */
    open val key: String? get() = null

    open val refreshOnResume: Boolean get() = false
    open fun onShow() {}
    open fun onStateChanged() {}

    /** The macro list changed. Default: rebuild. */
    open fun onDataChanged() {
        act.refreshScreen()
    }
}

class MainActivity : BaseActivity() {
    val dialogs: DialogHost by lazy { ActivityDialogHost(this) }

    private lateinit var barTitle: TextView
    private lateinit var barBack: ImageView
    private lateinit var barLang: ImageView
    private lateinit var content: FrameLayout
    private val stack = ArrayList<Screen>()
    private var paletteVersion = -1
    private var pendingExportId: String? = null

    private val stateListener: () -> Unit = { guard("status update") { stack.lastOrNull()?.onStateChanged() } }
    private val repoListener: () -> Unit = { guard("list update") { stack.lastOrNull()?.onDataChanged() } }
    private val settingsListener: (String) -> Unit = { key ->
        if (key == "theme") {
            Pal.apply(this)
            rebuildAll()
        } else if (key == "language") {
            // New language: Android rebuilds the screen with the new texts (see onSaveInstanceState / restoreStack).
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Pal.apply(this)
        super.onCreate(savedInstanceState)
        paletteVersion = Pal.version
        try {
            buildChrome()
            stack.add(HomeScreen(this))
            restoreStack(savedInstanceState?.getStringArrayList(STATE_STACK))
            show()
            backHandler = { back() }
            handleIntent(intent)
            checkLastCrash()
        } catch (t: Throwable) {
            // Show the real error instead of "Mareco has stopped".
            CrashReporter.record(this, "MainActivity.onCreate", t)
            val trace = CrashReporter.lastCrash(this) ?: t.toString()
            startActivity(Intent(this, CrashActivity::class.java).putExtra(CrashReporter.EXTRA_TRACE, trace))
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_STACK, ArrayList(stack.drop(1).mapNotNull { it.key }))
    }

    /** Puts back the screens that were open before the activity was recreated (the home screen is always first). */
    private fun restoreStack(keys: List<String>?) {
        if (keys == null) return
        for (k in keys) {
            val s: Screen? = when {
                k == KEY_MACROS -> MacrosScreen(this)
                k == KEY_SETTINGS -> SettingsScreen(this)
                k == KEY_HELP -> HelpScreen(this)
                k == KEY_SETUP -> SetupScreen(this)
                k.startsWith(KEY_EDITOR) -> EditorScreen(this, k.removePrefix(KEY_EDITOR))
                else -> null
            }
            if (s != null) stack.add(s)
        }
    }

    override fun onStart() {
        super.onStart()
        Mareco.state.addListener(stateListener)
        Mareco.repo.addListener(repoListener)
        Mareco.settings.addListener(settingsListener)
    }

    override fun onStop() {
        Mareco.state.removeListener(stateListener)
        Mareco.repo.removeListener(repoListener)
        Mareco.settings.removeListener(settingsListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        Pal.apply(this)
        if (paletteVersion != Pal.version) {
            paletteVersion = Pal.version
            rebuildAll()
        } else {
            val top = stack.lastOrNull()
            guard("resume") { if (top != null && top.refreshOnResume) show() else top?.onShow() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Pal.apply(this)
        paletteVersion = Pal.version
        rebuildAll()
    }

    // ------------------------------------------------------------------ chrome + navigation

    private fun buildChrome() {
        styleSystemBars()
        val root = vbox()
        root.setBackgroundColor(Pal.bg)
        root.applyBarInsets()
        val bar = hbox()
        bar.setPadding(dp(8), dp(4), dp(8), dp(4))
        barBack = iconBtn(R.drawable.ic_back, Pal.text, 24, 44, desc = getString(R.string.key_back)) { back() }
        barTitle = text(getString(R.string.app_name), 21f, Pal.text, true)
        barTitle.setSingleLine(true)
        barTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        barLang = iconBtn(R.drawable.ic_language, Pal.text, 24, 44, desc = getString(R.string.set_language)) { pickLanguage() }
        bar.add(barBack, WRAP, WRAP)
        bar.add(barTitle, 0, WRAP, 1f, l = 6)
        bar.add(barLang, WRAP, WRAP)
        root.add(bar, MATCH, dp(56))
        content = FrameLayout(this)
        root.add(content, MATCH, 0, 1f)
        setContentView(root)
        styleSystemBars()
    }

    /** The globe button: the same language list as Settings > Language. */
    private fun pickLanguage() {
        Dialogs.languagePicker(dialogs) { tag ->
            if (tag != Lang.saved(this)) Mareco.settings.language = tag
        }
    }

    /** A bug in one screen must never take the whole app down: show the error instead. */
    private fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            CrashReporter.record(this, what, t)
            toast(getString(R.string.toast_screen_error, what))
        }
    }

    private fun errorView(t: Throwable): View {
        val sv = ScrollView(this)
        val tv = TextView(this)
        tv.text = getString(R.string.err_screen) + "\n\n" + android.util.Log.getStackTraceString(t)
        tv.setTextColor(Pal.text)
        tv.textSize = 12f
        tv.setTextIsSelectable(true)
        tv.setPadding(dp(16), dp(16), dp(16), dp(16))
        sv.addView(tv)
        return sv
    }

    private fun checkLastCrash() {
        val trace = CrashReporter.lastCrash(this) ?: return
        val c = dialogs.context
        val box = c.vbox()
        box.add(c.text(getString(R.string.crash_last_title), 19f, bold = true))
        box.add(c.text(getString(R.string.crash_send_hint), 13.5f, Pal.textDim), t = 6)
        val tv = c.text(trace.take(5000), 11f, Pal.text)
        tv.typeface = android.graphics.Typeface.MONOSPACE
        tv.setTextIsSelectable(true)
        box.add(tv, t = 10)
        var handle: DialogHandle? = null
        val row = c.hbox()
        row.add(
            c.button(R.string.copy) {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Mareco crash", trace))
                toast(getString(R.string.toast_copied))
            },
            0, WRAP, 1f, r = 6
        )
        row.add(
            c.button(R.string.dismiss, Btn.SECONDARY) {
                CrashReporter.clear(this)
                handle?.dismiss()
            },
            0, WRAP, 1f, l = 6
        )
        box.add(row, t = 14)
        handle = dialogs.show(box, scroll = true)
    }

    private fun rebuildAll() {
        buildChrome()
        show()
    }

    private fun show() {
        val s = stack[stack.size - 1]
        content.removeAllViews()
        val built = try {
            s.build()
        } catch (t: Throwable) {
            CrashReporter.record(this, "Screen \"" + s.title + "\"", t)
            errorView(t)
        }
        content.addView(built, FrameLayout.LayoutParams(MATCH, MATCH))
        barTitle.text = s.title
        barBack.show(stack.size > 1)
        barLang.show(stack.size == 1)
        guard("screen " + s.title) { s.onShow() }
    }

    fun refreshScreen() {
        show()
    }

    fun push(s: Screen) {
        stack.add(s)
        show()
    }

    fun pop() {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            show()
        }
    }

    private fun back() {
        if (stack.size > 1) pop() else finish()
    }

    fun toast(msg: String) {
        (this as android.content.Context).toast(msg)
    }

    // ----------------------------------------------------------------- actions from screens

    /**
     * "Start Recording": only brings up the floating bar. Nothing is recorded until the user opens their game
     * and presses the blue ● record button on the bar.
     */
    fun recordFromApp() {
        val ctl = Mareco.controller
        if (ctl == null) {
            push(SetupScreen(this))
            return
        }
        ctl.armRecording()
        moveTaskToBack(true)
    }

    fun runFromApp(macroId: String) {
        val ctl = Mareco.controller
        if (ctl == null) {
            push(SetupScreen(this))
            return
        }
        ctl.startRun(macroId, fromApp = true)
        moveTaskToBack(true)
    }

    // ---------------------------------------------------------------- import / export / share

    fun importMacro() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            startActivityForResult(i, REQ_IMPORT)
        } catch (e: Exception) {
            toast(getString(R.string.toast_no_picker))
        }
    }

    fun exportMacro(m: Macro) {
        pendingExportId = m.id
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream")
            .putExtra(Intent.EXTRA_TITLE, MacroShareProvider.safeFileName(m.name) + ".mareco")
        try {
            startActivityForResult(i, REQ_EXPORT)
        } catch (e: Exception) {
            toast(getString(R.string.toast_no_picker))
        }
    }

    fun shareMacro(m: Macro) {
        try {
            val text = Mareco.repo.exportText(m.id) ?: return
            val f = File(MacroShareProvider.shareDir(this), MacroShareProvider.safeFileName(m.name) + ".mareco")
            f.writeText(text, Charsets.UTF_8)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/x-mareco")
                .putExtra(Intent.EXTRA_STREAM, MacroShareProvider.uriFor(this, f))
                .putExtra(Intent.EXTRA_SUBJECT, m.name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, getString(R.string.share_chooser)))
        } catch (e: Exception) {
            toast(getString(R.string.toast_share_failed, e.message ?: getString(R.string.unknown_error)))
        }
    }

    @Deprecated("Framework callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_IMPORT -> importFromUri(uri)
            REQ_EXPORT -> {
                val id = pendingExportId ?: return
                pendingExportId = null
                val text = Mareco.repo.exportText(id) ?: return
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    toast(getString(R.string.toast_saved))
                } catch (e: Exception) {
                    toast(getString(R.string.toast_save_failed, e.message ?: getString(R.string.unknown_error)))
                }
            }
        }
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val uri = i.data
        if (i.action == Intent.ACTION_VIEW && uri != null) importFromUri(uri)
    }

    private fun importFromUri(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException(getString(R.string.import_cannot_open))
            if (bytes.size > 20_000_000) throw IOException(getString(R.string.import_too_large))
            val m = Mareco.repo.importText(String(bytes, Charsets.UTF_8))
            toast(getString(R.string.toast_import_ok, m.name))
        } catch (e: MacroFormatException) {
            toast(
                getString(
                    when (e.kind) {
                        MacroFormatException.Kind.NOT_VALID -> R.string.import_invalid
                        MacroFormatException.Kind.NOT_MARECO -> R.string.import_not_mareco
                        MacroFormatException.Kind.NEWER -> R.string.import_newer
                        MacroFormatException.Kind.EMPTY -> R.string.import_empty
                    }
                )
            )
        } catch (e: Exception) {
            toast(getString(R.string.toast_import_failed, e.message ?: getString(R.string.unknown_error)))
        }
    }

    companion object {
        private const val REQ_IMPORT = 101
        private const val REQ_EXPORT = 102
        private const val STATE_STACK = "mareco_screens"
        const val KEY_MACROS = "macros"
        const val KEY_SETTINGS = "settings"
        const val KEY_HELP = "help"
        const val KEY_SETUP = "setup"
        const val KEY_EDITOR = "editor:"
    }
}
