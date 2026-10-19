package com.mareco.app.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.mareco.app.Lang

/**
 * Handles the Back button the way Android 16 requires: apps targeting API 36 get the predictive-back
 * callback API, older devices still use onBackPressed().
 * Also applies the language chosen in Settings (see [Lang]) to every screen.
 */
open class BaseActivity : Activity() {
    /** Return from this lambda after handling back; leave null to just close the screen. */
    protected var backHandler: (() -> Unit)? = null
    private var backCallback: OnBackInvokedCallback? = null
    private var langTag = ""

    override fun attachBaseContext(newBase: Context) {
        langTag = Lang.saved(newBase)
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val cb = OnBackInvokedCallback { dispatchBack() }
                backCallback = cb
                onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            } catch (e: Exception) {
                backCallback = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The language was changed while this screen was in the background (for example from the floating bar).
        if (langTag != Lang.saved(this)) recreate()
    }

    private fun dispatchBack() {
        val h = backHandler
        if (h != null) h() else finish()
    }

    @Deprecated("Used on Android 12 and older")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= 33) {
            super.onBackPressed()
        } else {
            dispatchBack()
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            } catch (e: Exception) {
                // already gone
            }
        }
        super.onDestroy()
    }
}
