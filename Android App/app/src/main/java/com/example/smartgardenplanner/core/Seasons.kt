package com.example.smartgardenplanner.core

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.math.sqrt

/**
 * One plant from a finished season (FR-033, schema 10). When the gardener starts a new season on a plot, its plants
 * move here and the plot keeps its fence, buildings, trees, paths, areas and outline. The variety name, family and
 * rotation group are copied so the record stays readable even if the variety later leaves the catalog.
 */
@Entity(
    tableName = "planting_history",
    foreignKeys = [ForeignKey(entity = PlotEntity::class, parentColumns = ["id"], childColumns = ["plotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plotId")]
)
data class PlantingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plotId: Long,
    val seasonYear: Int,
    val seedCode: String,
    val varietyName: String,
    val family: String,
    val rotationGroup: String?,
    val coordinateXM: Float,
    val coordinateYM: Float,
    val radiusM: Float,
    val datePlantedEpochMillis: Long
) {
    val group: RotationGroup? get() = rotationGroup?.let { RotationGroup.of(it) }
    val speciesName: String get() = varietyName.substringBefore(" - ").trim()
}

/**
 * Crop-rotation groups (FR-032). Plants of one group share pests, diseases and feeding habits, so a group should not
 * return to the same spot for [waitYears] years. [role] is the group's place in the usual four-course rotation
 * (legumes → leafy brassicas → fruiting crops → roots and onions → legumes).
 */
enum class RotationGroup(val label: String, val examples: String, val waitYears: Int, val role: Int, val why: String) {
    LEGUMES("Legumes", "beans, peas", 2, 0, "they add nitrogen to the soil"),
    BRASSICAS("Cabbage family", "cabbage, broccoli, kale, radish", 3, 1, "clubroot and cabbage pests build up in the soil"),
    NIGHTSHADES("Nightshades", "tomatoes, peppers, eggplant, potatoes", 3, 2, "blight, wilt and soil pests build up in the soil"),
    CUCURBITS("Squash family", "squash, cucumbers, melons", 2, 2, "powdery mildew and squash pests build up"),
    GRAINS("Corn and grains", "sweet corn", 2, 2, "they are heavy feeders"),
    ALLIUMS("Onion family", "onions, garlic, leeks", 3, 3, "white rot and onion fly build up in the soil"),
    ROOTS("Carrot family", "carrots, parsnips, celery, parsley", 3, 3, "carrot fly and root diseases build up"),
    BEETS("Beet and spinach family", "beets, chard, spinach", 2, 3, "leaf-spot diseases build up"),
    LETTUCE("Lettuce family", "lettuce, endive", 1, 1, "they are light feeders; a short break is enough");

    companion object {
        fun of(name: String): RotationGroup? = entries.firstOrNull { it.name == name }

        private val FAMILIES = mapOf(
            "fabaceae" to LEGUMES, "leguminosae" to LEGUMES, "brassicaceae" to BRASSICAS, "cruciferae" to BRASSICAS,
            "solanaceae" to NIGHTSHADES, "cucurbitaceae" to CUCURBITS, "poaceae" to GRAINS,
            "amaryllidaceae" to ALLIUMS, "alliaceae" to ALLIUMS, "apiaceae" to ROOTS, "umbelliferae" to ROOTS,
            "amaranthaceae" to BEETS, "chenopodiaceae" to BEETS, "asteraceae" to LETTUCE
        )

        /** Group of a variety, or null when rotation doesn't apply (perennials, flowers, ornamentals, most herbs). */
        fun forSeed(seed: SeedEntity): RotationGroup? {
            if (seed.lifecycle == "PERENNIAL") return null
            if (seed.plantType != "VEGETABLE" && seed.plantType != "FRUIT") return null
            return FAMILIES[seed.botanicalFamily.trim().lowercase()]
        }
    }
}

/** A past planting of the same group near a spot, [yearsAgo] seasons before the season being planned. */
data class RotationHit(val entry: PlantingHistoryEntity, val group: RotationGroup, val yearsAgo: Int)

object Seasons {

    /** Current calendar year on this device. */
    fun thisYear(): Int = CivilDate.year(PlatformClock.nowMillis())

    /** The season the current plants belong to: the year most of them were planted (this year if none). */
    fun seasonOf(nodes: List<PlantedNodeEntity>): Int =
        nodes.groupingBy { CivilDate.year(it.datePlantedEpochMillis) }.eachCount().maxByOrNull { it.value }?.key ?: thisYear()

