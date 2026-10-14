package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Measuring a plot from a photo (LLR-PMEAS-010…040). A simulated pinhole camera photographs a reference and a plot on
 * flat ground; measuring the projected pixels must give back the real sizes.
 */
class PhotoMeasureTest {

    /** A camera at height [h] m above (cx, cy) looking toward [yawDeg] (0 = up the plot's −y), pitched [pitchDeg] below level. */
    private class Camera(val cx: Double, val cy: Double, val h: Double, yawDeg: Double, pitchDeg: Double, val f: Double = 1500.0,
                         val w: Int = 3000, val ht: Int = 4000) {
        // World: X = plot x, Y = −plot y (north), Z up. Image: x right, y down.
        private val yaw = Math.toRadians(yawDeg); private val pitch = Math.toRadians(pitchDeg)
        private val d = doubleArrayOf(sin(yaw) * cos(pitch), cos(yaw) * cos(pitch), -sin(pitch))
        private val right = norm(doubleArrayOf(d[1], -d[0], 0.0))   // d × up
        private val down = cross(d, right)
        private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
        private fun norm(a: DoubleArray): DoubleArray { val l = hypot(hypot(a[0], a[1]), a[2]); return doubleArrayOf(a[0] / l, a[1] / l, a[2] / l) }
        /** Pixel of a ground point given in plot coordinates (x right, y down), or null if behind the camera. */
        fun px(x: Double, y: Double): PixelPoint? {
            val p = doubleArrayOf(x - cx, -y - (-cy), -h)
            val zc = p[0] * d[0] + p[1] * d[1] + p[2] * d[2]
            if (zc <= 0.01) return null
            val xc = p[0] * right[0] + p[1] * right[1] + p[2] * right[2]
            val yc = p[0] * down[0] + p[1] * down[1] + p[2] * down[2]
            return PixelPoint(f * xc / zc + w / 2.0, f * yc / zc + ht / 2.0)
        }
    }

    private fun ok(r: PhotoMeasureResult): PhotoMeasurement { assertTrue("$r", r is PhotoMeasureResult.Ok); return (r as PhotoMeasureResult.Ok).measurement }
    private fun failed(r: PhotoMeasureResult) = assertTrue("$r", r is PhotoMeasureResult.Failed)

    /** The 4×8 ft bed lying at (bx, by), long side along x, corners in tap order (clockwise on the layout). */
    private fun bed(bx: Double = 3.0, by: Double = 2.0) = listOf(bx to by, bx + 2.4384 to by, bx + 2.4384 to by + 1.2192, bx to by + 1.2192)

    private val cam = Camera(cx = 4.0, cy = 12.0, h = 2.0, yawDeg = 0.0, pitchDeg = 35.0)

    @Test
    fun rectanglePlot_comesBackAtItsRealSize() {
        val plot = listOf(0.0 to 0.0, 8.0 to 0.0, 8.0 to 5.0, 0.0 to 5.0)
        val m = ok(PhotoMeasure.measure(bed().map { cam.px(it.first, it.second)!! }, 2.4384, 1.2192, plot.map { cam.px(it.first, it.second)!! }, cam.w, cam.ht, tapErrorPx = 0.0))
        assertEquals(8.0, m.lengthM, 1e-3); assertEquals(5.0, m.widthM, 1e-3); assertEquals(40.0, m.areaM2, 1e-2)
        assertNull("a rectangle needs no outline", m.outline)
        assertEquals(listOf(8.0, 5.0, 8.0, 5.0), m.sidesM.map { Math.round(it * 1000) / 1000.0 })
        assertEquals(0.0, m.plusMinusM, 1e-9)
        assertEquals("8.00 × 5.00 m, 40.00 m²", PhotoMeasure.describe(m, DistanceUnit.METERS))
    }

