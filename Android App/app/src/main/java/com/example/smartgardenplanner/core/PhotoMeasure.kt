package com.example.smartgardenplanner.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A point in photo pixels (x to the right, y down). */
data class PixelPoint(val x: Double, val y: Double)

/**
 * Objects of known size the gardener lays flat on the ground in the photo (LLR-PMEAS-020). [longM] is the side tapped
 * first. Bigger references give better results.
 */
enum class ReferenceObject(val label: String, val longM: Double, val shortM: Double) {
    RAISED_BED_4X8("Raised bed 4 × 8 ft", 2.4384, 1.2192),
    SQUARE_1M("Square 1 × 1 m (string or tape)", 1.0, 1.0),
    PAVER_2FT("Paving slab 2 × 2 ft", 0.6096, 0.6096),
    LETTER("Letter paper 8.5 × 11 in", 0.2794, 0.2159),
    A4("A4 paper", 0.297, 0.210),
    CUSTOM("Something else (type its size)", 0.0, 0.0)
}

/** The result of measuring a plot in a photo (LLR-PMEAS-030/040). */
data class PhotoMeasurement(
    /** Plot length (along its first tapped edge) and width, in meters. */
    val lengthM: Double,
    val widthM: Double,
    val areaM2: Double,
    /** The outline in plot meters, or null when the corners make a rectangle (the plot needs no outline). */
    val outline: List<PlotPoint>?,
    /** Length of each side, in tap order (one value for a two-point measurement). */
    val sidesM: List<Double>,
    /** About a 95 % range for the length/width and the area if each tap is up to the tap error away; infinite when unknown. */
    val plusMinusM: Double,
    val areaPlusMinusM2: Double,
    val warnings: List<String>
)

sealed class PhotoMeasureResult {
    data class Ok(val measurement: PhotoMeasurement) : PhotoMeasureResult()
    data class Failed(val reason: String) : PhotoMeasureResult()
}

/**
 * Measures a plot from one photo (T2-FUN-025, HLR-MEAS-020): the four corners of a rectangle of known size lying on the
 * ground fix the perspective (a planar homography), and the plot corners tapped in the same photo are mapped to meters.
 * Valid when the plot and the reference lie on the same, roughly flat ground. Pure Kotlin, shared with the web planner.
 */
object PhotoMeasure {

    const val MIN_SIDE_M = 0.05
    const val MAX_SIDE_M = 1000.0
    const val MAX_CORNERS = 20
    private const val SAMPLES = 64

    /** 3 × 3 matrix, row-major, mapping photo pixels to ground meters. */
    class Homography(val m: DoubleArray) {
        /** The ground point for a pixel, or null when the pixel is at or beyond the horizon. */
        fun map(p: PixelPoint): PlotPoint? {
            val w = m[6] * p.x + m[7] * p.y + m[8]
            if (w <= 1e-9 || !w.isFinite()) return null
            val x = (m[0] * p.x + m[1] * p.y + m[2]) / w
            val y = (m[3] * p.x + m[4] * p.y + m[5]) / w
            return if (x.isFinite() && y.isFinite() && abs(x) < 1e7 && abs(y) < 1e7) PlotPoint(x.toFloat(), y.toFloat()) else null
        }
        internal fun mapD(p: PixelPoint): Pair<Double, Double>? {
            val w = m[6] * p.x + m[7] * p.y + m[8]
            if (w <= 1e-9 || !w.isFinite()) return null
            val x = (m[0] * p.x + m[1] * p.y + m[2]) / w
            val y = (m[3] * p.x + m[4] * p.y + m[5]) / w
            return if (x.isFinite() && y.isFinite() && abs(x) < 1e7 && abs(y) < 1e7) x to y else null
        }
    }

