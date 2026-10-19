package com.mareco.app.ui

import android.view.View
import android.widget.LinearLayout
import com.mareco.app.Lang
import com.mareco.app.Languages
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.core.Platform
import com.mareco.app.data.AppSettings

/**
 * The settings list. The very same view is shown on the app's Settings screen and inside the
 * floating controls' gear panel, so both always offer the same options.
 */
object SettingsView {

    fun build(host: DialogHost, inOverlay: Boolean, onOpenSetup: (() -> Unit)? = null): View {
        val c = host.context
        val s = Mareco.settings
        val root = c.vbox()

        fun section(title: String): LinearLayout {
            val box = c.vbox()
            box.add(c.sectionTitle(title), t = 18, b = 4)
            root.add(box)
            return box
        }

        fun secondsRow(label: String, current: Int, set: (Int) -> Unit): View {
            val values = listOf(0, 3, 5, 10)
            val labels = values.map { if (it == 0) c.getString(R.string.sec_off) else UiFmt.seconds(c, it) }
            val idx = values.indexOf(current)
            val btn = c.dropdown(if (idx >= 0) labels[idx] else UiFmt.seconds(c, current)) {}
            btn.setOnClickListener {
                Dialogs.choice(host, label, labels, idx) { i ->
                    set(values[i])
                    btn.text = labels[i]
                }
            }
            return c.stackRow(label, btn)
        }

        // ---- appearance
        val appearance = section(c.getString(R.string.set_appearance))

        // Language: "System default" follows the phone; every other entry is shown in its own language.
        val saved = Lang.saved(c)
        val currentName = Languages.byTag(saved)?.name ?: c.getString(R.string.lang_system)
        val langBtn = c.dropdown(currentName) {}
        langBtn.setOnClickListener {
            Dialogs.languagePicker(host) { tag ->
                if (tag != Lang.saved(c)) s.language = tag
            }
        }
        appearance.add(c.stackRow(c.getString(R.string.set_language), langBtn))
        appearance.add(c.text(R.string.set_language_sub, 12.5f, Pal.textDim), t = 2)

        val themeLabels = listOf(c.getString(R.string.theme_system), c.getString(R.string.theme_light), c.getString(R.string.theme_dark))
        val themeBtn = c.dropdown(themeLabels[s.theme.ordinal]) {}
        themeBtn.setOnClickListener {
            Dialogs.choice(host, c.getString(R.string.set_theme), themeLabels, s.theme.ordinal) { i ->
                s.theme = AppSettings.Theme.entries[i]
                themeBtn.text = themeLabels[i]
            }
        }
        appearance.add(c.stackRow(c.getString(R.string.set_theme), themeBtn), t = 8)
        appearance.add(c.switchRow(c.getString(R.string.set_haptics), c.getString(R.string.set_haptics_sub), s.haptics) { s.haptics = it })

        // ---- floating controls
        val floating = section(c.getString(R.string.set_floating))
        floating.add(
            c.stackRow(
                c.getString(R.string.set_size),
                Segmented(
                    c,
                    listOf(c.getString(R.string.size_small), c.getString(R.string.size_medium), c.getString(R.string.size_large)),
                    s.barSize.ordinal
                ) { s.barSize = AppSettings.BarSize.entries[it] }
            )
        )
        floating.add(
            c.stackRow(
                c.getString(R.string.set_opacity),
                NumberField(c, s.overlayAlpha * 100.0, 30.0, 100.0, 5.0, 0, "%") { s.overlayAlpha = (it / 100.0).toFloat() }
            )
        )
        floating.add(
            c.switchRow(
                c.getString(R.string.set_restore),
                c.getString(R.string.set_restore_sub),
                s.restoreOverlay
            ) { s.restoreOverlay = it }
        )
        floating.add(
            c.button(R.string.set_reset_pos, Btn.SECONDARY) {
                val ctl = Mareco.controller
                if (ctl != null) {
                    ctl.overlay.resetToolbarPosition()
                } else {
                    s.overlayX = -1
                    s.overlayY = -1
                }
                host.toast(c.getString(R.string.toast_pos_reset))
            },
            t = 6
        )

        // ---- recording
        val rec = section(c.getString(R.string.set_recording))
        rec.add(
            c.stackRow(
                c.getString(R.string.set_style),
                Segmented(c, listOf(c.getString(R.string.style_live), c.getString(R.string.style_plan)), if (s.recordLive) 0 else 1) { s.recordLive = it == 0 }
            )
        )
        rec.add(c.text(R.string.set_style_help, 12.5f, Pal.textDim), t = 2)
        rec.add(c.switchRow(c.getString(R.string.set_rec_keys), c.getString(R.string.set_rec_keys_sub), s.recordKeys) { s.recordKeys = it })
        rec.add(c.switchRow(c.getString(R.string.set_rec_markers), c.getString(R.string.set_rec_markers_sub), s.recordMarkers) { s.recordMarkers = it })
        rec.add(secondsRow(c.getString(R.string.set_rec_countdown), s.recordCountdown) { s.recordCountdown = it })

        // ---- playback
        val play = section(c.getString(R.string.set_playback))
        play.add(secondsRow(c.getString(R.string.set_run_countdown), s.runCountdown) { s.runCountdown = it })
        play.add(c.switchRow(c.getString(R.string.set_keep_on), c.getString(R.string.set_keep_on_sub), s.keepScreenOn) { s.keepScreenOn = it })
        play.add(c.switchRow(c.getString(R.string.set_run_markers), c.getString(R.string.set_run_markers_sub), s.runMarkers) { s.runMarkers = it })
        play.add(
            c.button(R.string.set_default_exec, Btn.SECONDARY) {
                Dialogs.executionSettings(host, c.getString(R.string.exec_default_title), s.defaultExecution, null, "") { s.defaultExecution = it }
            },
            t = 6
        )

        // ---- safety
        val safety = section(c.getString(R.string.set_safety))
        safety.add(c.switchRow(c.getString(R.string.set_confirm_inf), c.getString(R.string.set_confirm_inf_sub), s.confirmInfinite) { s.confirmInfinite = it })
        safety.add(c.switchRow(c.getString(R.string.set_stop_screen_off), null, s.stopOnScreenOff) { s.stopOnScreenOff = it })
        safety.add(
            c.switchRow(
                c.getString(R.string.set_vol_stop),
                c.getString(R.string.set_vol_stop_sub),
                s.volumeStop
            ) { s.volumeStop = it }
        )
        safety.add(
            c.stackRow(
                c.getString(R.string.set_max_time),
                NumberField(c, s.maxRuntimeMin.toDouble(), 0.0, 100_000.0, 5.0, 0, c.getString(R.string.unit_min)) { s.maxRuntimeMin = it.toInt() }
            )
        )
        safety.add(
            c.stackRow(
                c.getString(R.string.set_max_exec),
                NumberField(c, s.maxExecutions.toDouble(), 0.0, 10_000_000.0, 10.0, 0) { s.maxExecutions = it.toInt() }
            )
        )
        safety.add(
            c.stackRow(
                c.getString(R.string.set_stop_battery),
                NumberField(c, s.stopBatteryPercent.toDouble(), 0.0, 99.0, 5.0, 0, "%") { s.stopBatteryPercent = it.toInt() }
            )
        )

        // ---- permissions (app screen only)
        if (!inOverlay) {
            val perm = section(c.getString(R.string.set_permissions))
            val on = Platform.isServiceEnabled(c)
            perm.add(
                c.text(
                    if (on) "✓ " + c.getString(R.string.perm_on) else "⚠ " + c.getString(R.string.perm_off),
                    14.5f, if (on) Pal.ok else Pal.warn, true
                ),
                t = 4
            )
            perm.add(c.button(R.string.perm_open_guide, Btn.SECONDARY) { onOpenSetup?.invoke() }, t = 8)
        }

        // ---- about
        val about = section(c.getString(R.string.set_about))
        about.add(c.text(c.getString(R.string.about_text, Platform.versionName(c)), 13f, Pal.textDim))
        return root
    }
}
