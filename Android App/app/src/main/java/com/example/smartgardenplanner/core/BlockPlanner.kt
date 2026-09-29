package com.example.smartgardenplanner.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How a crop grows before it bears (FR-035). Sprawling vines (melons, squash, pumpkins, cucumbers left on the ground,
 * sweet potatoes) run [runwayM] metres along the ground toward the light; climbers (pole beans, peas) go up a trellis.
 */
data class VineHabit(val runwayM: Float, val climber: Boolean)

object VineHabits {
    private val HABITS = mapOf(
        "watermelon" to VineHabit(2.0f, false), "melon" to VineHabit(1.5f, false), "pumpkin" to VineHabit(2.5f, false),
        "winter squash" to VineHabit(2.0f, false), "cucumber" to VineHabit(1.2f, false), "sweet potato" to VineHabit(1.5f, false),
        "luffa" to VineHabit(2.0f, false), "bottle gourd" to VineHabit(2.0f, false), "bitter melon" to VineHabit(1.5f, false),
        "summer squash mix" to VineHabit(0.8f, false),
        "pole bean" to VineHabit(0f, true), "yard-long bean" to VineHabit(0f, true), "pea" to VineHabit(0f, true)
    )

    fun of(seed: SeedEntity): VineHabit? = HABITS[CropReference.speciesKey(seed)]
}

/** Where a vine is expected to run (FR-035), for drawing an arrow on the proposal. */
data class GrowthGuide(val species: String, val area: List<PlotPoint>, val from: PlotPoint, val to: PlotPoint)

/**
 * "Organised clumps" for Plan an area for me (FR-035): each crop becomes a block of rows × columns at its own
 * spacing, e.g. 20 sweet corn = 4 rows of 5, 7 tomatoes = a row of 4 and a row of 3. Blocks are separated by a
 * walkway so every plant can be reached and watered with a hose. Tall blocks go to the side away from the midday
 * sun; climbers at the very back on a trellis; sprawling vines at the sunny edge with a free runway toward the sun,
 * so they don't grow into their neighbours looking for light. Every plant passes the normal placement rules. Pure
 * Kotlin.
 */
/** A clump arrangement: plants per row, back row first (FR-047). */
data class ClumpShape(val rows: List<Int>) {
    val count: Int get() = rows.sum()
    val label: String get() = ClumpShapes.label(rows)
}

/**
 * Tidy ways to arrange n plants in a clump (FR-047), so the gardener can choose before planting: 50 → 5 rows of 10,
 * 10 rows of 5, 7 rows of 7 + 1, 6 rows of 8 + 2… and nearby counts that make a neat rectangle (49 = 7 × 7).
 */
object ClumpShapes {

    fun label(rows: List<Int>): String {
        if (rows.isEmpty()) return "nothing"
        if (rows.size == 1) return "1 row of ${rows[0]}"
        if (rows.distinct().size == 1) return "${rows.size} rows of ${rows[0]}"
        val main = rows.first()
        val full = rows.takeWhile { it == main }
        val rest = rows.drop(full.size)
        return if (rest.size == 1 && full.size > 1) "${full.size} rows of $main + 1 row of ${rest[0]}" else "rows of ${rows.joinToString(" + ")}"
    }

    private fun aspect(rows: List<Int>): Double { val c = rows.max().toDouble(); val r = rows.size.toDouble(); return max(c, r) / kotlin.math.min(c, r) }

    /** The planner's default first, then exact rectangles and "k rows of c + a shorter row", squarest first, then one long row. */
    fun options(n: Int): List<ClumpShape> {
        if (n <= 0) return emptyList()
        val out = linkedSetOf(BlockPlanner.rowSizes(n))
        val exact = mutableListOf<List<Int>>(); val withRest = mutableListOf<List<Int>>()
        for (k in 2..n) {
            val c = n / k
            if (c < 1) break
            val rem = n - k * c
            if (rem == 0) exact += List(k) { c } else if (rem < c) withRest += List(k) { c } + rem
        }
        exact.filter { aspect(it) <= 5.0 }.sortedBy { aspect(it) }.forEach { out += it }
        withRest.filter { aspect(it) <= 3.0 }.sortedBy { aspect(it) }.take(4).forEach { out += it }
        out += listOf(n)
        return out.map { ClumpShape(it) }.take(10)
    }