    /**
     * The homography taking four photo points to four ground points (LLR-PMEAS-010), by solving the 8 × 8 linear system
     * of the direct linear transform with partial pivoting, in coordinates scaled to about 1 for accuracy. Null when the
     * points are degenerate (three on a line, repeated or not finite). The sign is chosen so the four reference pixels
     * lie in front of the camera (w > 0).
     */
    fun homography(src: List<PixelPoint>, dst: List<Pair<Double, Double>>): Homography? {
        if (src.size != 4 || dst.size != 4) return null
        if (src.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val s = max(1.0, src.maxOf { max(abs(it.x), abs(it.y)) })
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val u = src[i].x / s; val v = src[i].y / s
            val (x, y) = dst[i]
            a[2 * i] = doubleArrayOf(u, v, 1.0, 0.0, 0.0, 0.0, -u * x, -v * x, x)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, u, v, 1.0, -u * y, -v * y, y)
        }
        val h = solve(a) ?: return null
        // Undo the pixel scaling: H' = H · diag(1/s, 1/s, 1).
        val m = doubleArrayOf(h[0] / s, h[1] / s, h[2], h[3] / s, h[4] / s, h[5], h[6] / s, h[7] / s, 1.0)
        val hom = Homography(m)
        val ws = src.map { m[6] * it.x + m[7] * it.y + m[8] }
        val flipped = if (ws.all { it < 0 }) Homography(DoubleArray(9) { -m[it] }) else hom
        return if (src.all { p -> (flipped.m[6] * p.x + flipped.m[7] * p.y + flipped.m[8]) > 0 }) flipped else null
    }

    /** Gaussian elimination with partial pivoting on an 8 × 9 augmented matrix; null when singular. */
    private fun solve(a: Array<DoubleArray>): DoubleArray? {
        val n = 8
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            if (abs(a[pivot][col]) < 1e-10) return null
            val t = a[col]; a[col] = a[pivot]; a[pivot] = t
            for (r in 0 until n) if (r != col) {
                val f = a[r][col] / a[col][col]
                if (f != 0.0) for (c in col until n + 1) a[r][c] -= f * a[col][c]
            }
        }
        return DoubleArray(n) { a[it][n] / a[it][it] }.takeIf { r -> r.all { it.isFinite() } }
    }

    private fun signedArea(p: List<PixelPoint>): Double =
        p.indices.sumOf { i -> val q = p[(i + 1) % p.size]; p[i].x * q.y - q.x * p[i].y } / 2

    private fun convexInOrder(p: List<PixelPoint>): Boolean {
        val n = p.size
        var sign = 0
        for (i in 0 until n) {
            val a = p[i]; val b = p[(i + 1) % n]; val c = p[(i + 2) % n]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1e-9) return false
            val sgn = if (cross > 0) 1 else -1
            if (sign == 0) sign = sgn else if (sgn != sign) return false
        }
        return true
    }

    /**
     * Measures the plot (LLR-PMEAS-030/040).
     * @param reference the four corners of the reference in the photo, tapped in order around it, starting along a long side
     * @param longM, shortM the reference's real size; the first tapped side is [longM]
     * @param plot 2 points (one distance) or 3 to [MAX_CORNERS] plot corners in order around the plot
     * @param imageWidth, imageHeight photo size in pixels
     * @param tapErrorPx how far a tap may be off, in photo pixels, for the ± estimate
     */
    fun measure(
        reference: List<PixelPoint>, longM: Double, shortM: Double, plot: List<PixelPoint>,
        imageWidth: Int, imageHeight: Int, tapErrorPx: Double = 6.0
    ): PhotoMeasureResult {
        if (reference.size != 4) return PhotoMeasureResult.Failed("Tap the 4 corners of the reference.")
        if (!(longM > 0 && shortM > 0 && longM.isFinite() && shortM.isFinite() && longM <= 100 && shortM <= 100))
            return PhotoMeasureResult.Failed("Enter the reference's real size (more than 0 and up to 100 m).")
        if (plot.size < 2) return PhotoMeasureResult.Failed("Tap at least 2 points of the plot.")
        if (plot.size > MAX_CORNERS) return PhotoMeasureResult.Failed("Tap at most $MAX_CORNERS corners.")
        if (imageWidth <= 0 || imageHeight <= 0) return PhotoMeasureResult.Failed("The photo couldn't be read.")
        val all = reference + plot
        if (all.any { !it.x.isFinite() || !it.y.isFinite() || it.x < 0 || it.y < 0 || it.x > imageWidth || it.y > imageHeight })
            return PhotoMeasureResult.Failed("Every tap must be on the photo.")
        if (!convexInOrder(reference))
            return PhotoMeasureResult.Failed("Tap the reference's corners in order around it (no crossing, no three in a line).")

        val nominal = compute(reference, longM, shortM, plot) ?: return PhotoMeasureResult.Failed(
            "A corner is too close to the horizon, or the plot corners cross. Take the photo from higher up, looking more down at the ground.")
        if (nominal.sides.size > 2 && nominal.outline.size >= 3 && PlotGeometry.validateOutline(nominal.outline) != null)
            return PhotoMeasureResult.Failed("The plot corners must go around the plot in order without crossing.")
        if (plot.size >= 3 && (nominal.lengthM < MIN_SIDE_M || nominal.widthM < MIN_SIDE_M))
            return PhotoMeasureResult.Failed("The plot comes out smaller than ${MIN_SIDE_M} m. Check the reference size and the taps.")
        if (nominal.lengthM > MAX_SIDE_M || nominal.widthM > MAX_SIDE_M)
            return PhotoMeasureResult.Failed("The plot comes out larger than ${MAX_SIDE_M.toInt()} m. Check the reference size and the taps.")

        // ± by moving every tap up to tapErrorPx in a random direction (fixed seed, so the same taps give the same
        // answer): two standard deviations of the change, about a 95 % range. If many of the moved taps can't be
        // measured at all (corners near the horizon), the range is unknown (infinite).
        val rnd = Random(20261014)
        var sumD = 0.0; var sumA = 0.0; var good = 0; var failures = 0
        val err = max(0.0, tapErrorPx)
        repeat(SAMPLES) {
            fun jitter(p: PixelPoint): PixelPoint {
                val r = err * sqrt(rnd.nextDouble()); val t = rnd.nextDouble() * 2 * kotlin.math.PI
                return PixelPoint(p.x + r * cos(t), p.y + r * sin(t))
            }
            val c = compute(reference.map(::jitter), longM, shortM, plot.map(::jitter))
            if (c == null) failures++ else {
                val d = max(abs(c.lengthM - nominal.lengthM), abs(c.widthM - nominal.widthM))
                sumD += d * d; sumA += (c.areaM2 - nominal.areaM2) * (c.areaM2 - nominal.areaM2); good++
            }
        }
        val unknown = failures * 4 >= SAMPLES
        val plusMinus = if (unknown) Double.POSITIVE_INFINITY else if (good == 0) 0.0 else 2 * sqrt(sumD / good)
        val dA = if (unknown) Double.POSITIVE_INFINITY else if (good == 0) 0.0 else 2 * sqrt(sumA / good)

        val warnings = mutableListOf<String>()
        val refDiagPx = max(hypot(reference[0].x - reference[2].x, reference[0].y - reference[2].y), hypot(reference[1].x - reference[3].x, reference[1].y - reference[3].y))
        if (refDiagPx < 0.08 * hypot(imageWidth.toDouble(), imageHeight.toDouble()))
            warnings += "The reference is small in the photo. Use a bigger reference or move closer to it for a better result."
        val extent = max(nominal.lengthM, nominal.widthM)
        if (extent > 12 * longM) warnings += "The plot is much bigger than the reference, so the far corners are less accurate."
        if (unknown || (extent > 0 && plusMinus > 0.10 * extent))
            warnings += "The result may be off by more than 10%. Take the photo from higher up, looking down, with a bigger reference."

        return PhotoMeasureResult.Ok(PhotoMeasurement(
            lengthM = nominal.lengthM, widthM = nominal.widthM, areaM2 = nominal.areaM2,
            outline = if (nominal.rectangle || plot.size < 3) null else nominal.outline,
            sidesM = nominal.sides, plusMinusM = plusMinus, areaPlusMinusM2 = dA, warnings = warnings
        ))
    }

    private class Computed(val lengthM: Double, val widthM: Double, val areaM2: Double, val outline: List<PlotPoint>, val sides: List<Double>, val rectangle: Boolean)

    private fun compute(reference: List<PixelPoint>, longM: Double, shortM: Double, plot: List<PixelPoint>): Computed? {
        // The ground rectangle on the same side of the first edge as the taps, so the photo isn't mirrored.
        val side = if (signedArea(reference) >= 0) 1.0 else -1.0
        val ground = listOf(0.0 to 0.0, longM to 0.0, longM to side * shortM, 0.0 to side * shortM)
        val h = homography(reference, ground) ?: return null
        val pts = plot.map { h.mapD(it) ?: return null }
        if (pts.size == 2) {
            val d = hypot(pts[1].first - pts[0].first, pts[1].second - pts[0].second)
            return Computed(d, 0.0, 0.0, emptyList(), listOf(d), false)
        }
        // Turn the plot so its first edge runs along x, then move it to start at (0, 0).
        val ang = atan2(pts[1].second - pts[0].second, pts[1].first - pts[0].first)
        val c = cos(-ang); val s = sin(-ang)
        // A rotation only, never a reflection, so the outline keeps the shape seen from above.
        val turned = pts.map { (x, y) -> (x - pts[0].first) * c - (y - pts[0].second) * s to (x - pts[0].first) * s + (y - pts[0].second) * c }
        val minX = turned.minOf { it.first }; val minY = turned.minOf { it.second }
        val placed = turned.map { (x, y) -> x - minX to y - minY }
        val length = placed.maxOf { it.first }; val width = placed.maxOf { it.second }
        val n = placed.size
        val sides = (0 until n).map { i -> val a = placed[i]; val b = placed[(i + 1) % n]; hypot(b.first - a.first, b.second - a.second) }
        val area = abs((0 until n).sumOf { i -> val a = placed[i]; val b = placed[(i + 1) % n]; a.first * b.second - b.first * a.second }) / 2
        val rectangle = n == 4 && (0 until 4).all { i ->
            val a = placed[(i + 3) % 4]; val b = placed[i]; val d = placed[(i + 1) % 4]
            val v1x = a.first - b.first; val v1y = a.second - b.second; val v2x = d.first - b.first; val v2y = d.second - b.second
            val cosA = (v1x * v2x + v1y * v2y) / (hypot(v1x, v1y) * hypot(v2x, v2y))
            abs(acos(cosA.coerceIn(-1.0, 1.0)) - kotlin.math.PI / 2) < RIGHT_ANGLE_TOLERANCE_RAD
        } && abs(sides[0] - sides[2]) <= 0.06 * max(sides[0], sides[2]) && abs(sides[1] - sides[3]) <= 0.06 * max(sides[1], sides[3])
        return Computed(length, width, area, placed.map { PlotPoint(it.first.toFloat(), it.second.toFloat()) }, sides, rectangle)
    }

    /** Corners within 6° of square and opposite sides within 6 % make a rectangle (no outline needed). */
    private const val RIGHT_ANGLE_TOLERANCE_RAD = 6.0 * kotlin.math.PI / 180.0

    /**
     * "6.20 × 4.05 m (± 0.15 m), 25.1 m²" in the display unit (LLR-UNIT-050 for the area). An unknown ± goes at the end,
     * "… (± unknown: retake the photo)", so each note is a whole phrase the dictionary can translate.
     */
    fun describe(m: PhotoMeasurement, unit: DistanceUnit): String {
        val pm = if (m.plusMinusM.isFinite() && m.plusMinusM > 0) " (± " + DistanceFormatter.format(m.plusMinusM.toFloat(), unit) + ")" else ""
        val unknown = if (!m.plusMinusM.isFinite()) " (± unknown: retake the photo)" else ""
        return if (m.widthM == 0.0) "Distance: " + DistanceFormatter.format(m.lengthM.toFloat(), unit) + pm + unknown
        else DistanceFormatter.format(m.lengthM.toFloat(), unit).substringBeforeLast(" ") + " × " + DistanceFormatter.format(m.widthM.toFloat(), unit) +
            pm + ", " + AreaFormatter.format(m.areaM2, unit) + unknown + if (m.outline != null) " (not a rectangle: outline kept)" else ""
    }
}

