package com.mareco.app

import com.mareco.app.core.BarSymbols
import com.mareco.app.core.MainSymbol
import org.junit.Assert.assertEquals
import org.junit.Test

/** The main button of the floating bar: ● in Record mode, ▶ in Start mode, ■ while something runs. */
class BarSymbolsTest {
    private fun symbol(
        engine: EngineState,
        recordMode: Boolean,
        recordingPaused: Boolean = false,
        countingDown: Boolean = false
    ) = BarSymbols.main(engine, recordingPaused, countingDown, recordMode)

    @Test
    fun idleInRecordModeShowsTheRecordButton() {
        for (e in listOf(EngineState.IDLE, EngineState.READY, EngineState.STOPPED)) {
            assertEquals("engine $e", MainSymbol.RECORD, symbol(e, recordMode = true))
        }
    }

    @Test
    fun idleInStartModeShowsPlay() {
        for (e in listOf(EngineState.IDLE, EngineState.READY, EngineState.STOPPED)) {
            assertEquals("engine $e", MainSymbol.PLAY, symbol(e, recordMode = false))
        }
    }

    @Test
    fun recordingShowsStopAndPausedRecordingShowsRecord() {
        assertEquals(MainSymbol.STOP, symbol(EngineState.RECORDING, recordMode = true))
        // a paused recording is resumed with the same button, so it shows the record dot again
        assertEquals(MainSymbol.RECORD, symbol(EngineState.RECORDING, recordMode = true, recordingPaused = true))
    }

    @Test
    fun runningShowsStopAndPausedRunShowsPlayWhateverTheMode() {
        for (mode in listOf(true, false)) {
            assertEquals(MainSymbol.STOP, symbol(EngineState.RUNNING, recordMode = mode))
            assertEquals(MainSymbol.PLAY, symbol(EngineState.PAUSED, recordMode = mode))
        }
    }

    @Test
    fun theCountdownAlwaysShowsStop() {
        for (e in EngineState.values()) {
            for (mode in listOf(true, false)) {
                assertEquals("engine $e", MainSymbol.STOP, symbol(e, recordMode = mode, countingDown = true))
            }
        }
    }
}
