package com.mareco.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A `.mareco` file that cannot be imported. [message] is plain English (for logs and tests);
 * the app shows a translated text chosen by [kind].
 */
class MacroFormatException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    enum class Kind { NOT_VALID, NOT_MARECO, NEWER, EMPTY }
}

/** Reads and writes the `.mareco` JSON file format (version 1). */
object MacroCodec {
    const val FORMAT = "mareco"
    const val VERSION = 1

    private const val MAX_ACTIONS = 20_000
    private const val MAX_POINTS = 4_000
    private const val MAX_STROKES = 10
    private const val MAX_MS = 86_400_000L
    private const val MAX_COORD = 20_000f

    // ---------------------------------------------------------------- encode

    fun encode(m: Macro, pretty: Boolean = false): String {
        val o = JSONObject()
        o.put("format", FORMAT)
        o.put("version", VERSION)
        o.put("id", m.id)
        o.put("name", m.name)
        o.put("description", m.description)
        o.put("createdAt", m.createdAt)
        o.put("modifiedAt", m.modifiedAt)
        o.put("device", deviceToJson(m.device))
        o.put("app", JSONObject().put("package", m.appPackage).put("label", m.appLabel))
        val arr = JSONArray()
        for (a in m.actions) arr.put(actionToJson(a))
        o.put("actions", arr)
        o.put("execution", executionToJson(m.execution))
        o.put(
            "stats",
            JSONObject().put("runs", m.stats.runs).put("totalRuntimeMs", m.stats.totalRuntimeMs)
                .put("lastRunAt", m.stats.lastRunAt)
        )
        return if (pretty) o.toString(2) else o.toString()
    }

    fun deviceToJson(d: DeviceInfo): JSONObject = JSONObject()
        .put("width", d.width).put("height", d.height).put("densityDpi", d.densityDpi)
        .put("rotation", d.rotation).put("model", d.model).put("sdk", d.sdk)

    fun executionToJson(e: ExecutionSettings): JSONObject = JSONObject()
        .put("mode", e.stopMode.id)
        .put("count", e.count)
        .put("timeMs", e.timeMs)
        .put("loopDelayMs", e.loopDelayMs)
        .put("speed", r2(e.speed))
        .put("simulate", e.simulate)
        .put("autoExecute", e.autoExecute)
        .put("delayMode", e.delayMode.id)
        .put("customDelayMs", e.customDelayMs)
        .put("randomDelay", e.randomDelay)
        .put("randomRangeMs", e.randomRangeMs)
        .put("humanize", e.humanize)
        .put("humanizePx", e.humanizePx)
        .put("scaling", e.scaleMode.id)
        .put("lockToApp", e.lockToApp)

    private fun actionToJson(a: MacroAction): JSONObject {
        val o = JSONObject()
        o.put("type", a.type.id)
        o.put("delay", a.delayMs)
        when (a.type) {
            ActionType.TAP, ActionType.LONG_PRESS -> {
                o.put("x", r1(a.x)).put("y", r1(a.y)).put("duration", a.durationMs)
            }
            ActionType.SWIPE -> {
                o.put("x", r1(a.x)).put("y", r1(a.y)).put("x2", r1(a.x2)).put("y2", r1(a.y2))
                o.put("duration", a.durationMs)
            }
            ActionType.DRAG -> {
                o.put("x", r1(a.x)).put("y", r1(a.y)).put("x2", r1(a.x2)).put("y2", r1(a.y2))
                o.put("duration", a.durationMs)
                o.put("path", pointsToJson(a.path))
            }
            ActionType.MULTI -> {
                val arr = JSONArray()
                for (s in a.strokes) {
                    arr.put(JSONObject().put("start", s.startOffsetMs).put("points", pointsToJson(s.points)))
                }
                o.put("strokes", arr)
            }
            ActionType.KEY -> o.put("key", a.key?.id ?: "")
            ActionType.WAIT -> o.put("duration", a.durationMs)
        }
        return o
    }

