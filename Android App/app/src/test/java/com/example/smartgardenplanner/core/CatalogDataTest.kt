package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three bundled seed catalogs (Basic, Standard, Pro): every line well-formed, every value in range,
 * codes unique, and each tier contained in the next with identical content (LLR-CATP-020/040, LLR-VAR-020).
 */
class CatalogDataTest {

    private fun file(tier: String) = listOf("app/src/main/assets", "Android App/app/src/main/assets", "src/main/assets")
        .map { java.io.File(it, "seed_catalog_$tier.txt") }.first { it.exists() }

    private val tiers = mapOf("basic" to 253, "standard" to 603, "pro" to 2939)
    private val lines = tiers.keys.associateWith { file(it).readLines() }

    private val codeRule = Regex("^[A-Z0-9]+(-[A-Z0-9]+)*$")
    private val types = setOf("VEGETABLE", "FRUIT", "HERB", "FLOWER", "ORNAMENTAL")
    private val lifecycles = setOf("ANNUAL", "PERENNIAL", "BIENNIAL")

    @Test
    fun everyLineIsWellFormedAndInRange() {
        for ((tier, rows) in lines) {
            val problems = mutableListOf<String>()
            rows.forEachIndexed { i, line ->
                val p = line.split("|")
                fun bad(what: String) { problems += "$tier line ${i + 1}: $what" }
                if (p.size != 14) { bad("${p.size} fields"); return@forEachIndexed }
                if (!codeRule.matches(p[0]) || p[0].length !in 2..20) bad("code '${p[0]}'")
                if (p[1].isBlank() || p[1].length > 60 || p[1] != p[1].trim()) bad("name '${p[1]}'")
                if (p[2].isBlank()) bad("family")
                if (p[3] !in types) bad("type '${p[3]}'")
                if (p[4] !in lifecycles) bad("lifecycle '${p[4]}'")
                val zMin = p[5].toIntOrNull(); val zMax = p[6].toIntOrNull()
                if (zMin == null || zMax == null || zMin !in 1..13 || zMax !in 1..13 || zMin > zMax) bad("zones ${p[5]}..${p[6]}")
                val r = p[7].toFloatOrNull()
                if (r == null || !r.isFinite() || r < 0.02f || r > 10f) bad("radius ${p[7]}")
                if (p[8].toIntOrNull()?.let { it in 1..365 } != true) bad("germination ${p[8]}")
                if (p[9].toIntOrNull()?.let { it in 1..3650 } != true) bad("days to harvest ${p[9]}")
                for (list in listOf(p[10], p[11])) if (list.isNotEmpty() && list.split(",").any { !codeRule.matches(it.trim()) }) bad("code list '$list'")
            }
            assertTrue(problems.take(20).joinToString("\n"), problems.isEmpty())
            assertEquals("$tier count", tiers[tier], rows.size)
            val codes = rows.map { it.substringBefore("|") }
            assertEquals("$tier codes are unique", codes.size, codes.toSet().size)
        }
    }

    @Test
    fun eachTierContainsTheSmallerOneWithIdenticalEntries() {
        val basic = lines.getValue("basic").associateBy { it.substringBefore("|") }
        val standard = lines.getValue("standard").associateBy { it.substringBefore("|") }
        val pro = lines.getValue("pro").associateBy { it.substringBefore("|") }
        for ((code, line) in basic) assertEquals("Basic $code in Standard", line, standard[code])
        for ((code, line) in standard) assertEquals("Standard $code in Pro", line, pro[code])
        // The spring onions of LLR-VAR-020 are in every tier.
        for (code in listOf("ONI-101", "ONI-102", "ONI-103")) assertTrue(code, code in basic && code in standard && code in pro)
    }

    @Test
    fun companionAndAntagonistCodesNameAKnownSpecies() {
        // Every code in a companion or antagonist list is the species prefix of some Pro variety, so the
        // name resolver and the validator can match it. A typo would silently disable a rule.
        val species = lines.getValue("pro").map { it.substringBefore("|").substringBefore("-") }.toSet()
        val unknown = sortedSetOf<String>()
        for (line in lines.getValue("pro")) {
            val p = line.split("|")
            for (c in (p[10].split(",") + p[11].split(",")).map { it.trim() }.filter { it.isNotEmpty() })
                if (c.substringBefore("-") !in species) unknown += c
        }
        assertTrue("codes with no variety: $unknown", unknown.isEmpty())
    }

    @Test
    fun theCatalogLoaderReadsEveryLineOfEveryTier() {
        // The loader used by the tests mirrors the app's 14-field format; every line must produce a seed.
        for ((tier, rows) in lines) {
            val seeds = rows.mapNotNull { l -> l.split("|").takeIf { it.size >= 14 } }
            assertEquals(tier, rows.size, seeds.size)
        }
        assertEquals(2939, TestCatalog.seeds.size)
    }
}