    @Test
    fun lShapedPlot_keepsItsOutlineUnmirrored_andTheTapDirectionDoesNotMatter() {
        val l = listOf(0.0 to 0.0, 6.0 to 0.0, 6.0 to 2.0, 3.0 to 2.0, 3.0 to 5.0, 0.0 to 5.0)
        for (refOrder in listOf(bed(), bed().let { listOf(it[1], it[0], it[3], it[2]) })) {
            // Both orders start along a long side; the second goes around the other way.
            val m = ok(PhotoMeasure.measure(refOrder.map { cam.px(it.first, it.second)!! }, 2.4384, 1.2192, l.map { cam.px(it.first, it.second)!! }, cam.w, cam.ht, 0.0))
            assertEquals(6.0, m.lengthM, 2e-3); assertEquals(5.0, m.widthM, 2e-3); assertEquals(21.0, m.areaM2, 2e-2)
            val out = assertNotNull(m.outline).let { m.outline!! }
            assertEquals(6, out.size)
            // Same shape: side lengths in tap order, and the same turning direction as on the ground (not a mirror image).
            assertEquals(listOf(6.0, 2.0, 3.0, 3.0, 3.0, 5.0), m.sidesM.map { Math.round(it * 100) / 100.0 })
            val signed = out.indices.sumOf { i -> val a = out[i]; val b = out[(i + 1) % out.size]; a.x.toDouble() * b.y - b.x.toDouble() * a.y }
            assertTrue("not mirrored", signed > 0)
            assertEquals(null, PlotGeometry.validateOutline(out))
        }
    }

    @Test
    fun twoPoints_measureOneDistance() {
        val m = ok(PhotoMeasure.measure(bed().map { cam.px(it.first, it.second)!! }, 2.4384, 1.2192,
            listOf(cam.px(1.0, 1.0)!!, cam.px(4.0, 5.0)!!), cam.w, cam.ht, 0.0))
        assertEquals(5.0, m.lengthM, 1e-3); assertEquals(0.0, m.widthM, 0.0); assertEquals(listOf(5.0), m.sidesM.map { Math.round(it * 1000) / 1000.0 })
        assertTrue(PhotoMeasure.describe(m, DistanceUnit.METERS).startsWith("Distance: 5.00 m"))
    }

    @Test
    fun badTapsAndSizesAreRefusedWithAReason() {
        val ref = bed().map { cam.px(it.first, it.second)!! }
        val plot = listOf(0.0 to 0.0, 8.0 to 0.0, 8.0 to 5.0, 0.0 to 5.0).map { cam.px(it.first, it.second)!! }
        failed(PhotoMeasure.measure(ref.take(3), 2.4384, 1.2192, plot, cam.w, cam.ht))                        // 3 reference corners
        failed(PhotoMeasure.measure(listOf(ref[0], ref[2], ref[1], ref[3]), 2.4384, 1.2192, plot, cam.w, cam.ht))   // crossing order
        failed(PhotoMeasure.measure(listOf(ref[0], ref[1], PixelPoint((ref[1].x + ref[0].x) * 2 - ref[0].x, (ref[1].y + ref[0].y) * 2 - ref[0].y), ref[3]), 2.4384, 1.2192, plot, cam.w, cam.ht))
        for ((a, b) in listOf(0.0 to 1.0, -1.0 to 1.0, Double.NaN to 1.0, 1.0 to Double.POSITIVE_INFINITY, 101.0 to 1.0))
            failed(PhotoMeasure.measure(ref, a, b, plot, cam.w, cam.ht))
        failed(PhotoMeasure.measure(ref, 2.4384, 1.2192, plot.take(1), cam.w, cam.ht))                        // 1 plot point
        failed(PhotoMeasure.measure(ref, 2.4384, 1.2192, List(21) { plot[it % 4] }, cam.w, cam.ht))             // 21 corners
        failed(PhotoMeasure.measure(ref, 2.4384, 1.2192, plot + PixelPoint(-5.0, 10.0), cam.w, cam.ht))          // off the photo
        failed(PhotoMeasure.measure(ref, 2.4384, 1.2192, listOf(plot[0], plot[2], plot[1], plot[3]), cam.w, cam.ht))   // plot crosses itself
        failed(PhotoMeasure.measure(ref, 2.4384, 1.2192, plot, 0, cam.ht))                                    // no photo size
        // A point above the horizon (sky): the reference is near, the "corner" is above the vanishing line.
        val horizonY = (0 until cam.ht).firstOrNull { y -> PhotoMeasure.homography(ref, listOf(0.0 to 0.0, 2.4384 to 0.0, 2.4384 to 1.2192, 0.0 to 1.2192))!!.map(PixelPoint(1500.0, y.toDouble())) != null }!!
        failed(PhotoMeasure.measure(ref, 2.4384, 1.2192, listOf(plot[0], plot[1], PixelPoint(1500.0, maxOf(0, horizonY - 50).toDouble())), cam.w, cam.ht))
        // Sizes outside 0.5 … 1000 m.
        failed(PhotoMeasure.measure(ref, 2.4384, 1.2192, listOf(0.0 to 0.0, 0.3 to 0.0, 0.3 to 0.3, 0.0 to 0.3).map { cam.px(it.first + 4, it.second + 4)!! }, cam.w, cam.ht))
        failed(PhotoMeasure.measure(ref, 2438.4, 1219.2, plot, cam.w, cam.ht))
    }

