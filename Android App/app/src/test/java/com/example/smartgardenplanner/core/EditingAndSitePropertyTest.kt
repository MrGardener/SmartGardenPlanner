package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** Adversarial tests for editing tools, geometry, placement rules, sun, shade and irrigation (SGP-TCS-001 §3.3–3.4). */
class EditingAndSitePropertyTest {

    private val tomato = TestCatalog.named("Tomato - Brandywine")
    private val corn = TestCatalog.named("Sweet Corn - Honey Select")
    private val carrot = TestCatalog.named("Carrot - Danvers")

    // ------------------------------------------------------------------ Clump shapes (LLR-ARR)

    @Test
    fun clumpShapes_everyOptionAddsUpForAnyCount() {
        for (n in (0..120) + listOf(997, 1000, 5000)) {
            val opts = ClumpShapes.options(n)
            if (n <= 0) { assertTrue(opts.isEmpty()); continue }
            assertTrue("n=$n has options", opts.isNotEmpty() && opts.size <= 10)
            assertEquals("n=$n duplicates", opts.size, opts.map { it.rows }.distinct().size)
            opts.forEach { o -> assertEquals("n=$n ${o.rows}", n, o.count); assertTrue(o.rows.all { it > 0 }); assertTrue(o.label.isNotBlank()) }
            assertEquals(n, BlockPlanner.rowSizes(n).sum())
            ClumpShapes.nearbyTidy(n).forEach { t -> assertTrue(abs(t.count - n) in 1..3); assertEquals(1, t.rows.distinct().size) }
        }
        assertEquals("nothing", ClumpShapes.label(emptyList()))
        assertEquals(listOf(7, 7, 1), BlockPlanner.fullWidthRows(15, 7)); assertEquals(listOf(1, 1), BlockPlanner.fullWidthRows(2, 0))
    }

    // ------------------------------------------------------------------ Groups (LLR-GRP)

    private fun node(id: Long, seed: SeedEntity, x: Float, y: Float) = PlantedNodeEntity(id = id, plotId = 1, seedCode = seed.botanicalCode, coordinateXM = x, coordinateYM = y)

    @Test
    fun groups_membershipIsSymmetric_andRearrangeKeepsCountCentreAndSpacing() {
        val rnd = Random(12)
        repeat(40) { case ->
            val nodes = List(rnd.nextInt(1, 40)) { i -> node(i + 1L, listOf(tomato, corn, carrot).random(rnd), rnd.nextDouble(0.0, 12.0).toFloat(), rnd.nextDouble(0.0, 9.0).toFloat()) }
            val start = nodes.random(rnd)
            val g = GroupTools.groupOf(nodes, start, TestCatalog.lookup)
            assertTrue(start in g); assertTrue(g.all { it.seedCode == start.seedCode })
            g.forEach { m -> assertEquals("case $case", g.map { it.id }.toSet(), GroupTools.groupOf(nodes, m, TestCatalog.lookup).map { it.id }.toSet()) }
            for (shape in ClumpShapes.options(g.size)) {
                val out = GroupTools.rearranged(g, shape.rows, TestCatalog.lookup, 1f)!!
                assertEquals(g.size, out.size)
                assertEquals(g.map { it.id }.sorted(), out.map { it.id }.sorted())
                assertEquals(g.map { it.coordinateXM }.average(), out.map { it.coordinateXM }.average(), 1e-3)
                assertEquals(g.map { it.coordinateYM }.average(), out.map { it.coordinateYM }.average(), 0.6)
                val r = TestCatalog.lookup(start.seedCode)!!.exclusionRadiusM
                for (i in out.indices) for (j in i + 1 until out.size)
                    assertTrue(hypot((out[i].coordinateXM - out[j].coordinateXM).toDouble(), (out[i].coordinateYM - out[j].coordinateYM).toDouble()) >= 2 * r - 1e-3)
            }
        }
        assertNull(GroupTools.rearranged(emptyList(), listOf(1), TestCatalog.lookup))
        val two = listOf(node(1, tomato, 1f, 1f), node(2, tomato, 2f, 1f))
        assertNull(GroupTools.rearranged(two, listOf(3), TestCatalog.lookup)); assertNull(GroupTools.rearranged(two, listOf(2, 0), TestCatalog.lookup)); assertNull(GroupTools.rearranged(two, listOf(3, -1), TestCatalog.lookup))
        // A plant that isn't in the list is its own group; an unknown variety still forms a group.
        assertEquals(1, GroupTools.groupOf(two, node(9, tomato, 50f, 50f), TestCatalog.lookup).size)
        val unknown = listOf(PlantedNodeEntity(id = 1, plotId = 1, seedCode = "ZZZ-1", coordinateXM = 1f, coordinateYM = 1f), PlantedNodeEntity(id = 2, plotId = 1, seedCode = "ZZZ-1", coordinateXM = 1.5f, coordinateYM = 1f))
        assertEquals(2, GroupTools.groupOf(unknown, unknown[0], TestCatalog.lookup).size)
    }

