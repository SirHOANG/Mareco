package com.mareco.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import com.mareco.app.EngineState
import com.mareco.app.Mareco
import com.mareco.app.R
import com.mareco.app.core.DispatchResult
import com.mareco.app.core.FinishReason
import com.mareco.app.core.Fmt
import com.mareco.app.core.GesturePlan
import com.mareco.app.core.GesturePlanner
import com.mareco.app.core.MacroRunner
import com.mareco.app.core.Platform
import com.mareco.app.core.RunSummary
import com.mareco.app.core.RunnerHost
import com.mareco.app.core.Safety
import com.mareco.app.core.Scaler
import com.mareco.app.core.TimingPolicy
import com.mareco.app.core.TraceRecorder
import com.mareco.app.data.ActionType
import com.mareco.app.data.AppSettings
import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.ExecutionSettings
import com.mareco.app.data.Macro
import com.mareco.app.data.StopMode
import com.mareco.app.data.SystemKey
import com.mareco.app.ui.Dialogs
import com.mareco.app.ui.Pal
import com.mareco.app.ui.PromptActivity
import com.mareco.app.ui.Texts
import com.mareco.app.ui.UiFmt

/**
 * The brain behind the floating controls. It connects the three parts of Mareco:
 *  - recording  : CaptureView -> TraceRecorder -> a new Macro
 *  - replaying  : MacroRunner -> this class (as RunnerHost) -> dispatchGesture
 *  - the UI     : toolbar, overlay dialogs, the app screens (through [Mareco.state])
 * Everything runs on the main thread.
 */
class MarecoController(private val service: MarecoAccessibilityService) : RunnerHost {
    private val settings = Mareco.settings
    private val repo = Mareco.repo
    private val state = Mareco.state
    private val main = Mareco.main

    val overlay = OverlayManager(service, this)
    private val panels = OverlayPanels(this)

    /** A text in the language chosen for Mareco (see Lang); the overlay context is rebuilt when it changes. */
    private fun s(id: Int, vararg args: Any): String = overlay.ui.getString(id, *args)

    private val runner = MacroRunner(HandlerScheduler(main), this)

    // recording
    private var recorder: TraceRecorder? = null
    private var recDevice: DeviceInfo = DeviceInfo(0, 0)
    private var recPackage = ""
    private var recStart = 0L
    private var recPausedAt = 0L

    // run
    private var runningMacro: Macro? = null
    private var displayCache: DeviceInfo = Platform.display(service)

    // countdown
    private var countdownTask: Runnable? = null
    private var countdownDone: (() -> Unit)? = null

    // text prompts (name a recording ...)
    private class Prompt(val id: Long, val dismissSaves: Boolean, val initial: String, val onResult: (String?) -> Unit)

    private var prompt: Prompt? = null
    private var promptSeq = 0L
    private var promptFallback: Runnable? = null

    // misc
    @Volatile
    private var foregroundPackage: String? = null
    private var volUp = false
    private var volDown = false
    private var screenReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var disposed = false
    private var autoRunTask: Runnable? = null
    private val recTicker = object : Runnable {
        override fun run() {
            val rec = recorder ?: return
            if (state.engine == EngineState.RECORDING && !state.recordingPaused) {
                state.recordMs = SystemClock.uptimeMillis() - recStart
                state.recordActions = rec.actions.size
                render()
                state.changed()
            }
            main.postDelayed(this, 500L)
        }
    }
    private val settingsListener: (String) -> Unit = { key -> onSettingChanged(key) }
    private val repoListener: () -> Unit = { refreshIdle() }

    val isRecording: Boolean get() = recorder != null
    val isRunActive: Boolean get() = runner.isActive
    val isCountingDown: Boolean get() = countdownTask != null
    val isBusy: Boolean get() = isRecording || isRunActive || isCountingDown

    // ------------------------------------------------------------------ lifecycle