    /** Counts within ±3 of [n] that make a neat near-square rectangle (at most 2 : 1), e.g. 50 → 48 (6 × 8), 49 (7 × 7). */
    fun nearbyTidy(n: Int): List<ClumpShape> =
        ((n - 3)..(n + 3)).filter { it > 1 && it != n }.mapNotNull { m ->
            (2..m).filter { k -> m % k == 0 && m / k >= k && (m / k).toDouble() / k <= 2.0 }.maxOrNull()?.let { k -> ClumpShape(List(k) { m / k }) }
        }
}

object BlockPlanner {

    const val WALKWAY_M = 0.45f

    /** Variant number for "Long rows": every crop in full-width rows, tallest at the back, walkways between crops. */
    const val LONG_ROWS = 10

    /** Rows and columns for [n] plants: as square as possible with no more rows than columns. */
    fun shape(n: Int): Pair<Int, Int> {
        val rows = max(1, floor(sqrt(n.toDouble())).toInt())
        return rows to ceil(n / rows.toDouble()).toInt()
    }

    /** [n] plants in rows of at most [perRow] (full rows first): long rows across the area. */
    fun fullWidthRows(n: Int, perRow: Int): List<Int> {
        val c = perRow.coerceAtLeast(1)
        return List(n / c) { c } + listOfNotNull((n % c).takeIf { it > 0 })
    }

    /** Plants per row, back row first and the front row shortest: 7 → [4, 3]. */
    fun rowSizes(n: Int): List<Int> {
        val (rows, cols) = shape(n)
        val out = MutableList(rows) { cols }
        var extra = rows * cols - n
        var i = rows - 1
        while (extra > 0) { out[i]--; extra--; i = if (i == 0) rows - 1 else i - 1 }
        return out.filter { it > 0 }
    }

    private class Placed(val seed: SeedEntity, val x: Float, val y: Float, val r: Float)
    private class Rect(val u0: Float, val u1: Float, val v0: Float, val v1: Float) {
        fun contains(u: Float, v: Float, pad: Float = 0f) = u in (u0 - pad)..(u1 + pad) && v in (v0 - pad)..(v1 + pad)
    }