    private fun pointsToJson(points: List<PathPoint>): JSONArray {
        val arr = JSONArray()
        for (p in points) arr.put(JSONArray().put(r1(p.x)).put(r1(p.y)).put(p.t))
        return arr
    }

    // ---------------------------------------------------------------- decode

    /** @throws MacroFormatException when the text is not a usable Mareco macro. */
    fun decode(text: String): Macro {
        val o = try {
            JSONObject(text.trim())
        } catch (e: JSONException) {
            throw MacroFormatException(MacroFormatException.Kind.NOT_VALID, "This is not a valid Mareco macro file.", e)
        }
        if (o.optString("format", FORMAT) != FORMAT) throw MacroFormatException(MacroFormatException.Kind.NOT_MARECO, "This is not a Mareco macro file.")
        val version = o.optInt("version", 1)
        if (version > VERSION) {
            throw MacroFormatException(
                MacroFormatException.Kind.NEWER,
                "This macro was made by a newer version of Mareco (format v$version)."
            )
        }
        val arr = o.optJSONArray("actions") ?: throw MacroFormatException(MacroFormatException.Kind.EMPTY, "The macro has no actions.")
        val actions = ArrayList<MacroAction>()
        for (i in 0 until minOf(arr.length(), MAX_ACTIONS)) {
            val ao = arr.optJSONObject(i) ?: continue
            val action = try {
                parseAction(ao)
            } catch (e: JSONException) {
                null
            }
            if (action != null) actions.add(action)
        }
        if (actions.isEmpty()) throw MacroFormatException(MacroFormatException.Kind.EMPTY, "The macro has no usable actions.")
        val app = o.optJSONObject("app")
        val now = System.currentTimeMillis()
        val stats = o.optJSONObject("stats")
        return Macro(
            id = o.optString("id", "").ifBlank { java.util.UUID.randomUUID().toString() },
            name = o.optString("name", "").trim().ifBlank { "Imported macro" },
            description = o.optString("description", ""),
            createdAt = o.optLong("createdAt", now),
            modifiedAt = o.optLong("modifiedAt", now),
            device = deviceFromJson(o.optJSONObject("device")),
            appPackage = app?.optString("package", "") ?: "",
            appLabel = app?.optString("label", "") ?: "",
            actions = actions,
            execution = executionFromJson(o.optJSONObject("execution")),
            stats = MacroStats(
                runs = stats?.optInt("runs", 0) ?: 0,
                totalRuntimeMs = stats?.optLong("totalRuntimeMs", 0L) ?: 0L,
                lastRunAt = stats?.optLong("lastRunAt", 0L) ?: 0L
            )
        )
    }

    fun deviceFromJson(o: JSONObject?): DeviceInfo {
        if (o == null) return DeviceInfo(0, 0)
        return DeviceInfo(
            width = o.optInt("width", 0).coerceIn(0, 100_000),
            height = o.optInt("height", 0).coerceIn(0, 100_000),
            densityDpi = o.optInt("densityDpi", 0),
            rotation = o.optInt("rotation", 0),
            model = o.optString("model", ""),
            sdk = o.optInt("sdk", 0)
        )
    }

