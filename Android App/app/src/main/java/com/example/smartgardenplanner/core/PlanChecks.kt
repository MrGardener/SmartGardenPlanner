package com.example.smartgardenplanner.core

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Checks shown BEFORE "Plan an area for me" / "Fill the whole plot" places anything (FR-043), so the user can change
 * the list, counts or which plants matter most before deciding. Pure Kotlin.
 */
data class PlanCheck(val severity: Severity, val text: String)

object PlanChecks {

    /** Sun hours sampled on a grid over the area (null entries = unknown). */
    private fun sunSamples(context: PlotContext, area: List<PlotPoint>, step: Float): List<Double?> {
        val out = mutableListOf<Double?>()
        val minX = area.minOf { it.x }; val maxX = area.maxOf { it.x }
        val minY = area.minOf { it.y }; val maxY = area.maxOf { it.y }
        var y = minY + step / 2f
        while (y <= maxY) {
            var x = minX + step / 2f
            while (x <= maxX) {
                if (PlotGeometry.pointInPolygon(x, y, area) && PlotShape.contains(context.plot, x, y)) out += context.sunHoursAt(x, y)
                x += step
            }
            y += step
        }
        return out
    }

    /** Ground a plant needs, m² (its spacing square plus a share of the walkway between clumps). */
    fun footprintM2(seed: SeedEntity, marginMultiplier: Float = 1f): Float {
        val pitch = 2f * seed.exclusionRadiusM * marginMultiplier
        val w = pitch + BlockPlanner.WALKWAY_M / 3f
        return w * w
    }

