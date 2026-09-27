package com.example.smartgardenplanner.core

import kotlin.math.sqrt

/**
 * Everything the advisors need to know about one plot. Built by the UI from the database.
 * [seedLookup] resolves a botanicalCode to its seed.
 */
data class PlotContext(
    val plot: PlotEntity,
    val nodes: List<PlantedNodeEntity>,
    val features: List<SiteFeatureEntity>,
    val seedLookup: (String) -> SeedEntity?,
    val guilds: List<Guild> = emptyList(),
    val enforceCompanionRules: Boolean = true,
    val dayOfYear: Int = 172
) {
    val soil: SoilProfile get() = SoilProfile.of(plot)
    val zone: String? get() = plot.hardinessZone
    val latitude: Double get() = plot.latitude ?: SunlightEngine.DEFAULT_LATITUDE
    val barriers: List<Barrier> get() = features.mapNotNull { Barrier.from(it) }
    val areaFeatures: List<SiteFeatureEntity> get() = features.filter { SiteFeatureType.of(it.featureType)?.isArea == true }

    fun plantedSeeds(): List<SeedEntity> = nodes.mapNotNull { seedLookup(it.seedCode) }

    fun sunHoursAt(x: Float, y: Float): Double? =
        SunlightEngine.effectiveSunHours(x, y, areaFeatures, latitude, dayOfYear, plot.northBearingDeg, barriers)

    fun floodZoneAt(x: Float, y: Float): SiteFeatureEntity? = areaFeatures.firstOrNull {
        it.featureType == SiteFeatureType.FLOOD.name && PlotGeometry.pointInPolygon(x, y, PlotGeometry.parsePoints(it.pointsJson))
    }

    fun slopeAt(x: Float, y: Float): SiteFeatureEntity? = areaFeatures.firstOrNull {
        it.featureType == SiteFeatureType.SLOPE.name && PlotGeometry.pointInPolygon(x, y, PlotGeometry.parsePoints(it.pointsJson))
    }
}

/** Species-level relationship helpers, matching the validator's prefix rule. */
object Relationships {
    fun prefix(seed: SeedEntity): String = seed.botanicalCode.substringBefore("-")
    private fun codes(list: String): Set<String> = list.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun areAntagonists(a: SeedEntity, b: SeedEntity): Boolean =
        prefix(b) in codes(a.antagonistCodes) || prefix(a) in codes(b.antagonistCodes)

    fun areCompanions(a: SeedEntity, b: SeedEntity): Boolean =
        prefix(b) in codes(a.companionCodes) || prefix(a) in codes(b.companionCodes)

    fun companionPrefixes(seed: SeedEntity): Set<String> = codes(seed.companionCodes)
}

/** One recommended variety with the reasons behind it (FR-014 / FR-015). */
data class Recommendation(val seed: SeedEntity, val score: Int, val reasons: List<String>)

/**
 * Recommends varieties for a plot or an area of it (FR-014, FR-015), and explains why a variety is
 * greyed out in the picker (FR-010). Pure Kotlin.
 */
object RecommendationEngine {

    /**
     * Reason a variety conflicts with what's already on the plot, or null when it's fine (FR-010).
     * Covers antagonists present on the plot and perennials that won't survive the plot's zone.
     */
    fun conflictReason(seed: SeedEntity, context: PlotContext): String? {
        if (HardinessZones.blocksPlacement(seed, context.zone)) {
            return "Won't survive winters in zone ${context.zone}"
        }
        if (context.enforceCompanionRules) {
            val planted = context.plantedSeeds().distinctBy { Relationships.prefix(it) }
            val enemy = planted.firstOrNull { other ->
                Relationships.areAntagonists(seed, other) && GuildCatalog.sharedGuilds(seed, other, context.guilds).isEmpty()
            }
            if (enemy != null) return "Conflicts with ${CropReference.speciesName(enemy)} on this plot"
        }
        return null
    }