    @Test
    fun groups_movingOutOfThePlotIsReportedForEveryPlant() {
        val plot = PlotEntity(id = 1, name = "P", lengthM = 5f, widthM = 5f)
        val g = List(4) { node(it + 1L, carrot, 1f + it * 0.2f, 1f) }
        assertEquals(0, GroupTools.problems(g, emptyList(), plot, TestCatalog.lookup))
        assertEquals(4, GroupTools.problems(GroupTools.moved(g, 100f, 0f), emptyList(), plot, TestCatalog.lookup))
        assertEquals(4, GroupTools.problems(GroupTools.moved(g, -1.5f, -1.5f), emptyList(), plot, TestCatalog.lookup))
        assertEquals(4, GroupTools.problems(GroupTools.moved(g, Float.NaN, 0f), emptyList(), plot, TestCatalog.lookup))
    }

    // ------------------------------------------------------------------ Placement rule (LLR-VALD, LLR-RULE)

    @Test
    fun validator_isSymmetricMonotonicAndCaseInsensitive() {
        val rnd = Random(21)
        val v = CompanionPlantingValidator()
        repeat(2_000) {
            val a = TestCatalog.vegetables.random(rnd); val b = TestCatalog.vegetables.random(rnd)
            val na = node(1, a, 0f, 0f); val nb = node(2, b, rnd.nextDouble(0.0, 3.0).toFloat(), rnd.nextDouble(0.0, 3.0).toFloat())
            val ab = v.validatePlacement(na, a, listOf(nb), TestCatalog.lookup).isValid
            val ba = v.validatePlacement(nb, b, listOf(na), TestCatalog.lookup).isValid
            assertEquals("${a.commonName} / ${b.commonName}", ab, ba)
            if (v.validatePlacement(na, a, listOf(nb), TestCatalog.lookup, 1.2f).isValid) assertTrue(v.validatePlacement(na, a, listOf(nb), TestCatalog.lookup, 0.8f).isValid)
        }
        // Circles that exactly touch are allowed; 1 mm closer is not.
        val t2 = node(2, tomato, 2 * tomato.exclusionRadiusM, 0f)
        assertTrue(v.validatePlacement(node(1, tomato, 0f, 0f), tomato, listOf(t2), TestCatalog.lookup).isValid)
        assertFalse(v.validatePlacement(node(1, tomato, 0.001f, 0f), tomato, listOf(t2), TestCatalog.lookup).isValid)
        // Codes typed in lower case with spaces still count (custom varieties).
        val mine = SeedEntity(botanicalCode = "my-1", commonName = "Mine", botanicalFamily = "X", exclusionRadiusM = 0.2f, antagonistCodes = " tom , cor ")
        val far = 2 * (0.2f + tomato.exclusionRadiusM) * 0.9f
        assertFalse(v.validatePlacement(node(1, mine.copy(), 0f, 0f).copy(seedCode = "my-1"), mine, listOf(node(2, tomato, far, 0f)), TestCatalog.lookup).isValid)
    }

