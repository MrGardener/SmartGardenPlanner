package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Property tests for Plan an area for me (SGP-TCS-001 §3.2): random plots, areas, lists, directions, latitudes and
 * obstacles, plus the minimum and maximum cases. Whatever the input, the rules below must hold for every layout.
 */
class PlannerPropertyTest {

    private val validator = CompanionPlantingValidator()

    private fun antagonists(a: SeedEntity, b: SeedEntity): Boolean {
        val pa = a.botanicalCode.substringBefore('-'); val pb = b.botanicalCode.substringBefore('-')
        return pb in a.antagonistCodes.split(',').map { it.trim() } || pa in b.antagonistCodes.split(',').map { it.trim() }
    }

    /** The rules every proposal must obey. */
    private fun checkInvariants(where: String, ctx: PlotContext, area: List<PlotPoint>, requests: List<PlantRequest>, r: AutoPlanResult,
                                margin: Float = 1f, blocked: (Float, Float, Float) -> Boolean = { _, _, _ -> false }) {
        val plot = ctx.plot
        r.placed.forEach { p ->
            assertTrue("$where: ${p.seed.commonName} at (${p.x}, ${p.y}) is outside the plot", PlotShape.contains(plot, p.x, p.y))
            assertTrue("$where: ${p.seed.commonName} at (${p.x}, ${p.y}) is outside the area", PlotGeometry.pointInPolygon(p.x, p.y, area))
            assertTrue("$where: ${p.seed.commonName} on a path", !blocked(p.x, p.y, p.seed.exclusionRadiusM))
        }
        val all = r.placed.map { Triple(it.seed, it.x, it.y) } + ctx.nodes.mapNotNull { n -> ctx.seedLookup(n.seedCode)?.let { Triple(it, n.coordinateXM, n.coordinateYM) } }
        for (i in r.placed.indices) for (j in i + 1 until all.size) {
            val (a, ax, ay) = all[i]; val (b, bx, by) = all[j]
            val d = hypot((ax - bx).toDouble(), (ay - by).toDouble())
            val need = (a.exclusionRadiusM + b.exclusionRadiusM) * margin.toDouble()
            assertTrue("$where: ${a.commonName} and ${b.commonName} ${"%.3f".format(d)} m apart, need ${"%.3f".format(need)}", d >= need - 1e-3)
            if (antagonists(a, b)) assertTrue("$where: antagonists ${a.commonName} / ${b.commonName} too close", d >= 2 * need - 1e-3)
        }
        // Every requested plant is either placed or reported as not fitting.
        val want = requests.filter { it.count > 0 }.groupBy { CropReference.speciesName(it.seed) }.mapValues { e -> e.value.sumOf { it.count } }
        want.forEach { (species, n) ->
            val got = r.placed.count { CropReference.speciesName(it.seed) == species }
            assertEquals("$where: $species placed $got + unplaced ${r.unplaced[species]} vs requested $n", n, got + (r.unplaced[species] ?: 0))
        }
        assertTrue("$where: no explanation", r.notes.isNotEmpty())
        // Vine runways stay inside the plot unless the proposal says there was no free ground.
        if (r.notes.none { it.startsWith("There was no completely free ground") }) r.guides.forEach { g ->
            g.area.forEach { p -> assertTrue("$where: ${g.species} runway leaves the plot at $p", p.x >= -0.01f && p.y >= -0.01f && p.x <= plot.lengthM + 0.01f && p.y <= plot.widthM + 0.01f) }
        }
    }

    private fun randomRequests(rnd: Random): List<PlantRequest> =
        List(rnd.nextInt(1, 7)) { PlantRequest(TestCatalog.vegetables.random(rnd), rnd.nextInt(0, 30), priority = rnd.nextInt(5) == 0) }

