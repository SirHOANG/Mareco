package com.mareco.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.mareco.app.data.AppSettings
import com.mareco.app.data.MacroRepository
import com.mareco.app.service.MarecoController
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** What the big status pill shows. */
enum class EngineState { IDLE, RECORDING, READY, RUNNING, PAUSED, STOPPED }

/** Live status shared by the app screens and the floating controls. Main thread only. */
class UiState {
    var engine: EngineState = EngineState.IDLE
    var recordingPaused = false
    var countdown = 0
    var countdownLabel = ""
    var message = ""
    var macroName = ""
    var loops = 0
    var totalLoops = 0
    var speed = 1f
    var runElapsedMs = 0L
    var recordMs = 0L
    var recordActions = 0

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) {
        listeners.addIfAbsent(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    fun changed() {
        if (Looper.myLooper() == Looper.getMainLooper()) fire() else Mareco.main.post { fire() }
    }

    private fun fire() {
        for (l in listeners) l()
    }
}

/** Process-wide singletons (the app and the accessibility service live in the same process). */
object Mareco {
    lateinit var app: Application
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var repo: MacroRepository
        private set

    val main = Handler(Looper.getMainLooper())
    val state = UiState()

    /** Set while the accessibility service is connected. */
    @Volatile
    var controller: MarecoController? = null

    fun init(application: Application) {
        app = application
        settings = AppSettings(application.getSharedPreferences("mareco_prefs", Context.MODE_PRIVATE))
        repo = MacroRepository(File(application.filesDir, "macros")) { main.post(it) }
    }
}
