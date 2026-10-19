package com.mareco.app

import android.app.Application
import java.io.File

class MarecoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The crash-report screen runs in its own process and must stay minimal.
        if (processName().endsWith(":crash")) return
        CrashReporter.install(this)
        Mareco.init(this)
    }

    private fun processName(): String =
        try {
            File("/proc/self/cmdline").readText().trim { it == '\u0000' }
        } catch (e: Exception) {
            ""
        }
}
