package com.example.smartgardenplanner.core

import kotlin.math.abs
import kotlin.math.sqrt

/** A point in plot coordinates, in metres. x runs left to right, y runs top to bottom. */
data class PlotPoint(val x: Float, val y: Float)

/**
 * Shared geometry for plot outlines (FR-002), site zones (FR-003/004/005), barriers (FR-006) and
 * area selection. Pure Kotlin so it can be unit-tested on the JVM.
 */
object PlotGeometry {

    /** "x1,y1;x2,y2;..." <-> points (metres). Malformed pairs are skipped. */
    fun parsePoints(text: String?): List<PlotPoint> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(";").mapNotNull { pair ->
            val parts = pair.split(",")
            if (parts.size != 2) return@mapNotNull null
            val x = parts[0].trim().toFloatOrNull()
            val y = parts[1].trim().toFloatOrNull()
            if (x != null && y != null && x.isFinite() && y.isFinite()) PlotPoint(x, y) else null
        }
    }

    /** Smallest area an outline may enclose: 1 cm², below the smallest round plot (5 cm across, about 20 cm²). */
    const val MIN_OUTLINE_AREA_M2 = 0.0001f

    fun serializePoints(points: List<PlotPoint>): String = points.joinToString(";") { "${it.x},${it.y}" }

    /** Ray-casting point-in-polygon test. A polygon needs at least 3 points. */
    fun pointInPolygon(x: Float, y: Float, polygon: List<PlotPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.y > y) != (pj.y > y) &&
                x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Area of a simple polygon (shoelace formula), in square metres. */
    fun polygonArea(polygon: List<PlotPoint>): Float {
        if (polygon.size < 3) return 0f
        var sum = 0.0
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            sum += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return (abs(sum) / 2.0).toFloat()
    }

    /** Shortest distance from a point to the segment a-b. */
    fun distanceToSegment(x: Float, y: Float, a: PlotPoint, b: PlotPoint): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSq = abx * abx + aby * aby
        val t = if (lengthSq == 0f) 0f else (((x - a.x) * abx + (y - a.y) * aby) / lengthSq).coerceIn(0f, 1f)
        val dx = x - (a.x + t * abx)
        val dy = y - (a.y + t * aby)
        return sqrt(dx * dx + dy * dy)
    }

    /** Shortest distance from a point to an open polyline (or to the single point if only one). */
    fun distanceToPolyline(x: Float, y: Float, points: List<PlotPoint>): Float {
        if (points.isEmpty()) return Float.MAX_VALUE
        if (points.size == 1) {
            val dx = x - points[0].x
            val dy = y - points[0].y
            return sqrt(dx * dx + dy * dy)
        }
        var best = Float.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val d = distanceToSegment(x, y, points[i], points[i + 1])
            if (d < best) best = d
        }
        return best
    }

    /** Shortest distance from a point to the closed outline of a polygon. */
    fun distanceToPolygonEdge(x: Float, y: Float, polygon: List<PlotPoint>): Float {
        if (polygon.size < 2) return distanceToPolyline(x, y, polygon)
        var best = Float.MAX_VALUE
        for (i in polygon.indices) {
            val d = distanceToSegment(x, y, polygon[i], polygon[(i + 1) % polygon.size])
            if (d < best) best = d
        }
        return best
    }

    /** True when segments p1-p2 and q1-q2 cross or touch. */
    fun segmentsIntersect(p1: PlotPoint, p2: PlotPoint, q1: PlotPoint, q2: PlotPoint): Boolean {
        fun cross(o: PlotPoint, a: PlotPoint, b: PlotPoint): Double =
            (a.x - o.x).toDouble() * (b.y - o.y) - (a.y - o.y).toDouble() * (b.x - o.x)
        fun onSegment(p: PlotPoint, q: PlotPoint, r: PlotPoint): Boolean =
            minOf(p.x, r.x) <= q.x && q.x <= maxOf(p.x, r.x) && minOf(p.y, r.y) <= q.y && q.y <= maxOf(p.y, r.y)
        val d1 = cross(q1, q2, p1)
        val d2 = cross(q1, q2, p2)
        val d3 = cross(p1, p2, q1)
        val d4 = cross(p1, p2, q2)
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
        if (d1 == 0.0 && onSegment(q1, p1, q2)) return true
        if (d2 == 0.0 && onSegment(q1, p2, q2)) return true
        if (d3 == 0.0 && onSegment(p1, q1, p2)) return true
        if (d4 == 0.0 && onSegment(p1, q2, p2)) return true
        return false
    }

    /** True when the polygon's edges cross each other (an outline drawn as a figure of eight). */
    fun isSelfIntersecting(polygon: List<PlotPoint>): Boolean {
        val n = polygon.size
        if (n < 4) return false
        for (i in 0 until n) {
            val a1 = polygon[i]
            val a2 = polygon[(i + 1) % n]
            for (j in i + 1 until n) {
                // Skip edges that share a corner.
                if (j == i || (j + 1) % n == i || (i + 1) % n == j) continue
                if (segmentsIntersect(a1, a2, polygon[j], polygon[(j + 1) % n])) return true
            }
        }
        return false
    }

    /**
     * Checks an outline: at least 3 corners, an area of at least [MIN_OUTLINE_AREA_M2] (so a small round pot still
     * counts, LLR-ROUND-010), edges not crossing.
     */
    fun validateOutline(polygon: List<PlotPoint>): String? {
        if (polygon.size < 3) return "An outline needs at least 3 corners."
        if (polygonArea(polygon) < MIN_OUTLINE_AREA_M2) return "The outline encloses almost no area."
        if (isSelfIntersecting(polygon)) return "The outline's edges cross each other. Tap the corners in order around the plot."
        return null
    }
}

