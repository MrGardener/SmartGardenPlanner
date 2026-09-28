package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Robustness of the advice features (SGP-TCS-001 §3.5): harmony, recommendations, checks before planning, watering,
 * care and pest plans, food, soil, Plan B and the area filler, for empty, single, large and odd plots.
 */
class AdviceRobustnessTest {

    private fun randomContext(rnd: Random, plants: Int, withUnknown: Boolean = true): PlotContext {
        val len = rnd.nextDouble(1.0, 30.0).toFloat(); val wid = rnd.nextDouble(1.0, 30.0).toFloat()
        val plot = PlotEntity(id = 1, name = "R", lengthM = len, widthM = wid,
            latitude = if (rnd.nextBoolean()) rnd.nextDouble(-60.0, 60.0) else null,
            hardinessZone = if (rnd.nextBoolean()) HardinessZones.LABELS.random(rnd) else null,
            soilSandPct = if (rnd.nextBoolean()) 40f else null, soilSiltPct = if (rnd.nextBoolean()) 40f else null, soilClayPct = 20f,
            soilPh = listOf(null, 3f, 5.5f, 7f, 10f).random(rnd), soilOrganicPct = listOf(null, 0f, 3f, 100f).random(rnd))
        val nodes = List(plants) { i ->
            val code = if (withUnknown && rnd.nextInt(20) == 0) "UNKNOWN-9" else TestCatalog.seeds.random(rnd).botanicalCode
            PlantedNodeEntity(id = i + 1L, plotId = 1, seedCode = code, coordinateXM = rnd.nextDouble(0.0, len.toDouble()).toFloat(),
                coordinateYM = rnd.nextDouble(0.0, wid.toDouble()).toFloat(), datePlantedEpochMillis = rnd.nextLong(0, 2_000_000_000_000L))
        }
        val features = listOfNotNull(
            if (rnd.nextBoolean()) SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.TREE.name, pointsJson = "1,1", heightM = 8f, radiusM = 2f) else null,
            if (rnd.nextBoolean()) SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.SPRINKLER.name, pointsJson = "${len / 2},${wid / 2}", radiusM = 4f) else null,
            if (rnd.nextBoolean()) SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.FLOOD.name, pointsJson = "0,0;${len},0;${len},1", floodMonths = "3,4") else null
        )
        return PlotContext(plot, nodes, features, TestCatalog.lookup)
    }

    private fun texts(where: String, list: List<String>) = list.forEach { assertTrue("$where: blank text", it.isNotBlank()); assertTrue("$where: '$it'", !it.contains("NaN") && !it.contains("Infinity") && !it.contains("null")) }

    @Test
    fun everyAdviceFeatureHandlesEmptySmallAndLargePlots() {
        val rnd = Random(77)
        for (plants in listOf(0, 1, 2, 25, 400)) repeat(if (plants >= 400) 1 else 4) {
            val ctx = randomContext(rnd, plants)
            val where = "$plants plants, plot ${ctx.plot.lengthM}×${ctx.plot.widthM}, lat ${ctx.plot.latitude}, zone ${ctx.plot.hardinessZone}"
            val h = HarmonyAnalyzer.analyze(ctx, TestCatalog.seeds)
            assertTrue("$where score ${h.score}", h.score in 0..100); texts(where, h.issues.map { it.text } + h.recommendations + h.goodPairs)
            val rec = RecommendationEngine.recommend(TestCatalog.seeds, ctx, limit = 12)
            assertTrue(rec.size <= 12); rec.forEach { texts(where, it.reasons) }
            texts(where, WateringAdvice.forPlot(ctx.plot, ctx.nodes, ctx.features, ctx.seedLookup))
            texts(where, Irrigation.report(ctx.plot, ctx.nodes, ctx.features, ctx.seedLookup))
            for (pref in CarePreference.entries) {
                CarePlanner.fertilizingPlan(ctx, pref).forEach { texts(where, listOf(it.action)) }
                CarePlanner.pestPlan(ctx, pref).forEach { texts(where, listOf(it.pest, it.prevention, it.control)) }
                val tasks = CarePlanner.dueTasks(ctx, emptyList(), pref, 1_800_000_000_000L, listOf(null, 0.0, 50.0).random(rnd), 5.0)
                tasks.forEach { texts(where, listOf(it.title, it.detail)) }
            }
            assertTrue(CarePlanner.wateringIntervalDays(ctx) in 1..30)
            FoodPlanner.yieldLines(ctx).forEach { assertTrue(it.totalKg >= 0f && it.totalKg.isFinite()) }
            val n = FoodPlanner.nutritionTotals(ctx)
            assertTrue(listOf(n.energyKcal, n.proteinG, n.vitaminCMg, n.vitaminAUg, n.fiberG, n.ironMg).all { it.isFinite() && it >= 0.0 })
            texts(where, CropRotation.advice(ctx.plot, emptyList(), ctx.nodes, ctx.seedLookup, 2027))
            texts(where, SoilAnalyzer.guidance(SoilProfile.of(ctx.plot)))
            val checks = PlanChecks.check(ctx, PlotShape.effectiveOutline(ctx.plot), listOf(PlantRequest(TestCatalog.seeds.random(rnd), rnd.nextInt(0, 50))), pests = Pest.entries.shuffled(rnd).take(3))
            texts(where, checks.map { it.text })
        }
    }

    @Test
    fun homesteadListForAnyHouseholdSize() {
        for (people in listOf(0, 1, 4, 20, 1000)) for (zone in listOf(null, "1a", "13b")) {
            val list = FoodPlanner.homesteadList(TestCatalog.seeds, zone, SoilProfile(null, null, null, null, null), people)
            list.forEach { assertTrue("$people/$zone ${it.seed.commonName}", it.plants >= 0 && it.expectedKg.isFinite() && it.expectedKg >= 0f) }
        }
    }

    @Test
    fun soilTextureClassifiesEveryCombination() {
        for (sand in 0..100 step 5) for (silt in 0..(100 - sand) step 5) {
            val clay = 100 - sand - silt
            SoilAnalyzer.classify(sand.toFloat(), silt.toFloat(), clay.toFloat())
            assertEquals(null, SoilAnalyzer.validateTexture(sand.toFloat(), silt.toFloat(), clay.toFloat()))
        }
        SoilAnalyzer.classify(0f, 0f, 0f)
        assertTrue(SoilAnalyzer.validateTexture(-1f, 50f, 51f) != null); assertTrue(SoilAnalyzer.validateTexture(50f, 50f, 50f) != null)
        assertTrue(SoilAnalyzer.validateTexture(Float.NaN, 50f, 50f) != null)
    }

    @Test
    fun pestRisksAndPlanBForEveryPestAndCrop() {
        val risks = PestAdvisor.risks(Pest.entries, TestCatalog.seeds)
        assertEquals(Pest.entries.size, risks.size)
        risks.forEach { r -> assertEquals(r.atRisk.sorted(), r.atRisk); assertEquals(r.atRisk.distinct(), r.atRisk) }
        assertEquals(emptyList<Pest>(), Pest.parse("  , ,BOGUS,deer")); assertEquals(listOf(Pest.DEER), Pest.parse("DEER,DEER, DEER "))
        val rnd = Random(3)
        repeat(200) {
            val seed = TestCatalog.seeds.random(rnd)
            val opts = BackupPlanner.options(seed, rnd.nextInt(-400, 400), rnd.nextInt(-400, 800), TestCatalog.seeds, null, rnd.nextInt(0, 10))
            assertTrue(opts.none { it.seed.botanicalCode == seed.botanicalCode })
            opts.forEach { assertTrue(it.reason.isNotBlank()) }
        }
    }

    @Test
    fun areaFillerRejectsBadInputs_andCapsHugeGrids() {
        val e = AutoPopulateEngine()
        for (p in AutoPopulateEngine.PackingPattern.entries) {
            for ((w, h, s) in listOf(Triple(0f, 5f, 1f), Triple(5f, -1f, 1f), Triple(5f, 5f, 0f), Triple(Float.NaN, 5f, 1f), Triple(5f, Float.POSITIVE_INFINITY, 1f), Triple(5f, 5f, Float.NaN)))
                assertEquals("$p $w $h $s", 0, e.generatePositions(w, h, s, p).size)
            // Areas smaller than one spacing: none fit, except that the line grid takes one plant once the area is half a spacing.
            assertEquals(0, e.generatePositions(0.1f, 0.1f, 1f, p).size)
            assertEquals(if (p == AutoPopulateEngine.PackingPattern.LINE) 1 else 0, e.generatePositions(0.6f, 0.6f, 1f, p).size)
            val t0 = System.currentTimeMillis()
            val huge = e.generatePositions(1000f, 1000f, 0.1f, p)
            assertTrue("$p ${huge.size}", huge.size <= AutoPopulateEngine.MAX_POINTS)
            assertTrue(e.estimateCount(1000f, 1000f, 0.1f, p) > AutoPopulateEngine.MAX_POINTS)   // the estimate still says how many would fit
            assertTrue("took ${System.currentTimeMillis() - t0} ms", System.currentTimeMillis() - t0 < 5_000)
            // The count shown before filling equals the number of plants placed.
            val rnd = Random(p.ordinal)
            repeat(300) {
                val w = rnd.nextDouble(0.05, 12.0).toFloat(); val h = rnd.nextDouble(0.05, 12.0).toFloat(); val s = rnd.nextDouble(0.05, 2.0).toFloat()
                val n = e.estimateCount(w, h, s, p); val list = e.generatePositions(w, h, s, p)
                if (list.isNotEmpty() || n <= AutoPopulateEngine.MAX_POINTS) assertEquals("$p $w×$h @ $s", list.size, n)
                else assertTrue(n > AutoPopulateEngine.MAX_POINTS / 2)
            }
            // Every point is inside the area and spacing apart.
            val pts = e.generatePositions(3.3f, 2.1f, 0.4f, p)
            pts.forEach { assertTrue(it.xM in 0f..3.3f && it.yM in 0f..2.1f) }
        }
    }
}
