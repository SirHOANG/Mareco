package com.mareco.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.mareco.app.ui.CrashActivity
import java.io.File
import java.util.Date
import kotlin.system.exitProcess

/**
 * Turns "Mareco has stopped" into something useful: the exact error is saved to a file and shown on
 * a small screen with Copy / Share buttons, so a crash can be reported (or fixed) in one message.
 * The screen lives in its own process, so it still opens when the main process is broken.
 */
object CrashReporter {
    const val EXTRA_TRACE = "trace"
    private const val FILE_NAME = "last_crash.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val saved = try {
                write(app, "Crash in thread \"" + thread.name + "\"", error, launchScreen = true)
                true
            } catch (t: Throwable) {
                false
            }
            if (saved) {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    /** Remembers an error that was caught (the app keeps running). */
    fun record(ctx: Context, where: String, error: Throwable) {
        try {
            write(ctx, where, error, launchScreen = false)
        } catch (t: Throwable) {
            Log.e("Mareco", where, error)
        }
    }

    fun lastCrash(ctx: Context): String? {
        return try {
            val f = File(ctx.filesDir, FILE_NAME)
            if (f.isFile) f.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun clear(ctx: Context) {
        try {
            File(ctx.filesDir, FILE_NAME).delete()
        } catch (e: Exception) {
            // nothing to do
        }
    }

    private fun write(ctx: Context, where: String, error: Throwable, launchScreen: Boolean) {
        val trace = build(ctx, where, error)
        val f = File(ctx.filesDir, FILE_NAME)
        // Two crashes within a few seconds (a crash loop): keep the file, do not pop the screen again.
        val recent = f.exists() && System.currentTimeMillis() - f.lastModified() < 8_000L
        f.writeText(trace)
        if (launchScreen && !recent) {
            val i = Intent(ctx, CrashActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_TRACE, trace)
            ctx.startActivity(i)
            try {
                Thread.sleep(500L)
            } catch (e: InterruptedException) {
                // fine
            }
        }
    }

    private fun build(ctx: Context, where: String, error: Throwable): String {
        val sb = StringBuilder()
        sb.append("Mareco ").append(com.mareco.app.core.Platform.versionName(ctx)).append('\n')
        sb.append("When: ").append(Date()).append('\n')
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Where: ").append(where).append("\n\n")
        sb.append(Log.getStackTraceString(error))
        return sb.toString()
    }
}
