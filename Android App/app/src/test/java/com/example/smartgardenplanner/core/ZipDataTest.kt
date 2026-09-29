package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the bundled ZIP data files themselves (FR-028, T2-FUN-090): they must be sorted (the lookup is a binary
 * search), well-formed, and give the published 2023 USDA zones for known ZIPs.
 * Gradle runs unit tests with the module directory (app/) as the working directory.
 */
class ZipDataTest {

    private fun asset(name: String): List<String> {
        val f = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name")).first { it.exists() }
        return f.readLines().filter { it.isNotBlank() }
    }

    @Test
    fun zipTable_isSortedWellFormedAndComplete() {
        val lines = asset("zip_data.txt")
        assertEquals(42277, lines.size)
        assertTrue(lines.zipWithNext().all { (a, b) -> a.take(5) < b.take(5) })
        // zip|lat|lon|state|zone, with the location fields or the zone empty where that source has no value.
        assertTrue(lines.all { Regex("^\\d{5}\\|(-?\\d+\\.\\d+\\|-?\\d+\\.\\d+\\|[A-Z]{2}|\\|\\|)\\|((1[0-3]|[1-9])[ab])?$").matches(it) })
        assertEquals(40502, lines.count { ZipTable.findZone(lines, it.take(5)) != null })
    }

    @Test
    fun zoneTable_givesPublishedZones() {
        val lines = asset("zip_data.txt")
        mapOf(
            "48201" to "6b", "48933" to "6a", "49938" to "4b", "49783" to "4b", "49855" to "5b",
            "10001" to "7b", "90210" to "10b", "33101" to "11a", "99501" to "5a", "96813" to "12b", "00901" to "13b"
        ).forEach { (zip, zone) -> assertEquals(zip, zone, ZipTable.findZone(lines, zip)) }
        assertNull(ZipTable.findZone(lines, "00000"))
        assertNull(ZipTable.findZone(lines, "4820"))
    }

    @Test
    fun locationTable_isSortedAndParses() {
        val lines = asset("zip_data.txt")
        assertTrue(lines.size > 40000)
        assertTrue(lines.zipWithNext().all { (a, b) -> a.take(5) < b.take(5) })
        val detroit = ZipTable.find(lines, "48201")!!
        assertEquals(42.35, detroit.latitude, 0.1)
        assertEquals(-83.06, detroit.longitude, 0.1)
    }

    @Test
    fun findZone_rejectsMalformedValues() {
        val lines = listOf("10001|7b", "10002|xx", "10003|14a")
        assertEquals("7b", ZipTable.findZone(lines, "10001"))
        assertNull(ZipTable.findZone(lines, "10002"))
        assertNull(ZipTable.findZone(lines, "10003"))
    }

    @Test
    fun zoneLabelsFollowTheZipTable() {
        // LLR-WIN-020: the ZIP table is the guide. Every zone in it is accepted, every accepted label occurs in it,
        // and bare numbers such as "7" (not in the table) are refused.
        val zones = asset("zip_data.txt").mapNotNull { it.split("|").getOrNull(4)?.takeIf { z -> z.isNotEmpty() } }
        val used = zones.toSet()
        assertTrue(zones.all { HardinessZones.isValid(it) })
        val all = (1..13).flatMap { n -> listOf("${n}a", "${n}b") }
        assertEquals("every label 1a..13b is in the table", all.toSet(), used)
        for (bare in (0..14).map { it.toString() } + listOf("0a", "14a", "7c", "7A", " 7a", "7a ", "07a", ""))
            assertTrue("'$bare' refused", !HardinessZones.isValid(bare))
        assertEquals(7, HardinessZones.number("7a")); assertEquals(null, HardinessZones.number("7"))
    }
}
