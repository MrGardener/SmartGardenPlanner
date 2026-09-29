package com.example.smartgardenplanner.data

import android.content.Context
import com.example.smartgardenplanner.core.SeedEntity

/**
 * [NEW] Loads the tiered seed catalog (Basic/Standard/Pro) from bundled asset files instead of
 * a small hardcoded list. Catalog data lives in app/src/main/assets/ as compact pipe-delimited
 * text files rather than as Kotlin source — 600+ full SeedEntity object literals would be an
 * enormous, unwieldy source file; a flat data format keeps this maintainable and is the standard
 * Android pattern for bundled reference data.
 *
 * Line format (one variety per line, 14 fields):
 * botanicalCode|commonName|botanicalFamily|plantType|lifecycle|zoneMin|zoneMax|exclusionRadiusM|
 * germinationDays|daysToHarvest|companionCodes|antagonistCodes|pestNotes|careNotes
 *
 * Sourced from general horticultural knowledge — common species and their real, commercially
 * available named cultivars (e.g. "Tomato - Brandywine", "Tomato - Cherokee Purple") — using
 * Burpee's catalog as a reference point for realistic, widely-recognized variety naming, not as
 * a literal scraped/verified source. Treat spacing/germination/harvest figures as reasonable
 * horticultural estimates suitable for planning, not authoritative agronomic data — worth
 * spot-checking against a seed packet for anything you're relying on precisely.
 */
enum class CatalogTier(val assetFileName: String, val displayName: String, val varietyCount: Int) {
    BASIC("seed_catalog_basic.txt", "Basic (253 varieties)", 253),
    STANDARD("seed_catalog_standard.txt", "Standard (603 varieties)", 603),
    PRO("seed_catalog_pro.txt", "Pro (2,939 varieties)", 2939)
}

class SeedCatalogLoader(private val context: Context) {

    fun loadTier(tier: CatalogTier): List<SeedEntity> {
        val lines = context.assets.open(tier.assetFileName).bufferedReader().readLines()
        return lines.mapNotNull { line -> parseLine(line) }
    }

    private fun parseLine(line: String): SeedEntity? {
        if (line.isBlank()) return null
        val parts = line.split("|")
        if (parts.size < 14) return null

        return try {
            SeedEntity(
                botanicalCode = parts[0],
                commonName = parts[1],
                botanicalFamily = parts[2],
                plantType = parts[3],
                lifecycle = parts[4],
                hardinessZoneMin = parts[5].toIntOrNull() ?: 3,
                hardinessZoneMax = parts[6].toIntOrNull() ?: 11,
                exclusionRadiusM = parts[7].toFloatOrNull() ?: 0.3f,
                germinationDays = parts[8].toIntOrNull() ?: 10,
                daysToHarvest = parts[9].toIntOrNull() ?: 60,
                companionCodes = parts[10],
                antagonistCodes = parts[11],
                pestNotes = parts[12],
                careNotes = parts[13],
                isCustom = false // bundled catalog entry, safe to replace on a future tier switch
            )
        } catch (e: Exception) {
            null // skip malformed lines rather than fail the whole catalog load
        }
    }
}
