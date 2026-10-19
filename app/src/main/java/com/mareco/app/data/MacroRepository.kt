package com.mareco.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stores every macro as one `<id>.mareco` file (the same JSON that is used for export/import).
 * [post] is used to deliver change notifications (the app passes "run on the main thread").
 */
class MacroRepository(
    private val dir: File,
    private val post: (Runnable) -> Unit = { it.run() }
) {
    private val lock = Any()
    private var cache: LinkedHashMap<String, Macro>? = null
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) {
        listeners.addIfAbsent(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChanged() {
        post(Runnable { for (l in listeners) l() })
    }

    private fun loaded(): LinkedHashMap<String, Macro> {
        cache?.let { return it }
        val map = LinkedHashMap<String, Macro>()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }
        if (files != null) {
            for (f in files) {
                try {
                    val m = MacroCodec.decode(f.readText(Charsets.UTF_8))
                    map[m.id] = m
                } catch (e: Exception) {
                    // A damaged file must never break the whole list; it is simply skipped.
                }
            }
        }
        cache = map
        return map
    }

    /** Newest first. */
    fun list(): List<Macro> = synchronized(lock) {
        loaded().values.sortedByDescending { it.modifiedAt }
    }

    fun get(id: String?): Macro? {
        if (id.isNullOrEmpty()) return null
        return synchronized(lock) { loaded()[id] }
    }

    fun count(): Int = synchronized(lock) { loaded().size }

    /** Saves (creates or overwrites) a macro. */
    fun save(macro: Macro) {
        synchronized(lock) {
            write(macro)
            loaded()[macro.id] = macro
        }
        notifyChanged()
    }

    /** Applies [change] to the stored macro, bumping its modified time. Returns the new macro. */
    fun update(id: String, touch: Boolean = true, change: (Macro) -> Macro): Macro? {
        val result: Macro?
        synchronized(lock) {
            val old = loaded()[id] ?: return null
            var next = change(old)
            if (touch) next = next.copy(modifiedAt = System.currentTimeMillis())
            write(next)
            loaded()[id] = next
            result = next
        }
        notifyChanged()
        return result
    }

    fun delete(id: String) {
        synchronized(lock) {
            loaded().remove(id)
            File(dir, id + EXT).delete()
        }
        notifyChanged()
    }

    /** [copyWord] is the word "copy" in the language of the screen, giving names like "Farm (copy)". */
    fun duplicate(id: String, copyWord: String = "copy"): Macro? {
        val src = get(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = src.copy(
            id = newId(),
            name = uniqueName(src.name + " (" + copyWord.trim().ifBlank { "copy" } + ")"),
            createdAt = now,
            modifiedAt = now,
            stats = MacroStats()
        )
        save(copy)
        return copy
    }

    fun rename(id: String, name: String, description: String? = null): Macro? =
        update(id) { it.copy(name = name.trim().ifBlank { it.name }, description = description ?: it.description) }

    /** Import from the text of a `.mareco` file. Always creates a NEW macro (never overwrites). */
    fun importText(text: String): Macro {
        val decoded = MacroCodec.decode(text)
        val now = System.currentTimeMillis()
        val macro = decoded.copy(
            id = newId(),
            name = uniqueName(decoded.name),
            modifiedAt = now,
            createdAt = if (decoded.createdAt > 0) decoded.createdAt else now
        )
        save(macro)
        return macro
    }

    fun exportText(id: String, pretty: Boolean = true): String? =
        get(id)?.let { MacroCodec.encode(it, pretty) }

    /** Adds the result of a finished run to the macro's statistics. */
    fun recordRun(id: String, loops: Int, runtimeMs: Long) {
        update(id, touch = false) {
            it.copy(
                stats = MacroStats(
                    runs = it.stats.runs + loops,
                    totalRuntimeMs = it.stats.totalRuntimeMs + runtimeMs,
                    lastRunAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** [base] when it is free, otherwise "base 2", "base 3" ... [fallback] is used when [base] is blank. */
    fun uniqueName(base: String, fallback: String = "Macro"): String {
        val clean = base.trim().ifBlank { fallback.trim().ifBlank { "Macro" } }
        val names = synchronized(lock) { loaded().values.map { it.name.lowercase() }.toSet() }
        if (clean.lowercase() !in names) return clean
        var n = 2
        while ("$clean $n".lowercase() in names) n++
        return "$clean $n"
    }

    /** "Macro 1", "Macro 2", ... the first unused number. [word] is the word "Macro" in the language of the screen. */
    fun nextDefaultName(word: String = "Macro"): String {
        val w = word.trim().ifBlank { "Macro" }
        val names = synchronized(lock) { loaded().values.map { it.name.lowercase() }.toSet() }
        var n = 1
        while ("${w.lowercase()} $n" in names) n++
        return "$w $n"
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun write(m: Macro) {
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create ${dir.path}")
        val target = File(dir, m.id + EXT)
        val tmp = File(dir, m.id + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(MacroCodec.encode(m).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not save the macro")
        }
    }

    companion object {
        const val EXT = ".mareco"
    }
}
