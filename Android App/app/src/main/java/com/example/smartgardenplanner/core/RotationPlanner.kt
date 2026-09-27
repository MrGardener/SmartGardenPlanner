package com.example.smartgardenplanner.core

/** One season of a multi-season rotation plan (FR-037). */
data class SeasonPlan(val year: Int, val result: AutoPlanResult, val summary: List<String>)

/**
 * Plans a plot for the next season, or for several seasons in a row (FR-037), so crops rotate: each season is planned
 * with "Plan an area for me" (organised clumps, strict rotation) using the real history plus the seasons planned before
 * it. The fixed features (fences, buildings, trees, paths, areas, outline) stay the same every year. Deterministic:
 * the same plot, list and history always give the same plan. Pure Kotlin.
 */
object RotationPlanner {

    /** The plant list of a season: one request per variety with its count, most plants first. */
    fun requestsFrom(nodes: List<PlantedNodeEntity>, seedLookup: (String) -> SeedEntity?): List<PlantRequest> =
        nodes.groupingBy { it.seedCode }.eachCount().entries
            .mapNotNull { (code, n) -> seedLookup(code)?.let { PlantRequest(it, n) } }
            .sortedByDescending { it.count }

    /** The list to re-plan from: this season's plants, else the last closed season's. */
    fun lastList(nodes: List<PlantedNodeEntity>, history: List<PlantingHistoryEntity>, seedLookup: (String) -> SeedEntity?): List<PlantRequest> {
        if (nodes.isNotEmpty()) return requestsFrom(nodes, seedLookup)
        val last = history.maxOfOrNull { it.seasonYear } ?: return emptyList()
        return history.filter { it.seasonYear == last }.groupingBy { it.seedCode }.eachCount().entries
            .mapNotNull { (code, n) -> seedLookup(code)?.let { PlantRequest(it, n) } }
            .sortedByDescending { it.count }
    }

    fun planSeasons(
        context: PlotContext,
        area: List<PlotPoint>,
        requests: List<PlantRequest>,
        history: List<PlantingHistoryEntity>,
        firstYear: Int,
        seasons: Int,
        isBlocked: (Float, Float, Float) -> Boolean = { _, _, _ -> false },
        marginMultiplier: Float = 1f,
        orientationKnown: Boolean = true
    ): List<SeasonPlan> {
        val out = mutableListOf<SeasonPlan>()
        var simulated = history
        val fresh = context.copy(nodes = emptyList())
        for (i in 0 until seasons.coerceIn(1, 10)) {
            val year = firstYear + i
            val result = AutoPlanner.plan(
                fresh, area, requests, isBlocked, marginMultiplier, orientationKnown,
                layout = PlantingLayout.CLUMPS, history = simulated, seasonYear = year, strictRotation = true
            )
            val nodes = result.placed.map { PlantedNodeEntity(plotId = context.plot.id, seedCode = it.seed.botanicalCode, coordinateXM = it.x, coordinateYM = it.y) }
            val archived = Seasons.archive(context.plot.id, nodes, year, context.seedLookup)
            out += SeasonPlan(year, result, summary(context.plot, archived))
            simulated = simulated + archived
        }
        return out
    }

    /** "Nightshades (Tomato, Pepper): north-west corner" per rotation family for one season. */
    fun summary(plot: PlotEntity, season: List<PlantingHistoryEntity>): List<String> =
        season.groupBy { it.group?.label ?: it.speciesName }.map { (label, list) ->
            val cx = list.map { it.coordinateXM }.average().toFloat(); val cy = list.map { it.coordinateYM }.average().toFloat()
            "$label (${list.map { it.speciesName }.distinct().joinToString(", ")}): ${CropRotation.describeSpot(plot, cx, cy)}"
        }.sorted()

    /**
     * "How many fit?" (FR-040): keeps the proportions of [requests] and scales them up to what the area holds as
     * organised clumps. Estimates from each crop's footprint (pitch² plus walkways), plans that, and returns the counts
     * that were actually placed.
     */
    fun howManyFit(context: PlotContext, area: List<PlotPoint>, requests: List<PlantRequest>, isBlocked: (Float, Float, Float) -> Boolean = { _, _, _ -> false }, marginMultiplier: Float = 1f): List<PlantRequest> {
        val wanted = requests.filter { it.count > 0 }
        if (wanted.isEmpty()) return requests
        val areaM2 = PlotGeometry.polygonArea(area).toDouble()
        val total = wanted.sumOf { it.count }.toDouble()
        val perUnit = wanted.sumOf { r ->
            val pitch = 2.0 * r.seed.exclusionRadiusM * marginMultiplier
            (r.count / total) * (pitch + BlockPlanner.WALKWAY_M / 3.0) * (pitch + BlockPlanner.WALKWAY_M / 3.0)
        }
        val k = areaM2 * 0.9 / perUnit
        val trial = wanted.map { PlantRequest(it.seed, kotlin.math.max(1, kotlin.math.ceil(k * it.count / total).toInt())) }
                // Plants already on the plot stay and take their room (pass a context without nodes to plan an empty season).
        val result = AutoPlanner.plan(context, area, trial, isBlocked, marginMultiplier, layout = PlantingLayout.CLUMPS, strictRotation = false)
        val placed = result.placed.groupingBy { it.seed.botanicalCode }.eachCount()
        return wanted.map { PlantRequest(it.seed, placed[it.seed.botanicalCode] ?: 0) }
    }
}
