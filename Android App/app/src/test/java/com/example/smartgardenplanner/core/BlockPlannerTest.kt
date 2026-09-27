package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Host tests for FR-035: organised clumps (rows × columns with walkways) and room for vines to run toward the sun. */
class BlockPlannerTest {

    private fun seed(code: String, name: String, family: String, radius: Float) =
        SeedEntity(botanicalCode = code, commonName = name, botanicalFamily = family, exclusionRadiusM = radius, plantType = "VEGETABLE")

    private val corn = seed("COR-001", "Sweet Corn - Silver Queen", "Poaceae", 0.2f)
    private val tomato = seed("TOM-001", "Tomato - Brandywine", "Solanaceae", 0.3f)
    private val lettuce = seed("LET-001", "Lettuce - Buttercrunch", "Asteraceae", 0.15f)
    private val melon = seed("WAT-001", "Watermelon - Sugar Baby", "Cucurbitaceae", 0.5f)
    private val bean = seed("BEA2-001", "Pole Bean - Kentucky Wonder", "Fabaceae", 0.15f)
    private val all = listOf(corn, tomato, lettuce, melon, bean)
    private val lookup: (String) -> SeedEntity? = { c -> all.firstOrNull { it.botanicalCode == c } }

    // Top edge faces north (bearing 0), northern hemisphere: back = small y, sunny front = large y.
    private val plot = PlotEntity(id = 1, name = "P", lengthM = 10f, widthM = 8f, latitude = 40.0, orientationSet = true)
    private val whole = listOf(PlotPoint(0f, 0f), PlotPoint(10f, 0f), PlotPoint(10f, 8f), PlotPoint(0f, 8f))
    private fun ctx() = PlotContext(plot, emptyList(), emptyList(), lookup, dayOfYear = 172)
    private fun plan(vararg r: PlantRequest) = AutoPlanner.plan(ctx(), whole, r.toList(), layout = PlantingLayout.CLUMPS)

    @Test
    fun shapesAreRowsByColumns() {
        assertEquals(4 to 5, BlockPlanner.shape(20))
        assertEquals(listOf(4, 3), BlockPlanner.rowSizes(7))
        assertEquals(listOf(3, 3, 3), BlockPlanner.rowSizes(9))
        assertEquals(listOf(1), BlockPlanner.rowSizes(1))
    }

    @Test
    fun twentyCornAreFourRowsOfFive_evenlySpaced() {
        val r = plan(PlantRequest(corn, 20))
        assertEquals(20, r.placed.size)
        val rows = r.placed.groupBy { (it.y * 100).toInt() }
        assertEquals(4, rows.size)
        rows.values.forEach { assertEquals(5, it.size) }
        val xs = rows.values.first().map { it.x }.sorted()
        val gaps = xs.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { abs(it - gaps[0]) < 0.01f })
        assertTrue(r.notes.first().contains("Sweet Corn: 20 in 4 rows of 5"))
    }

    @Test
    fun sevenTomatoesAreARowOfFourAndARowOfThree() {
        val r = plan(PlantRequest(tomato, 7))
        val rows = r.placed.groupBy { (it.y * 100).toInt() }.values.map { it.size }.sortedDescending()
        assertEquals(listOf(4, 3), rows)
        assertTrue(r.notes.first().contains("rows of 4 + 3"))
    }

    @Test
    fun clumpsOfDifferentCropsKeepAWalkway() {
        val r = plan(PlantRequest(corn, 12), PlantRequest(tomato, 6), PlantRequest(lettuce, 9))
        assertEquals(27, r.placed.size)
        for (a in r.placed) for (b in r.placed) {
            if (a.seed == b.seed) continue
            val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
            assertTrue("${a.seed.commonName}–${b.seed.commonName} only $d m apart", d >= a.seed.exclusionRadiusM + b.seed.exclusionRadiusM + BlockPlanner.WALKWAY_M - 0.01f)
        }
        fun meanY(s: SeedEntity) = r.placed.filter { it.seed == s }.map { it.y }.average()
        assertTrue("corn behind lettuce", meanY(corn) < meanY(lettuce))
    }

    @Test
    fun vinesGoToTheSunnyEdgeWithARunwayTowardTheSun_climbersToTheBack() {
        val r = plan(PlantRequest(melon, 2), PlantRequest(bean, 6), PlantRequest(lettuce, 6))
        fun meanY(s: SeedEntity) = r.placed.filter { it.seed == s }.map { it.y }.average()
        // The melon sits in the sunny (south, large y) half, leaving its runway free up to the south edge.
        assertTrue("melon in the sunny half", meanY(melon) > plot.widthM / 2)
        assertTrue("pole beans at the back", meanY(bean) < meanY(lettuce) && meanY(bean) < meanY(melon))
        val guide = r.guides.single()
        assertTrue("runway points south (toward larger y)", guide.to.y > guide.from.y)
        // Nothing else was planted in the runway.
        val minX = guide.area.minOf { it.x }; val maxX = guide.area.maxOf { it.x }
        val minY = guide.area.minOf { it.y }; val maxY = guide.area.maxOf { it.y }
        assertTrue(r.placed.filter { it.seed != melon }.none { it.x in minX..maxX && it.y in minY..maxY })
        assertTrue(r.notes.any { it.startsWith("Watermelon vines run toward the sun") })
        assertTrue(r.notes.any { it.contains("trellis") })
    }
}