    @Test
    fun uncertaintyGrowsWithTapErrorAndDistance_andWarnsWhenTheReferenceIsSmall() {
        val plot = listOf(0.0 to 0.0, 8.0 to 0.0, 8.0 to 5.0, 0.0 to 5.0).map { cam.px(it.first, it.second)!! }
        val ref = bed().map { cam.px(it.first, it.second)!! }
        val p0 = ok(PhotoMeasure.measure(ref, 2.4384, 1.2192, plot, cam.w, cam.ht, 0.0)).plusMinusM
        val p5 = ok(PhotoMeasure.measure(ref, 2.4384, 1.2192, plot, cam.w, cam.ht, 5.0)).plusMinusM
        val p20 = ok(PhotoMeasure.measure(ref, 2.4384, 1.2192, plot, cam.w, cam.ht, 20.0)).plusMinusM
        assertEquals(0.0, p0, 1e-9); assertTrue("$p5 < $p20", p5 > 0 && p5 < p20)
        // Same answer for the same taps (fixed seed).
        assertEquals(p5, ok(PhotoMeasure.measure(ref, 2.4384, 1.2192, plot, cam.w, cam.ht, 5.0)).plusMinusM, 0.0)
        // A sheet of Letter paper for a 30 m field, photographed from far away: less accurate and says so.
        val far = Camera(cx = 15.0, cy = 45.0, h = 1.6, yawDeg = 0.0, pitchDeg = 12.0)
        val paper = listOf(14.0 to 20.0, 14.2794 to 20.0, 14.2794 to 20.2159, 14.0 to 20.2159).map { far.px(it.first, it.second)!! }
        val field = listOf(0.0 to 0.0, 30.0 to 0.0, 30.0 to 25.0, 0.0 to 25.0).map { far.px(it.first, it.second)!! }
        val m = ok(PhotoMeasure.measure(paper, 0.2794, 0.2159, field, far.w, far.ht, 6.0))
        assertTrue(m.warnings.toString(), m.warnings.any { it.startsWith("The reference is small") })
        assertTrue(m.warnings.toString(), m.warnings.any { it.startsWith("The plot is much bigger") })
        assertTrue("far ${m.plusMinusM} vs near $p5, warnings ${m.warnings}", m.plusMinusM > p5)
    }

    @Test
    fun randomCamerasAndPlots_giveBackTheRealSizes() {
        val rnd = Random(10)
        var checked = 0
        repeat(300) {
            val cam = Camera(cx = rnd.nextDouble(-5.0, 15.0), cy = rnd.nextDouble(12.0, 25.0), h = rnd.nextDouble(1.2, 6.0),
                yawDeg = rnd.nextDouble(-25.0, 25.0), pitchDeg = rnd.nextDouble(25.0, 80.0), f = rnd.nextDouble(800.0, 3000.0))
            val L = rnd.nextDouble(1.0, 12.0); val W = rnd.nextDouble(1.0, 8.0)
            val plot = listOf(0.0 to 0.0, L to 0.0, L to W, 0.0 to W)
            val ref = bed(rnd.nextDouble(0.0, 4.0), rnd.nextDouble(0.0, 4.0))
            val px = (ref + plot).map { cam.px(it.first, it.second) }
            if (px.any { it == null || it.x !in 0.0..cam.w.toDouble() || it.y !in 0.0..cam.ht.toDouble() }) return@repeat
            val r = PhotoMeasure.measure(px.take(4).map { it!! }, 2.4384, 1.2192, px.drop(4).map { it!! }, cam.w, cam.ht, 0.0)
            val m = ok(r)
            assertEquals("L", L, m.lengthM, 2e-3 * L); assertEquals("W", W, m.widthM, 2e-3 * W)
            checked++
        }
        assertTrue("enough random cases in view: $checked", checked > 40)
        // Random taps anywhere never throw.
        repeat(2000) {
            val pts = List(rnd.nextInt(0, 12)) { PixelPoint(rnd.nextDouble(-10.0, 3010.0), rnd.nextDouble(-10.0, 4010.0)) }
            PhotoMeasure.measure(pts.take(4), rnd.nextDouble(-1.0, 3.0), rnd.nextDouble(-1.0, 3.0), pts.drop(4), 3000, 4000, rnd.nextDouble(0.0, 30.0))
        }
    }

