package com.example.smartgardenplanner.core

import kotlin.math.atan2
import kotlin.math.sqrt

/** How a spot on the plot gets water (FR-039), best first. */
enum class WaterSource(val label: String, val argb: Long) {
    DRIP("Drip line / soaker hose", 0x8C0EA5E9),
    SPRINKLER("Sprinkler", 0x663B82F6),
    HOSE("Hose (within reach)", 0x33818CF8),
    MANUAL("Watering can (out of reach)", 0x00000000)
}

/**
 * Irrigation coverage (FR-039): which parts of the plot, and which plants, a sprinkler, a drip line or a hose from a
 * tap reaches, and which need hand watering. A sprinkler wets a circle or an arc (compass bearing of its middle and
 * its width); a drip line or soaker hose wets a strip either side of it; a hose reaches anywhere within its length of
 * the tap (straight line; walls and beds in the way are not modelled). Pure Kotlin.
 */
object Irrigation {

    /** Crops whose leaves shouldn't be wetted every watering (blight, mildew): drip suits them better. */
    val LEAF_DISEASE_PRONE = setOf("tomato", "paste tomato", "potato", "cucumber", "zucchini", "summer squash mix", "winter squash", "pumpkin", "melon", "watermelon", "pepper", "eggplant")

    const val DEFAULT_THROW_M = 3f
    const val DEFAULT_DRIP_HALF_WIDTH_M = 0.3f
    const val DEFAULT_HOSE_M = 15f

    /** Compass bearing (0 = north) from (x0, y0) to (x, y) on a plot whose top edge faces [plotBearing]. */
    fun compassBearing(x0: Float, y0: Float, x: Float, y: Float, plotBearing: Float): Double {
        val (nx, ny) = SunlightEngine.sunDirectionInPlot(0.0, plotBearing)
        val (ex, ey) = SunlightEngine.sunDirectionInPlot(90.0, plotBearing)
        val dx = (x - x0).toDouble(); val dy = (y - y0).toDouble()
        val deg = atan2(dx * ex + dy * ey, dx * nx + dy * ny) * 180.0 / kotlin.math.PI
        return (deg + 360.0) % 360.0
    }

    fun sprinklerCovers(f: SiteFeatureEntity, x: Float, y: Float, plotBearing: Float): Boolean {
        val c = PlotGeometry.parsePoints(f.pointsJson).firstOrNull() ?: return false
        val r = if (f.radiusM > 0f) f.radiusM else DEFAULT_THROW_M
        val dx = x - c.x; val dy = y - c.y
        if (dx * dx + dy * dy > r * r) return false
        val arc = if (f.slopeGradePct <= 0f || f.slopeGradePct >= 360f) 360f else f.slopeGradePct
        if (arc >= 360f || (dx * dx + dy * dy) < 1e-6f) return true
        val b = compassBearing(c.x, c.y, x, y, plotBearing)
        var diff = kotlin.math.abs(b - f.slopeDirectionDeg) % 360.0
        if (diff > 180.0) diff = 360.0 - diff
        return diff <= arc / 2.0
    }

    fun dripCovers(f: SiteFeatureEntity, x: Float, y: Float): Boolean {
        val pts = PlotGeometry.parsePoints(f.pointsJson)
        if (pts.size < 2) return false
        return PlotGeometry.distanceToPolyline(x, y, pts) <= (if (f.radiusM > 0f) f.radiusM else DEFAULT_DRIP_HALF_WIDTH_M)
    }

    fun hoseReaches(f: SiteFeatureEntity, x: Float, y: Float): Boolean {
        val c = PlotGeometry.parsePoints(f.pointsJson).firstOrNull() ?: return false
        val len = if (f.radiusM > 0f) f.radiusM else DEFAULT_HOSE_M
        val dx = x - c.x; val dy = y - c.y
        return sqrt(dx * dx + dy * dy) <= len
    }

    /** The best water source at a spot. */
    fun sourceAt(x: Float, y: Float, features: List<SiteFeatureEntity>, plotBearing: Float): WaterSource {
        var best = WaterSource.MANUAL
        for (f in features) {
            val s = when (SiteFeatureType.of(f.featureType)) {
                SiteFeatureType.DRIP_LINE -> if (dripCovers(f, x, y)) WaterSource.DRIP else null
                SiteFeatureType.SPRINKLER -> if (sprinklerCovers(f, x, y, plotBearing)) WaterSource.SPRINKLER else null
                SiteFeatureType.HOSE_BIB -> if (hoseReaches(f, x, y)) WaterSource.HOSE else null
                else -> null
            } ?: continue
            if (s.ordinal < best.ordinal) best = s
        }
        return best
    }

    /** Coverage grid (cols × rows, row-major, cell centres) for the overlay. */
    fun grid(plot: PlotEntity, features: List<SiteFeatureEntity>, cols: Int, rows: Int): Array<WaterSource> =
        Array(cols * rows) { i ->
            val c = i % cols; val r = i / cols
            sourceAt((c + 0.5f) * plot.lengthM / cols, (r + 0.5f) * plot.widthM / rows, features, plot.northBearingDeg)
        }

    data class PlantWater(val node: PlantedNodeEntity, val seed: SeedEntity?, val source: WaterSource)

    fun plants(plot: PlotEntity, nodes: List<PlantedNodeEntity>, features: List<SiteFeatureEntity>, seedLookup: (String) -> SeedEntity?): List<PlantWater> =
        nodes.map { PlantWater(it, seedLookup(it.seedCode), sourceAt(it.coordinateXM, it.coordinateYM, features, plot.northBearingDeg)) }

    /** Plain-language summary: counts per source, plants needing hand watering, and leaf-wetting warnings. */
    fun report(plot: PlotEntity, nodes: List<PlantedNodeEntity>, features: List<SiteFeatureEntity>, seedLookup: (String) -> SeedEntity?): List<String> {
        val out = mutableListOf<String>()
        val irrigation = features.filter { SiteFeatureType.of(it.featureType)?.isIrrigation == true }
        if (irrigation.isEmpty()) {
            out += "No sprinklers, drip lines or hose taps are drawn yet. Add them (Irrigation tools) to see which plants they reach and which need a watering can."
            return out
        }
        if (nodes.isEmpty()) { out += "Nothing planted yet: the blue areas show where water will reach."; return out }
        val pw = plants(plot, nodes, irrigation, seedLookup)
        WaterSource.entries.forEach { s ->
            val list = pw.filter { it.source == s }
            if (list.isNotEmpty()) out += "${s.label}: ${list.size} plant${if (list.size == 1) "" else "s"} (" + list.groupBy { it.seed?.let { sd -> CropReference.speciesName(sd) } ?: it.node.seedCode }.entries.joinToString(", ") { "${it.value.size} ${it.key}" } + ")."
        }
        val manual = pw.count { it.source == WaterSource.MANUAL }
        out += if (manual == 0) "✓ Every plant is reached by a sprinkler, a drip line or a hose." else "⚠ $manual plant${if (manual == 1) "" else "s"} can only be watered by hand (circled on the layout). Move them, add a drip line, or use a longer hose."
        val wetLeaves = pw.filter { it.source == WaterSource.SPRINKLER && it.seed?.let { sd -> CropReference.speciesKey(sd) in LEAF_DISEASE_PRONE } == true }
        if (wetLeaves.isNotEmpty()) out += "Sprinklers wet the leaves of ${wetLeaves.map { CropReference.speciesName(it.seed!!) }.distinct().joinToString(", ")}, which spreads blight and mildew. A drip line along those plants, or watering early in the morning, is better."
        return out
    }
}
