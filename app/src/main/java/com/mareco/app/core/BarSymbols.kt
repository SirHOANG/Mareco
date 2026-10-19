package com.mareco.app.core

import com.mareco.app.EngineState

/** The picture on the main (blue) button of the floating bar. */
enum class MainSymbol {
    /** ● record dot */
    RECORD,

    /** ▶ play triangle */
    PLAY,

    /** ■ stop square */
    STOP
}

/**
 * Which symbol the main button of the floating bar shows. Pure logic, so it is unit-tested (BarSymbolsTest).
 *
 *  - ■ during the countdown (cancels it), while recording and while a macro runs: press it to stop
 *  - ● in Record mode when nothing is running (press to start recording), and while a recording is paused (press to resume)
 *  - ▶ in Start mode when nothing is running (press to run the macro), and while a macro run is paused (press to resume)
 *
 * What a press does is decided by MarecoController.onPlay(), which follows the same states.
 */
object BarSymbols {
    fun main(engine: EngineState, recordingPaused: Boolean, countingDown: Boolean, recordMode: Boolean): MainSymbol = when {
        countingDown -> MainSymbol.STOP
        engine == EngineState.RECORDING -> if (recordingPaused) MainSymbol.RECORD else MainSymbol.STOP
        engine == EngineState.RUNNING -> MainSymbol.STOP
        engine == EngineState.PAUSED -> MainSymbol.PLAY
        recordMode -> MainSymbol.RECORD
        else -> MainSymbol.PLAY
    }
}
