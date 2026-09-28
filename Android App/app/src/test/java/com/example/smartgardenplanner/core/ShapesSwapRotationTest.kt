package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Host tests for FR-047 (clump arrangements), FR-048 (rotation plan changes), FR-049 (photo turn) and FR-051 (replace all). */
class ShapesSwapRotationTest {

    private fun seed(code: String, name: String, family: String, radius: Float) =
        SeedEntity(botanicalCode = code, commonName = name, botanicalFamily = family, exclusionRadiusM = radius, plantType = "VEGETABLE")
    private val corn = seed("COR-001", "Sweet Corn - Silver Queen", "Poaceae", 0.2f)
    private val tomato = seed("TOM-001", "Tomato - Brandywine", "Solanaceae", 0.3f)
    private val paste = seed("TOM-003", "Tomato - San Marzano", "Solanaceae", 0.3f)
    private val bigTomato = seed("TOM-009", "Tomato - Giant", "Solanaceae", 0.6f)
    private val bean = seed("BEA-001", "Bush Bean - Provider", "Fabaceae", 0.15f)
    private val all = listOf(corn, tomato, paste, bigTomato, bean)
    private val lookup: (String) -> SeedEntity? = { c -> all.firstOrNull { it.botanicalCode == c } }

    @Test
    fun fiftyPlantsCanBeArrangedTheWayTheGardenerLikes() {
        val labels = ClumpShapes.options(50).map { it.label }
        listOf("5 rows of 10", "10 rows of 5", "7 rows of 7 + 1 row of 1", "6 rows of 8 + 1 row of 2", "1 row of 50").forEach {
            assertTrue("$it in $labels", it in labels)
        }
        assertTrue(ClumpShapes.options(50).all { it.count == 50 })
        val nearby = ClumpShapes.nearbyTidy(50).associate { it.count to it.label }
        assertEquals("7 rows of 7", nearby[49])
        assertEquals("6 rows of 8", nearby[48])
        assertEquals("4 rows of 5", ClumpShapes.label(listOf(5, 5, 5, 5)))
    }

    @Test
    fun theChosenArrangementIsUsed_andASplitIsExplained() {
        val plot = PlotEntity(id = 1, name = "P", lengthM = 12f, widthM = 8f, latitude = 40.0, orientationSet = true)
        val ctx = PlotContext(plot, emptyList(), emptyList(), lookup)
        val area = PlotShape.effectiveOutline(plot)
        val tenByFive = AutoPlanner.plan(ctx, area, listOf(PlantRequest(corn, 50, shape = List(5) { 10 })))
        assertEquals(50, tenByFive.placed.size)
        assertTrue(tenByFive.notes.joinToString(), tenByFive.notes.any { it.contains("Sweet Corn: 50 in 5 rows of 10") })
        // Distinct row positions (depth) = 5 when rows run along x (top faces north: depth is y).
        assertEquals(5, tenByFive.placed.map { (it.y * 100).toInt() }.distinct().size)

        // A strip too narrow for one block of 12 tomatoes: they're split, and the note says why.
        val strip = PlotEntity(id = 2, name = "S", lengthM = 3f, widthM = 8f, latitude = 40.0, orientationSet = true)
        val split = AutoPlanner.plan(PlotContext(strip, emptyList(), emptyList(), lookup), PlotShape.effectiveOutline(strip), listOf(PlantRequest(tomato, 12, shape = listOf(6, 6))))
        assertTrue(split.notes.joinToString(), split.notes.any { it.startsWith("Tomato is in") && it.contains("your arrangement (2 rows of 6)") })
    }