/** LLR-UNIT-050: areas in m² (2 decimals) or, when the unit is inches, ft² (1 decimal); ft² = m² / 0.09290304. */
object AreaFormatter {
    const val SQ_M_PER_SQ_FT = 0.09290304
    fun format(m2: Double, unit: DistanceUnit): String {
        if (!m2.isFinite() || m2 < 0) return "—"
        return if (unit == DistanceUnit.METERS) (m2 + m2 * 1e-9).fmt(2) + " m²" else (m2 / SQ_M_PER_SQ_FT * (1 + 1e-9)).fmt(1) + " ft²"
    }
}

/** Plot sizes that are allowed but unusual, so they are confirmed before use (LLR-LIM-010, LLR-EDIT-020). */
object PlotLimits {
    const val UNUSUAL_MIN_M = 0.30f
    const val UNUSUAL_MAX_M = 200f
    fun isUnusual(lengthM: Float, widthM: Float): Boolean = minOf(lengthM, widthM) < UNUSUAL_MIN_M || maxOf(lengthM, widthM) > UNUSUAL_MAX_M

    /** A round plot (a pot) is asked about only above [UNUSUAL_MAX_M]: pots are often 0.15–0.30 m across (LLR-ROUND-040). */
    fun isUnusualRound(diameterM: Float): Boolean = diameterM > UNUSUAL_MAX_M

    /** "Ø 0.20 m" or "8.00 m × 5.00 m": the size named in the "Is that size right?" question. */
    fun questionSize(lengthM: Float, widthM: Float, round: Boolean, unit: DistanceUnit): String =
        if (round) "Ø " + DistanceFormatter.format(lengthM, unit)
        else DistanceFormatter.format(lengthM, unit) + " × " + DistanceFormatter.format(widthM, unit)
}
