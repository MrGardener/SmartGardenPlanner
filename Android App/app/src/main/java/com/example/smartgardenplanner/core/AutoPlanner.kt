package com.example.smartgardenplanner.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Mature plant heights (m), used to put tall plants behind short ones (FR-027). Climbing crops use their
 * trellised height. Species without an entry are estimated from their spacing and type.
 */
object PlantHeights {

    private val HEIGHTS: Map<String, Float> = mapOf(
        "sweet corn" to 2.2f, "sunflower" to 2.5f, "tithonia (mexican sunflower)" to 1.8f, "pole bean" to 2.0f,
        "yard-long bean" to 2.0f, "cucumber" to 1.6f, "luffa" to 2.0f, "bitter melon" to 1.8f, "bottle gourd" to 1.8f,
        "tomato" to 1.5f, "paste tomato" to 1.2f, "tomatillo" to 1.2f, "okra" to 1.8f, "pea" to 1.2f,
        "jerusalem artichoke" to 2.5f, "artichoke" to 1.3f, "cardoon" to 1.5f, "fennel" to 1.2f, "dill" to 1.0f,
        "asparagus" to 1.5f, "grain amaranth" to 1.8f, "quinoa" to 1.5f, "hollyhock" to 2.0f, "cosmos" to 1.2f,
        "brussels sprouts" to 0.9f, "eggplant" to 0.8f, "pepper" to 0.7f, "shishito type pepper" to 0.6f,
        "broccoli" to 0.7f, "cauliflower" to 0.6f, "kale" to 0.7f, "collard greens" to 0.8f, "cabbage" to 0.45f,
        "swiss chard" to 0.6f, "potato" to 0.7f, "sweet potato" to 0.4f, "zucchini" to 0.7f,
        "summer squash mix" to 0.7f, "winter squash" to 0.5f, "pumpkin" to 0.5f, "melon" to 0.35f,
        "watermelon" to 0.35f, "bush bean" to 0.5f, "edamame" to 0.7f, "lima bean" to 0.6f, "fava bean" to 1.0f,
        "celery" to 0.6f, "leek" to 0.6f, "garlic" to 0.5f, "onion" to 0.45f, "spring onion" to 0.35f, "shallot" to 0.35f, "chives" to 0.3f,
        "lettuce" to 0.25f, "spinach" to 0.25f, "arugula" to 0.3f, "radish" to 0.2f, "carrot" to 0.35f,
        "beet" to 0.35f, "turnip" to 0.35f, "parsnip" to 0.5f, "strawberry" to 0.2f, "alpine strawberry" to 0.2f,
        "basil" to 0.5f, "parsley" to 0.35f, "cilantro" to 0.5f, "thyme" to 0.25f, "oregano" to 0.4f,
        "sage" to 0.6f, "rosemary" to 1.0f, "lavender" to 0.6f, "mint" to 0.5f, "marigold" to 0.4f,
        "nasturtium" to 0.3f, "borage" to 0.7f, "calendula" to 0.5f, "zinnia" to 0.8f, "sweet alyssum" to 0.15f,
        "yarrow" to 0.7f, "coneflower" to 1.0f, "bee balm" to 1.0f, "raspberry" to 1.6f, "blackberry" to 1.8f,
        "blueberry" to 1.5f, "currant" to 1.3f, "gooseberry" to 1.2f, "elderberry" to 3.0f, "fig" to 3.5f,
        "apple" to 4.0f, "pear" to 4.5f, "peach" to 4.0f, "plum" to 4.0f, "cherry" to 5.0f
    )

    fun heightM(seed: SeedEntity): Float {
        HEIGHTS[CropReference.speciesKey(seed)]?.let { return it }
        val fromSpacing = seed.exclusionRadiusM * 2.2f
        return when (seed.plantType) {
            "FRUIT" -> if (seed.lifecycle == "PERENNIAL") max(2.5f, fromSpacing) else fromSpacing.coerceIn(0.2f, 1.0f)
            "ORNAMENTAL" -> fromSpacing.coerceIn(0.3f, 3.0f)
            else -> fromSpacing.coerceIn(0.15f, 1.5f)
        }
    }
}

