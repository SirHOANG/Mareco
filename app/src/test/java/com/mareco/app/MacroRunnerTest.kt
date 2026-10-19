package com.mareco.app

import com.mareco.app.core.DispatchResult
import com.mareco.app.core.FinishReason
import com.mareco.app.core.MacroRunner
import com.mareco.app.core.Safety
import com.mareco.app.data.ExecutionSettings
import com.mareco.app.data.MacroAction
import com.mareco.app.data.StopMode
import com.mareco.app.data.DelayMode
import com.mareco.app.data.SystemKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class MacroRunnerTest {
    private val sch = VirtualScheduler()
    private val host = FakeHost(sch)
    private val runner = MacroRunner(sch, host, Random(1))

    private val twoTaps = macroOf(
        MacroAction.tap(100f, 100f, 60, 0),
        MacroAction.tap(200f, 200f, 60, 500)
    )

    private fun run(s: ExecutionSettings, safety: Safety = Safety(), macro: com.mareco.app.data.Macro = twoTaps) =
        runner.start(macro, s, safety)

    @Test
    fun countModeRunsExactLoopsWithOriginalTiming() {
        assertTrue(run(ExecutionSettings(stopMode = StopMode.COUNT, count = 3)))
        sch.runAll()
        val f = host.finished!!
        assertEquals(FinishReason.COMPLETED, f.reason)
        assertEquals(3, f.loops)
        assertEquals(1860L, f.runtimeMs)
        assertEquals(listOf(0L, 560L, 620L, 1180L, 1240L, 1800L), host.dispatchTimes)
        assertEquals(MacroRunner.Phase.FINISHED, runner.phase)
    }

    @Test
    fun speedTwoHalvesGapsAndDurations() {
        run(ExecutionSettings(stopMode = StopMode.COUNT, count = 1, speed = 2f))
        sch.runAll()
        assertEquals(listOf(0L, 280L), host.dispatchTimes)
        assertEquals(30L, host.plans[0].strokes[0].durationMs)
        assertEquals(310L, host.finished!!.runtimeMs)
    }

    @Test
    fun infiniteRunsUntilStopped() {
        run(ExecutionSettings(stopMode = StopMode.INFINITE))
        sch.runUntil(5_000)
        assertTrue(runner.isActive)
        runner.stop()
        assertEquals(FinishReason.USER, host.finished!!.reason)
        assertTrue(host.finished!!.loops >= 7)
        val seen = host.plans.size
        sch.runUntil(20_000)
        assertEquals("nothing may run after stop", seen, host.plans.size)
    }

    @Test
    fun timeModeStopsAtTheDeadline() {
        run(ExecutionSettings(stopMode = StopMode.TIME, timeMs = 1_000))
        sch.runAll()
        val f = host.finished!!
        assertEquals(FinishReason.TIME_LIMIT, f.reason)
        assertEquals(1_000L, f.runtimeMs)
        assertEquals(1, f.loops)
    }

    @Test
    fun pauseFreezesEverythingAndResumeContinues() {
        run(ExecutionSettings(stopMode = StopMode.INFINITE))
        sch.runUntil(100)
        runner.pause()
        assertEquals(MacroRunner.Phase.PAUSED, runner.phase)
        sch.runUntil(10_000)
        assertEquals(1, host.plans.size)
        runner.resume()
        sch.runUntil(11_000)
        assertTrue(host.plans.size > 1)
        assertEquals(10_500L, host.dispatchTimes[1])
        // paused time must not count as runtime
        assertTrue(runner.runtimeMs in 1_000L..1_200L)
    }

    @Test
    fun loopIntervalIsAddedBetweenLoops() {
        run(ExecutionSettings(stopMode = StopMode.COUNT, count = 2, loopDelayMs = 1_000))
        sch.runAll()
        assertEquals(listOf(0L, 560L, 1620L, 2180L), host.dispatchTimes)
    }

    @Test
    fun customDelayReplacesRecordedGapsButNotTheFirstStep() {
        val m = macroOf(MacroAction.tap(1f, 1f, 60, 300), MacroAction.tap(2f, 2f, 60, 500))
        run(ExecutionSettings(stopMode = StopMode.COUNT, count = 1, delayMode = DelayMode.CUSTOM, customDelayMs = 100), macro = m)
        sch.runAll()
        assertEquals(listOf(300L, 460L), host.dispatchTimes)
    }

    @Test
    fun randomDelayStaysInsideTheRange() {
        run(ExecutionSettings(stopMode = StopMode.COUNT, count = 30, randomDelay = true, randomRangeMs = 200))
        sch.runAll()
        var different = 0
        for (i in 0 until host.dispatchTimes.size step 2) {
            val gap = host.dispatchTimes[i + 1] - (host.dispatchTimes[i] + 60)
            assertTrue("gap $gap", gap in 300L..700L)
            if (gap != 500L) different++
        }
        assertTrue(different > 5)
    }

    @Test
    fun globalSafetyLimitsApplyToInfiniteMacros() {
        run(ExecutionSettings(stopMode = StopMode.INFINITE), Safety(maxLoops = 2))
        sch.runAll()
        assertEquals(FinishReason.SAFETY_COUNT, host.finished!!.reason)
        assertEquals(2, host.finished!!.loops)

        val sch2 = VirtualScheduler()
        val host2 = FakeHost(sch2)
        val r2 = MacroRunner(sch2, host2, Random(1))
        r2.start(twoTaps, ExecutionSettings(stopMode = StopMode.INFINITE), Safety(maxRuntimeMs = 1_000))
        sch2.runAll()
        assertEquals(FinishReason.SAFETY_TIME, host2.finished!!.reason)
        assertEquals(1_000L, host2.finished!!.runtimeMs)
    }

    @Test
    fun repeatedCancellationPausesInsteadOfFighting() {
        host.scripted = arrayListOf(DispatchResult.CANCELLED, DispatchResult.CANCELLED, DispatchResult.CANCELLED)
        run(ExecutionSettings(stopMode = StopMode.COUNT, count = 1))
        sch.runUntil(5_000)
        assertEquals(MacroRunner.Phase.PAUSED, runner.phase)
        assertTrue(host.phases.contains(Pair(MacroRunner.Phase.PAUSED, MacroRunner.PauseReason.INTERRUPTED)))
        runner.resume()
        sch.runAll()
        assertEquals(FinishReason.COMPLETED, host.finished!!.reason)
    }

    @Test
    fun repeatedFailuresEndTheRunWithAnError() {
        host.scripted = arrayListOf(DispatchResult.FAILED, DispatchResult.FAILED, DispatchResult.FAILED)
        run(ExecutionSettings(stopMode = StopMode.INFINITE))
        sch.runAll()
        assertEquals(FinishReason.ERROR, host.finished!!.reason)
    }

    @Test
    fun lockToAppWaitsForTheAppAndGivesUp() {
        val m = macroOf(MacroAction.tap(1f, 1f, 60, 0), pkg = "com.game")
        host.appActive = false
        sch.post(2_000, Runnable { host.appActive = true })
        run(ExecutionSettings(stopMode = StopMode.COUNT, count = 1, lockToApp = true), macro = m)
        sch.runAll()
        assertEquals(listOf(2_000L), host.dispatchTimes)
        assertEquals(FinishReason.COMPLETED, host.finished!!.reason)

        val sch2 = VirtualScheduler()
        val host2 = FakeHost(sch2).also { it.appActive = false }
        val r2 = MacroRunner(sch2, host2, Random(1))
        r2.start(m, ExecutionSettings(stopMode = StopMode.INFINITE, lockToApp = true), Safety())
        sch2.runAll()
        assertEquals(FinishReason.APP_LOST, host2.finished!!.reason)
        assertTrue(host2.plans.isEmpty())
    }

    @Test
    fun keyStepsUseTheSystemAction() {
        val m = macroOf(MacroAction.key(SystemKey.HOME, 100), MacroAction.tap(1f, 1f, 60, 100))
        run(ExecutionSettings(stopMode = StopMode.COUNT, count = 1), macro = m)
        sch.runAll()
        assertEquals(listOf(SystemKey.HOME), host.keys)
        assertEquals(1, host.plans.size)
    }

    @Test
    fun macroWithOnlyWaitsRefusesToStart() {
        assertFalse(run(ExecutionSettings(), macro = macroOf(MacroAction.wait(500))))
        assertEquals(FinishReason.EMPTY, host.finished!!.reason)
    }

    @Test
    fun veryShortLoopsAreNeverFasterThanFiftyMilliseconds() {
        val m = macroOf(MacroAction.tap(1f, 1f, 1, 0))
        run(ExecutionSettings(stopMode = StopMode.COUNT, count = 3), macro = m)
        sch.runAll()
        assertEquals(listOf(0L, 50L, 100L), host.dispatchTimes)
    }

    @Test
    fun waitStepsAreScaledBySpeedToo() {
        val m = macroOf(MacroAction.tap(1f, 1f, 60, 0), MacroAction.wait(1_000), MacroAction.tap(2f, 2f, 60, 0))
        run(ExecutionSettings(stopMode = StopMode.COUNT, count = 1, speed = 2f), macro = m)
        sch.runAll()
        assertEquals(listOf(0L, 530L), host.dispatchTimes)
        assertNotNull(host.finished)
    }
}