    @Test
    fun homography_mapsTheFourPointsExactly_andRefusesDegenerateOnes() {
        val src = listOf(PixelPoint(100.0, 900.0), PixelPoint(1900.0, 950.0), PixelPoint(1500.0, 300.0), PixelPoint(400.0, 280.0))
        val dst = listOf(0.0 to 0.0, 4.0 to 0.0, 4.0 to 3.0, 0.0 to 3.0)
        val h = PhotoMeasure.homography(src, dst)!!
        src.zip(dst).forEach { (s, d) -> val p = h.map(s)!!; assertEquals(d.first, p.x.toDouble(), 1e-4); assertEquals(d.second, p.y.toDouble(), 1e-4) }
        assertNull(PhotoMeasure.homography(listOf(PixelPoint(0.0, 0.0), PixelPoint(1.0, 1.0), PixelPoint(2.0, 2.0), PixelPoint(0.0, 5.0)), dst))
        assertNull(PhotoMeasure.homography(listOf(src[0], src[0], src[2], src[3]), dst))
        assertNull(PhotoMeasure.homography(listOf(PixelPoint(Double.NaN, 0.0), src[1], src[2], src[3]), dst))
        assertNull(PhotoMeasure.homography(src.take(3), dst.take(3)))
    }

    @Test
    fun areas_showInSquareMetersOrSquareFeet() {
        assertEquals("10.00 m²", AreaFormatter.format(10.0, DistanceUnit.METERS))
        assertEquals("107.6 ft²", AreaFormatter.format(10.0, DistanceUnit.INCHES))
        assertEquals("0.00 m²", AreaFormatter.format(0.0, DistanceUnit.METERS))
        assertEquals("1000000.00 m²", AreaFormatter.format(1e6, DistanceUnit.METERS))
        assertEquals("10763910.4 ft²", AreaFormatter.format(1e6, DistanceUnit.INCHES))
        for (bad in listOf(Double.NaN, -1.0, Double.POSITIVE_INFINITY)) assertEquals("—", AreaFormatter.format(bad, DistanceUnit.METERS))
        assertEquals("0.01 m²", AreaFormatter.format(0.005, DistanceUnit.METERS))   // half-up
        assertTrue(abs(1.0 / AreaFormatter.SQ_M_PER_SQ_FT - 10.7639) < 1e-4)
    }

    @Test
    fun describe_putsTheRangeAndTheNotesInWholePhrases() {
        val l = PhotoMeasurement(8.0, 5.0, 28.0, listOf(PlotPoint(0f, 0f), PlotPoint(8f, 0f), PlotPoint(8f, 5f)), listOf(8.0, 5.0), 0.25, 1.0, emptyList())
        assertEquals("8.00 × 5.00 m (± 0.25 m), 28.00 m² (not a rectangle: outline kept)", PhotoMeasure.describe(l, DistanceUnit.METERS))
        assertEquals("8.00 × 5.00 m, 28.00 m² (± unknown: retake the photo) (not a rectangle: outline kept)",
            PhotoMeasure.describe(l.copy(plusMinusM = Double.POSITIVE_INFINITY), DistanceUnit.METERS))
        assertEquals("Distance: 8.00 m (± unknown: retake the photo)",
            PhotoMeasure.describe(l.copy(widthM = 0.0, outline = null, plusMinusM = Double.POSITIVE_INFINITY), DistanceUnit.METERS))
        assertEquals("315.0 × 196.9 in (± 9.8 in), 301.4 ft²", PhotoMeasure.describe(l.copy(outline = null, areaM2 = 28.0), DistanceUnit.INCHES))
    }
}
