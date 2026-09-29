package com.example.smartgardenplanner.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Adversarial tests for the dictionary engine (SGP-TCS-001 §3.6, LLR-LANG-020/030). */
class I18nPropertyTest {

    @After fun english() = I18n.use("en", emptyMap())

    private fun spanish() {
        val file = listOf("app/src/main/assets/i18n/es.txt", "Android App/app/src/main/assets/i18n/es.txt", "src/main/assets/i18n/es.txt").map { java.io.File(it) }.first { it.exists() }
        I18n.use("es", I18n.parse(file.readLines()))
    }

    @Test
    fun englishIsAlwaysUnchanged_andRandomTextNeverThrows() {
        val rnd = Random(1)
        val alphabet = "abcXYZ {}0123456789.,;:—•✓()[]\\$^*+?|\"'“”«»\n\t😀ñ-"
        val samples = List(3_000) { buildString { repeat(rnd.nextInt(0, 80)) { append(alphabet[rnd.nextInt(alphabet.length)]) } } }
        samples.forEach { assertEquals(it, I18n.tr(it)) }
        spanish()
        samples.forEach { I18n.tr(it) }   // any text: a result, never an exception
    }

    @Test
    fun dictionariesWithTrickyEntries() {
        val dict = I18n.parse(listOf(
            "# comment\tignored", "", "no tab here", "\tempty key", "empty value\t", "Regex (.*) $1\tRegex ok",
            "{0} and {1}\t{1} y {0}", "cat\tgato", "dog\tperro", "x\tequis", "{0}{1}\tpegado", "A {0} B\tX {0} Y", "Ten {10}\tDiez {10}", "Line\\nbreak\tSalto\\nlínea"))
        I18n.use("xx", dict)
        assertEquals("Regex ok", I18n.tr("Regex (.*) $1"))
        assertEquals("perro y gato", I18n.tr("cat and dog"))       // parts swap places
        assertEquals("rows and columns", I18n.tr("rows and columns"))   // a loose template needs every part translated
        assertEquals("X 3 Y", I18n.tr("A 3 B"))
        assertEquals("Salto\nlínea", I18n.tr("Line\nbreak"))
        assertEquals("  X 3 Y\n", I18n.tr("  A 3 B\n"))            // surrounding spaces kept
        assertEquals("ignored", I18n.tr("ignored"))                // comments aren't entries
        assertEquals("no tab here", I18n.tr("no tab here"))
        // A value containing "{1}" in a part does not get substituted twice.
        assertEquals("{1} y equis", I18n.tr("x and {1}"))
    }

    @Test
    fun deepAndLongInputFinishQuickly() {
        spanish()
        val t0 = System.currentTimeMillis()
        I18n.tr(List(2_000) { "Tomato" }.joinToString(", "))
        I18n.tr("• ".repeat(500) + "Slugs")
        I18n.tr(List(300) { "Water deeply." }.joinToString(" "))
        I18n.tr("x".repeat(200_000))
        assertTrue("took ${System.currentTimeMillis() - t0} ms", System.currentTimeMillis() - t0 < 5_000)
    }

    @Test
    fun switchingLanguagesNeverLeaksOldTranslations() {
        spanish()
        assertEquals("Tomate", I18n.tr("Tomato"))
        I18n.use("en", emptyMap())
        assertEquals("Tomato", I18n.tr("Tomato"))
        I18n.use("xx", mapOf("Tomato" to "Pomodoro"))
        assertEquals("Pomodoro", I18n.tr("Tomato")); assertEquals("Pepper", I18n.tr("Pepper"))
        assertEquals("xx", I18n.language)
    }

    @Test
    fun everyTemplateInTheSpanishDictionaryKeepsItsPlaceholders() {
        val file = listOf("app/src/main/assets/i18n/es.txt", "Android App/app/src/main/assets/i18n/es.txt", "src/main/assets/i18n/es.txt").map { java.io.File(it) }.first { it.exists() }
        val ph = Regex("\\{\\d+\\}")
        val bad = I18n.parse(file.readLines()).filter { (k, v) -> ph.findAll(k).map { it.value }.sorted().toList() != ph.findAll(v).map { it.value }.sorted().toList() }
        assertTrue("placeholders differ: $bad", bad.isEmpty())
        val dup = file.readLines().filter { '\t' in it && !it.startsWith("#") }.groupBy { it.substringBefore('\t').trim() }.filter { e -> e.value.map { it.substringAfter('\t').trim() }.distinct().size > 1 }
        assertTrue("same English, different Spanish: ${dup.keys}", dup.isEmpty())
    }
}