    @Test
    fun rotationPlanCanSwapAVarietyFromAChosenYearOn() {
        val list = listOf(PlantRequest(tomato, 4), PlantRequest(bean, 10))
        assertEquals(list, RotationPlanner.requestsFor(list, emptyMap(), 2027))
        val changes = mapOf(2029 to mapOf("TOM-001" to paste))
        assertEquals("TOM-001", RotationPlanner.requestsFor(list, changes, 2028).first().seed.botanicalCode)
        assertEquals("TOM-003", RotationPlanner.requestsFor(list, changes, 2030).first().seed.botanicalCode)
        val plot = PlotEntity(id = 1, name = "P", lengthM = 8f, widthM = 6f, latitude = 40.0, orientationSet = true)
        val plans = RotationPlanner.planSeasons(PlotContext(plot, emptyList(), emptyList(), lookup), PlotShape.effectiveOutline(plot), list, emptyList(), 2027, 12, changes = changes)
        assertEquals(12, plans.size)
        assertTrue(plans.first { it.year == 2028 }.result.placed.all { it.seed != paste })
        assertTrue(plans.first { it.year == 2029 }.result.placed.any { it.seed == paste })
        assertTrue(plans.first { it.year == 2029 }.result.placed.none { it.seed == tomato })
    }

    @Test
    fun photoTurnStaysWithin180EitherWayAndTurnsAboutItsCentre() {
        assertEquals(-170f, Backdrop.normalizeDeg(190f), 1e-4f)
        assertEquals(160f, Backdrop.normalizeDeg(-200f), 1e-4f)
        assertEquals(180f, Backdrop.normalizeDeg(180f), 1e-4f)
        val b = Backdrop(xM = 0f, yM = 0f, widthM = 10f, aspect = 0.5f).turned(180f)
        assertEquals(180f, Backdrop.parse(b.encode())!!.rotationDeg, 1e-3f)
        assertEquals("30° clockwise", Backdrop.describeTurn(30f))
        assertEquals("45° counterclockwise", Backdrop.describeTurn(-45f))
        // Old files stored 0–360: 350 reads as 10° counter-clockwise.
        assertEquals(-10f, Backdrop.parse("0;0;10;350;0.6;0.5")!!.rotationDeg, 1e-3f)
        // Scaling about a point keeps that point fixed even when turned.
        val t = Backdrop(xM = 1f, yM = 1f, widthM = 10f, aspect = 0.5f, rotationDeg = 30f)
        val cal = t.calibrate(PlotPoint(3f, 3f), PlotPoint(5f, 3f), 4f)!!
        assertEquals(20f, cal.widthM, 1e-3f)
        assertEquals(3f + (t.centreX - 3f) * 2f, cal.centreX, 1e-3f)
        assertEquals(3f + (t.centreY - 3f) * 2f, cal.centreY, 1e-3f)
    }

    @Test
    fun replaceAllSwapsEveryPlantOfAVarietyInOneStep() {
        val nodes = listOf(
            PlantedNodeEntity(id = 1, plotId = 1, seedCode = "TOM-001", coordinateXM = 1f, coordinateYM = 1f),
            PlantedNodeEntity(id = 2, plotId = 1, seedCode = "TOM-001", coordinateXM = 2f, coordinateYM = 1f),
            PlantedNodeEntity(id = 3, plotId = 1, seedCode = "BEA-001", coordinateXM = 4f, coordinateYM = 4f)
        )
        val same = PlantSwap.replaceAll(nodes, "TOM-001", paste, lookup, null)
        assertEquals(2, same.changed); assertEquals(0, same.crowded)
        assertEquals(listOf("TOM-003", "TOM-003", "BEA-001"), same.nodes.map { it.seedCode })
        assertEquals(nodes.map { it.coordinateXM }, same.nodes.map { it.coordinateXM })
        // A bigger variety crowds its neighbour and says so; nothing is moved.
        val big = PlantSwap.replaceAll(nodes, "TOM-001", bigTomato, lookup, null)
        assertEquals(2, big.crowded)
        assertTrue(big.messages.joinToString(), big.messages.any { it.contains("120 cm between plants (was 60 cm)") })
        assertEquals(0, PlantSwap.replaceAll(nodes, "TOM-001", tomato, lookup, null).changed)
        assertTrue(abs(big.nodes[0].coordinateXM - 1f) < 1e-6f)
    }
}