/**
 * How "Plan an area for me" arranges each crop (FR-032). CLUMPS (default) keeps each crop in a compact group, which
 * is easy to move to another part of the plot next year (crop rotation). ROWS lines crops up by height.
 */
enum class PlantingLayout(val label: String, val description: String) {
    CLUMPS("Organised clumps (recommended)", "Each crop is a small block of rows and columns (e.g. 20 corn = 4 rows of 5) with walkways between blocks for watering with a hose. Vines get room to run toward the sun. Next year the blocks can swap places for crop rotation."),
    ROWS("Long rows", "Crops are lined up in long rows by height, tallest at the back. Tidy, but a long row of tomatoes at the back leaves no free place for them next year without shading other plants.")
}

/** Plants the user asked for (FR-027): a variety and how many. */
data class PlantRequest(val seed: SeedEntity, val count: Int)

data class PlannedPlant(val seed: SeedEntity, val x: Float, val y: Float)

data class AutoPlanResult(
    val placed: List<PlannedPlant>,
    /** Species name → number of plants that didn't fit. */
    val unplaced: Map<String, Int>,
    /** Plain-language explanation of what was done and why. */
    val notes: List<String>,
    /** Where vines are expected to run (FR-035), drawn as arrows on the proposal. */
    val guides: List<GrowthGuide> = emptyList()
)

/**
 * "Plan an area for me" (FR-027). Given an area and a list of varieties with counts, places the plants:
 *  - **Height:** tall plants go on the side away from the midday sun (north in the northern hemisphere,
 *    using the plot's orientation), short plants on the sunny side, so tall plants don't shade short ones.
 *  - **Sun:** plants that need full sun get the sunniest spots, using marked sun/shade areas and the shade
 *    cast by obstacles (trees, fences, walls, buildings).
 *  - **Pollination:** sweet corn is planted as a block rather than a line (it's wind-pollinated); pollinator
 *    flowers and herbs are spread among the crops that need insects to set fruit.
 *  - **Watering:** plants with similar watering needs are grouped, so one watering zone suits them all.
 *  - **Companions:** plants are placed next to their companions where possible; antagonists are kept apart,
 *    and every placement passes the normal spacing, path, outline and companion rules.
 *
 * The result is a proposal; nothing is saved until the caller stores it. Pure Kotlin.
 */
object AutoPlanner {

    /** Flowers and herbs that attract pollinators and beneficial insects. */
    val POLLINATOR_PLANTS = setOf(
        "marigold", "borage", "calendula", "nasturtium", "sunflower", "zinnia", "cosmos", "sweet alyssum",
        "dill", "basil", "lavender", "yarrow", "bee balm", "coneflower", "tithonia (mexican sunflower)",
        "oregano", "thyme", "chives", "cilantro", "phacelia", "wild bergamot", "catnip", "hyssop"
    )

    /** Crops that need insects to carry pollen between flowers to set fruit. */
    val INSECT_POLLINATED = setOf(
        "cucumber", "zucchini", "summer squash mix", "winter squash", "pumpkin", "melon", "watermelon",
        "tomato", "paste tomato", "pepper", "eggplant", "strawberry", "alpine strawberry", "tomatillo",
        "bitter melon", "bottle gourd", "luffa", "blueberry", "raspberry", "blackberry", "apple", "pear",
        "cherry", "plum", "peach", "okra"
    )

    /** Wind-pollinated crops that set seed or ears best in a compact block. */
    val BLOCK_PLANTED = setOf("sweet corn", "grain amaranth", "quinoa")

    private class Candidate(val x: Float, val y: Float, val depth: Float, val sunHours: Double?)

    private class Group(var sumX: Double = 0.0, var sumY: Double = 0.0, var n: Int = 0) {
        fun add(x: Float, y: Float) { sumX += x; sumY += y; n++ }
        fun dist(x: Float, y: Float): Double {
            if (n == 0) return 0.0
            val dx = x - sumX / n
            val dy = y - sumY / n
            return sqrt(dx * dx + dy * dy)
        }
    }

