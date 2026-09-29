package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host tests for FR-038 (shade at a time / date, plant shade), FR-039 (irrigation) and FR-040 (how many fit). */
class ShadeIrrigationTest {

    private fun seed(code: String, name: String, family: String, radius: Float) =
        SeedEntity(botanicalCode = code, commonName = name, botanicalFamily = family, exclusionRadiusM = radius, plantType = "VEGETABLE")
    private val corn = seed("COR-001", "Sweet Corn - Silver Queen", "Poaceae", 0.2f)
    private val lettuce = seed("LET-001", "Lettuce - Buttercrunch", "Asteraceae", 0.15f)
    private val tomato = seed("TOM-001", "Tomato - Brandywine", "Solanaceae", 0.3f)
    private val lookup: (String) -> SeedEntity? = { c -> listOf(corn, lettuce, tomato).firstOrNull { it.botanicalCode == c } }
    private val plot = PlotEntity(id = 1, name = "P", lengthM = 10f, widthM = 10f, latitude = 40.0, orientationSet = true)

    @Test
    fun tallPlantsShadeTheirNorthernNeighbours() {
        val cornRow = (0 until 5).map { PlantedNodeEntity(plotId = 1, seedCode = "COR-001", coordinateXM = 3f + it * 0.4f, coordinateYM = 5f) }
        val barriers = ShadeTools.plantBarriers(cornRow, lookup)
        assertEquals(5, barriers.size) // corn 2.2 m casts shade
        assertTrue(ShadeTools.plantBarriers(listOf(PlantedNodeEntity(plotId = 1, seedCode = "LET-001", coordinateXM = 1f, coordinateYM = 1f)), lookup).isEmpty()) // lettuce too low
        // Top faces north: north of the corn (smaller y) loses sun, south of it doesn't.
        val north = SunlightEngine.directSunHours(3.8f, 4.3f, 40.0, 355, 0f, barriers)
        val south = SunlightEngine.directSunHours(3.8f, 5.7f, 40.0, 355, 0f, barriers)
        assertTrue("north $north < south $south", north < south - 1.0)
    }

    @Test
    fun shadowAtATimeAndSunWindowsFollowTheSun() {
        val wall = listOf(Barrier(SiteFeatureType.WALL, listOf(PlotPoint(0f, 5f), PlotPoint(10f, 5f)), 2f, 0f))
        // Morning sun comes from the east: a wall along x = 5 (north-south) shades its west side in the morning only.
        val nsWall = listOf(Barrier(SiteFeatureType.WALL, listOf(PlotPoint(5f, 0f), PlotPoint(5f, 10f)), 2f, 0f))
        val morning = ShadeTools.shadowGridAt(10f, 10f, 10, 10, 40.0, 172, 0f, nsWall, 8.0)
        val afternoon = ShadeTools.shadowGridAt(10f, 10f, 10, 10, 40.0, 172, 0f, nsWall, 16.0)
        val westOfWall = 5 * 10 + 4 // row 5, column 4 (x 4.5)
        val eastOfWall = 5 * 10 + 5 // x 5.5
        assertTrue(morning[westOfWall] && !morning[eastOfWall])
        assertTrue(!afternoon[westOfWall] && afternoon[eastOfWall])
        val w = ShadeTools.sunWindows(4.5f, 5f, 40.0, 172, 0f, nsWall)
        assertTrue("west of the wall gets afternoon sun only: $w", w.isNotEmpty() && w.first().first >= 10.5)
        assertTrue(ShadeTools.describeWindows(w).startsWith("Sun 1"))
        val (rise, set) = ShadeTools.sunriseSunset(40.0, 172)
        assertTrue(rise < 5.0 && set > 19.0)
        assertEquals(355, ShadeDay.MIDWINTER.dayOfYear(40.0, 100)); assertEquals(172, ShadeDay.MIDWINTER.dayOfYear(-33.0, 100))
        assertTrue(wall.isNotEmpty())
    }