    /**
     * The season being planned on a plot: the year its current plants were planted (this year when there are none),
     * but always after the last closed season, so closing 2026 in autumn and planning straight away plans 2027.
     */
    fun currentSeason(nodes: List<PlantedNodeEntity>, history: List<PlantingHistoryEntity>): Int {
        val base = if (nodes.isEmpty()) thisYear() else seasonOf(nodes)
        val lastClosed = history.maxOfOrNull { it.seasonYear } ?: return base
        return maxOf(base, lastClosed + 1)
    }

    /** History records for the plot's current plants, ready to store when the season is closed. */
    fun archive(plotId: Long, nodes: List<PlantedNodeEntity>, seasonYear: Int, seedLookup: (String) -> SeedEntity?): List<PlantingHistoryEntity> =
        nodes.map { n ->
            val seed = seedLookup(n.seedCode)
            PlantingHistoryEntity(
                plotId = plotId, seasonYear = seasonYear, seedCode = n.seedCode,
                varietyName = seed?.commonName ?: n.seedCode, family = seed?.botanicalFamily ?: "",
                rotationGroup = seed?.let { RotationGroup.forSeed(it)?.name },
                coordinateXM = n.coordinateXM, coordinateYM = n.coordinateYM,
                radiusM = seed?.exclusionRadiusM ?: 0.3f, datePlantedEpochMillis = n.datePlantedEpochMillis
            )
        }

    /** Seasons with history, newest first. */
    fun years(history: List<PlantingHistoryEntity>): List<Int> = history.map { it.seasonYear }.distinct().sortedDescending()

    /**
     * Varieties this gardener plants most often (FR-034), from every plot's current plants and history: one variety per
     * species (the most used one), most frequent species first.
     */
    fun usualVarieties(currentCodes: List<String>, historyCodes: List<String>, seedLookup: (String) -> SeedEntity?, limit: Int = 8): List<Pair<SeedEntity, Int>> {
        val seeds = (currentCodes + historyCodes).mapNotNull { seedLookup(it) }
        return seeds.groupBy { CropReference.speciesKey(it) }
            .map { (_, list) -> list.groupingBy { it.botanicalCode }.eachCount().maxByOrNull { it.value }!!.key.let { code -> list.first { it.botanicalCode == code } } to list.size }
            .sortedWith(compareByDescending<Pair<SeedEntity, Int>> { it.second }.thenBy { it.first.commonName })
            .take(limit)
    }
}

/** Crop-rotation checks and advice (FR-032). Pure Kotlin. */
object CropRotation {

    /**
     * How far a past plant's family "occupied" the soil: its own spacing, widened to at least 0.6 m, because a crop
     * grown as a group uses the whole patch between plants, not just the circles.
     */
    fun reach(h: PlantingHistoryEntity): Float = maxOf(h.radiusM * 1.5f, 0.6f)

    /** The most recent planting of [seed]'s group that overlapped a circle at (x, y), within the group's wait. */
    fun conflict(x: Float, y: Float, seed: SeedEntity, history: List<PlantingHistoryEntity>, seasonYear: Int): RotationHit? {
        val group = RotationGroup.forSeed(seed) ?: return null
        return history.asSequence()
            .filter { it.group == group }
            .map { it to seasonYear - it.seasonYear }
            .filter { (_, ago) -> ago in 1..group.waitYears }
            .filter { (h, _) -> dist(x, y, h.coordinateXM, h.coordinateYM) < reach(h) + seed.exclusionRadiusM * 0.5f }
            .minByOrNull { it.second }
            ?.let { (h, ago) -> RotationHit(h, group, ago) }
    }

    /** The group that should follow [previous] in the four-course rotation. */
    fun successor(previous: RotationGroup): RotationGroup = when (previous.role) {
        0 -> RotationGroup.BRASSICAS
        1 -> RotationGroup.NIGHTSHADES
        2 -> RotationGroup.ALLIUMS
        else -> RotationGroup.LEGUMES
    }

    /** One-line warning for a placement, or null. */
    fun warning(hit: RotationHit, speciesName: String): String =
        "$speciesName (${hit.group.label.lowercase()}) grew here ${if (hit.yearsAgo == 1) "last season" else "${hit.yearsAgo} seasons ago"} " +
            "(${hit.entry.speciesName}, ${hit.entry.seasonYear}). Wait ${hit.group.waitYears} years before the same family returns: ${hit.group.why}. " +
            "Better here: ${successor(hit.group).label.lowercase()} (${successor(hit.group).examples})."

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float { val dx = x1 - x2; val dy = y1 - y2; return sqrt(dx * dx + dy * dy) }