    /** Unit vector (plot coordinates) pointing toward the midday sun: south in the north, north in the south. */
    fun sunwardVector(latitudeDeg: Double, northBearingDeg: Float): Pair<Double, Double> =
        SunlightEngine.sunDirectionInPlot(if (latitudeDeg >= 0) 180.0 else 0.0, northBearingDeg)

    fun plan(
        context: PlotContext,
        area: List<PlotPoint>,
        requests: List<PlantRequest>,
        isBlocked: (x: Float, y: Float, radiusM: Float) -> Boolean = { _, _, _ -> false },
        marginMultiplier: Float = 1f,
        orientationKnown: Boolean = true,
        maxCandidates: Int = 3000,
        layout: PlantingLayout = PlantingLayout.CLUMPS,
        history: List<PlantingHistoryEntity> = emptyList(),
        seasonYear: Int = Seasons.thisYear()
    ): AutoPlanResult {
        val wanted = requests.filter { it.count > 0 }
        if (wanted.isEmpty() || area.size < 3) return AutoPlanResult(emptyList(), emptyMap(), listOf("Nothing to plan."))
        if (layout == PlantingLayout.CLUMPS) return BlockPlanner.plan(context, area, wanted, isBlocked, marginMultiplier, orientationKnown, history, seasonYear)
        val notes = mutableListOf<String>()
        val plot = context.plot

        // --- Geometry: which way is "back" (away from the midday sun)?
        val (sx, sy) = sunwardVector(context.latitude, plot.northBearingDeg)
        val minX = area.minOf { it.x }; val maxX = area.maxOf { it.x }
        val minY = area.minOf { it.y }; val maxY = area.maxOf { it.y }
        val diag = sqrt(((maxX - minX) * (maxX - minX) + (maxY - minY) * (maxY - minY)).toDouble()).coerceAtLeast(0.1)
        val projections = area.map { it.x * sx + it.y * sy }
        val pMin = projections.min(); val pMax = projections.max()
        fun depthOf(x: Float, y: Float): Float {
            val span = (pMax - pMin).takeIf { it > 1e-6 } ?: return 0.5f
            // 0 = sunny front edge, 1 = back edge (farthest from the sun).
            return ((pMax - (x * sx + y * sy)) / span).toFloat().coerceIn(0f, 1f)
        }

        // --- Candidate positions on a grid fine enough for the smallest plant.
        val minRadius = wanted.minOf { it.seed.exclusionRadiusM }.coerceAtLeast(0.05f)
        val bboxArea = ((maxX - minX) * (maxY - minY)).coerceAtLeast(0.0001f)
        val step = max(minRadius * 0.5f, sqrt(bboxArea / maxCandidates))
        val candidates = mutableListOf<Candidate>()
        var y = minY + step / 2f
        while (y <= maxY) {
            var x = minX + step / 2f
            while (x <= maxX) {
                if (PlotGeometry.pointInPolygon(x, y, area) && PlotShape.contains(plot, x, y)) {
                    candidates += Candidate(x, y, depthOf(x, y), context.sunHoursAt(x, y))
                }
                x += step
            }
            y += step
        }
        if (candidates.isEmpty()) return AutoPlanResult(emptyList(), wanted.associate { CropReference.speciesName(it.seed) to it.count }, listOf("The selected area is outside the plot."))
        val sunKnown = candidates.any { it.sunHours != null }

        // --- Height targets: tallest species at the back (depth 1), shortest at the front (depth 0).
        val heights = wanted.associate { it.seed.botanicalCode to PlantHeights.heightM(it.seed) }
        val hMin = heights.values.min(); val hMax = heights.values.max()
        fun targetDepth(seed: SeedEntity): Float =
            if (hMax - hMin < 0.05f) 0.5f else (heights.getValue(seed.botanicalCode) - hMin) / (hMax - hMin)

        // --- Order: crops tallest first (they claim the back), species together; pollinator plants last.
        val isPollinator = { s: SeedEntity -> CropReference.speciesKey(s) in POLLINATOR_PLANTS }
        val crops = wanted.filterNot { isPollinator(it.seed) }.sortedByDescending { heights.getValue(it.seed.botanicalCode) }
        val helpers = wanted.filter { isPollinator(it.seed) }
        val order = (crops + helpers).flatMap { r -> List(r.count) { r.seed } }

        val validator = CompanionPlantingValidator()
        val placedNodes = mutableListOf<PlantedNodeEntity>()
        val placed = mutableListOf<PlannedPlant>()
        val bySpecies = mutableMapOf<String, Group>()
        val byWater = mutableMapOf<Int, Group>()
        val insectCrops = Group()
        val pollinatorSpots = mutableListOf<Pair<Float, Float>>()
        val unplaced = linkedMapOf<String, Int>()
        val existing = context.nodes
        val clumps = layout == PlantingLayout.CLUMPS
        val depthWeight = if (clumps) 1.5 else 3.0
        var rotationAvoided = 0
        var rotationStuck = 0
        // Past plantings by rotation group, for the rotation penalty (FR-032).
        val pastByGroup = history.filter { it.group != null && seasonYear - it.seasonYear in 1..it.group!!.waitYears }.groupBy { it.group!! }

        for (seed in order) {
            val key = CropReference.speciesKey(seed)
            val crop = CropReference.forSeed(seed)
            val target = targetDepth(seed)
            val speciesGroup = bySpecies[key]
            val waterGroup = byWater[crop.waterIntervalDays]
            val block = key in BLOCK_PLANTED
            val helper = isPollinator(seed)
            val companionCentres = bySpecies.filterKeys { k -> k != key }
                .filter { (k, _) -> placed.firstOrNull { CropReference.speciesKey(it.seed) == k }?.let { Relationships.areCompanions(seed, it.seed) } == true }
                .values.toList()

            val rotGroup = RotationGroup.forSeed(seed)
            val past = rotGroup?.let { pastByGroup[it] }.orEmpty()
            fun rotationPenalty(c: Candidate): Double {
                var worst = 0.0
                for (h in past) {
                    val dx = c.x - h.coordinateXM; val dy = c.y - h.coordinateYM
                    if (sqrt((dx * dx + dy * dy).toDouble()) < CropRotation.reach(h) + seed.exclusionRadiusM * 0.5) {
                        val ago = seasonYear - h.seasonYear
                        worst = max(worst, 6.0 * (1.0 - (ago - 1).toDouble() / rotGroup!!.waitYears))
                    }
                }
                return worst
            }
            val otherClumps = if (clumps && (speciesGroup == null || speciesGroup.n == 0)) bySpecies.filterKeys { it != key }.values.toList() else emptyList()

            val scored = candidates.map { c ->
                var score = -abs(c.depth - target) * depthWeight
                if (past.isNotEmpty()) score -= rotationPenalty(c)
                // A new clump starts a little away from the other clumps, so each crop stays a distinct group.
                otherClumps.forEach { g -> score -= 1.0 / (1.0 + g.dist(c.x, c.y)) }
                if (c.sunHours != null) {
                    val need = crop.sun.minHours.toDouble()
                    score -= (max(0.0, need - c.sunHours) / need) * 4.0
                    score += c.sunHours / 24.0
                }
                if (speciesGroup != null && speciesGroup.n > 0) score -= speciesGroup.dist(c.x, c.y) / diag * (if (clumps) (if (block) 9.0 else 7.0) else (if (block) 5.0 else 2.0))
                if (waterGroup != null && waterGroup.n > 0) score -= waterGroup.dist(c.x, c.y) / diag
                companionCentres.forEach { g -> if (g.dist(c.x, c.y) < 2.0) score += 0.5 }
                if (helper) {
                    if (insectCrops.n > 0) score -= insectCrops.dist(c.x, c.y) / diag * 2.0
                    val nearest = pollinatorSpots.minOfOrNull { (px, py) -> sqrt(((c.x - px) * (c.x - px) + (c.y - py) * (c.y - py)).toDouble()) }
                    if (nearest != null) score -= 1.5 / (1.0 + nearest)
                }
                c to score
            }.sortedByDescending { it.second }

            var chosen: Candidate? = null
            for ((c, _) in scored) {
                if (isBlocked(c.x, c.y, seed.exclusionRadiusM)) continue
                if (context.floodZoneAt(c.x, c.y) != null && !crop.floodTolerant) continue
                val node = PlantedNodeEntity(plotId = plot.id, seedCode = seed.botanicalCode, coordinateXM = c.x, coordinateYM = c.y)
                val result = validator.validatePlacement(node, seed, existing + placedNodes, context.seedLookup, marginMultiplier, context.enforceCompanionRules, context.guilds)
                if (result.isValid) { chosen = c; placedNodes += node; break }
            }
            if (chosen == null) {
                unplaced[CropReference.speciesName(seed)] = (unplaced[CropReference.speciesName(seed)] ?: 0) + 1
                continue
            }
            placed += PlannedPlant(seed, chosen.x, chosen.y)
            if (past.isNotEmpty()) { if (rotationPenalty(chosen) > 0.0) rotationStuck++ else rotationAvoided++ }
            bySpecies.getOrPut(key) { Group() }.add(chosen.x, chosen.y)
            byWater.getOrPut(crop.waterIntervalDays) { Group() }.add(chosen.x, chosen.y)
            if (key in INSECT_POLLINATED) insectCrops.add(chosen.x, chosen.y)
            if (helper) pollinatorSpots += chosen.x to chosen.y
        }

        // --- Explain the result.
        val backBearing = ((if (context.latitude >= 0) 0f else 180f))
        val backName = if (backBearing == 0f) "north" else "south"
        val speciesByHeight = wanted.map { it.seed }.distinctBy { CropReference.speciesKey(it) }.sortedByDescending { heights.getValue(it.botanicalCode) }
        if (speciesByHeight.size > 1) {
            notes += "Tallest plants (${speciesByHeight.take(2).joinToString(", ") { CropReference.speciesName(it) }}) are on the $backName side and the shortest (${CropReference.speciesName(speciesByHeight.last())}) on the sunny side, so tall plants don't shade short ones."
        }
        notes += "Crops are in long rows by height. Rows are harder to rotate: next year the tall row has nowhere to go without shading the others. Organised clumps make rotation easier."
        if (rotationAvoided > 0 || rotationStuck > 0) {
            notes += if (rotationStuck == 0) "Crop rotation: no crop was put where its family grew in the last seasons."
            else "Crop rotation: $rotationStuck plant(s) had to go where the same family grew recently (not enough other room). Consider a different area for them."
        }
        if (!orientationKnown) notes += "The plot's compass direction isn't set, so the top edge is assumed to face north. Set it for accurate sun placement."
        notes += if (sunKnown) "Full-sun crops got the sunniest spots, using your sun/shade areas and the shade from obstacles." else "No obstacles or sun/shade areas are marked, so the whole area is treated as full sun."
        if (wanted.any { CropReference.speciesKey(it.seed) in BLOCK_PLANTED }) notes += "Sweet corn (and other wind-pollinated grains) is planted as a compact block, not a single row, so the pollen reaches every ear."
        val needsInsects = wanted.filter { CropReference.speciesKey(it.seed) in INSECT_POLLINATED }.map { CropReference.speciesName(it.seed) }.distinct()
        if (needsInsects.isNotEmpty()) {
            notes += if (helpers.isNotEmpty()) "Pollinator plants (${helpers.joinToString(", ") { CropReference.speciesName(it.seed) }}) are spread among ${needsInsects.joinToString(", ")} to bring bees to their flowers."
            else "${needsInsects.joinToString(", ")} need bees to set fruit. Consider adding a few pollinator plants such as marigold, borage, basil or dill."
        }
        val waterGroups = placed.groupBy { CropReference.forSeed(it.seed).waterIntervalDays }
        if (waterGroups.size > 1) {
            notes += "Plants with similar watering needs are grouped: " + waterGroups.entries.sortedBy { it.key }.joinToString("; ") { (days, list) ->
                "every $days day${if (days == 1) "" else "s"}: ${list.map { CropReference.speciesName(it.seed) }.distinct().joinToString(", ")}"
            } + "."
        }
        if (unplaced.isNotEmpty()) notes += "Didn't fit: " + unplaced.entries.joinToString(", ") { "${it.value} ${it.key}" } + ". Choose a larger area or fewer plants."
        return AutoPlanResult(placed, unplaced, notes)
    }
}