    private fun sprinkler(x: Float, y: Float, r: Float, arcCentre: Float = 0f, arc: Float = 360f) =
        SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.SPRINKLER.name, pointsJson = "$x,$y", radiusM = r, slopeDirectionDeg = arcCentre, slopeGradePct = arc)

    @Test
    fun sprinklerDripAndHoseCoverage() {
        val full = sprinkler(5f, 5f, 2f)
        assertTrue(Irrigation.sprinklerCovers(full, 6f, 5f, 0f)); assertTrue(!Irrigation.sprinklerCovers(full, 7.5f, 5f, 0f))
        // Half circle facing south (bearing 180). Top faces north, so south = larger y.
        val half = sprinkler(5f, 5f, 2f, 180f, 180f)
        assertTrue(Irrigation.sprinklerCovers(half, 5f, 6f, 0f)); assertTrue(!Irrigation.sprinklerCovers(half, 5f, 4f, 0f))
        val drip = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.DRIP_LINE.name, pointsJson = "1,8;9,8", radiusM = 0.3f)
        assertTrue(Irrigation.dripCovers(drip, 4f, 8.2f)); assertTrue(!Irrigation.dripCovers(drip, 4f, 8.5f))
        val tap = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.HOSE_BIB.name, pointsJson = "0,0", radiusM = 5f)
        val features = listOf(half, drip, tap)
        assertEquals(WaterSource.DRIP, Irrigation.sourceAt(4f, 8.1f, features, 0f))
        assertEquals(WaterSource.SPRINKLER, Irrigation.sourceAt(5f, 6f, features, 0f))
        assertEquals(WaterSource.HOSE, Irrigation.sourceAt(2f, 2f, features, 0f))
        assertEquals(WaterSource.MANUAL, Irrigation.sourceAt(9f, 2f, features, 0f))
        val nodes = listOf(PlantedNodeEntity(plotId = 1, seedCode = "TOM-001", coordinateXM = 5f, coordinateYM = 6f), PlantedNodeEntity(plotId = 1, seedCode = "LET-001", coordinateXM = 9f, coordinateYM = 2f))
        val report = Irrigation.report(plot, nodes, features, lookup)
        assertTrue(report.any { it.startsWith("⚠ 1 plant can only be watered by hand") })
        assertTrue(report.any { it.startsWith("Sprinklers wet the leaves of Tomato") })
    }

    @Test
    fun irrigationTravelsInPlanFiles() {
        val features = listOf(sprinkler(5f, 5f, 3f, 90f, 120f),
            SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.HOSE_BIB.name, pointsJson = "0,0", radiusM = 25f))
        val text = PlanFileCodec.encode(PlanBundle(listOf(PlanPlot(plot, emptyList(), emptyList(), features)), emptyList(), 0L), lookup, "t")
        val back = PlanFileCodec.decode(text).bundle!!.plots[0].features
        assertEquals(2, back.size)
        val s = back.first { it.featureType == "SPRINKLER" }
        assertEquals(3f, s.radiusM); assertEquals(90f, s.slopeDirectionDeg); assertEquals(120f, s.slopeGradePct)
        assertEquals(25f, back.first { it.featureType == "HOSE_BIB" }.radiusM)
    }

    @Test
    fun howManyFitKeepsTheProportions() {
        val ctx = PlotContext(plot, emptyList(), emptyList(), lookup, dayOfYear = 172)
        val fit = RotationPlanner.howManyFit(ctx, PlotShape.effectiveOutline(plot), listOf(PlantRequest(corn, 2), PlantRequest(lettuce, 1)))
        val c = fit.first { it.seed == corn }.count; val l = fit.first { it.seed == lettuce }.count
        assertTrue("corn $c, lettuce $l", c > 20 && l > 10)
        assertTrue("roughly 2:1, got $c:$l", c.toDouble() / l in 1.3..3.0)
        // Everything it says fits really fits.
        val r = AutoPlanner.plan(ctx, PlotShape.effectiveOutline(plot), fit, layout = PlantingLayout.CLUMPS)
        assertTrue("unplaced ${r.unplaced}", r.unplaced.isEmpty())
    }
}
