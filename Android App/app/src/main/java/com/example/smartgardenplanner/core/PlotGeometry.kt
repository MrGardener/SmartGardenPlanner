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

    /** Checks an outline the user drew: at least 3 corners, non-zero area, edges not crossing. */
    fun validateOutline(polygon: List<PlotPoint>): String? {
        if (polygon.size < 3) return "An outline needs at least 3 corners."
        if (polygonArea(polygon) < 0.01f) return "The outline encloses almost no area."
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
        if (x < 0f || y < 0f || x > plot.lengthM || y > plot.widthM) return false
        val custom = outline(plot)
        return custom.isEmpty() || PlotGeometry.pointInPolygon(x, y, custom)
    }

    fun areaM2(plot: PlotEntity): Float {
        val custom = outline(plot)
        return if (custom.isEmpty()) plot.lengthM * plot.widthM else PlotGeometry.polygonArea(custom)
    }
}
