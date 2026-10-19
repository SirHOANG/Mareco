package com.mareco.app.core

import com.mareco.app.data.ActionType
import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.ExecutionSettings
import com.mareco.app.data.Macro
import com.mareco.app.data.MacroAction
import com.mareco.app.data.StopMode
import com.mareco.app.data.SystemKey
import kotlin.math.max

/** Abstraction over "run this later" so the engine can be tested with virtual time. */
interface Scheduler {
    fun post(delayMs: Long, task: Runnable): Any
    fun cancel(token: Any)
    fun now(): Long
}

enum class DispatchResult { COMPLETED, CANCELLED, FAILED }

enum class FinishReason {
    COMPLETED, USER, TIME_LIMIT, SAFETY_TIME, SAFETY_COUNT, ERROR, APP_LOST, SCREEN_OFF, SERVICE_LOST, BATTERY, EMPTY
}

data class RunSummary(val reason: FinishReason, val loops: Int, val runtimeMs: Long)

/** Global safety limits from Settings (0 = off). */
data class Safety(val maxRuntimeMs: Long = 0L, val maxLoops: Int = 0)

/** Everything the engine needs from the outside world. Implemented by the accessibility service. */
interface RunnerHost {
    fun dispatch(plan: GesturePlan, result: (DispatchResult) -> Unit)
    fun performKey(key: SystemKey): Boolean
    fun display(): DeviceInfo
    fun targetAppActive(packageName: String): Boolean
    fun onLoop(loops: Int)
    fun onPhase(phase: MacroRunner.Phase, reason: MacroRunner.PauseReason?)
    fun onFinished(summary: RunSummary)
}

/**
 * The replay engine: Run -> Pause -> Resume -> Stop, with loops, time limits and safety limits.
 * Single-threaded: call everything from the same (main) thread.
 */
