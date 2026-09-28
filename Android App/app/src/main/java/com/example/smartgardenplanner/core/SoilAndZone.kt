package com.example.smartgardenplanner.core

/** Soil composition entered by the user (FR-013). Any value may be missing. */
data class SoilProfile(
    val sandPct: Float?,
    val siltPct: Float?,
    val clayPct: Float?,
    val organicPct: Float?,
    val ph: Float?
) {
    val hasTexture: Boolean get() = sandPct != null && siltPct != null && clayPct != null
    val isEmpty: Boolean get() = !hasTexture && organicPct == null && ph == null

    companion object {
        fun of(plot: PlotEntity) = SoilProfile(plot.soilSandPct, plot.soilSiltPct, plot.soilClayPct, plot.soilOrganicPct, plot.soilPh)
    }
}

enum class SoilTexture(val label: String, val drainage: String) {
    SAND("Sand", "fast"), LOAMY_SAND("Loamy sand", "fast"), SANDY_LOAM("Sandy loam", "good"),
    LOAM("Loam", "good"), SILT_LOAM("Silt loam", "moderate"), SILT("Silt", "moderate"),
    SANDY_CLAY_LOAM("Sandy clay loam", "moderate"), CLAY_LOAM("Clay loam", "slow"),
    SILTY_CLAY_LOAM("Silty clay loam", "slow"), SANDY_CLAY("Sandy clay", "slow"),
    SILTY_CLAY("Silty clay", "slow"), CLAY("Clay", "very slow")
}

/** Soil texture classification and improvement guidance (FR-013). */
object SoilAnalyzer {

    /** Checks the three texture fractions add up to about 100 %. Returns an error message or null. */
    fun validateTexture(sand: Float, silt: Float, clay: Float): String? {
        if (!sand.isFinite() || !silt.isFinite() || !clay.isFinite()) return "Sand, silt and clay must be numbers."
        if (sand < 0f || silt < 0f || clay < 0f) return "Percentages can't be negative."
        val total = sand + silt + clay
        if (total < 97f || total > 103f) return "Sand, silt and clay should add up to 100 % (now ${(total).fmt(0)} %)."
        return null
    }

    /** USDA soil texture triangle. Fractions are normalised to 100 % first. */
    fun classify(sandIn: Float, siltIn: Float, clayIn: Float): SoilTexture {
        val total = (sandIn + siltIn + clayIn).takeIf { it > 0f } ?: 100f
        val sand = sandIn * 100f / total
        val silt = siltIn * 100f / total
        val clay = clayIn * 100f / total
        return when {
            clay >= 40f && silt >= 40f -> SoilTexture.SILTY_CLAY
            clay >= 40f && sand <= 45f -> SoilTexture.CLAY
            clay >= 35f && sand > 45f -> SoilTexture.SANDY_CLAY
            clay >= 27f && sand <= 20f -> SoilTexture.SILTY_CLAY_LOAM
            clay >= 27f && sand <= 45f -> SoilTexture.CLAY_LOAM
            clay >= 20f && silt < 28f && sand > 45f -> SoilTexture.SANDY_CLAY_LOAM
            silt >= 80f && clay < 12f -> SoilTexture.SILT
            silt >= 50f && (clay < 27f) -> SoilTexture.SILT_LOAM
            clay >= 7f && silt >= 28f && sand <= 52f -> SoilTexture.LOAM
            silt + 1.5f * clay < 15f -> SoilTexture.SAND
            silt + 2f * clay < 30f -> SoilTexture.LOAMY_SAND
            else -> SoilTexture.SANDY_LOAM
        }
    }

    /** Multiplier on watering intervals: sandy soils dry faster, clays hold water longer. */
    fun wateringFactor(soil: SoilProfile): Float {
        if (!soil.hasTexture) return 1f
        return when (classify(soil.sandPct!!, soil.siltPct!!, soil.clayPct!!).drainage) {
            "fast" -> 0.67f
            "good" -> 1f
            "moderate" -> 1.15f
            "slow" -> 1.33f
            else -> 1.5f
        }
    }