    /**
     * Ranks varieties for the given area (or the whole plot when [area] is null). Returns at most [limit]
     * results, one variety per species (the first cultivar in the catalog).
     */
    fun recommend(
        catalog: List<SeedEntity>,
        context: PlotContext,
        area: List<PlotPoint>? = null,
        limit: Int = 12,
        foodOnly: Boolean = false
    ): List<Recommendation> {
        val outline = area ?: PlotShape.effectiveOutline(context.plot)
        val cx = outline.map { it.x }.average().toFloat()
        val cy = outline.map { it.y }.average().toFloat()
        val sunHours = context.sunHoursAt(cx, cy)
        val flood = context.floodZoneAt(cx, cy)
        val soil = context.soil

        // Neighbours: plants inside the area or within 2 m of its outline.
        val neighbours = context.nodes.filter { n ->
            PlotGeometry.pointInPolygon(n.coordinateXM, n.coordinateYM, outline) ||
                PlotGeometry.distanceToPolygonEdge(n.coordinateXM, n.coordinateYM, outline) <= 2f
        }.mapNotNull { context.seedLookup(it.seedCode) }.distinctBy { Relationships.prefix(it) }
        val plotRoles = context.plantedSeeds().flatMap { CropReference.forSeed(it).roles }.toSet()

        val results = mutableListOf<Recommendation>()
        for ((_, cultivars) in catalog.groupBy { Relationships.prefix(it) }) {
            val seed = cultivars.first()
            val crop = CropReference.forSeed(seed)
            if (foodOnly && !crop.isFood) continue
            if (HardinessZones.blocksPlacement(seed, context.zone)) continue
            if (context.enforceCompanionRules && neighbours.any { Relationships.areAntagonists(seed, it) && GuildCatalog.sharedGuilds(seed, it, context.guilds).isEmpty() }) continue
            if (flood != null && !crop.floodTolerant) continue
            if (sunHours != null && sunHours < crop.sun.minHours - 0.5) continue

            var score = 0
            val reasons = mutableListOf<String>()
            if (crop.isFood) { score += 3 }
            val friends = neighbours.filter { Relationships.areCompanions(seed, it) }
            if (friends.isNotEmpty()) {
                score += 2 * friends.size
                reasons += "Companion of ${friends.joinToString(", ") { CropReference.speciesName(it) }}"
            }
            val guildMates = neighbours.filter { GuildCatalog.sharedGuilds(seed, it, context.guilds).isNotEmpty() }
            if (guildMates.isNotEmpty()) {
                score += 2
                reasons += "Guild partner of ${guildMates.joinToString(", ") { CropReference.speciesName(it) }}"
            }
            if (soil.ph != null) {
                if (SoilAnalyzer.phMismatch(soil, crop)) {
                    score -= 3
                } else {
                    score += 1
                    reasons += "Suits pH ${(soil.ph).fmt(1)}"
                }
            }
            if (context.zone != null) {
                if (HardinessZones.isHardy(seed, context.zone)) {
                    score += 1
                    reasons += "Hardy in zone ${context.zone}"
                } else {
                    score -= 1
                }
            }
            if (sunHours != null) {
                reasons += "Gets ~${(sunHours).fmt(1)} h sun (needs ${crop.sun.minHours.toInt()}+)"
                score += 1
            }
            if (flood != null) reasons += "Tolerates wet ground"
            val newRoles = crop.roles - plotRoles
            if (crop.isFood && newRoles.isNotEmpty()) {
                score += 2
                reasons += "Adds ${newRoles.joinToString(", ") { it.label.lowercase() }}"
            }
            if (crop.feeding == FeedingClass.NITROGEN_FIXER && neighbours.any { CropReference.forSeed(it).feeding == FeedingClass.HEAVY }) {
                score += 1
                reasons += "Feeds nitrogen to the heavy feeders nearby"
            }
            if (reasons.isEmpty()) reasons += "No conflicts with this spot"
            results += Recommendation(seed, score, reasons)
        }
        return results.sortedWith(compareByDescending<Recommendation> { it.score }.thenBy { it.seed.commonName }).take(limit)
    }
}