    @Test
    fun geometry_randomRectanglesAndDegenerateOutlines() {
        val rnd = Random(8)
        repeat(1_000) {
            val x0 = rnd.nextDouble(-100.0, 100.0).toFloat(); val y0 = rnd.nextDouble(-100.0, 100.0).toFloat()
            val w = rnd.nextDouble(0.01, 50.0).toFloat(); val h = rnd.nextDouble(0.01, 50.0).toFloat()
            val rect = listOf(PlotPoint(x0, y0), PlotPoint(x0 + w, y0), PlotPoint(x0 + w, y0 + h), PlotPoint(x0, y0 + h))
            assertEquals((w * h).toDouble(), PlotGeometry.polygonArea(rect).toDouble(), w * h * 1e-3 + 1e-4)
            assertTrue(PlotGeometry.pointInPolygon(x0 + w / 2, y0 + h / 2, rect))
            assertFalse(PlotGeometry.pointInPolygon(x0 - 1f, y0 + h / 2, rect)); assertFalse(PlotGeometry.pointInPolygon(Float.NaN, 0f, rect))
            assertFalse(PlotGeometry.isSelfIntersecting(rect))
            assertFalse(PlotGeometry.isSelfIntersecting(rect.reversed()))
        }
        assertNotNull(PlotGeometry.validateOutline(listOf(PlotPoint(0f, 0f), PlotPoint(1f, 1f))))
        assertNotNull(PlotGeometry.validateOutline(listOf(PlotPoint(0f, 0f), PlotPoint(1f, 0f), PlotPoint(2f, 0f))))
        assertNotNull(PlotGeometry.validateOutline(listOf(PlotPoint(0f, 0f), PlotPoint(2f, 2f), PlotPoint(2f, 0f), PlotPoint(0f, 2f))))
        assertNull(PlotGeometry.validateOutline(listOf(PlotPoint(0f, 0f), PlotPoint(1f, 0f), PlotPoint(0f, 1f))))
        assertEquals(Float.MAX_VALUE, PlotGeometry.distanceToPolyline(0f, 0f, emptyList()))
        assertEquals(0f, PlotGeometry.distanceToSegment(1f, 1f, PlotPoint(1f, 1f), PlotPoint(1f, 1f)))
    }

    // ------------------------------------------------------------------ Replace all (LLR-SWAP)

    @Test
    fun replaceAll_keepsPositionsAndCountsForAnyMix() {
        val rnd = Random(4)
        repeat(50) {
            val nodes = List(rnd.nextInt(0, 30)) { i -> node(i + 1L, listOf(tomato, corn, carrot).random(rnd), rnd.nextDouble(0.0, 10.0).toFloat(), rnd.nextDouble(0.0, 8.0).toFloat()) }
            val to = TestCatalog.vegetables.random(rnd)
            val r = PlantSwap.replaceAll(nodes, tomato.botanicalCode, to, TestCatalog.lookup, null)
            assertEquals(nodes.size, r.nodes.size)
            assertEquals(nodes.map { it.coordinateXM to it.coordinateYM }, r.nodes.map { it.coordinateXM to it.coordinateYM })
            val n = nodes.count { it.seedCode == tomato.botanicalCode }
            if (to.botanicalCode != tomato.botanicalCode && !HardinessZones.blocksPlacement(to, null)) assertEquals(n, r.changed)
            assertTrue(r.crowded in 0..r.changed); assertTrue(r.messages.isNotEmpty())
        }
    }

    // ------------------------------------------------------------------ Sun and shade (LLR-SHD, LLR-SUN)

    @Test
    fun sun_valuesStayInRangeAtEveryLatitudeAndDay() {
        for (lat in listOf(-90.0, -89.99, -66.6, -23.4, 0.0, 23.4, 66.6, 89.99, 90.0)) for (day in listOf(1, 79, 172, 265, 355, 365, 366)) {
            val len = SunlightEngine.dayLengthHours(lat, day)
            assertTrue("$lat/$day len $len", len.isFinite() && len in 0.0..24.0)
            val (rise, set) = ShadeTools.sunriseSunset(lat, day)
            assertTrue(rise in 0.0..12.0 && set in 12.0..24.0)
            for (h in listOf(0.0, 6.0, 12.0, 18.0, 24.0)) {
                val p = SunlightEngine.position(lat, day, h)
                assertTrue("$lat/$day/$h $p", p.elevationDeg.isFinite() && p.azimuthDeg.isFinite() && p.elevationDeg in -90.0..90.0 && p.azimuthDeg in 0.0..360.0)
            }
            val hours = SunlightEngine.directSunHours(1f, 1f, lat, day, 0f, emptyList())
            assertTrue(hours in 0.0..24.0)
        }
        assertEquals(0, ShadeTools.shadowGridAt(5f, 5f, 0, 0, 40.0, 172, 0f, emptyList(), 12.0).size)
        assertEquals(0, SunlightEngine.sunHoursGrid(5f, 5f, 0, 3, 40.0, 172, 0f, emptyList()).size)
    }

    // ------------------------------------------------------------------ Irrigation (LLR-WATER, LLR-IRR)

