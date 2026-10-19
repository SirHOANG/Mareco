package com.mareco.app.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import android.widget.Toast
import com.mareco.app.Lang
import com.mareco.app.R
import com.mareco.app.data.DeviceInfo
import com.mareco.app.service.MarecoAccessibilityService

/** Thin wrappers around Android APIs that need a bit of care. */
object Platform {

    /** Real display size in pixels in the current orientation (this is what gestures use). */
    @Suppress("DEPRECATION")
    fun display(ctx: Context): DeviceInfo {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics()
        d.getRealMetrics(m)
        return DeviceInfo(m.widthPixels, m.heightPixels, m.densityDpi, d.rotation, Build.MODEL ?: "", Build.VERSION.SDK_INT)
    }

    @Suppress("DEPRECATION")
    private fun vibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun vibrate(ctx: Context, ms: Long) {
        try {
            vibrator(ctx)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // Haptics are a nicety, never a reason to fail.
        }
    }

    @Suppress("DEPRECATION")
    fun appLabel(ctx: Context, pkg: String): String {
        if (pkg.isEmpty()) return ""
        return try {
            val pm = ctx.packageManager
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    /** True when the user has switched the Mareco accessibility service on in Android settings. */
    fun isServiceEnabled(ctx: Context): Boolean {
        return try {
            val on = Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
            if (!on) return false
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(ctx, MarecoAccessibilityService::class.java)
            list.split(':').any { ComponentName.unflattenFromString(it) == me }
        } catch (e: Exception) {
            false
        }
    }

    /** True when Android has actually connected the service (the floating controls can work). */
    val isServiceConnected: Boolean get() = MarecoAccessibilityService.instance != null

    fun openAccessibilitySettings(ctx: Context) {
        val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            toast(ctx, Lang.wrap(ctx).getString(R.string.toast_a11y_manual), true)
        }
    }

    fun openAppInfo(ctx: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            toast(ctx, Lang.wrap(ctx).getString(R.string.toast_appinfo_manual), true)
        }
    }

    @Suppress("DEPRECATION")
    fun versionName(ctx: Context): String =
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

    fun toast(ctx: Context, msg: String, long: Boolean = false) {
        Toast.makeText(ctx, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}
