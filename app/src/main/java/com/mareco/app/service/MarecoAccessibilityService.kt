package com.mareco.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.mareco.app.CrashReporter
import com.mareco.app.Lang
import com.mareco.app.Mareco
import com.mareco.app.R

/**
 * The only thing Android lets an app use to perform taps and swipes for the user (dispatchGesture)
 * and to draw floating controls without the "display over other apps" permission.
 * Everything interesting happens in [MarecoController].
 */
class MarecoAccessibilityService : AccessibilityService() {
    private var controller: MarecoController? = null
    private var eventErrorReported = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            Mareco.controller?.dispose()
            val c = MarecoController(this)
            controller = c
            Mareco.controller = c
            c.onConnected()
        } catch (t: Throwable) {
            // Never crash a system-bound service: keep it alive and make the error readable instead.
            controller = null
            Mareco.controller = null
            CrashReporter.record(this, "Accessibility service failed to start", t)
            Toast.makeText(this, Lang.wrap(this).getString(R.string.toast_service_failed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event != null) controller?.onAccessibilityEvent(event)
        } catch (t: Throwable) {
            if (!eventErrorReported) {
                eventErrorReported = true
                CrashReporter.record(this, "Accessibility event failed", t)
            }
        }
    }

    override fun onInterrupt() {
        // nothing to interrupt: Mareco gives no spoken or haptic feedback through the service
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = controller?.onKeyEvent(event) ?: false

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        controller?.dispose()
        if (Mareco.controller === controller) Mareco.controller = null
        controller = null
        if (instance === this) instance = null
    }

    companion object {
        @Volatile
        var instance: MarecoAccessibilityService? = null
            private set
    }
}