    fun check(
        context: PlotContext,
        area: List<PlotPoint>,
        requests: List<PlantRequest>,
        history: List<PlantingHistoryEntity> = emptyList(),
        seasonYear: Int = Seasons.thisYear(),
        pests: List<Pest> = emptyList(),
        marginMultiplier: Float = 1f
    ): List<PlanCheck> {
        val wanted = requests.filter { it.count > 0 }
        if (wanted.isEmpty() || area.size < 3) return emptyList()
        @Suppress("NAME_SHADOWING") val context = context.forPlanning()
        val out = mutableListOf<PlanCheck>()
        val areaM2 = PlotGeometry.polygonArea(area).toFloat()
        val step = max(0.25f, kotlin.math.sqrt(areaM2 / 400f))
        val cellM2 = step * step
        val samples = sunSamples(context, area, step)
        val sunKnown = context.barriers.isNotEmpty() || context.areaFeatures.any { SiteFeatureType.of(it.featureType)?.let { t -> t == SiteFeatureType.FULL_SUN || t == SiteFeatureType.PART_SHADE || t == SiteFeatureType.FULL_SHADE } == true }

        // Space.
        val need = wanted.sumOf { (footprintM2(it.seed, marginMultiplier) * it.count).toDouble() }.toFloat()
        val pct = if (areaM2 > 0f) (need / areaM2 * 100f).roundToInt() else 999
        out += when {
            pct > 110 -> PlanCheck(Severity.HIGH, "Space: these plants need about ${need.fmt(1)} m² with walkways, but the area is ${areaM2.fmt(1)} m² (${pct}%). Not all will fit: lower some counts, or mark the most important ones so they're placed first.")
            pct > 85 -> PlanCheck(Severity.MEDIUM, "Space: about ${pct}% of the area is needed (${need.fmt(1)} of ${areaM2.fmt(1)} m²). It's tight; a few plants may not fit.")
            else -> PlanCheck(Severity.LOW, "Space: about ${pct}% of the area is needed (${need.fmt(1)} of ${areaM2.fmt(1)} m²).")
        }

        // Sun: for each sun need, how much of the area gets enough light, compared with what those plants need.
        if (!sunKnown) {
            out += PlanCheck(Severity.MEDIUM, "Sun: no trees, fences, buildings or sun/shade areas are marked, so the whole area is treated as full sun. Draw them first for a real sun check.")
        } else if (samples.isNotEmpty()) {
            for (sun in SunNeed.entries) {
                val group = wanted.filter { CropReference.forSeed(it.seed).sun == sun }
                if (group.isEmpty()) continue
                val okM2 = samples.count { (it ?: 8.0) >= sun.minHours } * cellM2
                val needM2 = group.sumOf { (footprintM2(it.seed, marginMultiplier) * it.count).toDouble() }.toFloat()
                val names = group.map { CropReference.speciesName(it.seed) }.distinct()
                val list = names.take(4).joinToString(", ") + if (names.size > 4) "…" else ""
                if (okM2 + 0.01f < needM2) {
                    out += PlanCheck(
                        if (sun == SunNeed.FULL) Severity.HIGH else Severity.MEDIUM,
                        "Sun: $list need ${sun.label.lowercase()}, about ${needM2.fmt(1)} m², but only ${okM2.fmt(1)} m² of this area gets ${sun.minHours.toInt()}+ hours. Mark the ones that matter most so they get the sunniest spots; the rest may grow poorly."
                    )
                } else if (sun == SunNeed.FULL) {
                    out += PlanCheck(Severity.LOW, "Sun: ${okM2.fmt(1)} m² of this area gets 6+ hours, enough for $list (${needM2.fmt(1)} m²).")
                }
            }
        }

        // Neighbours that don't get along.
        val seeds = wanted.map { it.seed }.distinctBy { CropReference.speciesKey(it) }
        val clashes = mutableListOf<String>()
        for (i in seeds.indices) for (j in i + 1 until seeds.size) {
            if (Relationships.areAntagonists(seeds[i], seeds[j])) clashes += "${CropReference.speciesName(seeds[i])} and ${CropReference.speciesName(seeds[j])}"
        }
        if (clashes.isNotEmpty()) out += PlanCheck(Severity.MEDIUM, "Neighbors: ${clashes.take(3).joinToString("; ")} grow poorly together. They'll be kept apart, which uses more room.")

        // Hardiness.
        wanted.map { it.seed }.distinctBy { it.botanicalCode }.forEach { s ->
            HardinessZones.describe(s, context.zone)?.let { out += PlanCheck(if (HardinessZones.blocksPlacement(s, context.zone)) Severity.HIGH else Severity.LOW, "Zone: $it") }
        }

        // Rotation.
        val last = history.filter { seasonYear - it.seasonYear == 1 }
        if (last.isNotEmpty()) {
            val repeats = wanted.map { it.seed }.filter { s -> val g = RotationGroup.forSeed(s); last.any { h -> if (g != null) h.group == g else h.speciesName.equals(CropReference.speciesName(s), true) } }
                .map { CropReference.speciesName(it) }.distinct()
            if (repeats.isNotEmpty()) out += PlanCheck(Severity.LOW, "Rotation: ${repeats.take(4).joinToString(", ")} (or their family) grew here last season; they'll be kept off those spots where there's room.")
        }

        // Water.
        val intervals = wanted.map { CropReference.forSeed(it.seed).waterIntervalDays }.distinct()
        if (intervals.size > 1 && intervals.max() - intervals.min() >= 3) out += PlanCheck(Severity.LOW, "Water: the list mixes thirsty and drought-tolerant plants; they're grouped so each watering zone suits its plants.")

        // Pests the user said they see.
        PestAdvisor.risks(pests, wanted.map { it.seed }).filter { it.atRisk.isNotEmpty() }.forEach { r ->
            out += PlanCheck(Severity.MEDIUM, "Pests: ${r.pest.label.lowercase()} go for ${r.atRisk.take(4).joinToString(", ")}. ${r.pest.tips.first()}")
        }

        val priority = wanted.filter { it.priority }
        out += if (priority.isEmpty()) PlanCheck(Severity.LOW, "Most important: none marked. Mark the plants you care about most and they're placed first, in the sunniest spots.")
        else PlanCheck(Severity.LOW, "Most important: ${priority.map { CropReference.speciesName(it.seed) }.distinct().joinToString(", ")} will be placed first, in the sunniest spots that suit them.")
        return out.sortedBy { it.severity.ordinal }
    }
}

/** Watering recommendations for the Care section (FR-045). */
object WateringAdvice {

