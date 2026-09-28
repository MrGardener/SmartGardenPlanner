package com.example.smartgardenplanner.core

/** A US ZIP code's approximate centre (FR-028, T2-FUN-090). */
data class ZipLocation(val zip: String, val latitude: Double, val longitude: Double, val state: String)

/**
 * Offline ZIP lookups over the one bundled ZIP table `assets/zip_data.txt`: one line per ZIP,
 * "zip|lat|lon|state|zone", sorted by ZIP, a field empty when its source has no value. Location: "zipcodes" package
 * by Dav Glass (BSD licence), from the free federalgovernmentzipcodes.us database; zone: 2023 USDA/PRISM ZIP tables.
 * See assets/NOTICE_zip_data.txt.
 */
object ZipTable {

    fun isValidZip(zip: String): Boolean = zip.length == 5 && zip.all { it.isDigit() }

    fun parseLine(line: String): ZipLocation? {
        val f = line.split("|")
        if (f.size < 3) return null
        val lat = f[1].toDoubleOrNull() ?: return null
        val lon = f[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return ZipLocation(f[0], lat, lon, f.getOrElse(3) { "" })
    }

    /** Binary search over lines sorted by their leading 5-digit ZIP. */
    fun find(sortedLines: List<String>, zip: String): ZipLocation? = findLine(sortedLines, zip)?.let { parseLine(it) }

    /**
     * Hardiness zone (2023 USDA/PRISM) from the ZIP table: the 5th field of "zip|lat|lon|state|zone" (or the 2nd of a
     * "zip|zone" line). Returns null when the ZIP isn't listed or the value isn't a valid zone.
     */
    fun findZone(sortedLines: List<String>, zip: String): String? =
        findLine(sortedLines, zip)?.split("|")?.let { f -> if (f.size >= 5) f[4] else f.getOrNull(1) }?.trim()?.takeIf { HardinessZones.number(it) != null && (it.endsWith("a") || it.endsWith("b")) }

    private fun findLine(sortedLines: List<String>, zip: String): String? {
        if (!isValidZip(zip)) return null
        var lo = 0
        var hi = sortedLines.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val key = sortedLines[mid].take(5)
            val cmp = key.compareTo(zip)
            when {
                cmp == 0 -> return sortedLines[mid]
                cmp < 0 -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return null
    }
}
