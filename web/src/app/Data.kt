package sgp.web

import com.example.smartgardenplanner.core.CropReference
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.ZipLocation
import com.example.smartgardenplanner.core.ZipTable
import kotlinx.browser.document
import org.w3c.dom.HTMLScriptElement

/** Reads the data blocks that build.sh embeds in the HTML file (catalog and ZIP tables). */
object Embedded {
    fun text(id: String): String = (document.getElementById(id) as? HTMLScriptElement)?.text ?: ""
    fun lines(id: String): List<String> = text(id).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * The seed catalog: the Android app's Pro catalog (a superset of Basic and Standard, 2,936 varieties), same line
 * format as data/SeedCatalogLoader.kt, plus custom varieties loaded from plan files.
 */
object Catalog {
    val seeds: MutableList<SeedEntity> by lazy { Embedded.lines("sgp-catalog").mapNotNull { parse(it) }.toMutableList() }
    val byCode: MutableMap<String, SeedEntity> by lazy { seeds.associateBy { it.botanicalCode }.toMutableMap() }
    val byName: Map<String, SeedEntity> by lazy { seeds.associateBy { it.commonName.lowercase() } }

    fun parse(line: String): SeedEntity? {
        val p = line.split("|")
        if (p.size < 14) return null
        return SeedEntity(
            botanicalCode = p[0], commonName = p[1], botanicalFamily = p[2], plantType = p[3], lifecycle = p[4],
            hardinessZoneMin = p[5].toIntOrNull() ?: 3, hardinessZoneMax = p[6].toIntOrNull() ?: 11,
            exclusionRadiusM = p[7].toFloatOrNull() ?: 0.3f, germinationDays = p[8].toIntOrNull() ?: 10,
            daysToHarvest = p[9].toIntOrNull() ?: 60, companionCodes = p[10], antagonistCodes = p[11],
            pestNotes = p[12], careNotes = p[13], isCustom = false
        )
    }

    fun addCustom(seed: SeedEntity) {
        if (seed.botanicalCode !in byCode) { seeds += seed; byCode[seed.botanicalCode] = seed }
    }

    fun get(code: String): SeedEntity? = byCode[code]

    val types: List<String> by lazy { seeds.map { it.plantType }.distinct().sorted() }

    fun search(query: String, type: String?, limit: Int = 150): List<SeedEntity> {
        val q = query.trim().lowercase()
        return seeds.asSequence()
            .filter { type == null || it.plantType == type }
            .filter { q.isEmpty() || it.commonName.lowercase().contains(q) || it.botanicalFamily.lowercase().contains(q) }
            .sortedBy { it.commonName }
            .take(limit).toList()
    }
}

/** Offline ZIP lookups from the same bundled tables as the Android app. */
object Zips {
    private val zones: List<String> by lazy { Embedded.lines("sgp-zipzones") }
    private val locations: List<String> by lazy { Embedded.lines("sgp-ziplocs") }
    fun zone(zip: String): String? = ZipTable.findZone(zones, zip.trim())
    fun location(zip: String): ZipLocation? = ZipTable.find(locations, zip.trim())
}

/** A stable colour per species (or the variety's own colour), readable on the light layout background. */
object Colors {
    fun of(seed: SeedEntity?): String {
        if (seed == null) return "#9ca3af"
        seed.colorHex?.let { return it }
        val key = CropReference.speciesKey(seed)
        var hash = 0
        for (c in key) hash = (hash * 31 + c.code) and 0x7fffffff
        val hue = when (seed.plantType) {
            "FLOWER" -> 280 + hash % 80
            "HERB" -> 70 + hash % 60
            "FRUIT" -> (330 + hash % 70) % 360
            "ORNAMENTAL" -> 170 + hash % 50
            else -> hash % 360
        }
        return "hsl(${hue % 360}, 75%, 40%)"
    }
}