    /** Plain-language advice on improving the soil over time. */
    fun guidance(soil: SoilProfile): List<String> {
        val tips = mutableListOf<String>()
        if (soil.isEmpty) {
            tips += "No soil data yet. A home test kit or a lab test gives pH and organic matter. For texture, try a jar test: shake soil with water in a jar, let it settle for a day, and measure the sand (bottom), silt (middle) and clay (top) layers."
            return tips
        }
        if (soil.hasTexture) {
            val texture = classify(soil.sandPct!!, soil.siltPct!!, soil.clayPct!!)
            tips += "Texture: ${texture.label} (drainage ${texture.drainage})."
            when (texture.drainage) {
                "fast" -> tips += "Sandy soil drains fast and loses nutrients. Add 5–8 cm of compost each year and mulch to hold moisture; water more often in smaller amounts."
                "slow", "very slow" -> tips += "Heavy soil drains slowly and compacts. Add compost and coarse organic matter (leaf mold, bark fines), avoid working it when wet, and consider raised beds for crops that dislike wet feet."
                "moderate" -> tips += "Silty soil is fertile but crusts easily. Keep it covered with mulch and add compost to improve structure."
                else -> tips += "Good texture. Keep it that way with yearly compost and by not walking on the beds."
            }
        }
        soil.organicPct?.let { om ->
            tips += when {
                om < 2f -> "Organic matter is low (${(om).fmt(1)} %). Aim for 4–6 %: add compost every season, grow cover crops (clover, vetch, rye) over winter, and keep the soil mulched."
                om < 4f -> "Organic matter is moderate (${(om).fmt(1)} %). A yearly layer of compost and a winter cover crop will raise it."
                else -> "Organic matter is good (${(om).fmt(1)} %)."
            }
        }
        soil.ph?.let { ph ->
            tips += when {
                ph < 5.5f -> "Soil is strongly acidic (pH ${(ph).fmt(1)}). Most vegetables prefer 6.0–7.0: apply garden lime (roughly 250 g/m² raises pH about 0.5 in loam; less in sand, more in clay) and re-test after 3–6 months. Blueberries and azaleas like it as it is."
                ph < 6.0f -> "Soil is slightly acidic (pH ${(ph).fmt(1)}). A light liming (about 100–150 g/m²) suits most vegetables."
                ph <= 7.2f -> "pH ${(ph).fmt(1)} suits most vegetables."
                ph <= 7.8f -> "Soil is slightly alkaline (pH ${(ph).fmt(1)}). Add compost and consider elemental sulfur (about 50 g/m² lowers pH roughly 0.5 in loam) for acid-loving plants."
                else -> "Soil is strongly alkaline (pH ${(ph).fmt(1)}). Lowering it takes time: use elemental sulfur in small yearly doses, lots of organic matter, and choose tolerant crops (asparagus, beets, cabbage family)."
            }
        }
        return tips
    }

    /** True when the plot's pH is outside the crop's preferred range (with a 0.3 margin). */
    fun phMismatch(soil: SoilProfile, crop: CropInfo): Boolean {
        val ph = soil.ph ?: return false
        return ph < crop.phMin - 0.3f || ph > crop.phMax + 0.3f
    }
}

/** USDA hardiness zones (FR-014). */
object HardinessZones {

    /** All zone labels offered in the UI. */
    val LABELS: List<String> = (1..13).flatMap { listOf("${it}a", "${it}b") }

    private val LABEL = Regex("^(1[0-3]|[1-9])[ab]$")

    /** True only for a real zone label, "1a" … "13b". */
    fun isValid(zone: String?): Boolean = zone != null && LABEL.matches(zone)

    /** "7b" -> 7, "10a" -> 10. Returns null for anything else. */
    fun number(zone: String?): Int? {
        if (zone.isNullOrBlank()) return null
        val digits = zone.trim().takeWhile { it.isDigit() }
        return digits.toIntOrNull()?.takeIf { it in 1..13 }
    }

    /** True when a seed can survive the winter in the given zone. Unknown zone = assume yes. */
    fun isHardy(seed: SeedEntity, zone: String?): Boolean {
        val z = number(zone) ?: return true
        return z in seed.hardinessZoneMin..seed.hardinessZoneMax
    }

    /**
     * A perennial outside its zone range won't survive the winter, so placement is blocked.
     * Annuals are only warned about (they are grown for one season anyway).
     */
    fun blocksPlacement(seed: SeedEntity, zone: String?): Boolean =
        seed.lifecycle == "PERENNIAL" && !isHardy(seed, zone)

    fun describe(seed: SeedEntity, zone: String?): String? {
        if (isHardy(seed, zone)) return null
        return if (seed.lifecycle == "PERENNIAL") {
            "${seed.commonName} is a perennial for zones ${seed.hardinessZoneMin}–${seed.hardinessZoneMax}; it won't survive winters in zone $zone."
        } else {
            "${seed.commonName} is listed for zones ${seed.hardinessZoneMin}–${seed.hardinessZoneMax}; in zone $zone grow it as a short-season annual and check planting dates."
        }
    }
}
