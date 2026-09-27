package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** Host tests for FR-037: plan next season / several seasons with strict crop rotation. */
class RotationPlannerTest {

    private fun seed(code: String, name: String, family: String, radius: Float) =
        SeedEntity(botanicalCode = code, commonName = name, botanicalFamily = family, exclusionRadiusM = radius, plantType = "VEGETABLE")

    private val tomato = seed("TOM-001", "Tomato - Brandywine", "Solanaceae", 0.3f)
    private val bean = seed("BEA-001", "Bush Bean - Provider", "Fabaceae", 0.15f)
    private val onion = seed("ONI-001", "Onion - Yellow Sweet Spanish", "Amaryllidaceae", 0.08f)
    private val cabbage = seed("CAB-001", "Cabbage - Golden Acre", "Brassicaceae", 0.3f)
    private val all = listOf(tomato, bean, onion, cabbage)
    private val lookup: (String) -> SeedEntity? = { c -> all.firstOrNull { it.botanicalCode == c } }

    private val plot = PlotEntity(id = 1, name = "P", lengthM = 8f, widthM = 6f, latitude = 40.0, orientationSet = true)
    private val whole = PlotShape.effectiveOutline(plot)
    private val ctx = PlotContext(plot, emptyList(), emptyList(), lookup, dayOfYear = 172)
    private val list = listOf(PlantRequest(tomato, 6), PlantRequest(bean, 12), PlantRequest(onion, 16), PlantRequest(cabbage, 4))

    @Test
    fun fiveSeasonsNeverPutAFamilyWhereItGrewTheYearBefore() {
        val plans = RotationPlanner.planSeasons(ctx, whole, list, emptyList(), 2027, 5)
        assertEquals(listOf(2027, 2028, 2029, 2030, 2031), plans.map { it.year })
        plans.forEach { assertEquals("every plant placed in ${it.year}: ${it.result.unplaced}", 38, it.result.placed.size) }
        for (i in 1 until plans.size) {
            val before = plans[i - 1].result.placed; val now = plans[i].result.placed
            for (p in now) {
                val g = RotationGroup.forSeed(p.seed)
                for (q in before.filter { RotationGroup.forSeed(it.seed) == g }) {
                    val reach = maxOf(q.seed.exclusionRadiusM * 1.5f, 0.6f) + p.seed.exclusionRadiusM * 0.5f
                    val d = sqrt((p.x - q.x) * (p.x - q.x) + (p.y - q.y) * (p.y - q.y))
                    assertTrue("${plans[i].year}: ${p.seed.commonName} at ${p.x},${p.y} is where ${q.seed.commonName} grew in ${plans[i - 1].year}", d >= reach - 0.001f)
                }
            }
        }
        assertTrue(plans.all { it.summary.isNotEmpty() })
        // Deterministic: the same inputs give the same plan.
        assertEquals(plans.map { it.result.placed }, RotationPlanner.planSeasons(ctx, whole, list, emptyList(), 2027, 5).map { it.result.placed })
    }

    @Test
    fun lastListComesFromThisSeasonOrElseTheLastClosedSeason() {
        val nodes = listOf(PlantedNodeEntity(plotId = 1, seedCode = "TOM-001", coordinateXM = 1f, coordinateYM = 1f), PlantedNodeEntity(plotId = 1, seedCode = "TOM-001", coordinateXM = 2f, coordinateYM = 1f))
        assertEquals(listOf(PlantRequest(tomato, 2)), RotationPlanner.lastList(nodes, emptyList(), lookup))
        val history = Seasons.archive(1, nodes, 2026, lookup) + Seasons.archive(1, nodes.take(1), 2025, lookup)
        assertEquals(listOf(PlantRequest(tomato, 2)), RotationPlanner.lastList(emptyList(), history, lookup))
    }

    @Test
    fun strictRuleIsRelaxedOnlyWhenThereIsNoRoom_andSaysSo() {
        // Last year tomatoes covered the whole small plot: this year they can only go back there.
        val tiny = PlotEntity(id = 2, name = "T", lengthM = 1.2f, widthM = 1.2f, latitude = 40.0, orientationSet = true)
        val tinyCtx = PlotContext(tiny, emptyList(), emptyList(), lookup, dayOfYear = 172)
        val last = Seasons.archive(2, listOf(PlantedNodeEntity(plotId = 2, seedCode = "TOM-001", coordinateXM = 0.6f, coordinateYM = 0.6f)), 2026, lookup)
        val r = AutoPlanner.plan(tinyCtx, PlotShape.effectiveOutline(tiny), listOf(PlantRequest(tomato, 1)), history = last, seasonYear = 2027)
        assertEquals(1, r.placed.size)
        assertTrue(r.notes.any { it.startsWith("Not enough room to keep Tomato off last season's spots") })
    }
}
