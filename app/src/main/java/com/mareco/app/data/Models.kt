package com.mareco.app.data

import kotlin.math.max

/** Kinds of steps a macro can contain. [id] is what ends up in the .mareco file. */
enum class ActionType(val id: String) {
    TAP("tap"),
    LONG_PRESS("long_press"),
    SWIPE("swipe"),
    DRAG("drag"),
    MULTI("multi"),
    KEY("key"),
    WAIT("wait");

    val isTouch: Boolean
        get() = this == TAP || this == LONG_PRESS || this == SWIPE || this == DRAG || this == MULTI

    companion object {
        fun fromId(id: String?): ActionType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * System buttons Mareco can record and replay. Android only allows apps to perform these through
 * accessibility global actions, so arbitrary key codes (volume, letters ...) are not replayable.
 */
enum class SystemKey(val id: String, val label: String) {
    BACK("back", "Back"),
    HOME("home", "Home"),
    RECENTS("recents", "Recent apps"),
    NOTIFICATIONS("notifications", "Notification shade"),
    QUICK_SETTINGS("quick_settings", "Quick settings");

    companion object {
        fun fromId(id: String?): SystemKey? = entries.firstOrNull { it.id == id }
    }
}

/** A point of a finger trace. [t] is milliseconds since the stroke started. */
data class PathPoint(val x: Float, val y: Float, val t: Long)

/** One finger of a multi-touch gesture. */
data class StrokeData(val startOffsetMs: Long, val points: List<PathPoint>) {
    val durationMs: Long get() = if (points.isEmpty()) 1L else max(1L, points.last().t)
    val endOffsetMs: Long get() = startOffsetMs + durationMs
}

/**
 * One recorded step.
 *
 * - [delayMs]    time to wait BEFORE this step, counted from the end of the previous step.
 * - [durationMs] how long the step itself lasts (press time, swipe time, wait time).
 * - TAP / LONG_PRESS use [x],[y]. SWIPE / DRAG go from ([x],[y]) to ([x2],[y2]); DRAG also keeps [path].
 * - MULTI keeps one [StrokeData] per finger. KEY keeps a [SystemKey]. WAIT only uses [durationMs].
 */
data class MacroAction(
    val type: ActionType,
    val delayMs: Long = 0L,
    val durationMs: Long = 0L,
    val x: Float = 0f,
    val y: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val path: List<PathPoint> = emptyList(),
    val strokes: List<StrokeData> = emptyList(),
    val key: SystemKey? = null
) {
    /** How long the step runs once it has started. */
    val busyMs: Long
        get() = when (type) {
            ActionType.KEY -> 0L
            ActionType.MULTI -> strokes.maxOfOrNull { it.endOffsetMs } ?: 0L
            else -> durationMs
        }

    val totalMs: Long get() = delayMs + busyMs

    companion object {
        fun tap(x: Float, y: Float, durationMs: Long = 60L, delayMs: Long = 0L) =
            MacroAction(ActionType.TAP, delayMs, durationMs, x, y)

        fun longPress(x: Float, y: Float, durationMs: Long = 800L, delayMs: Long = 0L) =
            MacroAction(ActionType.LONG_PRESS, delayMs, durationMs, x, y)

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300L, delayMs: Long = 0L) =
            MacroAction(ActionType.SWIPE, delayMs, durationMs, x1, y1, x2, y2)

        fun wait(ms: Long) = MacroAction(ActionType.WAIT, 0L, ms)

        fun key(key: SystemKey, delayMs: Long = 0L) =
            MacroAction(ActionType.KEY, delayMs, 0L, key = key)
    }
}

enum class StopMode(val id: String) {
    INFINITE("infinite"), COUNT("count"), TIME("time");

    companion object {
        fun fromId(id: String?): StopMode = entries.firstOrNull { it.id == id } ?: INFINITE
    }
}

enum class DelayMode(val id: String) {
    ORIGINAL("original"), CUSTOM("custom");

    companion object {
        fun fromId(id: String?): DelayMode = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

enum class ScaleMode(val id: String) {
    ORIGINAL("original"), SCALE("scale"), ASPECT("aspect");

    companion object {
        fun fromId(id: String?): ScaleMode = entries.firstOrNull { it.id == id } ?: SCALE
    }
}

/** Per-macro execution settings (the "Execution Settings" dialog). */
data class ExecutionSettings(
    val stopMode: StopMode = StopMode.INFINITE,
    val count: Int = 1,
    val timeMs: Long = 30L * 60_000L,
    val loopDelayMs: Long = 0L,
    val speed: Float = 1f,
    val simulate: Boolean = true,
    val autoExecute: Boolean = false,
    val delayMode: DelayMode = DelayMode.ORIGINAL,
    val customDelayMs: Long = 500L,
    val randomDelay: Boolean = false,
    val randomRangeMs: Long = 200L,
    val humanize: Boolean = false,
    val humanizePx: Int = 3,
    val scaleMode: ScaleMode = ScaleMode.SCALE,
    val lockToApp: Boolean = false
)

/** Screen the macro was recorded on (or the screen it is about to run on). */
data class DeviceInfo(
    val width: Int,
    val height: Int,
    val densityDpi: Int = 0,
    val rotation: Int = 0,
    val model: String = "",
    val sdk: Int = 0
) {
    val isLandscape: Boolean get() = width > height
    val label: String get() = "$width \u00D7 $height"
}

data class MacroStats(
    val runs: Int = 0,
    val totalRuntimeMs: Long = 0L,
    val lastRunAt: Long = 0L
)

data class Macro(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = 0L,
    val modifiedAt: Long = 0L,
    val device: DeviceInfo = DeviceInfo(0, 0),
    val appPackage: String = "",
    val appLabel: String = "",
    val actions: List<MacroAction> = emptyList(),
    val execution: ExecutionSettings = ExecutionSettings(),
    val stats: MacroStats = MacroStats()
) {
    /** Steps that are not plain waits (what the user thinks of as "actions"). */
    val actionCount: Int get() = actions.count { it.type != ActionType.WAIT }

    /** Length of one pass through the macro at 1x speed. */
    val loopLengthMs: Long get() = actions.sumOf { it.totalMs }
}