class MacroRunner(
    private val sch: Scheduler,
    private val host: RunnerHost,
    private val rnd: java.util.Random = java.util.Random()
) {
    enum class Phase { IDLE, RUNNING, PAUSED, FINISHED }
    enum class PauseReason { USER, INTERRUPTED }

    var phase: Phase = Phase.IDLE
        private set
    var loops: Int = 0
        private set

    val isActive: Boolean get() = phase == Phase.RUNNING || phase == Phase.PAUSED
    val speed: Float get() = timing.speed
    val runtimeMs: Long get() = activeMs()

    private var macro: Macro? = null
    private var settings = ExecutionSettings()
    private var safety = Safety()
    private var actions: List<MacroAction> = emptyList()
    private var scaler: Scaler = Scaler.IDENTITY
    private var timing = TimingPolicy(settings)
    private var index = 0
    private var gen = 0
    private var inFlight = false
    private var cancelStreak = 0
    private var failStreak = 0
    private var pending: Any? = null
    private var deadline: Any? = null
    private var accumulated = 0L
    private var activeSince = 0L
    private var loopStart = 0L
    private var appWaitStart = -1L

    fun start(m: Macro, s: ExecutionSettings, safe: Safety): Boolean {
        if (isActive) return false
        gen++
        if (m.actions.none { it.type != ActionType.WAIT }) {
            phase = Phase.FINISHED
            host.onFinished(RunSummary(FinishReason.EMPTY, 0, 0L))
            return false
        }
        macro = m
        settings = s
        safety = safe
        actions = m.actions
        timing = TimingPolicy(s, rnd)
        scaler = Scaler(m.device, host.display(), s.scaleMode)
        index = 0
        loops = 0
        inFlight = false
        cancelStreak = 0
        failStreak = 0
        accumulated = 0L
        activeSince = sch.now()
        loopStart = activeSince
        appWaitStart = -1L
        phase = Phase.RUNNING
        host.onPhase(phase, null)
        armDeadline()
        step()
        return true
    }

    fun pause(reason: PauseReason = PauseReason.USER) {
        if (phase != Phase.RUNNING) return
        accumulated += sch.now() - activeSince
        phase = Phase.PAUSED
        cancelTimers()
        host.onPhase(phase, reason)
    }

    fun resume() {
        if (phase != Phase.PAUSED) return
        activeSince = sch.now()
        phase = Phase.RUNNING
        cancelStreak = 0
        host.onPhase(phase, null)
        armDeadline()
        if (!inFlight) step()
    }

    fun stop(reason: FinishReason = FinishReason.USER) {
        if (isActive) finish(reason)
    }

    // ---------------------------------------------------------------- engine

    private fun activeMs(): Long =
        accumulated + (if (phase == Phase.RUNNING) sch.now() - activeSince else 0L)

    private fun cancelTimers() {
        pending?.let { sch.cancel(it) }
        deadline?.let { sch.cancel(it) }
        pending = null
        deadline = null
    }

    private fun armDeadline() {
        deadline?.let { sch.cancel(it) }
        deadline = null
        var limit = Long.MAX_VALUE
        var reason = FinishReason.TIME_LIMIT
        if (settings.stopMode == StopMode.TIME) limit = settings.timeMs
        if (safety.maxRuntimeMs > 0L && safety.maxRuntimeMs < limit) {
            limit = safety.maxRuntimeMs
            reason = FinishReason.SAFETY_TIME
        }
        if (limit == Long.MAX_VALUE) return
        val g = gen
        deadline = sch.post(max(0L, limit - activeMs()), Runnable {
            if (g == gen && phase == Phase.RUNNING) finish(reason)
        })
    }

    private fun after(ms: Long, task: () -> Unit) {
        val g = gen
        pending = sch.post(max(0L, ms), Runnable {
            if (g == gen && phase == Phase.RUNNING) {
                pending = null
                task()
            }
        })
    }

    private fun step() {
        if (phase != Phase.RUNNING) return
        if (index >= actions.size) {
            endOfLoop()
            return
        }
        val a = actions[index]
        if (a.type == ActionType.WAIT) {
            after(timing.waitMs(a)) {
                index++
                step()
            }
        } else {
            after(timing.gapMs(a, index == 0)) { execute(a) }
        }
    }

    private fun execute(a: MacroAction) {
        val pkg = macro?.appPackage ?: ""
        if (settings.lockToApp && pkg.isNotEmpty() && !host.targetAppActive(pkg)) {
            if (appWaitStart < 0L) appWaitStart = sch.now()
            if (sch.now() - appWaitStart > APP_WAIT_TIMEOUT_MS) {
                finish(FinishReason.APP_LOST)
            } else {
                after(APP_POLL_MS) { execute(a) }
            }
            return
        }
        appWaitStart = -1L
        if (a.type == ActionType.KEY) {
            a.key?.let { host.performKey(it) }
            index++
            after(0L) { step() }
            return
        }
        val plan = GesturePlanner.plan(
            a, scaler, host.display(), timing, settings.simulate,
            if (settings.humanize) settings.humanizePx else 0, rnd
        )
        if (plan == null) {
            index++
            after(0L) { step() }
            return
        }
        inFlight = true
        val g = gen
        host.dispatch(plan) { res -> onDispatch(g, res) }
    }

    private fun onDispatch(g: Int, res: DispatchResult) {
        if (g != gen) return
        inFlight = false
        when (res) {
            DispatchResult.COMPLETED -> {
                cancelStreak = 0
                failStreak = 0
                index++
            }
            DispatchResult.CANCELLED -> {
                failStreak = 0
                cancelStreak++
                if (cancelStreak >= MAX_CANCELS) {
                    cancelStreak = 0
                    pause(PauseReason.INTERRUPTED)
                    return
                }
            }
            DispatchResult.FAILED -> {
                cancelStreak = 0
                failStreak++
                if (failStreak >= MAX_FAILS) {
                    finish(FinishReason.ERROR)
                    return
                }
                index++
            }
        }
        if (phase == Phase.RUNNING) {
            after(if (res == DispatchResult.CANCELLED) RETRY_MS else 0L) { step() }
        }
    }

    private fun endOfLoop() {
        loops++
        host.onLoop(loops)
        if (settings.stopMode == StopMode.COUNT && loops >= settings.count) {
            finish(FinishReason.COMPLETED)
            return
        }
        if (safety.maxLoops > 0 && loops >= safety.maxLoops) {
            finish(FinishReason.SAFETY_COUNT)
            return
        }
        index = 0
        val elapsed = sch.now() - loopStart
        var wait = settings.loopDelayMs
        if (elapsed + wait < Limits.MIN_LOOP_MS) wait = Limits.MIN_LOOP_MS - elapsed
        after(wait) {
            loopStart = sch.now()
            step()
        }
    }

    private fun finish(reason: FinishReason) {
        val rt = activeMs()
        cancelTimers()
        gen++
        inFlight = false
        phase = Phase.FINISHED
        host.onPhase(phase, null)
        host.onFinished(RunSummary(reason, loops, rt))
    }

    companion object {
        private const val MAX_CANCELS = 3
        private const val MAX_FAILS = 3
        private const val RETRY_MS = 150L
        private const val APP_POLL_MS = 500L
        private const val APP_WAIT_TIMEOUT_MS = 30_000L
    }
}
