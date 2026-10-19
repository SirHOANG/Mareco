package com.mareco.app

import com.mareco.app.core.DispatchResult
import com.mareco.app.core.GesturePlan
import com.mareco.app.core.MacroRunner
import com.mareco.app.core.RunSummary
import com.mareco.app.core.RunnerHost
import com.mareco.app.core.Scheduler
import com.mareco.app.data.DeviceInfo
import com.mareco.app.data.Macro
import com.mareco.app.data.MacroAction
import com.mareco.app.data.SystemKey
import java.util.PriorityQueue

/** Virtual clock: tasks run in time order without real waiting. */
class VirtualScheduler : Scheduler {
    private class Task(val at: Long, val seq: Long, val run: Runnable) {
        var cancelled = false
    }

    var time = 0L
        private set
    private var seq = 0L
    private val queue = PriorityQueue<Task>(compareBy<Task>({ it.at }, { it.seq }))

    override fun post(delayMs: Long, task: Runnable): Any {
        val t = Task(time + delayMs, seq++, task)
        queue.add(t)
        return t
    }

    override fun cancel(token: Any) {
        (token as Task).cancelled = true
    }

    override fun now(): Long = time

    fun runUntil(limit: Long) {
        while (true) {
            val t = queue.peek() ?: break
            if (t.at > limit) break
            queue.poll()
            if (t.cancelled) continue
            time = t.at
            t.run.run()
        }
        if (time < limit) time = limit
    }

    fun runAll(maxTime: Long = 10_000_000L) = runUntil(maxTime)
}

class FakeHost(private val sch: VirtualScheduler, var display: DeviceInfo = DeviceInfo(1080, 2400)) : RunnerHost {
    val plans = ArrayList<GesturePlan>()
    val dispatchTimes = ArrayList<Long>()
    val keys = ArrayList<SystemKey>()
    val phases = ArrayList<Pair<MacroRunner.Phase, MacroRunner.PauseReason?>>()
    var scripted = ArrayList<DispatchResult>()
    private var scriptIndex = 0
    var appActive = true
    var finished: RunSummary? = null
    var loopsSeen = 0

    override fun dispatch(plan: GesturePlan, result: (DispatchResult) -> Unit) {
        plans.add(plan)
        dispatchTimes.add(sch.now())
        val r = if (scriptIndex < scripted.size) scripted[scriptIndex++] else DispatchResult.COMPLETED
        sch.post(plan.totalMs, Runnable { result(r) })
    }

    override fun performKey(key: SystemKey): Boolean {
        keys.add(key)
        return true
    }

    override fun display(): DeviceInfo = display
    override fun targetAppActive(packageName: String): Boolean = appActive
    override fun onLoop(loops: Int) {
        loopsSeen = loops
    }

    override fun onPhase(phase: MacroRunner.Phase, reason: MacroRunner.PauseReason?) {
        phases.add(Pair(phase, reason))
    }

    override fun onFinished(summary: RunSummary) {
        finished = summary
    }
}

fun macroOf(vararg actions: MacroAction, device: DeviceInfo = DeviceInfo(1080, 2400), pkg: String = ""): Macro =
    Macro(id = "m1", name = "Test", device = device, appPackage = pkg, actions = actions.toList())