/** Plot-level helpers that account for a custom outline when one is set (FR-002). */
object PlotShape {

    fun outline(plot: PlotEntity): List<PlotPoint> {
        val points = PlotGeometry.parsePoints(plot.boundaryJson)
        return if (points.size >= 3) points else emptyList()
    }

    fun rectangle(plot: PlotEntity): List<PlotPoint> = listOf(
        PlotPoint(0f, 0f), PlotPoint(plot.lengthM, 0f), PlotPoint(plot.lengthM, plot.widthM), PlotPoint(0f, plot.widthM)
    )

    /** The plot's usable area: the custom outline, or the full rectangle. */
    fun effectiveOutline(plot: PlotEntity): List<PlotPoint> = outline(plot).ifEmpty { rectangle(plot) }

    /** True when (x, y) lies inside the plot (its outline if it has one, else its rectangle). */
    fun contains(plot: PlotEntity, x: Float, y: Float): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        if (x < 0f || y < 0f || x > plot.lengthM || y > plot.widthM) return false
        val custom = outline(plot)
        return custom.isEmpty() || PlotGeometry.pointInPolygon(x, y, custom)
    }

    fun areaM2(plot: PlotEntity): Float {
        roundDiameter(plot)?.let { d -> return (kotlin.math.PI * d * d / 4.0).toFloat() }
        val custom = outline(plot)
        return if (custom.isEmpty()) plot.lengthM * plot.widthM else PlotGeometry.polygonArea(custom)
    }

    /** Corners of a round plot's outline (LLR-ROUND-010): enough for a smooth circle, well under MAX_POINTS_PER_SHAPE. */
    const val ROUND_SEGMENTS = 64

    /** Smallest and largest diameter of a round plot, in meters (T2-CFG-090 absolute range). */
    const val ROUND_MIN_DIAMETER_M = 0.05f
    const val ROUND_MAX_DIAMETER_M = 1000f

    /**
     * The outline of a round plot of [diameterM] (LLR-ROUND-010): [ROUND_SEGMENTS] corners on the circle that touches
     * all four sides of the diameter × diameter box, starting at the top and going clockwise on screen.
     */
    fun circleOutline(diameterM: Float): List<PlotPoint> {
        val r = diameterM / 2.0
        return (0 until ROUND_SEGMENTS).map { i ->
            val a = 2.0 * kotlin.math.PI * i / ROUND_SEGMENTS
            PlotPoint((r + r * kotlin.math.sin(a)).toFloat().coerceIn(0f, diameterM), (r - r * kotlin.math.cos(a)).toFloat().coerceIn(0f, diameterM))
        }
    }

    /**
     * The diameter when [plot] is round, else null (LLR-ROUND-020): a square box and an outline of at least 32 corners,
     * every one within 1 % of the radius from the box's center. Plan files keep round plots round this way, with no
     * extra field.
     */
    fun roundDiameter(plot: PlotEntity): Float? {
        val l = plot.lengthM; val w = plot.widthM
        if (!(l > 0f) || !l.isFinite() || kotlin.math.abs(l - w) > 1e-4f * l) return null
        val pts = outline(plot)
        if (pts.size < 32) return null
        val r = l / 2.0
        val ok = pts.all { p ->
            val d = kotlin.math.hypot(p.x - r, p.y - r)
            kotlin.math.abs(d - r) <= 0.01 * r
        }
        return if (ok) l else null
    }

    /** "Ø 0.40 m" for a round plot, "8.00 × 5.00 m" otherwise, in the display unit (LLR-ROUND-030). */
    fun describeSize(plot: PlotEntity, unit: DistanceUnit): String {
        roundDiameter(plot)?.let { return "Ø " + DistanceFormatter.format(it, unit) }
        return DistanceFormatter.format(plot.lengthM, unit).substringBeforeLast(" ") + " × " + DistanceFormatter.format(plot.widthM, unit)
    }
}
