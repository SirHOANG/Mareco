package com.mareco.app.service

import android.os.Handler
import android.os.SystemClock
import com.mareco.app.core.Scheduler

/** Runs the engine's timers on the main thread. */
class HandlerScheduler(private val handler: Handler) : Scheduler {
    override fun post(delayMs: Long, task: Runnable): Any {
        handler.postDelayed(task, delayMs)
        return task
    }

    override fun cancel(token: Any) {
        handler.removeCallbacks(token as Runnable)
    }

    override fun now(): Long = SystemClock.uptimeMillis()
}
