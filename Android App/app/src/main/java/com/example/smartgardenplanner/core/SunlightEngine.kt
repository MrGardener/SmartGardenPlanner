package com.example.smartgardenplanner.core

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A shade-casting object on the plot (FR-006), in plot metres. */
data class Barrier(val type: SiteFeatureType, val points: List<PlotPoint>, val heightM: Float, val radiusM: Float) {
    companion object {
        fun from(feature: SiteFeatureEntity): Barrier? {
            val type = SiteFeatureType.of(feature.featureType) ?: return null
            if (!type.isBarrier) return null
            val pts = PlotGeometry.parsePoints(feature.pointsJson)
            if (pts.isEmpty() || feature.heightM <= 0f) return null
            return Barrier(type, pts, feature.heightM, feature.radiusM)
        }
    }
}

data class SunPosition(val elevationDeg: Double, val azimuthDeg: Double)

/**
 * Sun position and shading estimates (FR-006, and the offline part of FR-007). Uses local solar time
 * (noon = sun due south / north), so no time zone or longitude is needed. Accuracy is a few degrees, which
 * is plenty for estimating hours of direct sun in a garden. Clouds are not modelled: results are
 * clear-sky hours.
 */
object SunlightEngine {

    private const val DEG = PI / 180.0
    const val DEFAULT_LATITUDE = 40.0
    private const val STEP_MINUTES = 15

    fun declinationDeg(dayOfYear: Int): Double = 23.44 * sin(2.0 * PI / 365.0 * (284 + dayOfYear))

    /** Sun elevation and compass azimuth (0 = north, 90 = east) at a solar hour (0–24). */
    fun position(latitudeDeg: Double, dayOfYear: Int, solarHour: Double): SunPosition {
        val phi = latitudeDeg * DEG
        val delta = declinationDeg(dayOfYear) * DEG
        val h = (solarHour - 12.0) * 15.0 * DEG
        val sinEl = sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(h)
        val el = asin(sinEl.coerceIn(-1.0, 1.0))
        var az = atan2(sin(h), cos(h) * sin(phi) - tan(delta) * cos(phi)) / DEG + 180.0
        az = ((az % 360.0) + 360.0) % 360.0
        return SunPosition(el / DEG, az)
    }

    /** Hours between sunrise and sunset. */
    fun dayLengthHours(latitudeDeg: Double, dayOfYear: Int): Double {
        val phi = latitudeDeg * DEG
        val delta = declinationDeg(dayOfYear) * DEG
        val x = -tan(phi) * tan(delta)
        return when {
            x <= -1.0 -> 24.0
            x >= 1.0 -> 0.0
            else -> 2.0 * acos(x) / DEG / 15.0
        }
    }

    /** Average clear-sky day length for each month (index 0 = January). */
    fun monthlyDayLength(latitudeDeg: Double): List<Double> {
        val monthStarts = intArrayOf(1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366)
        return (0 until 12).map { m ->
            val days = monthStarts[m] until monthStarts[m + 1]
            days.map { dayLengthHours(latitudeDeg, it) }.average()
        }
    }

    /** Horizontal unit vector in plot coordinates pointing toward the sun. */
    fun sunDirectionInPlot(azimuthDeg: Double, northBearingDeg: Float): Pair<Double, Double> {
        val rel = (azimuthDeg - northBearingDeg) * DEG
        return Pair(sin(rel), -cos(rel))
    }

    /** True when a barrier blocks the sun for a ground-level point. */
    fun isShaded(x: Float, y: Float, sun: SunPosition, northBearingDeg: Float, barriers: List<Barrier>): Boolean {
        if (sun.elevationDeg <= 0.0) return true
        val (dx, dy) = sunDirectionInPlot(sun.azimuthDeg, northBearingDeg)
        val tanEl = tan(sun.elevationDeg * DEG)
        for (b in barriers) {
            val distance = rayDistanceToBarrier(x.toDouble(), y.toDouble(), dx, dy, b) ?: continue
            if (distance * tanEl < b.heightM) return true
        }
        return false
    }

