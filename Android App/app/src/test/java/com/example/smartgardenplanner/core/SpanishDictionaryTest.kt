package com.example.smartgardenplanner.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Spanish dictionary covers text built at run time: planner notes, variety names, lists, bullets and dates (FR-062). */
class SpanishDictionaryTest {

    private fun useSpanish() {
        val file = listOf("app/src/main/assets/i18n/es.txt", "Android App/app/src/main/assets/i18n/es.txt", "src/main/assets/i18n/es.txt")
            .map { java.io.File(it) }.first { it.exists() }
        I18n.use("es", I18n.parse(file.readLines()))
    }

    @After fun backToEnglish() = I18n.use("en", emptyMap())

    private val english = Regex("\\\\b(the|and|of|with|your|plants|are|is|so|they|from|which|toward)\\\\b")

    @Test
    fun plannerNotesForTheOwnersListComeOutInSpanish() {
        val t = RunwaysAndRowsTest()
        val plot = PlotEntity(id = 1, name = "P", lengthM = 15f, widthM = 12f, latitude = 42.3, orientationSet = false)
        val ctx = PlotContext(plot, emptyList(), emptyList(), t.lookup)
        val notes = (0 until AutoPlanner.VARIANT_COUNT).flatMap { v -> AutoPlanner.planVariant(v, ctx, PlotShape.effectiveOutline(plot), t.ownersList(), orientationKnown = false).notes }.distinct()
        useSpanish()
        val left = notes.map { I18n.tr(it) }.filter { english.containsMatchIn(it) }
        assertTrue("Still in English:\\n" + left.joinToString("\\n"), left.isEmpty())
    }

    @Test
    fun namesListsBulletsAndDates() {
        useSpanish()
        assertEquals("Maíz dulce - Honey Select", I18n.tr("Sweet Corn - Honey Select"))
        assertEquals("Calabacita mixta - De media estación", I18n.tr("Summer Squash Mix - Mid-Season"))
        assertEquals("Pulgones, babosas", I18n.tr("Aphids, slugs"))
        assertEquals("• Babosas", I18n.tr("• Slugs"))
        assertEquals("12 may", I18n.tr("May 12"))
        assertEquals("18 (3 filas de 6)", I18n.tr("18 (3 rows of 6)"))
        assertEquals("Colocadas: 3 × Calabaza de invierno - Spaghetti, 49 × Maíz dulce - Honey Select.",
            I18n.tr("Placed: 3 × Winter Squash - Spaghetti, 49 × Sweet Corn - Honey Select."))
    }
}