    fun executionFromJson(o: JSONObject?): ExecutionSettings {
        val d = ExecutionSettings()
        if (o == null) return d
        return ExecutionSettings(
            stopMode = StopMode.fromId(o.optString("mode", d.stopMode.id)),
            count = o.optInt("count", d.count).coerceIn(1, 1_000_000),
            timeMs = o.optLong("timeMs", d.timeMs).coerceIn(1_000L, MAX_MS),
            loopDelayMs = o.optLong("loopDelayMs", d.loopDelayMs).coerceIn(0L, MAX_MS),
            speed = o.optDouble("speed", d.speed.toDouble()).toFloat().coerceIn(0.1f, 20f),
            simulate = o.optBoolean("simulate", d.simulate),
            autoExecute = o.optBoolean("autoExecute", d.autoExecute),
            delayMode = DelayMode.fromId(o.optString("delayMode", d.delayMode.id)),
            customDelayMs = o.optLong("customDelayMs", d.customDelayMs).coerceIn(0L, MAX_MS),
            randomDelay = o.optBoolean("randomDelay", d.randomDelay),
            randomRangeMs = o.optLong("randomRangeMs", d.randomRangeMs).coerceIn(0L, MAX_MS),
            humanize = o.optBoolean("humanize", d.humanize),
            humanizePx = o.optInt("humanizePx", d.humanizePx).coerceIn(0, 100),
            scaleMode = ScaleMode.fromId(o.optString("scaling", d.scaleMode.id)),
            lockToApp = o.optBoolean("lockToApp", d.lockToApp)
        )
    }

    private fun parseAction(o: JSONObject): MacroAction? {
        val type = ActionType.fromId(o.optString("type", "")) ?: return null
        val delay = o.optLong("delay", 0L).coerceIn(0L, MAX_MS)
        val dur = o.optLong("duration", 0L).coerceIn(0L, MAX_MS)
        return when (type) {
            ActionType.TAP, ActionType.LONG_PRESS ->
                MacroAction(type, delay, dur.coerceAtLeast(1L), coord(o, "x"), coord(o, "y"))
            ActionType.SWIPE ->
                MacroAction(type, delay, dur.coerceAtLeast(1L), coord(o, "x"), coord(o, "y"), coord(o, "x2"), coord(o, "y2"))
            ActionType.DRAG -> {
                val path = parsePoints(o.optJSONArray("path"))
                if (path.size < 2) {
                    MacroAction(ActionType.SWIPE, delay, dur.coerceAtLeast(1L), coord(o, "x"), coord(o, "y"), coord(o, "x2"), coord(o, "y2"))
                } else {
                    MacroAction(type, delay, dur.coerceAtLeast(1L), coord(o, "x"), coord(o, "y"), coord(o, "x2"), coord(o, "y2"), path = path)
                }
            }
            ActionType.MULTI -> {
                val sa = o.optJSONArray("strokes") ?: return null
                val strokes = ArrayList<StrokeData>()
                for (i in 0 until minOf(sa.length(), MAX_STROKES)) {
                    val so = sa.optJSONObject(i) ?: continue
                    val pts = parsePoints(so.optJSONArray("points"))
                    if (pts.isNotEmpty()) strokes.add(StrokeData(so.optLong("start", 0L).coerceIn(0L, MAX_MS), pts))
                }
                if (strokes.isEmpty()) null else MacroAction(type, delay, 0L, strokes = strokes)
            }
            ActionType.KEY -> {
                val key = SystemKey.fromId(o.optString("key", "")) ?: return null
                MacroAction(type, delay, 0L, key = key)
            }
            ActionType.WAIT -> MacroAction(type, 0L, dur)
        }
    }

    private fun parsePoints(arr: JSONArray?): List<PathPoint> {
        if (arr == null) return emptyList()
        val out = ArrayList<PathPoint>()
        for (i in 0 until minOf(arr.length(), MAX_POINTS)) {
            val p = arr.optJSONArray(i) ?: continue
            if (p.length() < 3) continue
            out.add(PathPoint(finite(p.optDouble(0, 0.0)), finite(p.optDouble(1, 0.0)), p.optLong(2, 0L).coerceIn(0L, MAX_MS)))
        }
        return out
    }

    private fun coord(o: JSONObject, key: String): Float = finite(o.optDouble(key, 0.0))

    private fun finite(v: Double): Float =
        if (v.isNaN() || v.isInfinite()) 0f else v.toFloat().coerceIn(-MAX_COORD, MAX_COORD)

    private fun r1(v: Float): Double = Math.round(v * 10f) / 10.0
    private fun r2(v: Float): Double = Math.round(v * 100f) / 100.0
}