    fun onConnected() {
        safely("theme") { Pal.apply(service) }
        safely("listeners") {
            settings.addListener(settingsListener)
            repo.addListener(repoListener)
        }
        safely("screen receiver") { registerScreenReceiver() }
        safely("battery receiver") { registerBatteryReceiver() }
        safely("state") { refreshIdle() }
        safely("floating controls") { if (settings.overlayVisible && settings.restoreOverlay) showToolbar() }
        safely("auto execute") { checkAutoExecute() }
    }

    /** A failing optional step must not stop the others (or crash the service). */
    private fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            com.mareco.app.CrashReporter.record(service, "Service step failed: $what", t)
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        settings.removeListener(settingsListener)
        repo.removeListener(repoListener)
        cancelCountdown()
        autoRunTask?.let { main.removeCallbacks(it) }
        promptFallback?.let { main.removeCallbacks(it) }
        prompt = null
        if (runner.isActive) runner.stop(FinishReason.SERVICE_LOST)
        if (recorder != null) {
            recorder = null
            main.removeCallbacks(recTicker)
        }
        unregisterScreenReceiver()
        unregisterBatteryReceiver()
        overlay.removeAll()
        state.engine = EngineState.IDLE
        state.countdown = 0
        state.changed()
    }

    fun onConfigurationChanged() {
        val old = displayCache
        displayCache = Platform.display(service)
        if (Pal.apply(service)) onThemeChanged()
        overlay.onConfigurationChanged()
        if (recorder != null && old.rotation != displayCache.rotation) {
            toast(s(R.string.toast_rotated))
            finishRecording()
        }
    }

    private fun onSettingChanged(key: String) {
        when (key) {
            "theme" -> {
                if (Pal.apply(service)) onThemeChanged()
            }
            "language" -> onLanguageChanged()
            "bar_size" -> overlay.rebuildToolbar()
            "overlay_alpha" -> overlay.applyAppearance()
            "mode", "default_macro" -> {
                refreshIdle()
                render()
            }
        }
        updateKeyFilter()
    }

    /** New language: the bar is drawn again with the new texts; an open settings panel comes back in the new language. */
    private fun onLanguageChanged() {
        val reopen = overlay.hasPanel
        overlay.refreshLanguage()
        if (reopen) main.post { panels.openSettings() }
    }

    private fun onThemeChanged() {
        val reopen = overlay.hasPanel
        overlay.closePanels()
        overlay.rebuildToolbar()
        if (reopen) main.post { panels.openSettings() }
    }

    // ----------------------------------------------------------------------- state

    private fun refreshIdle() {
        val e = state.engine
        if (e == EngineState.IDLE || e == EngineState.READY || e == EngineState.STOPPED) {
            val has = repo.get(settings.defaultMacroId) != null
            if (e != EngineState.STOPPED) state.engine = if (has) EngineState.READY else EngineState.IDLE
        }
        state.changed()
        render()
    }

    private fun setEngine(e: EngineState, message: String = "") {
        state.engine = e
        state.message = message
        state.changed()
        render()
        updateKeyFilter()
    }

    fun render() {
        val tb = overlay.toolbar ?: return
        val e = state.engine
        val mode = settings.mode
        val ui = overlay.ui
        val l1: String
        val l2: String
        val color: Int
        when {
            state.countdown > 0 -> {
                l1 = state.countdown.toString()
                l2 = s(R.string.bar_get_ready)
                color = Pal.warn
            }
            e == EngineState.RECORDING -> {
                if (state.recordingPaused) {
                    l1 = s(R.string.bar_paused)
                    l2 = Fmt.clock(state.recordMs)
                    color = Pal.warn
                } else {
                    l1 = s(R.string.bar_rec)
                    l2 = Fmt.clock(state.recordMs)
                    color = 0xFFFF5A5A.toInt()
                }
            }
            e == EngineState.RUNNING -> {
                l1 = loopsText()
                l2 = UiFmt.speed(ui, state.speed)
                color = 0xFF4ADE80.toInt()
            }
            e == EngineState.PAUSED -> {
                l1 = s(R.string.bar_paused)
                l2 = loopsText()
                color = Pal.warn
            }
            else -> {
                l1 = s(if (mode == AppSettings.Mode.RECORD) R.string.bar_mode_rec else R.string.bar_mode_run)
                l2 = if (e == EngineState.STOPPED) s(R.string.bar_stopped) else ""
                color = ToolbarView.modeColor(mode)
            }
        }
        tb.render(Hud(e, state.recordingPaused, state.countdown, l1, l2, color))
    }

    private fun loopsText(): String =
        state.loops.toString() + "/" + (if (state.totalLoops > 0) state.totalLoops.toString() else "\u221E")

    /** One-line summary shown when the status block of the floating bar is tapped. */
    fun statusText(): String = when (state.engine) {
        EngineState.RECORDING -> s(
            if (state.recordingPaused) R.string.status_recording_paused else R.string.status_recording,
            Fmt.clock(state.recordMs), state.recordActions.toString()
        )
        EngineState.RUNNING -> s(R.string.status_running, state.macroName, loopsText(), UiFmt.speed(overlay.ui, state.speed))
        EngineState.PAUSED -> s(R.string.status_paused, state.macroName, loopsText())
        else -> {
            val m = repo.get(settings.defaultMacroId)
            if (m != null) s(R.string.status_ready, m.name) else s(R.string.status_no_macro)
        }
    }

    fun tick() {
        if (settings.haptics) Platform.vibrate(service, 14L)
    }

    private fun strongTick() {
        if (settings.haptics) Platform.vibrate(service, 40L)
    }

    fun toast(msg: String, long: Boolean = false) {
        Platform.toast(overlay.ui, msg, long)
    }

    // ----------------------------------------------------------------- toolbar actions

    /** Shows the floating bar. Returns false (with a message) when Android refused to draw it. */
    fun showToolbar(): Boolean {
        if (overlay.showToolbar()) {
            settings.overlayVisible = true
            render()
            return true
        }
        toast(s(R.string.toast_bar_failed), true)
        return false
    }

    fun hideToolbar() {
        if (isBusy) {
            toast(s(R.string.toast_stop_first))
            return
        }
        overlay.hideToolbar()
        settings.overlayVisible = false
    }

    /**
     * "Start Recording" in the app. Only brings up the floating bar in Record mode: nothing is recorded until
     * the user has opened their game and presses the blue play button on the bar.
     */
    fun armRecording() {
        if (isBusy) {
            toast(s(R.string.toast_stop_first))
            return
        }
        settings.mode = AppSettings.Mode.RECORD
        if (!overlay.isToolbarShown && !showToolbar()) return
        refreshIdle()
        render()
        toast(s(R.string.toast_bar_ready_record), true)
    }

    /**
     * The blue button. ● starts recording (Record mode) and ▶ runs the macro (Start mode). While recording it is ■:
     * press it to stop and save. While a macro runs it is ■ too: press it to stop.
     */
    fun onPlay() {
        tick()
        when {
            prompt != null -> {} // the name dialog is still open: answer that first
            isCountingDown -> {
                cancelCountdown()
                toast(s(R.string.toast_cancelled))
            }
            state.engine == EngineState.RECORDING ->
                if (state.recordingPaused) toggleRecordingPause() else finishRecording()
            runner.phase == MacroRunner.Phase.RUNNING -> stopRun(FinishReason.USER)
            runner.phase == MacroRunner.Phase.PAUSED -> resumeRun()
            settings.mode == AppSettings.Mode.RECORD -> startRecording()
            else -> startRun(null)
        }
    }

    /**
     * Holding the blue button: pause / resume while recording or running, stop while paused.
     * Returns false when there is nothing to do (the bar then just shows the button's name).
     */
    fun onPlayLong(): Boolean {
        if (prompt != null || isCountingDown) return false
        return when {
            state.engine == EngineState.RECORDING -> {
                strongTick()
                if (state.recordingPaused) finishRecording() else toggleRecordingPause()
                true
            }
            runner.phase == MacroRunner.Phase.RUNNING -> {
                strongTick()
                pauseRun()
                true
            }
            runner.phase == MacroRunner.Phase.PAUSED -> {
                strongTick()
                stopRun(FinishReason.USER)
                true
            }
            else -> false
        }
    }

    /** The X: closes the floating bar (the app brings it back). */
    fun onClose() {
        tick()
        overlay.closePanels()
        hideToolbar()
    }

    fun onSettings() {
        tick()
        panels.openSettings()
    }

    /** Stops whatever is happening right now (also used by the Volume Up + Down shortcut). */
    fun emergencyStop() {
        if (isCountingDown) cancelCountdown()
        if (runner.isActive) runner.stop(FinishReason.USER)
        if (recorder != null) finishRecording()
        strongTick()
    }

    // --------------------------------------------------------------------- countdown

    private fun beginCountdown(seconds: Int, done: () -> Unit) {
        cancelCountdown()
        if (seconds <= 0) {
            done()
            return
        }
        countdownDone = done
        var left = seconds
        state.countdown = left
        state.changed()
        render()
        val task = object : Runnable {
            override fun run() {
                left--
                if (left <= 0) {
                    countdownTask = null
                    state.countdown = 0
                    val d = countdownDone
                    countdownDone = null
                    state.changed()
                    render()
                    d?.invoke()
                } else {
                    state.countdown = left
                    state.changed()
                    render()
                    main.postDelayed(this, 1000L)
                }
            }
        }
        countdownTask = task
        main.postDelayed(task, 1000L)
    }

    private fun cancelCountdown() {
        countdownTask?.let { main.removeCallbacks(it) }
        countdownTask = null
        countdownDone = null
        if (state.countdown != 0) {
            state.countdown = 0
            state.changed()
            render()
        }
    }

    // --------------------------------------------------------------------- recording

    fun startRecording() {
        if (isBusy) {
            toast(s(R.string.toast_stop_first))
            return
        }
        if (!overlay.isToolbarShown && !showToolbar()) return
        settings.mode = AppSettings.Mode.RECORD
        if (settings.recordCountdown > 0) toast(s(R.string.toast_rec_starts, UiFmt.seconds(overlay.ui, settings.recordCountdown)))
        beginCountdown(settings.recordCountdown) { beginRecording() }
    }

    private fun beginRecording() {
        displayCache = Platform.display(service)
        recDevice = displayCache
        recPackage = foregroundPackage ?: ""
        val slop = ViewConfiguration.get(service).scaledTouchSlop * 2f
        val rec = TraceRecorder(slop) { a -> onRecorded(a) }
        rec.begin(SystemClock.uptimeMillis())
        recorder = rec
        recStart = SystemClock.uptimeMillis()
        state.recordingPaused = false
        state.recordMs = 0L
        state.recordActions = 0
        if (!overlay.showCapture(captureSink, settings.recordMarkers)) {
            recorder = null
            toast(s(R.string.toast_rec_failed), true)
            refreshIdle()
            return
        }
        setEngine(EngineState.RECORDING)
        strongTick()
        main.removeCallbacks(recTicker)
        main.postDelayed(recTicker, 500L)
    }

    private val captureSink = object : CaptureView.Sink {
        override fun down(id: Int, x: Float, y: Float, t: Long) {
            recorder?.down(id, x, y, t)
        }

        override fun move(id: Int, x: Float, y: Float, t: Long) {
            recorder?.move(id, x, y, t)
        }

        override fun up(id: Int, x: Float, y: Float, t: Long) {
            recorder?.up(id, x, y, t)
        }

        override fun cancel(t: Long) {
            recorder?.cancelAll(t)
        }
    }

    /** A gesture was finished while recording. In Live mode it is replayed to the app right now. */
    private fun onRecorded(a: com.mareco.app.data.MacroAction) {
        state.recordActions = recorder?.actions?.size ?: 0
        state.changed()
        if (!settings.recordLive || !a.type.isTouch) return
        val plan = GesturePlanner.plan(
            a, Scaler.IDENTITY, displayCache, TimingPolicy(ExecutionSettings()), true
        ) ?: return
        overlay.setCaptureTouchable(false)
        val restore = Runnable { overlay.setCaptureTouchable(!state.recordingPaused) }
        main.postDelayed(restore, plan.totalMs + 1500L)
        dispatchPlan(plan) { _ ->
            main.removeCallbacks(restore)
            if (recorder != null) overlay.setCaptureTouchable(!state.recordingPaused)
        }
    }

    private fun toggleRecordingPause() {
        val rec = recorder ?: return
        if (!state.recordingPaused) {
            state.recordingPaused = true
            recPausedAt = SystemClock.uptimeMillis()
            overlay.setCaptureTouchable(false)
            overlay.setCaptureFrame(false)
            toast(s(R.string.toast_rec_paused))
        } else {
            state.recordingPaused = false
            rec.resume(SystemClock.uptimeMillis())
            recStart += SystemClock.uptimeMillis() - recPausedAt
            overlay.setCaptureTouchable(true)
            overlay.setCaptureFrame(true)
            toast(s(R.string.toast_rec_resumed))
        }
        state.changed()
        render()
    }

    fun finishRecording() {
        val rec = recorder ?: return
        val actions = rec.finish()
        recorder = null
        main.removeCallbacks(recTicker)
        state.recordingPaused = false
        overlay.hideCapture()
        strongTick()
        if (actions.isEmpty()) {
            toast(s(R.string.toast_nothing_recorded))
            refreshIdle()
            setEngine(if (repo.get(settings.defaultMacroId) != null) EngineState.READY else EngineState.IDLE)
            return
        }
        val now = System.currentTimeMillis()
        val defName = repo.nextDefaultName(s(R.string.default_macro_name))
        val macro = Macro(
            id = repo.newId(),
            name = defName,
            createdAt = now,
            modifiedAt = now,
            device = recDevice,
            appPackage = recPackage,
            appLabel = Platform.appLabel(service, recPackage),
            actions = actions,
            execution = settings.defaultExecution
        )
        setEngine(EngineState.READY)
        askText(s(R.string.prompt_title), defName, s(R.string.save), s(R.string.prompt_discard), dismissSaves = true) { text ->
            if (text == null) {
                toast(s(R.string.toast_discarded))
                refreshIdle()
            } else {
                val saved = macro.copy(name = repo.uniqueName(text.ifBlank { defName }, s(R.string.default_macro_name)))
                repo.save(saved)
                settings.defaultMacroId = saved.id
                settings.mode = AppSettings.Mode.START
                toast(s(R.string.toast_saved_macro, saved.name), true)
                refreshIdle()
            }
        }
    }

    // ---------------------------------------------------------------- text prompts

    /**
     * Asks for a line of text. A small dialog-style screen is used (a real window gives a reliable
     * keyboard); if Android refuses to open it from the background, the same dialog is drawn
     * inside the overlay instead.
     */
    fun askText(
        title: String, initial: String, positive: String, negative: String?,
        dismissSaves: Boolean, onResult: (String?) -> Unit
    ) {
        val id = ++promptSeq
        prompt = Prompt(id, dismissSaves, initial, onResult)
        val intent = Intent(service, PromptActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(PromptActivity.EXTRA_ID, id)
            .putExtra(PromptActivity.EXTRA_TITLE, title)
            .putExtra(PromptActivity.EXTRA_INITIAL, initial)
            .putExtra(PromptActivity.EXTRA_POSITIVE, positive)
            .putExtra(PromptActivity.EXTRA_NEGATIVE, negative)
        val fallback = Runnable { overlayPrompt(id, title, initial, positive, negative) }
        promptFallback = fallback
        try {
            service.startActivity(intent)
            main.postDelayed(fallback, 1_800L)
        } catch (e: Exception) {
            fallback.run()
        }
    }

    private fun overlayPrompt(id: Long, title: String, initial: String, positive: String, negative: String?) {
        val p = prompt ?: return
        if (p.id != id) return
        promptFallback = null
        Dialogs.name(overlay.dialogHost, title, initial, positive, negative) { text ->
            promptResult(id, text)
        }
    }

    /** Called by PromptActivity when it is on screen. Returns false when the request is stale. */
    fun promptShown(id: Long): Boolean {
        val p = prompt ?: return false
        if (p.id != id) return false
        promptFallback?.let { main.removeCallbacks(it) }
        promptFallback = null
        return true
    }

    fun promptResult(id: Long, text: String?) {
        val p = prompt ?: return
        if (p.id != id) return
        prompt = null
        p.onResult(text)
    }

    /** The prompt screen was closed with back / outside tap. */
    fun promptDismissed(id: Long) {
        val p = prompt ?: return
        if (p.id != id) return
        promptResult(id, if (p.dismissSaves) p.initial else null)
    }

    // --------------------------------------------------------------------------- run

    /** Starts [macroId] (or the default macro). [fromApp] = started from the Mareco app screens. */
    fun startRun(macroId: String?, fromApp: Boolean = false) {
        if (isBusy) {
            toast(s(R.string.toast_stop_first))
            return
        }
        val id = if (macroId.isNullOrEmpty()) settings.defaultMacroId else macroId
        val m = repo.get(id)
        if (m == null) {
            toast(s(R.string.toast_no_macro_run))
            return
        }
        if (m.actions.none { it.type != ActionType.WAIT }) {
            toast(s(R.string.toast_no_steps))
            return
        }
        if (!overlay.isToolbarShown && !showToolbar()) return
        settings.defaultMacroId = m.id
        val warnings = Texts.scaleWarnings(overlay.ui, m.device, Platform.display(service))
        val proceed = {
            confirmInfiniteThenGo(m, fromApp)
        }
        if (warnings.isEmpty()) {
            proceed()
        } else {
            Dialogs.confirm(
                overlay.dialogHost, s(R.string.dlg_diff_screen), warnings.joinToString("\n\n"),
                s(R.string.dlg_run_anyway), s(R.string.cancel)
            ) { ok -> if (ok) proceed() }
        }
    }

    private fun confirmInfiniteThenGo(m: Macro, fromApp: Boolean) {
        val infinite = m.execution.stopMode == StopMode.INFINITE
        val go = {
            val secs = maxOf(settings.runCountdown, if (fromApp) 3 else 0)
            toast(s(R.string.toast_starting, m.name, UiFmt.seconds(overlay.ui, secs)))
            beginCountdown(secs) { startNow(m) }
        }
        if (infinite && settings.confirmInfinite) {
            Dialogs.confirm(
                overlay.dialogHost, s(R.string.dlg_forever_title), s(R.string.dlg_forever_msg, m.name),
                s(R.string.start), s(R.string.cancel)
            ) { ok -> if (ok) go() }
        } else {
            go()
        }
    }

    private fun startNow(m: Macro) {
        val fresh = repo.get(m.id) ?: m
        runningMacro = fresh
        displayCache = Platform.display(service)
        state.macroName = fresh.name
        state.loops = 0
        state.totalLoops = if (fresh.execution.stopMode == StopMode.COUNT) fresh.execution.count else 0
        state.speed = fresh.execution.speed
        if (settings.keepScreenOn) overlay.keepScreenOn(true)
        if (settings.runMarkers) overlay.showMarkers()
        val safety = Safety(settings.maxRuntimeMin * 60_000L, settings.maxExecutions)
        val started = runner.start(fresh, fresh.execution, safety)
        if (started) {
            setEngine(EngineState.RUNNING)
            strongTick()
        }
    }

    fun pauseRun() {
        runner.pause(MacroRunner.PauseReason.USER)
    }

    fun resumeRun() {
        runner.resume()
    }

    fun stopRun(reason: FinishReason) {
        runner.stop(reason)
    }

    // ------------------------------------------------------------- RunnerHost methods

    override fun dispatch(plan: GesturePlan, result: (DispatchResult) -> Unit) {
        overlay.markPlan(plan)
        // If the gesture would land on our own toolbar, let it pass through for a moment.
        val rect = overlay.toolbarRect()
        var passThrough = false
        if (rect != null) {
            for (s in plan.strokes) {
                var i = 0
                while (i + 1 < s.xy.size) {
                    if (rect.contains(s.xy[i].toInt(), s.xy[i + 1].toInt())) passThrough = true
                    i += 2
                }
            }
        }
        if (passThrough) overlay.setToolbarTouchable(false)
        dispatchPlan(plan) { r ->
            if (passThrough) overlay.setToolbarTouchable(true)
            result(r)
        }
    }

    private fun dispatchPlan(plan: GesturePlan, done: (DispatchResult) -> Unit) {
        val gesture = buildGesture(plan)
        if (gesture == null) {
            done(DispatchResult.FAILED)
            return
        }
        val ok = try {
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    done(DispatchResult.COMPLETED)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    done(DispatchResult.CANCELLED)
                }
            }, main)
        } catch (e: Exception) {
            false
        }
        if (!ok) done(DispatchResult.FAILED)
    }

    private fun buildGesture(plan: GesturePlan): GestureDescription? {
        return try {
            val b = GestureDescription.Builder()
            for (s in plan.strokes) {
                if (s.xy.size < 2) continue
                val path = Path()
                path.moveTo(s.xy[0], s.xy[1])
                var i = 2
                while (i + 1 < s.xy.size) {
                    path.lineTo(s.xy[i], s.xy[i + 1])
                    i += 2
                }
                b.addStroke(GestureDescription.StrokeDescription(path, s.startOffsetMs, maxOf(1L, s.durationMs)))
            }
            b.build()
        } catch (e: Exception) {
            null
        }
    }

    override fun performKey(key: SystemKey): Boolean {
        val action = when (key) {
            SystemKey.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            SystemKey.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            SystemKey.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            SystemKey.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            SystemKey.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        }
        return try {
            service.performGlobalAction(action)
        } catch (e: Exception) {
            false
        }
    }

    override fun display(): DeviceInfo = displayCache

    override fun targetAppActive(packageName: String): Boolean {
        val fg = foregroundPackage ?: return true
        return fg == packageName
    }

    override fun onLoop(loops: Int) {
        state.loops = loops
        state.runElapsedMs = runner.runtimeMs
        state.changed()
        render()
    }

    override fun onPhase(phase: MacroRunner.Phase, reason: MacroRunner.PauseReason?) {
        when (phase) {
            MacroRunner.Phase.RUNNING -> setEngine(EngineState.RUNNING)
            MacroRunner.Phase.PAUSED -> {
                setEngine(EngineState.PAUSED)
                if (reason == MacroRunner.PauseReason.INTERRUPTED) {
                    toast(s(R.string.toast_interrupted), true)
                }
            }
            else -> {}
        }
    }

    override fun onFinished(summary: RunSummary) {
        val m = runningMacro
        runningMacro = null
        if (m != null && summary.loops > 0) repo.recordRun(m.id, summary.loops, summary.runtimeMs)
        overlay.keepScreenOn(false)
        overlay.hideMarkers()
        val text = when (summary.reason) {
            FinishReason.COMPLETED -> s(R.string.fin_done, summary.loops.toString())
            FinishReason.USER -> s(R.string.fin_user)
            FinishReason.TIME_LIMIT -> s(R.string.fin_time)
            FinishReason.SAFETY_TIME -> s(R.string.fin_safety_time)
            FinishReason.SAFETY_COUNT -> s(R.string.fin_safety_count)
            FinishReason.ERROR -> s(R.string.fin_error)
            FinishReason.APP_LOST -> s(R.string.fin_app_lost)
            FinishReason.SCREEN_OFF -> s(R.string.fin_screen_off)
            FinishReason.SERVICE_LOST -> s(R.string.fin_service_lost)
            FinishReason.BATTERY -> s(R.string.fin_battery, settings.stopBatteryPercent.toString())
            FinishReason.EMPTY -> s(R.string.fin_empty)
        }
        setEngine(EngineState.STOPPED, text)
        toast(text)
        strongTick()
    }

    // ------------------------------------------------------------- keys and events

    fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == service.packageName || pkg == "com.android.systemui" || pkg == "android" || pkg.isEmpty()) return
        val ime = try {
            Settings.Secure.getString(service.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
        } catch (ex: Exception) {
            null
        }
        if (pkg == ime) return
        foregroundPackage = pkg
    }

    /** Never consumes keys: Back / Home / volume keep working. It only watches. */
    fun onKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> volUp = down
            KeyEvent.KEYCODE_VOLUME_DOWN -> volDown = down
        }
        if (down && volUp && volDown && settings.volumeStop && (isRunActive || isCountingDown)) {
            volUp = false
            volDown = false
            emergencyStop()
            return false
        }
        if (down && e.repeatCount == 0 && recorder != null && !state.recordingPaused && settings.recordKeys) {
            val key = when (e.keyCode) {
                KeyEvent.KEYCODE_BACK -> SystemKey.BACK
                KeyEvent.KEYCODE_HOME -> SystemKey.HOME
                KeyEvent.KEYCODE_APP_SWITCH -> SystemKey.RECENTS
                else -> null
            }
            if (key != null) recorder?.key(key, e.eventTime)
        }
        return false
    }

    /** Key events are only requested while recording or running (least privilege). */
    private fun updateKeyFilter() {
        val want = (recorder != null && settings.recordKeys) || ((isRunActive || isCountingDown) && settings.volumeStop)
        try {
            val info = service.serviceInfo ?: return
            val has = (info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS) != 0
            if (want == has) return
            info.flags = if (want) info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            else info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
            service.serviceInfo = info
        } catch (e: Exception) {
            // not fatal: only the optional key shortcuts are affected
        }
    }

    private fun registerScreenReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF && settings.stopOnScreenOff) {
                    if (runner.isActive) runner.stop(FinishReason.SCREEN_OFF)
                    if (isCountingDown) cancelCountdown()
                }
            }
        }
        screenReceiver = r
        val f = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        else service.registerReceiver(r, f)
    }

    private fun registerBatteryReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) checkBattery(intent)
            }
        }
        batteryReceiver = r
        val f = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        else service.registerReceiver(r, f)
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try {
                service.unregisterReceiver(it)
            } catch (e: Exception) {
                // already unregistered
            }
        }
        batteryReceiver = null
    }

    /** "Stop on low battery": never stops while the phone is charging. */
    private fun checkBattery(i: Intent) {
        val limit = settings.stopBatteryPercent
        if (limit <= 0 || !runner.isActive) return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (level * 100 / scale < limit && !charging) runner.stop(FinishReason.BATTERY)
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                service.unregisterReceiver(it)
            } catch (e: Exception) {
                // already unregistered
            }
        }
        screenReceiver = null
    }

    // ----------------------------------------------------------- auto execute on boot

    /** "Auto-execute upon starting the device": runs once per phone boot, with a cancelable countdown. */
    private fun checkAutoExecute() {
        val boot = try {
            Settings.Global.getInt(service.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (e: Exception) {
            -1
        }
        if (boot == -1 || boot == settings.lastBootCount) return
        settings.lastBootCount = boot
        val candidates = repo.list().filter { it.execution.autoExecute }
        if (candidates.isEmpty()) return
        val pick = candidates.firstOrNull { it.id == settings.defaultMacroId } ?: candidates.first()
        val task = Runnable {
            if (!isBusy && !disposed) {
                showToolbar()
                toast(s(R.string.toast_auto, pick.name), true)
                beginCountdown(maxOf(settings.runCountdown, 10)) { startNow(pick) }
            }
        }
        autoRunTask = task
        main.postDelayed(task, 20_000L)
    }

    // ------------------------------------------------------------------- panels help

    fun editExecutionSettings(macroId: String, host: com.mareco.app.ui.DialogHost, after: (() -> Unit)? = null) {
        val m = repo.get(macroId) ?: return
        Dialogs.executionSettings(host, m.name, m.execution, m.device, m.appLabel) { saved ->
            repo.update(m.id) { it.copy(execution = saved) }
            after?.invoke()
        }
    }

    fun openMacroSettingsFromApp() {
        panels.openSettings()
    }
}
