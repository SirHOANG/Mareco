package com.mareco.app.data

import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArrayList

/** Typed wrapper around SharedPreferences. Every setter notifies the listeners with the key. */
class AppSettings(private val prefs: SharedPreferences) {

    enum class Theme { SYSTEM, LIGHT, DARK }

    /** Which job the floating play button does. */
    enum class Mode { RECORD, START }

    enum class BarSize(val iconDp: Int) { SMALL(30), MEDIUM(38), LARGE(46) }

    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun addListener(l: (String) -> Unit) {
        listeners.addIfAbsent(l)
    }

    fun removeListener(l: (String) -> Unit) {
        listeners.remove(l)
    }

    private fun fire(key: String) {
        for (l in listeners) l(key)
    }

    private fun putBool(key: String, v: Boolean) {
        prefs.edit().putBoolean(key, v).apply(); fire(key)
    }

    private fun putInt(key: String, v: Int) {
        prefs.edit().putInt(key, v).apply(); fire(key)
    }

    private fun putFloat(key: String, v: Float) {
        prefs.edit().putFloat(key, v).apply(); fire(key)
    }

    private fun putStr(key: String, v: String) {
        prefs.edit().putString(key, v).apply(); fire(key)
    }

    private inline fun <reified T : Enum<T>> enumVal(key: String, def: T): T {
        val s = prefs.getString(key, null) ?: return def
        return try {
            enumValueOf<T>(s)
        } catch (e: IllegalArgumentException) {
            def
        }
    }

    // ---- appearance
    var theme: Theme
        get() = enumVal("theme", Theme.SYSTEM)
        set(v) = putStr("theme", v.name)

    /** Language tag picked in Settings (see Languages), or "" to follow the phone's language. */
    var language: String
        get() = prefs.getString("language", "") ?: ""
        set(v) = putStr("language", v)

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = putBool("haptics", v)

    // ---- floating controls
    var mode: Mode
        get() = enumVal("mode", Mode.RECORD)
        set(v) = putStr("mode", v.name)

    var defaultMacroId: String
        get() = prefs.getString("default_macro", "") ?: ""
        set(v) = putStr("default_macro", v)

    var overlayX: Int
        get() = prefs.getInt("overlay_x", -1)
        set(v) = putInt("overlay_x", v)

    var overlayY: Int
        get() = prefs.getInt("overlay_y", -1)
        set(v) = putInt("overlay_y", v)

    var overlayAlpha: Float
        get() = prefs.getFloat("overlay_alpha", 0.92f).coerceIn(0.3f, 1f)
        set(v) = putFloat("overlay_alpha", v.coerceIn(0.3f, 1f))

    var barSize: BarSize
        get() = enumVal("bar_size", BarSize.MEDIUM)
        set(v) = putStr("bar_size", v.name)

    var overlayVisible: Boolean
        get() = prefs.getBoolean("overlay_visible", false)
        set(v) = putBool("overlay_visible", v)

    var restoreOverlay: Boolean
        get() = prefs.getBoolean("restore_overlay", true)
        set(v) = putBool("restore_overlay", v)

    // ---- recording
    /** true = "Live": touches also reach the app while recording. false = "Plan": record only. */
    var recordLive: Boolean
        get() = prefs.getBoolean("record_live", true)
        set(v) = putBool("record_live", v)

    var recordKeys: Boolean
        get() = prefs.getBoolean("record_keys", true)
        set(v) = putBool("record_keys", v)

    var recordMarkers: Boolean
        get() = prefs.getBoolean("record_markers", true)
        set(v) = putBool("record_markers", v)

    var recordCountdown: Int
        get() = prefs.getInt("record_countdown", 3).coerceIn(0, 30)
        set(v) = putInt("record_countdown", v.coerceIn(0, 30))

    // ---- running
    var runCountdown: Int
        get() = prefs.getInt("run_countdown", 3).coerceIn(0, 30)
        set(v) = putInt("run_countdown", v.coerceIn(0, 30))

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(v) = putBool("keep_screen_on", v)

    var runMarkers: Boolean
        get() = prefs.getBoolean("run_markers", false)
        set(v) = putBool("run_markers", v)

    // ---- safety
    var confirmInfinite: Boolean
        get() = prefs.getBoolean("confirm_infinite", true)
        set(v) = putBool("confirm_infinite", v)

    var stopOnScreenOff: Boolean
        get() = prefs.getBoolean("stop_screen_off", true)
        set(v) = putBool("stop_screen_off", v)

    var volumeStop: Boolean
        get() = prefs.getBoolean("volume_stop", true)
        set(v) = putBool("volume_stop", v)

    /** 0 = no limit. */
    var maxRuntimeMin: Int
        get() = prefs.getInt("max_runtime_min", 0).coerceIn(0, 100_000)
        set(v) = putInt("max_runtime_min", v.coerceIn(0, 100_000))

    /** 0 = no limit. */
    var maxExecutions: Int
        get() = prefs.getInt("max_executions", 0).coerceIn(0, 10_000_000)
        set(v) = putInt("max_executions", v.coerceIn(0, 10_000_000))

    /** Stop a running macro when the battery drops below this percent (0 = off). Ignored while charging. */
    var stopBatteryPercent: Int
        get() = prefs.getInt("stop_battery", 0).coerceIn(0, 99)
        set(v) = putInt("stop_battery", v.coerceIn(0, 99))

    var lastBootCount: Int
        get() = prefs.getInt("last_boot_count", -1)
        set(v) = putInt("last_boot_count", v)

    /** Execution settings given to every NEW recording. */
    var defaultExecution: ExecutionSettings
        get() {
            val s = prefs.getString("default_execution", null) ?: return ExecutionSettings()
            return try {
                MacroCodec.executionFromJson(org.json.JSONObject(s))
            } catch (e: Exception) {
                ExecutionSettings()
            }
        }
        set(v) = putStr("default_execution", MacroCodec.executionToJson(v).toString())
}