enum class Severity(val label: String) { HIGH("Fix"), MEDIUM("Check"), LOW("Note") }

data class HarmonyIssue(val severity: Severity, val text: String)

data class HarmonyReport(
    val plantCounts: List<Pair<String, Int>>,
    val issues: List<HarmonyIssue>,
    val goodPairs: List<String>,
    val recommendations: List<String>,
    val score: Int
)

/** Garden harmony report (FR-011): what's planted, what clashes, and what to do about it. */
object HarmonyAnalyzer {

    fun analyze(context: PlotContext, catalog: List<SeedEntity>, marginMultiplier: Float = 1f): HarmonyReport {
        val planted = context.nodes.mapNotNull { n -> context.seedLookup(n.seedCode)?.let { n to it } }
        val counts = planted.groupBy { CropReference.speciesName(it.second) }.map { it.key to it.value.size }.sortedByDescending { it.second }
        val issues = mutableListOf<HarmonyIssue>()
        val goodPairs = mutableSetOf<String>()
        val recommendations = mutableListOf<String>()

        // Pairwise: close antagonists and spacing overlaps.
        val closeAntagonists = mutableSetOf<Pair<String, String>>()
        var overlaps = 0
        for (i in planted.indices) {
            for (j in i + 1 until planted.size) {
                val (na, sa) = planted[i]
                val (nb, sb) = planted[j]
                val dx = (na.coordinateXM - nb.coordinateXM).toDouble()
                val dy = (na.coordinateYM - nb.coordinateYM).toDouble()
                val d = sqrt(dx * dx + dy * dy)
                val full = (sa.exclusionRadiusM + sb.exclusionRadiusM) * marginMultiplier.toDouble()
                val guild = GuildCatalog.sharedGuilds(sa, sb, context.guilds).isNotEmpty()
                val required = if (guild) maxOf(sa.exclusionRadiusM, sb.exclusionRadiusM) * marginMultiplier.toDouble() else full
                if (d < required - CompanionPlantingValidator.SPACING_TOLERANCE_M) overlaps++
                if (!guild && Relationships.areAntagonists(sa, sb) && d < full * 2.0) {
                    val names = listOf(CropReference.speciesName(sa), CropReference.speciesName(sb)).sorted()
                    closeAntagonists += names[0] to names[1]
                }
                if (Relationships.areCompanions(sa, sb) && d < full * 2.0) {
                    val names = listOf(CropReference.speciesName(sa), CropReference.speciesName(sb)).sorted()
                    goodPairs += "${names[0]} + ${names[1]}"
                }
            }
        }
        closeAntagonists.forEach { (a, b) ->
            issues += HarmonyIssue(Severity.HIGH, "$a and $b are planted close together but don't grow well side by side. Move one group further apart or replace it.")
        }
        if (overlaps > 0) {
            issues += HarmonyIssue(Severity.MEDIUM, "$overlaps pair${if (overlaps == 1) " of plants is" else "s of plants are"} closer than their spacing allows. Thin them out or move them.")
        }

        // Antagonists on the same plot but far apart: informational.
        val species = planted.map { it.second }.distinctBy { Relationships.prefix(it) }
        for (i in species.indices) for (j in i + 1 until species.size) {
            val a = species[i]
            val b = species[j]
            val names = listOf(CropReference.speciesName(a), CropReference.speciesName(b)).sorted()
            if (Relationships.areAntagonists(a, b) && (names[0] to names[1]) !in closeAntagonists &&
                GuildCatalog.sharedGuilds(a, b, context.guilds).isEmpty()
            ) {
                issues += HarmonyIssue(Severity.LOW, "${names[0]} and ${names[1]} are both on this plot. They're far enough apart now; keep them that way.")
            }
        }

        // Per-species site checks.
        val soil = context.soil
        for (seed in species) {
            val name = CropReference.speciesName(seed)
            val crop = CropReference.forSeed(seed)
            HardinessZones.describe(seed, context.zone)?.let {
                issues += HarmonyIssue(if (seed.lifecycle == "PERENNIAL") Severity.HIGH else Severity.LOW, it)
            }
            if (SoilAnalyzer.phMismatch(soil, crop)) {
                issues += HarmonyIssue(Severity.MEDIUM, "$name prefers pH ${crop.phMin}–${crop.phMax}; this plot is ${soil.ph?.fmt(1)}.")
            }
        }

        // Per-plant site checks, summarised per species.
        val outside = mutableMapOf<String, Int>()
        val flooded = mutableMapOf<String, Int>()
        val shaded = mutableMapOf<String, Int>()
        for ((node, seed) in planted) {
            val name = CropReference.speciesName(seed)
            val crop = CropReference.forSeed(seed)
            if (!PlotShape.contains(context.plot, node.coordinateXM, node.coordinateYM)) outside[name] = (outside[name] ?: 0) + 1
            if (context.floodZoneAt(node.coordinateXM, node.coordinateYM) != null && !crop.floodTolerant) flooded[name] = (flooded[name] ?: 0) + 1
            val sun = context.sunHoursAt(node.coordinateXM, node.coordinateYM)
            if (sun != null && sun < crop.sun.minHours - 0.5) shaded[name] = (shaded[name] ?: 0) + 1
        }
        outside.forEach { (n, c) -> issues += HarmonyIssue(Severity.HIGH, "$c $n plant${if (c == 1) " is" else "s are"} outside the plot outline.") }
        flooded.forEach { (n, c) -> issues += HarmonyIssue(Severity.MEDIUM, "$c $n plant${if (c == 1) " is" else "s are"} in an area that floods; $n doesn't like wet feet. Move it or plant in a raised bed.") }
        shaded.forEach { (n, c) -> issues += HarmonyIssue(Severity.MEDIUM, "$c $n plant${if (c == 1) " gets" else "s get"} less sun than $n needs. Move to a sunnier spot or pick a shade-tolerant crop.") }

        // Recommendations.
        val prefixToName = catalog.associate { Relationships.prefix(it) to CropReference.speciesName(it) }
        val presentPrefixes = species.map { Relationships.prefix(it) }.toSet()
        val companionIdeas = species.flatMap { Relationships.companionPrefixes(it) }
            .filter { it !in presentPrefixes }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            .mapNotNull { prefixToName[it.key] }.take(4)
        if (companionIdeas.isNotEmpty()) recommendations += "Add companions that help what you grow: ${companionIdeas.joinToString(", ")}."
        val heavy = species.count { CropReference.forSeed(it).feeding == FeedingClass.HEAVY }
        val fixers = species.count { CropReference.forSeed(it).feeding == FeedingClass.NITROGEN_FIXER }
        if (heavy >= 2 && fixers == 0) recommendations += "You have $heavy heavy feeders and no nitrogen fixers. Add peas or beans, or follow with a clover cover crop."
        if (closeAntagonists.isNotEmpty()) recommendations += "Use Move Mode (lock icon) to separate the clashing plants, or change their variety."
        if (context.zone == null) recommendations += "Set the plot's hardiness zone (Plot insights → Site) to check winter survival."
        if (soil.isEmpty) recommendations += "Enter a soil test (Plot insights → Site) to check pH and drainage."
        if (species.isEmpty()) recommendations += "Nothing planted yet. Try Recommend in the area tool to get suggestions for a spot."

        val high = issues.count { it.severity == Severity.HIGH }
        val medium = issues.count { it.severity == Severity.MEDIUM }
        val low = issues.count { it.severity == Severity.LOW }
        val score = (100 - 15 * high - 7 * medium - 2 * low).coerceIn(0, 100)
        return HarmonyReport(counts, issues.sortedBy { it.severity.ordinal }, goodPairs.sorted(), recommendations, score)
    }
}
