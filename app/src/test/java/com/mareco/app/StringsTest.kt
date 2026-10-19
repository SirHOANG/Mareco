package com.mareco.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Checks the translations without a phone: every language of [Languages] has a complete strings.xml (same keys, same
 * placeholders), the Android 13 language list matches, and phone locales map to the right language.
 *
 * Gradle runs unit tests with the module folder (app/) as the working directory.
 */
class StringsTest {
    private val res: File = listOf("src/main/res", "app/src/main/res")
        .map { File(it) }
        .firstOrNull { File(it, "values/strings.xml").exists() }
        ?: error("res folder not found from " + File(".").absolutePath)

    /** key -> raw text of every translatable <string> in a strings.xml */
    private fun load(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = LinkedHashMap<String, String>()
        val nodes = doc.documentElement.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            if (e.getAttribute("translatable") == "false") continue
            out[e.getAttribute("name")] = e.textContent ?: ""
        }
        return out
    }

    private val english: Map<String, String> by lazy { load(File(res, "values/strings.xml")) }

    private val specifier = Regex("%(?:(\\d+)\\$)?s")

    /** The placeholders of a text, numbered (a lone %s is %1$s), sorted: ["1", "2"]. */
    private fun placeholders(text: String): List<String> {
        var next = 0
        return specifier.findAll(text).map { m ->
            val n = m.groupValues[1]
            if (n.isEmpty()) (++next).toString() else n
        }.sorted().toList()
    }

    private fun language(l: Language) = if (l.qualifier.isEmpty()) "values" else "values-" + l.qualifier

    @Test
    fun englishIsComplete() {
        assertTrue("expected hundreds of strings, found " + english.size, english.size > 300)
        for ((k, v) in english) assertTrue("empty English text: $k", v.isNotBlank())
    }

    @Test
    fun everyListedLanguageHasAFullTranslation() {
        for (l in Languages.all) {
            if (l.qualifier.isEmpty()) continue
            val file = File(res, language(l) + "/strings.xml")
            assertTrue("missing " + file.path + " for " + l.tag, file.exists())
            val tr = load(file)
            val missing = english.keys - tr.keys
            val unknown = tr.keys - english.keys
            assertTrue(l.tag + ": missing keys " + missing.take(5), missing.isEmpty())
            assertTrue(l.tag + ": unknown keys " + unknown.take(5), unknown.isEmpty())
        }
    }

    @Test
    fun translationsKeepPlaceholdersAndLineBreaks() {
        for (l in Languages.all) {
            if (l.qualifier.isEmpty()) continue
            val tr = load(File(res, language(l) + "/strings.xml"))
            for ((k, en) in english) {
                val t = tr[k] ?: continue
                assertTrue(l.tag + "/" + k + ": empty", t.isNotBlank())
                assertEquals(l.tag + "/" + k + ": placeholders", placeholders(en), placeholders(t))
                assertEquals(l.tag + "/" + k + ": line breaks", en.split("\\n").size, t.split("\\n").size)
                assertEquals(l.tag + "/" + k + ": literal %", en.split("%%").size, t.split("%%").size)
                // anything left over after removing %s, %n$s and %% is a stray % that would crash String.format
                val rest = t.replace("%%", "").replace(specifier, "")
                assertTrue(l.tag + "/" + k + ": stray % in " + t, !rest.contains('%'))
                // aapt2 rejects an apostrophe that is not escaped, and drops a bare double quote
                assertTrue(l.tag + "/" + k + ": unescaped apostrophe", !Regex("(?<!\\\\)'").containsMatchIn(t))
                assertTrue(l.tag + "/" + k + ": unescaped quote", !Regex("(?<!\\\\)\"").containsMatchIn(t))
            }
        }
    }

    @Test
    fun noEnglishFormatSpecifiersOtherThanString() {
        // numbers are passed in as text, so only %s / %1$s / %% may appear (a %d would crash on a text argument)
        val bad = Regex("%(?!\\d+\\\$s|s)")
        for ((k, v) in english) assertTrue("$k: unsupported % in $v", !bad.containsMatchIn(v.replace("%%", "")))
    }

    @Test
    fun languageListIsConsistent() {
        assertEquals("English first", "en", Languages.all.first().tag)
        assertEquals("tags are unique", Languages.all.size, Languages.all.map { it.tag }.toSet().size)
        assertEquals("qualifiers are unique", Languages.all.size, Languages.all.map { it.qualifier }.toSet().size)
        for (l in Languages.all) {
            assertTrue(l.tag + ": native name", l.name.isNotBlank())
            assertTrue(l.tag + ": qualifier " + l.qualifier, l.qualifier.isEmpty() || Regex("[a-z]{2,3}(-r[A-Z]{2})?").matches(l.qualifier))
        }
    }

    @Test
    fun androidLanguageListMatchesTheAppList() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "xml/locales_config.xml"))
        val nodes = doc.documentElement.getElementsByTagName("locale")
        val tags = ArrayList<String>()
        for (i in 0 until nodes.length) tags += (nodes.item(i) as Element).getAttribute("android:name")
        assertEquals(Languages.all.map { it.tag }, tags)
    }

    @Test
    fun everyLanguageIsFoundFromItsPhoneLocale() {
        for (l in Languages.all) {
            val parts = l.tag.split("-")
            val country = parts.getOrNull(1)
            val found = Languages.match(parts[0], null, country)
            assertEquals("phone locale " + l.tag, l.tag, found.tag)
        }
    }

    @Test
    fun oldAndAlternativeCodesMapToTheRightLanguage() {
        fun tagOf(language: String, script: String? = null, country: String? = null) = Languages.match(language, script, country).tag
        val tags = Languages.all.map { it.tag }.toSet()
        if ("he" in tags) assertEquals("he", tagOf("iw"))
        if ("id" in tags) assertEquals("id", tagOf("in"))
        if ("fil" in tags) assertEquals("fil", tagOf("tl"))
        if ("nb" in tags) {
            assertEquals("nb", tagOf("no"))
            assertEquals("nb", tagOf("nn"))
        }
        if ("zh-TW" in tags) {
            assertEquals("zh-TW", tagOf("zh", "Hant"))
            assertEquals("zh-TW", tagOf("zh", null, "HK"))
        }
        if ("zh-CN" in tags) assertEquals("zh-CN", tagOf("zh", "Hans", "CN"))
        if ("pt-PT" in tags) assertEquals("pt-PT", tagOf("pt", null, "PT"))
        if ("pt-BR" in tags) assertEquals("pt-BR", tagOf("pt", null, "BR"))
        // a language that is not translated falls back to English, like Android itself does
        assertEquals("en", tagOf("xx"))
    }
}