    /** Horizontal distance along the ray (x, y) + t·(dx, dy), t ≥ 0, to the barrier, or null if it misses. */
    private fun rayDistanceToBarrier(x: Double, y: Double, dx: Double, dy: Double, b: Barrier): Double? {
        if (b.type == SiteFeatureType.TREE) {
            val c = b.points.first()
            val r = b.radiusM.toDouble().coerceAtLeast(0.2)
            val fx = x - c.x
            val fy = y - c.y
            val cTerm = fx * fx + fy * fy - r * r
            if (cTerm <= 0.0) return 0.0 // under the crown
            val bTerm = fx * dx + fy * dy
            val disc = bTerm * bTerm - cTerm
            if (disc < 0.0) return null
            val t = -bTerm - sqrt(disc)
            return if (t >= 0.0) t else null
        }
        val pts = b.points
        if (pts.size == 1) {
            // A single post: treat as a thin cylinder of radius 0.15 m.
            return rayDistanceToBarrier(x, y, dx, dy, Barrier(SiteFeatureType.TREE, pts, b.heightM, 0.15f))
        }
        val edges = (0 until pts.size - 1).map { pts[it] to pts[it + 1] }.toMutableList()
        if (b.type == SiteFeatureType.BUILDING && pts.size >= 3) edges += pts.last() to pts.first()
        var best: Double? = null
        for ((a, c) in edges) {
            val t = raySegment(x, y, dx, dy, a, c) ?: continue
            if (best == null || t < best) best = t
        }
        return best
    }

    private fun raySegment(x: Double, y: Double, dx: Double, dy: Double, a: PlotPoint, b: PlotPoint): Double? {
        val ex = (b.x - a.x).toDouble()
        val ey = (b.y - a.y).toDouble()
        val denom = dx * ey - dy * ex
        if (kotlin.math.abs(denom) < 1e-12) return null
        val ax = a.x - x
        val ay = a.y - y
        val t = (ax * ey - ay * ex) / denom
        val u = (ax * dy - ay * dx) / denom
        return if (t >= 0.0 && u >= 0.0 && u <= 1.0) t else null
    }

    /** Estimated hours of direct (clear-sky) sun at a point on a given day, accounting for barriers. */
    fun directSunHours(
        x: Float,
        y: Float,
        latitudeDeg: Double,
        dayOfYear: Int,
        northBearingDeg: Float,
        barriers: List<Barrier>
    ): Double {
        var sunnySteps = 0
        val steps = 24 * 60 / STEP_MINUTES
        for (i in 0 until steps) {
            val hour = (i + 0.5) * STEP_MINUTES / 60.0
            val sun = position(latitudeDeg, dayOfYear, hour)
            if (sun.elevationDeg > 0.0 && !isShaded(x, y, sun, northBearingDeg, barriers)) sunnySteps++
        }
        return sunnySteps * STEP_MINUTES / 60.0
    }

    /** Sun-hours grid over the plot for an overlay: cols × rows cells, row-major, cell centres. */
    fun sunHoursGrid(
        lengthM: Float,
        widthM: Float,
        cols: Int,
        rows: Int,
        latitudeDeg: Double,
        dayOfYear: Int,
        northBearingDeg: Float,
        barriers: List<Barrier>
    ): FloatArray {
        val out = FloatArray(if (cols <= 0 || rows <= 0) 0 else cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = (c + 0.5f) * lengthM / cols
                val y = (r + 0.5f) * widthM / rows
                out[r * cols + c] = directSunHours(x, y, latitudeDeg, dayOfYear, northBearingDeg, barriers).toFloat()
            }
        }
        return out
    }

    /**
     * Sun hours to use for a point: a zone the user marked (FR-005) wins; otherwise the barrier estimate if
     * there are barriers; otherwise null (unknown, assume the plot is open).
     */
    fun effectiveSunHours(
        x: Float,
        y: Float,
        zones: List<SiteFeatureEntity>,
        latitudeDeg: Double,
        dayOfYear: Int,
        northBearingDeg: Float,
        barriers: List<Barrier>
    ): Double? {
        for (z in zones) {
            val type = SiteFeatureType.of(z.featureType) ?: continue
            if (!type.isArea) continue
            val poly = PlotGeometry.parsePoints(z.pointsJson)
            if (!PlotGeometry.pointInPolygon(x, y, poly)) continue
            when (type) {
                SiteFeatureType.FULL_SUN -> return 8.0
                SiteFeatureType.PART_SHADE -> return 4.5
                SiteFeatureType.FULL_SHADE -> return 2.0
                else -> {}
            }
        }
        if (barriers.isEmpty()) return null
        return directSunHours(x, y, latitudeDeg, dayOfYear, northBearingDeg, barriers)
    }

    /** Day of year (1–366) for a timestamp in the device's time zone. */
    fun dayOfYear(epochMillis: Long): Int {
        return CivilDate.dayOfYear(epochMillis)
    }

    /** Mid-summer day for the hemisphere (21 June north, 21 December south). */
    fun midsummerDay(latitudeDeg: Double): Int = if (latitudeDeg >= 0) 172 else 355
}
