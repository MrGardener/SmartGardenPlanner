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
    fun zoneTable_isSortedWellFormedAndComplete() {
        val lines = asset("zip_zones.txt")
        assertEquals(40502, lines.size)
        assertTrue(lines.zipWithNext().all { (a, b) -> a.take(5) < b.take(5) })
        assertTrue(lines.all { Regex("^\\d{5}\\|(1[0-3]|[1-9])[ab]$").matches(it) })
    }

    @Test
    fun zoneTable_givesPublishedZones() {
        val lines = asset("zip_zones.txt")
        mapOf(
            "48201" to "6b", "48933" to "6a", "49938" to "4b", "49783" to "4b", "49855" to "5b",
            "10001" to "7b", "90210" to "10b", "33101" to "11a", "99501" to "5a", "96813" to "12b", "00901" to "13b"
        ).forEach { (zip, zone) -> assertEquals(zip, zone, ZipTable.findZone(lines, zip)) }
        assertNull(ZipTable.findZone(lines, "00000"))
        assertNull(ZipTable.findZone(lines, "4820"))
    }

    @Test
    fun locationTable_isSortedAndParses() {
        val lines = asset("zip_locations.txt")
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
}
