package com.mareco.app

import com.mareco.app.data.Macro
import com.mareco.app.data.MacroAction
import com.mareco.app.data.MacroFormatException
import com.mareco.app.data.MacroRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MacroRepositoryTest {
    private val dir: File = Files.createTempDirectory("mareco-test").toFile()
    private var repo = MacroRepository(dir)

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun macro(name: String, at: Long = System.currentTimeMillis()) =
        Macro(id = repo.newId(), name = name, createdAt = at, modifiedAt = at, actions = listOf(MacroAction.tap(1f, 2f, 50, 0)))

    @Test
    fun saveListGetDeleteWork_andSurviveARestart() {
        val a = macro("A", 1_000)
        val b = macro("B", 2_000)
        repo.save(a); repo.save(b)
        assertEquals(listOf("B", "A"), repo.list().map { it.name })
        assertEquals(a, repo.get(a.id))
        assertEquals(2, MacroRepository(dir).count())
        repo.delete(a.id)
        assertNull(repo.get(a.id))
        assertEquals(1, MacroRepository(dir).count())
    }

    @Test
    fun duplicateRenameAndUniqueNames() {
        val a = macro("Farm")
        repo.save(a)
        val copy = repo.duplicate(a.id)!!
        assertEquals("Farm (copy)", copy.name)
        assertNotEquals(a.id, copy.id)
        assertEquals("Farm 2", repo.uniqueName("Farm"))
        assertEquals("Macro 1", repo.nextDefaultName())
        repo.rename(a.id, "  Daily  ", "desc")
        assertEquals("Daily", repo.get(a.id)!!.name)
        assertEquals("desc", repo.get(a.id)!!.description)
    }

    @Test
    fun importNeverOverwritesAndExportIsReadable() {
        val a = macro("Farm")
        repo.save(a)
        val text = repo.exportText(a.id)!!
        assertTrue(text.contains("\"format\""))
        val imported = repo.importText(text)
        assertNotEquals(a.id, imported.id)
        assertEquals("Farm 2", imported.name)
        assertEquals(2, repo.count())
        try {
            repo.importText("garbage")
            fail()
        } catch (e: MacroFormatException) {
            // expected
        }
    }

    @Test
    fun runStatisticsAccumulate() {
        val a = macro("Farm")
        repo.save(a)
        repo.recordRun(a.id, 3, 5_000)
        repo.recordRun(a.id, 2, 1_000)
        val s = repo.get(a.id)!!.stats
        assertEquals(5, s.runs)
        assertEquals(6_000L, s.totalRuntimeMs)
        assertNotNull(s.lastRunAt)
    }

    @Test
    fun aDamagedFileDoesNotBreakTheList() {
        repo.save(macro("Good"))
        File(dir, "broken.mareco").writeText("{ this is not json")
        val fresh = MacroRepository(dir)
        assertEquals(listOf("Good"), fresh.list().map { it.name })
    }

    @Test
    fun listenersAreNotified() {
        var n = 0
        repo.addListener { n++ }
        repo.save(macro("X"))
        assertEquals(1, n)
    }
}
