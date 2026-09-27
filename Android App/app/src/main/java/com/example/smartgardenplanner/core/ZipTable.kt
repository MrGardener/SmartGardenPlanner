package com.example.smartgardenplanner.core

/** A US ZIP code's approximate centre (FR-028, T2-FUN-090). */
data class ZipLocation(val zip: String, val latitude: Double, val longitude: Double, val state: String)

/**
 * Offline ZIP → location lookup over the bundled table `assets/zip_locations.txt`: one line per ZIP,
 * "zip|lat|lon|state", sorted by ZIP. Data: "zipcodes" package by Dav Glass (BSD licence), from the free
 * federalgovernmentzipcodes.us database; see assets/NOTICE_zip_locations.txt.
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
     * Hardiness zone from the bundled 2023 USDA/PRISM table `assets/zip_zones.txt` ("zip|zone", sorted by ZIP;
     * see assets/NOTICE_zip_zones.txt). Returns null when the ZIP isn't listed or the value isn't a valid zone.
     */
    fun findZone(sortedLines: List<String>, zip: String): String? =
        findLine(sortedLines, zip)?.split("|")?.getOrNull(1)?.trim()?.takeIf { HardinessZones.number(it) != null && (it.endsWith("a") || it.endsWith("b")) }

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