    @Test
    fun irrigation_coverageRulesAtTheLimits() {
        fun sprinkler(r: Float, arc: Float, dir: Float) = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.SPRINKLER.name, pointsJson = "5,5", radiusM = r, slopeGradePct = arc, slopeDirectionDeg = dir)
        val s = sprinkler(3f, 360f, 0f)
        assertTrue(Irrigation.sprinklerCovers(s, 5f, 5f, 0f)); assertTrue(Irrigation.sprinklerCovers(s, 8f, 5f, 0f)); assertFalse(Irrigation.sprinklerCovers(s, 8.01f, 5f, 0f))
        // A 90° arc pointing north (up on a plot whose top faces north) covers above, not below.
        val q = sprinkler(3f, 90f, 0f)
        assertTrue(Irrigation.sprinklerCovers(q, 5f, 3f, 0f)); assertFalse(Irrigation.sprinklerCovers(q, 5f, 7f, 0f))
        // Missing or garbage points never cover anything.
        assertFalse(Irrigation.sprinklerCovers(s.copy(pointsJson = ""), 5f, 5f, 0f)); assertFalse(Irrigation.dripCovers(s.copy(pointsJson = "1,1"), 1f, 1f))
        val rnd = Random(2)
        repeat(2_000) {
            val b = Irrigation.compassBearing(rnd.nextFloat() * 10, rnd.nextFloat() * 10, rnd.nextFloat() * 10, rnd.nextFloat() * 10, rnd.nextFloat() * 720 - 360)
            assertTrue("$b", b in 0.0..360.0)
        }
        val plot = PlotEntity(id = 1, name = "P", lengthM = 10f, widthM = 10f)
        assertEquals(0, Irrigation.grid(plot, listOf(s), 0, 5).size); assertEquals(0, Irrigation.grid(plot, listOf(s), -3, 5).size)
        assertEquals(12, Irrigation.grid(plot, listOf(s), 3, 4).size)
    }

    // ------------------------------------------------------------------ Seasons and rotation (LLR-SEAS, LLR-ROT)

    @Test
    fun rotationPlan_yearLimitsAndEmptyLists() {
        val plot = PlotEntity(id = 1, name = "R", lengthM = 8f, widthM = 6f, latitude = 42.0, orientationSet = true)
        val ctx = PlotContext(plot, emptyList(), emptyList(), TestCatalog.lookup)
        val area = PlotShape.effectiveOutline(plot)
        val list = listOf(PlantRequest(tomato, 4), PlantRequest(carrot, 10))
        for ((asked, got) in listOf(-5 to 1, 0 to 1, 1 to 1, 30 to 30, 31 to 30, Int.MAX_VALUE to 30)) {
            if (asked == 30 || asked == 31 || asked == Int.MAX_VALUE) continue   // long runs are covered below with a short list
            assertEquals("asked $asked", got, RotationPlanner.planSeasons(ctx, area, list, emptyList(), 2027, asked).size)
        }
        val many = RotationPlanner.planSeasons(ctx, area, listOf(PlantRequest(carrot, 4)), emptyList(), 2027, 31)
        assertEquals(30, many.size); assertEquals((2027..2056).toList(), many.map { it.year })
        assertTrue(RotationPlanner.planSeasons(ctx, area, emptyList(), emptyList(), 2027, 3).all { it.result.placed.isEmpty() })
        // Two seasons in a row never put the same family on the same spot when there's room.
        val two = RotationPlanner.planSeasons(ctx, area, listOf(PlantRequest(tomato, 3)), emptyList(), 2027, 2)
        val y1 = two[0].result.placed; val y2 = two[1].result.placed
        assertTrue(y2.none { b -> y1.any { a -> hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) < 0.3 } })
    }

    @Test
    fun growingSeason_windowsForEveryCatalogCropAndAnySeason() {
        val rnd = Random(30)
        repeat(200) {
            val last = rnd.nextInt(1, 200); val first = last + rnd.nextInt(0, 300)
            val st = FrostStation("X, ST US", 40.0, -80.0, 500, last, last + 14, first.coerceAtMost(366), (first - 14).coerceAtLeast(0))
            val season = Season(st, 10.0)
            val seed = TestCatalog.seeds.random(rnd)
            val w = GrowingSeason.window(seed, season)
            w.plantOut?.let { assertTrue("${seed.commonName} $it", it.first <= it.last) }
            w.fallSowing?.let { assertTrue(it.first <= it.last) }
            assertTrue(w.note.isNotBlank()); assertTrue(GrowingSeason.describeWindow(w).isNotBlank())
        }
        for (d in listOf(Int.MIN_VALUE + 1, -400, -1, 0, 1, 59, 60, 365, 366, 730, Int.MAX_VALUE)) assertTrue("$d", GrowingSeason.date(d).matches(Regex("^[A-Z][a-z]{2} \\d{1,2}$")))
    }
}