    /** Where on the plot a point is, in compass words, using the plot's orientation ("north-west corner", "middle"). */
    fun describeSpot(plot: PlotEntity, x: Float, y: Float): String {
        val (nx, ny) = SunlightEngine.sunDirectionInPlot(0.0, plot.northBearingDeg)
        val (ex, ey) = SunlightEngine.sunDirectionInPlot(90.0, plot.northBearingDeg)
        val cx = x - plot.lengthM / 2f; val cy = y - plot.widthM / 2f
        val half = (maxOf(plot.lengthM, plot.widthM) / 2f).coerceAtLeast(0.1f)
        val north = (cx * nx + cy * ny) / half
        val east = (cx * ex + cy * ey) / half
        val ns = when { north > 0.33 -> "north"; north < -0.33 -> "south"; else -> "" }
        val ew = when { east > 0.33 -> "east"; east < -0.33 -> "west"; else -> "" }
        return when {
            ns.isEmpty() && ew.isEmpty() -> "middle"
            ns.isEmpty() -> "$ew side"
            ew.isEmpty() -> "$ns side"
            else -> "$ns-$ew corner"
        }
    }

    /** True when a group's plants from one season form a long thin row rather than a clump. */
    fun isRow(points: List<PlotPoint>, plot: PlotEntity): Boolean {
        if (points.size < 4) return false
        val w = points.maxOf { it.x } - points.minOf { it.x }
        val h = points.maxOf { it.y } - points.minOf { it.y }
        val long = maxOf(w, h); val short = minOf(w, h)
        val plotSide = if (w >= h) plot.lengthM else plot.widthM
        return long >= plotSide * 0.6f && short <= long * 0.25f
    }

    /**
     * Plain-language rotation advice for the season being planned: what grew where in past seasons, where each group
     * should go next, current plants that break the rotation, and a note when last season used long rows.
     */
    fun advice(plot: PlotEntity, history: List<PlantingHistoryEntity>, current: List<PlantedNodeEntity>, seedLookup: (String) -> SeedEntity?, seasonYear: Int): List<String> {
        val out = mutableListOf<String>()
        val years = Seasons.years(history).filter { it < seasonYear }
        if (years.isEmpty()) {
            out += "No past seasons recorded for this plot yet. When this season ends, use “Start a new season”: the plants are kept as history and the fence, buildings, trees, paths and areas stay, so next year's plan can rotate crops."
            return out
        }
        val last = years.first()
        val lastGroups = history.filter { it.seasonYear == last && it.group != null }.groupBy { it.group!! }
        if (lastGroups.isNotEmpty()) {
            out += "$last: " + lastGroups.entries.joinToString("; ") { (g, list) ->
                val cx = list.map { it.coordinateXM }.average().toFloat(); val cy = list.map { it.coordinateYM }.average().toFloat()
                "${g.label.lowercase()} (${list.map { it.speciesName }.distinct().joinToString(", ")}) in the ${describeSpot(plot, cx, cy)}"
            } + "."
            lastGroups.forEach { (g, list) ->
                val cx = list.map { it.coordinateXM }.average().toFloat(); val cy = list.map { it.coordinateYM }.average().toFloat()
                val next = successor(g)
                out += "Where the ${g.label.lowercase()} were (${describeSpot(plot, cx, cy)}), plant ${next.label.lowercase()} next (${next.examples}). Keep ${g.label.lowercase()} out of that spot for ${g.waitYears} years: ${g.why}."
            }
            lastGroups.filter { (_, list) -> isRow(list.map { PlotPoint(it.coordinateXM, it.coordinateYM) }, plot) }.keys.forEach { g ->
                out += "In $last the ${g.label.lowercase()} ran in one long row. A row that long leaves no free strip for them this year without shading other plants; planting in clumps makes it easy to swap places each year."
            }
        }
        val clashes = current.mapNotNull { n -> seedLookup(n.seedCode)?.let { s -> conflict(n.coordinateXM, n.coordinateYM, s, history, seasonYear)?.let { CropReference.speciesName(s) to it } } }
        if (clashes.isNotEmpty()) {
            clashes.groupBy { it.first }.forEach { (species, hits) ->
                val h = hits.minBy { it.second.yearsAgo }.second
                out += "⚠ ${hits.size} $species plant${if (hits.size == 1) " is" else "s are"} where ${h.group.label.lowercase()} grew in ${h.entry.seasonYear}. Move ${if (hits.size == 1) "it" else "them"} if you can: ${h.group.why}."
            }
        } else if (current.isNotEmpty()) {
            out += "✓ This season's plants respect the rotation."
        }
        if (years.size > 1) out += "History kept for ${years.size} seasons (${years.joinToString(", ")})."
        return out
    }
}