    fun plan(
        context: PlotContext,
        area: List<PlotPoint>,
        wanted: List<PlantRequest>,
        isBlocked: (x: Float, y: Float, radiusM: Float) -> Boolean,
        marginMultiplier: Float,
        orientationKnown: Boolean,
                history: List<PlantingHistoryEntity>,
        seasonYear: Int,
        strictRotation: Boolean = true,
        variant: Int = 0
    ): AutoPlanResult {
        val plot = context.plot
        // FR-060: variants give genuinely different layouts to choose from (0 = the suggested one):
        // 1/2 favour one side of the rows or the other, 3 changes the order crops choose in, 4 uses the second tidy
        // clump shape, 5 puts sun ahead of height, 6 = 1 + 3, 7 = 2 + 4, 8 spreads the clumps out, 9 keeps them close.
        val sideBias = when (variant) { 1, 6 -> 4.0; 2, 7 -> -4.0; else -> 0.0 }
        val spread = when (variant) { 8 -> 6.0; 9 -> -6.0; else -> 0.0 }
        val reorder = variant == 3 || variant == 6
        val altShape = variant == 4 || variant == 7
        val longRows = variant == LONG_ROWS
        val depthWeight = when { longRows -> 10.0; variant == 5 -> 1.0; else -> 4.0 }
        val notes = mutableListOf<String>()
        // Frame: v grows toward the back (away from the midday sun), u runs along the rows.
        val (sx, sy) = AutoPlanner.sunwardVector(context.latitude, plot.northBearingDeg)
        val lx = -sy; val ly = sx
        fun toU(x: Float, y: Float) = (x * lx + y * ly).toFloat()
        fun toV(x: Float, y: Float) = (-(x * sx + y * sy)).toFloat()
        fun toXY(u: Float, v: Float) = PlotPoint((u * lx - v * sx).toFloat(), (u * ly - v * sy).toFloat())
        val us = area.map { toU(it.x, it.y) }; val vs = area.map { toV(it.x, it.y) }
        val uMin = us.min(); val uMax = us.max(); val vMin = vs.min(); val vMax = vs.max()
        val vSpan = (vMax - vMin).coerceAtLeast(0.01f)
        val diag = sqrt(((uMax - uMin) * (uMax - uMin) + vSpan * vSpan).toDouble()).coerceAtLeast(0.1)

        val heights = wanted.associate { it.seed.botanicalCode to PlantHeights.heightM(it.seed) }
        val hMin = heights.values.min(); val hMax = heights.values.max()
        val isPollinator = { s: SeedEntity -> CropReference.speciesKey(s) in AutoPlanner.POLLINATOR_PLANTS }
        fun targetDepth(seed: SeedEntity): Float {
            val habit = VineHabits.of(seed)
            return when {
                habit?.climber == true -> 1f
                habit != null -> 0f
                hMax - hMin < 0.05f -> 0.5f
                else -> (heights.getValue(seed.botanicalCode) - hMin) / (hMax - hMin)
            }
        }
        // Order: climbers (back edge), sprawling vines (they claim the sunny edge and its runway), then the rest tallest
        // first, pollinator plants last.
        fun rank(r: PlantRequest): Int { val h = VineHabits.of(r.seed); return when { h?.climber == true -> 0; isPollinator(r.seed) -> 3; h != null -> 1; else -> 2 } }
        // FR-043: the plants marked most important go first, so they claim the sunniest spots that suit them.
        // FR-055: within each group, full-sun crops choose before part-shade and shade-tolerant ones.
        val sorted = wanted.sortedWith(compareBy<PlantRequest> { if (it.priority) 0 else 1 }.thenBy { rank(it) }
            .thenBy { CropReference.forSeed(it.seed).sun.ordinal }.thenByDescending { heights.getValue(it.seed.botanicalCode) })
        // Variant 3/6: within each group (priority, rank), the crops choose in reverse order.
        val order = if (!reorder) sorted else sorted.groupBy { (if (it.priority) 0 else 1) * 10 + rank(it) }.toList().sortedBy { it.first }.flatMap { it.second.reversed() }

        val sunMemo = HashMap<Long, Double?>()
        fun sunAt(x: Float, y: Float): Double? {
            val key = (floor(x / 0.25f).toLong() shl 32) xor (floor(y / 0.25f).toLong() and 0xffffffffL)
            return sunMemo.getOrPut(key) { context.sunHoursAt(x, y) }
        }
        val sunKnown = context.barriers.isNotEmpty() || context.areaFeatures.any { SiteFeatureType.of(it.featureType)?.let { t -> t == SiteFeatureType.FULL_SUN || t == SiteFeatureType.PART_SHADE || t == SiteFeatureType.FULL_SHADE } == true }
        val pastByGroup = history.filter { it.group != null && seasonYear - it.seasonYear in 1..it.group!!.waitYears }.groupBy { it.group!! }

        val plotOutline = PlotShape.effectiveOutline(plot)
        val walls = context.features.filter { SiteFeatureType.of(it.featureType)?.let { t -> t.isBarrier && t != SiteFeatureType.TREE } == true }
            .map { PlotGeometry.parsePoints(it.pointsJson) }.filter { it.size >= 2 }
        val existing = context.nodes.mapNotNull { n -> context.seedLookup(n.seedCode)?.let { Placed(it, n.coordinateXM, n.coordinateYM, it.exclusionRadiusM) } }
        val placedNodes = mutableListOf<PlantedNodeEntity>()
        val placed = mutableListOf<Placed>()
        val result = mutableListOf<PlannedPlant>()
        val unplaced = linkedMapOf<String, Int>()
        val reserved = mutableListOf<Rect>()   // vine runways, in frame coordinates
        val guides = mutableListOf<GrowthGuide>()
        val blockNotes = mutableListOf<String>()
        val insectCentres = mutableListOf<PlotPoint>()
        val validator = CompanionPlantingValidator()
                var rotationAvoided = 0; var rotationStuck = 0
        val relaxedFor = mutableSetOf<String>()
        val split = mutableListOf<String>()
        val reshaped = mutableListOf<String>()
        val blockedRunways = mutableListOf<String>()

        for (req in order) {
            val seed = req.seed
            val crop = CropReference.forSeed(seed)
            val key = CropReference.speciesKey(seed)
            val habit = VineHabits.of(seed)
            val r = seed.exclusionRadiusM
            val pitch = (2f * r * marginMultiplier).coerceAtLeast(0.05f) + 0.001f
            val target = targetDepth(seed)
            val group = RotationGroup.forSeed(seed)
            val past = group?.let { pastByGroup[it] }.orEmpty()
                        var remaining = req.count
            // FR-037: with strict rotation, no plant goes where its family (or, for plants without a family, the same
            // species) grew last season. Only if nothing else fits is the rule relaxed for the rest, and reported.
            var strictNow = strictRotation
            val lastSeason = history.filter { seasonYear - it.seasonYear == 1 && (if (group != null) it.group == group else it.speciesName.equals(CropReference.speciesName(seed), true)) }
            var blocks = 0
            // FR-058: spots that failed the full rule check (e.g. too near an antagonist such as corn for tomatoes) are
            // remembered, so the next search looks elsewhere instead of giving up.
            val rejected = HashSet<Long>()
            fun key(x: Float, y: Float) = (floor(x * 20f).toLong() shl 32) xor (floor(y * 20f).toLong() and 0xffffffffL)
            var failedTries = 0
            var wholeTries = 0
            val shapes = mutableListOf<List<Int>>()

            fun rotationPenalty(p: PlotPoint): Double {
                var worst = 0.0
                for (h in past) {
                    val dx = p.x - h.coordinateXM; val dy = p.y - h.coordinateYM
                    if (sqrt((dx * dx + dy * dy).toDouble()) < CropRotation.reach(h) + r * 0.5) {
                        worst = max(worst, 6.0 * (1.0 - (seasonYear - h.seasonYear - 1).toDouble() / group!!.waitYears))
                    }
                }
                return worst
            }
            // Cheap checks: inside, not blocked, clear of other plants (plus a walkway from other crops) and runways.
            fun cellOk(u: Float, v: Float): Boolean {
                val p = toXY(u, v)
                if (rejected.isNotEmpty() && key(p.x, p.y) in rejected) return false
                if (!PlotGeometry.pointInPolygon(p.x, p.y, area) || !PlotShape.contains(plot, p.x, p.y)) return false
                // At least half of the plant's spacing radius inside the area and the plot, so a plant never sits on the
                // edge or in a spot far too small for it (a squash in a 0.5 m bed).
                if (PlotGeometry.distanceToPolygonEdge(p.x, p.y, area) < r * 0.5f || PlotGeometry.distanceToPolygonEdge(p.x, p.y, plotOutline) < r * 0.5f) return false
                if (isBlocked(p.x, p.y, r)) return false
                if (context.floodZoneAt(p.x, p.y) != null && !crop.floodTolerant) return false
                // FR-059: a plant's whole circle stays out of every vine runway, not just its centre.
                                if (reserved.any { it.contains(u, v, r * 0.8f) }) return false
                if (strictNow && lastSeason.any { h -> val dx = p.x - h.coordinateXM; val dy = p.y - h.coordinateYM; dx * dx + dy * dy < (CropRotation.reach(h) + r * 0.5f).let { it * it } }) return false
                for (o in existing) {
                    val need = (r + o.r) * marginMultiplier
                    val dx = p.x - o.x; val dy = p.y - o.y
                    if (dx * dx + dy * dy < need * need) return false
                }
                for (o in placed) {
                    val gap = if (CropReference.speciesKey(o.seed) == key) 0f else WALKWAY_M
                    val need = (r + o.r) * marginMultiplier + gap
                    val dx = p.x - o.x; val dy = p.y - o.y
                    if (dx * dx + dy * dy < need * need) return false
                }
                return true
            }

            val step = max(pitch / 2f, sqrt(((uMax - uMin) * vSpan / 1200f).toDouble()).toFloat()).coerceAtLeast(0.05f)
            class Found(val score: Double, val pts: List<PlotPoint>, val u0: Float, val v0: Float, val rows: List<Int>, val runwayBlocked: Boolean)
            // Best position for a block of [rows] (plants per row, back row first), or null if no cell is free.
            fun search(rows: List<Int>): Found? {
                val cols = rows.max()
                val blockW = (cols - 1) * pitch
                val blockD = (rows.size - 1) * pitch
                var best: Found? = null
                var v0 = vMax - r * 0.5f
                while (v0 >= vMin - blockD) {
                    var u0 = uMin + r * 0.5f
                    while (u0 <= uMax) {
                        // Cells: back row first; each row centred on the block's width.
                        val cells = mutableListOf<Pair<Float, Float>>()
                        rows.forEachIndexed { ri, n -> val off = (cols - n) * pitch / 2f; for (ci in 0 until n) cells += (u0 + off + ci * pitch) to (v0 - ri * pitch) }
                        val ok = cells.filter { (u, v) -> cellOk(u, v) }
                        if (ok.isNotEmpty()) {
                            val pts = ok.map { (u, v) -> toXY(u, v) }
                            var score = ok.size * 10.0
                            var runwayBad = false
                            val meanDepth = ok.map { (_, v) -> (v - vMin) / vSpan }.average()
                            score -= abs(meanDepth - target) * depthWeight
                            if (spread != 0.0 && placed.isNotEmpty()) {
                                val c = PlotPoint(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
                                score += spread * placed.minOf { o -> sqrt(((o.x - c.x) * (o.x - c.x) + (o.y - c.y) * (o.y - c.y)).toDouble()) } / diag
                            }
                            if (sideBias != 0.0) score += sideBias * ((ok.map { (u, _) -> u.toDouble() }.average() - (uMin + uMax) / 2.0) / (uMax - uMin).toDouble().coerceAtLeast(0.1))
                            if (sunKnown) {
                                val need = crop.sun.minHours.toDouble()
                                // FR-055: a sun shortfall outweighs the tall-at-the-back preference, and sun lovers prefer
                                // the sunniest of the spots that are sunny enough.
                                score -= pts.map { p -> sunAt(p.x, p.y)?.let { h -> max(0.0, need - h) / need } ?: 0.0 }.average() * (if (req.priority) 20.0 else 10.0)
                                if (crop.sun == SunNeed.FULL || req.priority) score += pts.map { p -> sunAt(p.x, p.y) ?: 8.0 }.average() / (if (req.priority) 2.0 else 4.0)
                                // Part-shade and shade crops leave the sunniest ground to the sun lovers.
                                else score -= pts.map { p -> max(0.0, (sunAt(p.x, p.y) ?: 8.0) - (need + 3.0)) }.average() / 2.0
                            }
                            if (past.isNotEmpty()) score -= pts.map { rotationPenalty(it) }.average()
                            if (isPollinator(seed) && insectCentres.isNotEmpty()) {
                                val c = PlotPoint(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
                                score -= insectCentres.minOf { sqrt(((c.x - it.x) * (c.x - it.x) + (c.y - it.y) * (c.y - it.y)).toDouble()) } / diag * 4.0
                            }
                            if (habit != null && !habit.climber) {
                                // Runway toward the sun, in front of the block: it should be free, inside the plot and sunny.
                                val front = v0 - blockD - r
                                var good = 0; var bad = 0; var shade = 0
                                // Samples at most 25 cm apart over the whole runway (the block's width plus a plant radius each
                                // side, out to its full length), edges included.
                                val nu = ceil((blockW + 2 * r) / 0.25f).toInt().coerceAtLeast(1)
                                val nt = ceil(habit.runwayM / 0.25f).toInt().coerceAtLeast(1)
                                // Only plants near this runway can block it.
                                val corners = listOf(toXY(u0 - r, front), toXY(u0 + blockW + r, front), toXY(u0 - r, front - habit.runwayM), toXY(u0 + blockW + r, front - habit.runwayM))
                                val bx0 = corners.minOf { it.x }; val bx1 = corners.maxOf { it.x }; val by0 = corners.minOf { it.y }; val by1 = corners.maxOf { it.y }
                                val near = (existing + placed).filter { o -> o.x + o.r >= bx0 && o.x - o.r <= bx1 && o.y + o.r >= by0 && o.y - o.r <= by1 }
                                for (ti in 1..nt) {
                                    val t = habit.runwayM * ti / nt
                                    for (ui in 0..nu) {
                                        val uu = u0 - r + (blockW + 2 * r) * ui / nu
                                        val p = toXY(uu, front - t)
                                        val occupied = near.any { o -> (o.x - p.x) * (o.x - p.x) + (o.y - p.y) * (o.y - p.y) < o.r * o.r }
                                        val shaded = sunKnown && (sunAt(p.x, p.y) ?: 8.0) < 3.0
                                        // FR-059: the runway must stay inside the plot and clear of fences, walls and buildings.
                                        val walled = walls.any { w -> PlotGeometry.distanceToPolyline(p.x, p.y, w) < 0.4f }
                                        val otherRunway = reserved.any { it.contains(uu, front - t) }
                                        // Shade doesn't block a runway (vines still grow there); it only makes the spot less good.
                                        if (occupied || walled || otherRunway || !PlotShape.contains(plot, p.x, p.y) || !PlotGeometry.pointInPolygon(p.x, p.y, area)) bad++
                                        else { good++; if (shaded) shade++ }
                                    }
                                }
                                // FR-059: a runway must be completely free: over no plant (or another runway), inside the plot
                                // and clear of fences. Any blocked runway ranks below every free one, and is used only when no
                                // free runway exists (the proposal then says so).
                                if (good + bad > 0) {
                                    score += 3.0 * (good - shade) / (good + bad)
                                    if (bad > 0) { score -= 200.0 + 100.0 * bad / (good + bad); runwayBad = true }
                                }
                            }
                            if (best == null || score > best.score) best = Found(score, pts, u0, v0, rows, runwayBad)
                        }
                        u0 += step
                    }
                    v0 -= step
                }
                return best
            }
            val wantShape = req.shape?.takeIf { it.isNotEmpty() && it.all { n -> n > 0 } && it.sum() == req.count }

            while (remaining > 0) {
                // FR-047: the user's chosen arrangement first; otherwise the default, and before splitting a crop
                // into several groups, other tidy arrangements that keep it in one block.
                val preferred = if (blocks == 0 && wantShape != null) wantShape
                    else if (longRows) fullWidthRows(remaining, ((uMax - uMin - r) / pitch).toInt() + 1)
                    else if (altShape && remaining > 3) ClumpShapes.options(remaining).getOrNull(1)?.rows ?: rowSizes(remaining) else rowSizes(remaining)
                var best = search(preferred)
                if (!longRows && blocks == 0 && wantShape == null && (best == null || best.pts.size < remaining)) {
                    for (alt in ClumpShapes.options(remaining).take(6)) {
                        if (alt.rows == preferred) continue
                        val f = search(alt.rows) ?: continue
                        if (f.pts.size == remaining && (best == null || best.pts.size < remaining || f.score > best.score)) best = f
                    }
                    if (best != null && best.pts.size == remaining && best.rows != preferred) reshaped += "${CropReference.speciesName(seed)} (${ClumpShapes.label(best.rows)})"
                }
                val rows = best?.rows ?: preferred
                val cols = rows.max()
                val blockW = (cols - 1) * pitch
                val blockD = (rows.size - 1) * pitch
                                if (best == null) {
                    if (strictNow && lastSeason.isNotEmpty()) { strictNow = false; relaxedFor += CropReference.speciesName(seed); continue }
                    break
                }
                // Full rule check on the chosen block; keep the cells that pass.
                val kept = mutableListOf<PlotPoint>()
                val nodesBefore = placedNodes.size
                for (p in best.pts) {
                    val node = PlantedNodeEntity(plotId = plot.id, seedCode = seed.botanicalCode, coordinateXM = p.x, coordinateYM = p.y)
                    if (validator.validatePlacement(node, seed, context.nodes + placedNodes, context.seedLookup, marginMultiplier, context.enforceCompanionRules, context.guilds).isValid) {
                        kept += p; placedNodes += node
                    } else rejected += key(p.x, p.y)
                }
                if (kept.isEmpty()) { if (++failedTries < 25) continue else break }
                // A whole block that only partly passed: try again elsewhere (a few times) rather than split the crop.
                if (kept.size < best.pts.size && blocks == 0 && best.pts.size == remaining && wholeTries < 8) {
                    wholeTries++
                    while (placedNodes.size > nodesBefore) placedNodes.removeAt(placedNodes.lastIndex)
                    kept.forEach { p -> rejected += key(p.x, p.y) }
                    continue
                }
                kept.forEach { p ->
                    placed += Placed(seed, p.x, p.y, r); result += PlannedPlant(seed, p.x, p.y)
                    if (past.isNotEmpty()) { if (rotationPenalty(p) > 0.0) rotationStuck++ else rotationAvoided++ }
                }
                val bu = best.u0; val bv = best.v0
                if (habit != null && !habit.climber) {
                    if (best.runwayBlocked) blockedRunways += CropReference.speciesName(seed)
                    val front = bv - blockD - r
                    reserved += Rect(bu - r, bu + blockW + r, front - habit.runwayM, front)
                    val poly = listOf(toXY(bu - r, front), toXY(bu + blockW + r, front), toXY(bu + blockW + r, front - habit.runwayM), toXY(bu - r, front - habit.runwayM))
                    guides += GrowthGuide(CropReference.speciesName(seed), poly, toXY(bu + blockW / 2f, front), toXY(bu + blockW / 2f, front - habit.runwayM))
                }
                if (key in AutoPlanner.INSECT_POLLINATED) insectCentres += PlotPoint(kept.map { it.x }.average().toFloat(), kept.map { it.y }.average().toFloat())
                shapes += if (kept.size == rows.sum()) rows else rowSizes(kept.size)
                remaining -= kept.size
                blocks++
                if (kept.size < best.pts.size && blocks > 6) break
            }
            if (blocks > 1 && !longRows) {
                val sizes = shapes.map { it.sum() }
                split += "${CropReference.speciesName(seed)} is in $blocks groups (${sizes.joinToString(" + ")}): " +
                    (if (wantShape != null) "your arrangement (${ClumpShapes.label(wantShape)}) didn't fit in one piece of free ground" else "no single block of ${req.count} fitted in the free ground") +
                    ". To keep them together, choose another arrangement, a bigger area or fewer plants."
            }
            if (remaining > 0) unplaced[CropReference.speciesName(seed)] = (unplaced[CropReference.speciesName(seed)] ?: 0) + remaining
            val done = req.count - remaining
            if (done > 0) {
                val shapeText = shapes.joinToString(" + ") { ClumpShapes.label(it) }
                blockNotes += "${CropReference.speciesName(seed)}: $done in $shapeText, ${(pitch * 100).toInt()} cm apart"
            }
        }

        // --- Explain.
        val backName = if (context.latitude >= 0) "north" else "south"
        val sunName = if (context.latitude >= 0) "south" else "north"
        if (blockNotes.isNotEmpty()) notes += if (longRows)
            "Long rows: " + blockNotes.joinToString("; ") + ". Each crop has its own full-width rows with a ${(WALKWAY_M * 100).toInt()} cm walkway to the next crop."
        else "Organized clumps: " + blockNotes.joinToString("; ") + ". Each clump is in rows and columns with a ${(WALKWAY_M * 100).toInt()} cm walkway around it, so you can reach and hose every plant. Next year the clumps can swap places for crop rotation."
        if (longRows && order.size > 1) notes += "Crop rotation with long rows: tall rows must stay at the back so they don't shade the rest, so next year a row can only trade places with a crop of similar height. Organized clumps leave more room to rotate."
        val tall = order.filter { VineHabits.of(it.seed) == null && !isPollinator(it.seed) }.map { it.seed }.distinctBy { CropReference.speciesKey(it) }
        if (tall.size > 1) notes += "Taller clumps (${tall.take(2).joinToString(", ") { CropReference.speciesName(it) }}) are on the $backName side, shorter ones on the sunny side, so tall plants don't shade short ones."
        val climbers = order.filter { VineHabits.of(it.seed)?.climber == true }.map { CropReference.speciesName(it.seed) }.distinct()
        if (climbers.isNotEmpty()) notes += "${climbers.joinToString(", ")} climb: they are at the back ($backName) edge. Put up a trellis or poles there so they grow up, not over their neighbors."
        guides.groupBy { it.species }.forEach { (species, g) ->
            val habit = order.first { CropReference.speciesName(it.seed) == species }.seed.let { VineHabits.of(it) }
            notes += "$species vines run toward the sun: the clump is at the sunny ($sunName) edge with about ${habit?.runwayM?.fmt(1) ?: "1"} m kept free toward the $sunName (arrow on the plan). Guide the runners that way so they don't grow into other crops looking for light."
        }
        split.forEach { notes += it }
        if (blockedRunways.isNotEmpty()) notes += "There was no completely free ground for the runners of ${blockedRunways.distinct().joinToString(", ")}: part of the runway (arrow) is blocked. Choose a bigger area, fewer plants, or train the vines up a trellis."
        if (reshaped.isNotEmpty()) notes += "To keep each crop in one block, these got a different arrangement: ${reshaped.joinToString("; ")}."
                if (relaxedFor.isNotEmpty()) notes += "Not enough room to keep ${relaxedFor.joinToString(", ")} off last season's spots, so some went back where the same family grew last year. Try a bigger area or fewer plants."
        if (rotationAvoided > 0 || rotationStuck > 0) {
            notes += if (rotationStuck == 0) "Crop rotation: no crop was put where its family grew in the last seasons."
            else "Crop rotation: $rotationStuck plant(s) had to go where the same family grew recently (not enough other room). Consider a different area for them."
        }
        val important = wanted.filter { it.priority }.map { CropReference.speciesKey(it.seed) }.toSet()
        if (important.isNotEmpty()) {
            val mine = result.filter { CropReference.speciesKey(it.seed) in important }
            val names = mine.map { CropReference.speciesName(it.seed) }.distinct().joinToString(", ")
            if (mine.isNotEmpty()) notes += if (sunKnown) "Most important first: $names went in first, in the sunniest spots that suit them (about ${mine.map { sunAt(it.x, it.y) ?: 8.0 }.average().fmt(1)} hours of sun on average)."
            else "Most important first: $names went in first."
        }
        if (!orientationKnown) notes += "The plot's compass direction isn't set, so the top edge is assumed to face north. Set it for accurate sun placement."
        notes += if (sunKnown) "Full-sun crops got the sunniest spots, using your sun/shade areas and the shade from obstacles." else "No obstacles or sun/shade areas are marked, so the whole area is treated as full sun."
        if (wanted.any { CropReference.speciesKey(it.seed) in AutoPlanner.BLOCK_PLANTED }) notes += "Sweet corn (and other wind-pollinated grains) is a square-ish block, not a single row, so the pollen reaches every ear."
        val needsInsects = wanted.filter { CropReference.speciesKey(it.seed) in AutoPlanner.INSECT_POLLINATED }.map { CropReference.speciesName(it.seed) }.distinct()
        val helpers = wanted.filter { isPollinator(it.seed) }
        if (needsInsects.isNotEmpty()) {
            notes += if (helpers.isNotEmpty()) "Pollinator plants (${helpers.joinToString(", ") { CropReference.speciesName(it.seed) }}) are spread among the clumps of ${needsInsects.joinToString(", ")}, right next to them, to bring bees to their flowers."
            else "${needsInsects.joinToString(", ")} need bees to set fruit. Consider adding a few pollinator plants such as marigold, borage, basil or dill."
        }
        if (unplaced.isNotEmpty()) notes += "Didn't fit: " + unplaced.entries.joinToString(", ") { "${it.value} ${it.key}" } + ". Choose a larger area or fewer plants."
        return AutoPlanResult(result, unplaced, notes, guides)
    }
}
