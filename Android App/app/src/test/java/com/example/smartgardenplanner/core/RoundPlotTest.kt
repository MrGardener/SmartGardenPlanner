package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** Round plots for pots and round beds (LLR-ROUND-010…030): the circle outline, recognizing it, its size and area. */
class RoundPlotTest {

    private fun round(d: Float) = PlotEntity(name = "Pot", lengthM = d, widthM = d, boundaryJson = PlotGeometry.serializePoints(PlotShape.circleOutline(d)))

    @Test
    fun circleOutline_touchesTheBoxAndIsAValidOutline() {
        for (d in listOf(PlotShape.ROUND_MIN_DIAMETER_M, 0.4f, 2.5f, PlotShape.ROUND_MAX_DIAMETER_M)) {
            val pts = PlotShape.circleOutline(d)
            assertEquals(PlotShape.ROUND_SEGMENTS, pts.size)
            assertTrue("inside the box at $d", pts.all { it.x in 0f..d && it.y in 0f..d })
            assertTrue("on the circle at $d", pts.all { abs(hypot(it.x - d / 2.0, it.y - d / 2.0) - d / 2.0) < 1e-4 * d + 1e-6 })
            assertEquals(PlotPoint(d / 2, 0f), pts[0])                       // starts at the top
            assertTrue("clockwise on screen", pts[1].x > pts[0].x)
            assertNull("valid at $d", PlotGeometry.validateOutline(pts))
            assertTrue(pts.size <= 200)                                       // MAX_POINTS_PER_SHAPE
        }
    }

    @Test
    fun roundPlots_areRecognized_andOtherShapesAreNot() {
        assertEquals(0.4f, PlotShape.roundDiameter(round(0.4f))!!, 0f)
        assertNull("no outline", PlotShape.roundDiameter(PlotEntity(name = "R", lengthM = 2f, widthM = 2f)))
        assertNull("not square", PlotShape.roundDiameter(round(2f).copy(widthM = 1.9f)))
        val octagon = (0 until 8).map { i -> val a = 2 * PI * i / 8; PlotPoint((1 + kotlin.math.sin(a)).toFloat(), (1 - kotlin.math.cos(a)).toFloat()) }
        assertNull("too few corners", PlotShape.roundDiameter(PlotEntity(name = "O", lengthM = 2f, widthM = 2f, boundaryJson = PlotGeometry.serializePoints(octagon))))
        val dented = PlotShape.circleOutline(2f).toMutableList().also { it[10] = PlotPoint(1f, 1f) }
        assertNull("a corner off the circle", PlotShape.roundDiameter(PlotEntity(name = "D", lengthM = 2f, widthM = 2f, boundaryJson = PlotGeometry.serializePoints(dented))))
        assertNull("bad sizes", PlotShape.roundDiameter(round(2f).copy(lengthM = Float.NaN, widthM = Float.NaN)))
        assertNull("zero", PlotShape.roundDiameter(PlotEntity(name = "Z", lengthM = 0f, widthM = 0f, boundaryJson = "0,0;0,0;0,0")))
    }

    @Test
    fun plantsGoOnlyInsideTheCircle_andTheAreaIsPiRSquared() {
        val p = round(0.4f)
        assertTrue(PlotShape.contains(p, 0.2f, 0.2f))                        // center
        assertTrue(PlotShape.contains(p, 0.2f, 0.01f))                       // near the top edge
        assertFalse("box corner is outside the pot", PlotShape.contains(p, 0.02f, 0.02f))
        assertFalse(PlotShape.contains(p, 0.39f, 0.39f))
        assertEquals(PI * 0.2 * 0.2, PlotShape.areaM2(p).toDouble(), 1e-6)
        assertEquals(64f, PlotShape.areaM2(PlotEntity(name = "R", lengthM = 8f, widthM = 8f)), 0f)   // a square stays L × W
    }

    @Test
    fun sizeText_showsTheDiameterForRoundPlots() {
        assertEquals("Ø 0.40 m", PlotShape.describeSize(round(0.4f), DistanceUnit.METERS))
        assertEquals("Ø 15.7 in", PlotShape.describeSize(round(0.4f), DistanceUnit.INCHES))
        assertEquals("8.00 × 5.00 m", PlotShape.describeSize(PlotEntity(name = "R", lengthM = 8f, widthM = 5f), DistanceUnit.METERS))
    }

    @Test
    fun planFiles_keepRoundPlotsRound_atEverySize() {
        val rnd = Random(25)
        val sizes = listOf(PlotShape.ROUND_MIN_DIAMETER_M, 0.1f, 0.3f, PlotShape.ROUND_MAX_DIAMETER_M) + List(40) { rnd.nextDouble(0.05, 1000.0).toFloat() }
        for (d in sizes) {
            val pot = round(d)
            val plant = PlantedNodeEntity(plotId = 0, seedCode = TestCatalog.seeds.first().botanicalCode, coordinateXM = d / 2, coordinateYM = d / 2, datePlantedEpochMillis = 0L)
            val back = PlanFileCodec.decode(PlanFileCodec.encode(PlanBundle(listOf(PlanPlot(pot, listOf(plant), emptyList(), emptyList())), emptyList()), TestCatalog.lookup))
            val plot = assertNotNull(back.bundle).let { back.bundle!!.plots.single() }
            assertTrue("no warnings at $d: ${back.warnings}", back.warnings.none { "outline" in it })
            assertEquals("round after the file at $d", d.toDouble(), (PlotShape.roundDiameter(plot.plot) ?: -1f).toDouble(), 1e-4 * d + 1e-4)
            assertEquals(1, plot.plants.size)
        }
    }
}
