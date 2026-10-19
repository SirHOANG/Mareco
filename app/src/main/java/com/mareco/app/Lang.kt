package com.mareco.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies the language the user picked in Settings. Without a choice Mareco simply follows the phone:
 * Android picks the matching res/values-xx folder by itself, so a phone set to Vietnamese shows Vietnamese.
 *
 * The choice is stored in the same SharedPreferences file as the other settings. It is read directly here
 * (not through [Mareco.settings]) because activities need it in attachBaseContext, before anything else runs.
 */
object Lang {
    private const val PREFS = "mareco_prefs"
    private const val KEY = "language"

    /** The saved language tag, or "" for "follow the phone". */
    fun saved(ctx: Context): String =
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        } catch (e: Exception) {
            ""
        }

    /**
     * Returns [base] with the chosen language applied (or [base] itself when none was chosen).
     * Only the locale is overridden. Font size, dark mode, rotation ... keep following the system.
     */
    fun wrap(base: Context): Context {
        val tag = saved(base)
        if (Languages.byTag(tag) == null) return base
        return try {
            val cfg = Configuration()
            cfg.fontScale = 0f // 0 = "not overridden"
            cfg.setLocale(Locale.forLanguageTag(tag))
            base.createConfigurationContext(cfg)
        } catch (e: Exception) {
            base
        }
    }

    /** The locale this context shows its texts in. */
    fun locale(ctx: Context): Locale = ctx.resources.configuration.locales[0]

    /** The language the phone itself asks for (including Android 13+ "app language" in system settings). */
    fun phoneLanguage(ctx: Context): Language {
        val l = ctx.applicationContext.resources.configuration.locales[0]
        return Languages.match(l.language, l.script, l.country)
    }
}