    /** Typical water need from the crop's watering interval. */
    fun needLabel(seed: SeedEntity): String = when (CropReference.forSeed(seed).waterIntervalDays) {
        in 0..1 -> "high: keep the soil moist, about 4 cm (1½ in) a week"
        2 -> "high: about 3–4 cm (1¼–1½ in) a week"
        3 -> "moderate: about 2.5 cm (1 in) a week"
        4 -> "moderate: about 2–2.5 cm (¾–1 in) a week"
        else -> "low: about 1–2 cm (½–¾ in) a week once established"
    }

    val GENERAL = listOf(
        "Most vegetables need about 2.5 cm (1 inch) of water a week from rain or watering, more in hot, windy weather and sandy soil.",
        "Water deeply and less often (soak the root zone 15–20 cm deep) rather than a little every day; roots grow deeper and plants cope better with heat.",
        "Water early in the morning at the base of the plants. Leaves then dry quickly, which reduces blight and mildew, and less water is lost to evaporation.",
        "Push a finger into the soil: water when the top 2–5 cm (1–2 in) is dry.",
        "A 5–8 cm (2–3 in) layer of mulch (straw, leaves, wood chips) keeps the soil moist and cool and cuts watering a lot.",
        "Seedlings and new transplants need gentle watering every day or two until they root in.",
        "A rain gauge, or a tuna can placed under the sprinkler, shows how much water actually fell."
    )

    val SYSTEMS = listOf(
        "Drip lines or soaker hoses put water at the roots and keep leaves dry: best for tomatoes, peppers, squash, cucumbers and potatoes, and they use the least water.",
        "Sprinklers cover big areas quickly and suit lawns, carrots, lettuce and dense plantings, but wet the leaves; run them early in the morning.",
        "A hose with a watering wand is fine for small gardens and containers; make sure it reaches every bed without dragging across plants.",
        "Group plants with similar needs in the same watering zone; the planner already does this when it plans an area.",
        "A simple timer on the tap makes watering regular and saves water; check it after heavy rain."
    )

    /** Plot-specific recommendations: coverage, gaps and the per-plant needs of what's planted. */
    fun forPlot(plot: PlotEntity, nodes: List<PlantedNodeEntity>, features: List<SiteFeatureEntity>, seedLookup: (String) -> SeedEntity?): List<String> {
        val out = mutableListOf<String>()
        val irrigation = features.filter { SiteFeatureType.of(it.featureType)?.isIrrigation == true }
        val pw = Irrigation.plants(plot, nodes, irrigation, seedLookup)
        val manual = pw.filter { it.source == WaterSource.MANUAL }
        if (irrigation.isEmpty() && nodes.isNotEmpty()) {
            out += "Nothing waters this plot automatically yet. Draw a sprinkler, drip line or hose tap (Irrigation) to see which plants it reaches and which need a watering can."
        }
        if (manual.isNotEmpty() && irrigation.isNotEmpty()) {
            val names = manual.mapNotNull { it.seed }.map { CropReference.speciesName(it) }.distinct()
            out += "${manual.size} plant${if (manual.size == 1) "" else "s"} (${names.take(4).joinToString(", ")}) aren't reached by any sprinkler, drip line or hose. A drip line along them, or moving a sprinkler, would cover them."
        }
        val thirsty = nodes.mapNotNull { seedLookup(it.seedCode) }.filter { CropReference.forSeed(it).waterIntervalDays <= 2 }.map { CropReference.speciesName(it) }.distinct()
        if (thirsty.isNotEmpty()) out += "Thirsty plants: ${thirsty.take(5).joinToString(", ")}. Check them first in hot weather; drip lines on a timer suit them well."
        val leafy = nodes.mapNotNull { seedLookup(it.seedCode) }.filter { CropReference.speciesKey(it) in Irrigation.LEAF_DISEASE_PRONE }.map { CropReference.speciesName(it) }.distinct()
        if (leafy.isNotEmpty() && irrigation.none { it.featureType == SiteFeatureType.DRIP_LINE.name }) out += "${leafy.take(4).joinToString(", ")} get leaf diseases when their leaves stay wet; a drip line or soaker hose is the best way to water them."
        return out
    }
}