    @Test
    fun randomPlotsListsAndDirections_everyLayoutObeysTheRules() {
        val rnd = Random(20260928)
        repeat(30) { case ->
            val len = rnd.nextDouble(2.0, 25.0).toFloat(); val wid = rnd.nextDouble(2.0, 20.0).toFloat()
            val plot = PlotEntity(id = 1, name = "P$case", lengthM = len, widthM = wid, latitude = rnd.nextDouble(-60.0, 60.0),
                northBearingDeg = rnd.nextInt(0, 360).toFloat(), orientationSet = rnd.nextBoolean())
            val features = if (rnd.nextBoolean()) listOf(SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.FENCE.name,
                pointsJson = "0,${wid - 0.2f};$len,${wid - 0.2f}", heightM = 1.8f)) else emptyList()
            val ctx = PlotContext(plot, emptyList(), features, TestCatalog.lookup)
            // Area: the whole plot, a random rectangle, or a random triangle.
            val area = when (rnd.nextInt(3)) {
                0 -> PlotShape.effectiveOutline(plot)
                1 -> { val x0 = rnd.nextDouble(0.0, len / 2.0).toFloat(); val y0 = rnd.nextDouble(0.0, wid / 2.0).toFloat()
                    listOf(PlotPoint(x0, y0), PlotPoint(len, y0), PlotPoint(len, wid), PlotPoint(x0, wid)) }
                else -> listOf(PlotPoint(0f, 0f), PlotPoint(len, rnd.nextDouble(0.0, wid.toDouble()).toFloat()), PlotPoint(rnd.nextDouble(0.0, len.toDouble()).toFloat(), wid))
            }
            val path = rnd.nextBoolean()
            val blocked: (Float, Float, Float) -> Boolean = { x, y, r -> path && x + r > len * 0.45f && x - r < len * 0.55f }
            val margin = listOf(0.8f, 1f, 1.2f).random(rnd)
            val requests = randomRequests(rnd)
            val variant = rnd.nextInt(AutoPlanner.VARIANT_COUNT)
            val r = AutoPlanner.planVariant(variant, ctx, area, requests, blocked, margin, plot.orientationSet)
            checkInvariants("case $case (variant $variant)", ctx, area, requests, r, margin, blocked)
        }
    }

    @Test
    fun plantsAlreadyThereAreNeverOverlapped() {
        val rnd = Random(42)
        val plot = PlotEntity(id = 1, name = "P", lengthM = 10f, widthM = 8f, latitude = 42.0, orientationSet = true)
        val existing = List(15) { i -> PlantedNodeEntity(id = i + 1L, plotId = 1, seedCode = TestCatalog.named("Tomato - Brandywine").botanicalCode,
            coordinateXM = rnd.nextDouble(0.5, 9.5).toFloat(), coordinateYM = rnd.nextDouble(0.5, 7.5).toFloat()) }
        val ctx = PlotContext(plot, existing, emptyList(), TestCatalog.lookup)
        repeat(5) {
            val requests = randomRequests(rnd)
            checkInvariants("existing $it", ctx, PlotShape.effectiveOutline(plot), requests, AutoPlanner.plan(ctx, PlotShape.effectiveOutline(plot), requests))
        }
    }

    @Test
    fun minimumAndDegenerateInputs() {
        val squash = TestCatalog.named("Winter Squash - Spaghetti")
        val tiny = PlotEntity(id = 1, name = "T", lengthM = 0.5f, widthM = 0.5f, latitude = 42.0)
        val ctx = PlotContext(tiny, emptyList(), emptyList(), TestCatalog.lookup)
        val r = AutoPlanner.plan(ctx, PlotShape.effectiveOutline(tiny), listOf(PlantRequest(squash, 3)))
        assertEquals(0, r.placed.size); assertEquals(3, r.unplaced.values.sum()); assertTrue(r.notes.any { it.startsWith("Didn't fit") })
        // No plants, zero or negative counts, too few area corners, a flat area, an area outside the plot.
        val big = PlotEntity(id = 1, name = "B", lengthM = 10f, widthM = 10f, latitude = 42.0)
        val bctx = PlotContext(big, emptyList(), emptyList(), TestCatalog.lookup)
        val whole = PlotShape.effectiveOutline(big)
        assertEquals(listOf("Nothing to plan."), AutoPlanner.plan(bctx, whole, emptyList()).notes)
        assertEquals(listOf("Nothing to plan."), AutoPlanner.plan(bctx, whole, listOf(PlantRequest(squash, 0), PlantRequest(squash, -4))).notes)
        assertEquals(listOf("Nothing to plan."), AutoPlanner.plan(bctx, whole.take(2), listOf(PlantRequest(squash, 1))).notes)
        val flat = listOf(PlotPoint(1f, 1f), PlotPoint(5f, 1f), PlotPoint(9f, 1f))
        assertEquals(0, AutoPlanner.plan(bctx, flat, listOf(PlantRequest(squash, 2))).placed.size)
        val outside = listOf(PlotPoint(20f, 20f), PlotPoint(30f, 20f), PlotPoint(30f, 30f))
        val o = AutoPlanner.plan(bctx, outside, listOf(PlantRequest(squash, 2)))
        assertEquals(0, o.placed.size); assertEquals(2, o.unplaced.values.sum())
        // A chosen arrangement that doesn't add up to the count is ignored, not trusted.
        val corn = TestCatalog.named("Sweet Corn - Honey Select")
        val bad = AutoPlanner.plan(bctx, whole, listOf(PlantRequest(corn, 10, shape = listOf(3, 3))))
        checkInvariants("bad shape", bctx, whole, listOf(PlantRequest(corn, 10)), bad)
        val zeroRow = AutoPlanner.plan(bctx, whole, listOf(PlantRequest(corn, 6, shape = listOf(6, 0))))
        checkInvariants("zero row", bctx, whole, listOf(PlantRequest(corn, 6)), zeroRow)
    }

    @Test
    fun extremeLatitudesDirectionsAndMargins() {
        val list = listOf(PlantRequest(TestCatalog.named("Sweet Corn - Honey Select"), 12), PlantRequest(TestCatalog.named("Carrot - Danvers"), 20),
            PlantRequest(TestCatalog.named("Winter Squash - Spaghetti"), 2))
        for (lat in listOf(-89.9, -45.0, 0.0, 0.0001, 45.0, 89.9)) for (bearing in listOf(0f, 90f, 180f, 270f, 359.9f)) {
            val plot = PlotEntity(id = 1, name = "L", lengthM = 14f, widthM = 10f, latitude = lat, northBearingDeg = bearing, orientationSet = true)
            val ctx = PlotContext(plot, emptyList(), emptyList(), TestCatalog.lookup)
            checkInvariants("lat $lat bearing $bearing", ctx, PlotShape.effectiveOutline(plot), list, AutoPlanner.plan(ctx, PlotShape.effectiveOutline(plot), list))
        }
        for (margin in listOf(0.3f, 2f)) {
            val plot = PlotEntity(id = 1, name = "M", lengthM = 14f, widthM = 10f, latitude = 40.0, orientationSet = true)
            val ctx = PlotContext(plot, emptyList(), emptyList(), TestCatalog.lookup)
            checkInvariants("margin $margin", ctx, PlotShape.effectiveOutline(plot), list, AutoPlanner.plan(ctx, PlotShape.effectiveOutline(plot), list, marginMultiplier = margin), margin)
        }
    }

    @Test
    fun largePlotAndLargeCountsFinishInReasonableTime() {
        val plot = PlotEntity(id = 1, name = "Farm", lengthM = 1000f, widthM = 1000f, latitude = 40.0, orientationSet = true)
        val ctx = PlotContext(plot, emptyList(), emptyList(), TestCatalog.lookup)
        val list = listOf(PlantRequest(TestCatalog.named("Sweet Corn - Honey Select"), 400), PlantRequest(TestCatalog.named("Tomato - San Marzano"), 50))
        val t0 = System.currentTimeMillis()
        val r = AutoPlanner.plan(ctx, PlotShape.effectiveOutline(plot), list)
        val ms = System.currentTimeMillis() - t0
        checkInvariants("1000 m plot", ctx, PlotShape.effectiveOutline(plot), list, r)
        assertTrue("took $ms ms", ms < 20_000)
    }

    @Test
    fun optionsAreDistinctAndEachObeysTheRules() {
        val plot = PlotEntity(id = 1, name = "O", lengthM = 12f, widthM = 9f, latitude = 42.0, orientationSet = true)
        val ctx = PlotContext(plot, emptyList(), emptyList(), TestCatalog.lookup)
        val list = listOf(PlantRequest(TestCatalog.named("Sweet Corn - Honey Select"), 16), PlantRequest(TestCatalog.named("Bush Bean - Blue Lake"), 12))
        val opts = AutoPlanner.options(ctx, PlotShape.effectiveOutline(plot), list, count = 10)
        assertEquals(opts.size, opts.map { AutoPlanner.signature(it.result) }.distinct().size)
        opts.forEach { checkInvariants(it.label, ctx, PlotShape.effectiveOutline(plot), list, it.result) }
        assertTrue(AutoPlanner.options(ctx, PlotShape.effectiveOutline(plot), list, count = 1).size == 1)
    }
}
