package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests written while tracing every LLR to test cases (SGP-TCS-001). Each covers a nominal value, the
 * limits and an unexpected value for a unit that had no test of its own; the failures found are in the
 * TCS regression register (REG-22, REG-23).
 */
class LlrGapTest {

    @Test
    fun settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes() {
        val d = AppSettings.DEFAULT
        assertEquals("defaults are already valid", d, d.sanitized())

        // The slider limits themselves are kept.
        val low = d.copy(spacingMarginMultiplier = 0.3f, zoomMin = 0.1f, zoomMax = 1.5f, undoHistoryDepth = 5, householdSize = 1,
            minPlotDimensionM = 0.01f, maxPlotDimensionM = 10f, rulerTickIntervalM = 0.25f, rulerFontSizeSp = 8f)
        assertEquals(low, low.sanitized())
        val high = d.copy(spacingMarginMultiplier = 2f, zoomMin = 1f, zoomMax = 8f, undoHistoryDepth = 100, householdSize = 12,
            minPlotDimensionM = 5f, maxPlotDimensionM = 2000f, rulerTickIntervalM = 5f, rulerFontSizeSp = 28f)
        assertEquals(high, high.sanitized())

        // A damaged row (NaN, infinity, negative, out of range, unknown code) falls back to the default.
        val bad = d.copy(spacingMarginMultiplier = Float.NaN, zoomStep = Float.POSITIVE_INFINITY, undoHistoryDepth = -1,
            householdSize = 0, rulerTickIntervalM = 0f, maxPlotDimensionM = 1e9f, catalogTier = "GOLD",
            carePreference = "", planLayout = "SPIRAL", language = "xx", rainSkipThresholdMm = -5f)
        val s = bad.sanitized()
        assertEquals(d.spacingMarginMultiplier, s.spacingMarginMultiplier)
        assertEquals(d.zoomStep, s.zoomStep)
        assertEquals(d.undoHistoryDepth, s.undoHistoryDepth)
        assertEquals(d.householdSize, s.householdSize)
        assertEquals(d.rulerTickIntervalM, s.rulerTickIntervalM)
        assertEquals(d.maxPlotDimensionM, s.maxPlotDimensionM)
        assertEquals(d.catalogTier, s.catalogTier)
        assertEquals(d.carePreference, s.carePreference)
        assertEquals(d.planLayout, s.planLayout)
        assertEquals(d.language, s.language)
        assertEquals(d.rainSkipThresholdMm, s.rainSkipThresholdMm)

        // Zoom minimum equal to the maximum would leave no zoom range: both go back to the defaults.
        val flat = d.copy(zoomMin = 1f, zoomMax = 1f).sanitized()
        assertEquals(d.zoomMin, flat.zoomMin); assertEquals(d.zoomMax, flat.zoomMax)

        // Random values: whatever is stored, the result is always inside every range.
        val rnd = Random(7)
        repeat(500) {
            val f = { listOf(Float.NaN, Float.NEGATIVE_INFINITY, -1f, 0f, rnd.nextFloat() * 3000f - 500f).random(rnd) }
            val r = d.copy(spacingMarginMultiplier = f(), zoomMin = f(), zoomMax = f(), zoomStep = f(), rulerTickIntervalM = f(),
                undoHistoryDepth = rnd.nextInt(-10, 500), householdSize = rnd.nextInt(-3, 40)).sanitized()
            assertTrue(r.spacingMarginMultiplier in 0.3f..2f && r.zoomMin < r.zoomMax && r.zoomStep in 0.05f..1f)
            assertTrue(r.rulerTickIntervalM in 0.25f..5f && r.undoHistoryDepth in 5..100 && r.householdSize in 1..12)
        }
    }

    @Test
    fun historyStack_keepsTheNewestStepsUpToItsLimit() {
        val s = BoundedHistoryStack<Int>(3)
        assertNull("empty stack pops nothing", s.pop())
        (1..5).forEach { s.push(it) }
        assertEquals(listOf(3, 4, 5), s.toList())
        assertEquals(5, s.pop()); assertEquals(2, s.size())

        // Lowering the limit drops the oldest; raising it keeps what's there.
        (6..9).forEach { s.push(it) }
        s.updateLimit(2); assertEquals(listOf(8, 9), s.toList())
        s.updateLimit(100); s.push(10); assertEquals(listOf(8, 9, 10), s.toList())

        // A limit of 1 keeps only the last step; 0 or a negative limit keeps nothing (it used to keep one).
        val one = BoundedHistoryStack<Int>(1); (1..4).forEach { one.push(it) }; assertEquals(listOf(4), one.toList())
        val zero = BoundedHistoryStack<Int>(0); zero.push(1); assertEquals(0, zero.size())
        val neg = BoundedHistoryStack<Int>(-3); neg.push(1); assertEquals(0, neg.size())
        s.updateLimit(-1); assertEquals(0, s.size())

        // Random pushes and pops never exceed the limit, and pops come back newest first.
        val rnd = Random(3); val h = BoundedHistoryStack<Int>(25); val model = ArrayDeque<Int>()
        repeat(2000) { i ->
            if (rnd.nextInt(3) == 0) assertEquals(model.removeLastOrNull(), h.pop())
            else { h.push(i); model.addLast(i); if (model.size > 25) model.removeFirst() }
            assertTrue(h.size() <= 25)
        }
        assertEquals(model.toList(), h.toList())
    }

    @Test
    fun units_convertAndParseAtTheLimits() {
        // Nominal: 1 m shows as 1.00 m or 39.37 in, and comes back as 1 m.
        assertEquals("1.00m", DistanceFormatter.format(1f, DistanceUnit.METERS))
        assertEquals(39.37f, DistanceFormatter.metersToDisplay(1f, DistanceUnit.INCHES), 0.01f)
        assertEquals(1f, DistanceFormatter.parseToMeters("39.3701", DistanceUnit.INCHES)!!, 1e-4f)
        // Limits: 0, the smallest plot side and the largest, in both units, round trip within 0.01 %.
        for (m in listOf(0f, 0.01f, 0.05f, 1000f, 2000f)) for (u in DistanceUnit.entries) {
            val back = DistanceFormatter.displayToMeters(DistanceFormatter.metersToDisplay(m, u), u)
            assertEquals(m, back, m * 1e-4f + 1e-6f)
        }
        // Unexpected text: letters, two separators, blanks and signs of infinity are rejected.
        for (t in listOf("", " ", "abc", "1.2.3", "1,2,3", "12m", "NaN", "Infinity", "-Infinity", "1e40", "0x10"))
            assertNull("\"$t\"", DistanceFormatter.parseToMeters(t, DistanceUnit.METERS))
        assertEquals(2.5f, DistanceFormatter.parseToMeters(" 2,5 ", DistanceUnit.METERS)!!, 0f)
    }

    @Test
    fun germination_isOverdueOnlyAfterTheWholeWindow() {
        val day = 86_400_000L
        val seed = TestCatalog.named("Tomato - Brandywine")          // 7 germination days
        val planted = 1_700_000_000_000L
        val node = PlantedNodeEntity(id = 1, plotId = 1, seedCode = seed.botanicalCode, coordinateXM = 1f, coordinateYM = 1f, datePlantedEpochMillis = planted)
        val e = GerminationContingencyEngine()
        assertTrue("day 0", !e.isGerminationOverdue(node, seed, planted))
        assertTrue("exactly the window", !e.isGerminationOverdue(node, seed, planted + seed.germinationDays * day))
        assertTrue("one day more", e.isGerminationOverdue(node, seed, planted + (seed.germinationDays + 1) * day))
        assertTrue("resolved plants are never overdue", !e.isGerminationOverdue(node.copy(germinationFlagResolved = true), seed, planted + 400 * day))
        assertTrue("a planting date in the future is not overdue", !e.isGerminationOverdue(node, seed, planted - 30 * day))
        assertTrue("far future and far past don't overflow", e.isGerminationOverdue(node.copy(datePlantedEpochMillis = 0), seed, Long.MAX_VALUE / 2))
    }

    @Test
    fun irrigationRoute_randomPlantsAreEachVisitedOnce() {
        val calc = IrrigationRouteCalculator()
        val rnd = Random(11)
        repeat(200) {
            val n = rnd.nextInt(0, 60)
            val nodes = (1..n).map { PlantedNodeEntity(id = it.toLong(), plotId = 1, seedCode = "X", coordinateXM = rnd.nextFloat() * 50f, coordinateYM = rnd.nextFloat() * 50f) }
            val route = calc.calculateDripRoute(nodes)
            assertEquals(nodes.map { it.id }.toSet(), route.map { it.nodeId }.toSet())
            assertEquals(n, route.size)
            val legs = (1 until route.size).sumOf { i -> Math.hypot((route[i].xM - route[i - 1].xM).toDouble(), (route[i].yM - route[i - 1].yM).toDouble()) }
            assertEquals(legs, calc.totalRouteLengthM(route).toDouble(), 1e-2)
        }
        // Every plant on the same spot: a route of zero length.
        val same = (1..5).map { PlantedNodeEntity(id = it.toLong(), plotId = 1, seedCode = "X", coordinateXM = 2f, coordinateYM = 2f) }
        assertEquals(0f, calc.totalRouteLengthM(calc.calculateDripRoute(same)), 0f)
    }

    @Test
    fun units_randomValuesRoundTripInBothUnits() {
        val rnd = Random(21)
        repeat(5000) {
            val m = rnd.nextDouble(0.0, 2000.0).toFloat()
            for (u in DistanceUnit.entries) {
                val back = DistanceFormatter.parseToMeters(DistanceFormatter.metersToDisplay(m, u).toString(), u)!!
                assertEquals("$m $u", m, back, m * 1e-4f + 1e-5f)
            }
        }
    }

    @Test
    fun sunBands_oddHourValues() {
        assertEquals(SunBand.FULL_SUN, SunBand.of(6f)); assertEquals(SunBand.PART_SHADE, SunBand.of(5.999f))
        assertEquals(SunBand.PART_SHADE, SunBand.of(3f)); assertEquals(SunBand.FULL_SHADE, SunBand.of(2.999f))
        assertEquals(SunBand.FULL_SUN, SunBand.of(24f)); assertEquals(SunBand.FULL_SUN, SunBand.of(Float.POSITIVE_INFINITY))
        // Unexpected values never throw and read as shade (the safe side for sun-loving crops).
        for (h in listOf(-1f, 0f, Float.NaN, Float.NEGATIVE_INFINITY)) assertEquals("$h", SunBand.FULL_SHADE, SunBand.of(h))
    }

    @Test
    fun planB_respectsTheFirstFrostAndOddLimits() {
        val station = FrostStation("TEST", 42.0, -83.0, 800, lastSpring50 = 120, lastSpring10 = 130, firstFall50 = 280, firstFall10 = 270)
        val season = Season(station, 10.0)
        val tomato = TestCatalog.named("Tomato - Brandywine")
        for (today in listOf(121, 180, 250, 279, 280, 300)) {
            val opts = BackupPlanner.options(tomato, 130, today, TestCatalog.seeds, season)
            opts.forEach { o ->
                val limit = 280 + if (GrowingSeason.isHardy(o.seed)) 21 else 0
                assertTrue("day $today: ${o.seed.commonName} ready ${o.readyDay} after the frost limit", o.readyDay <= limit)
                assertTrue(o.seed.lifecycle != "PERENNIAL" && o.seed.botanicalCode != tomato.botanicalCode)
            }
            assertTrue(opts.size <= 6)
        }
        // A frost-free place has no frost limit; a negative or zero limit gives no options instead of an exception.
        val free = Season(station.copy(lastSpring50 = 0, firstFall50 = 0), 10.0)
        assertTrue(BackupPlanner.options(tomato, 130, 300, TestCatalog.seeds, free).isNotEmpty())
        assertTrue(BackupPlanner.options(tomato, 130, 180, TestCatalog.seeds, season, -1).isEmpty())
        assertTrue(BackupPlanner.options(tomato, 130, 180, TestCatalog.seeds, season, 0).isEmpty())
        assertTrue(BackupPlanner.options(tomato, 130, 180, emptyList(), season).isEmpty())
    }

    @Test
    fun planningWindows_frostFreeAndOneDaySeasons() {
        val base = FrostStation("TEST", 30.0, -90.0, 10, 0, 0, 0, 0)
        val free = Season(base, 5.0)
        assertEquals(365, free.frostFreeDays)
        val tight = Season(base.copy(lastSpring50 = 180, lastSpring10 = 185, firstFall50 = 181, firstFall10 = 181), 5.0)
        assertEquals(1, tight.frostFreeDays)
        for (seed in TestCatalog.vegetables.take(300)) for (s in listOf(free, tight)) {
            val w = GrowingSeason.window(seed, s)
            assertTrue(w.note.isNotBlank() || w.plantOut != null || w.startIndoors != null || w.fallSowing != null)
            w.plantOut?.let { assertTrue("${seed.commonName}", it.first <= it.last) }
        }
    }

    @Test
    fun layoutOptions_countLimitsAndEveryoneStarred() {
        val plot = PlotEntity(id = 1, name = "O", lengthM = 8f, widthM = 6f, latitude = 42.0, orientationSet = true)
        val ctx = PlotContext(plot, emptyList(), emptyList(), TestCatalog.lookup)
        val area = PlotShape.effectiveOutline(plot)
        val req = listOf(PlantRequest(TestCatalog.named("Tomato - Brandywine"), 4), PlantRequest(TestCatalog.vegetables.first { it.commonName.startsWith("Lettuce") }, 6))
        for ((asked, most) in listOf(-5 to 1, 0 to 1, 1 to 1, 10 to 10, 50 to 10)) {
            val o = AutoPlanner.options(ctx, area, req, count = asked)
            assertTrue("count $asked gave ${o.size}", o.size in 1..most)
        }
        // Everything starred is the same as nothing starred: a valid plan with every plant placed or reported.
        val starred = req.map { it.copy(priority = true) }
        val r = AutoPlanner.plan(ctx, area, starred)
        assertEquals(10, r.placed.size + r.unplaced.values.sum())
        r.placed.forEach { assertTrue(PlotShape.contains(plot, it.x, it.y)) }
    }

    @Test
    fun planFile_damagedNumbersInTheAppNeverMakeAnUnreadableFile() {
        // If a NaN or infinite value ever reached the database, the saved file must still open (that item skipped).
        val plot = PlotEntity(id = 1, name = "D", lengthM = 5f, widthM = 4f, latitude = Double.NaN, soilPh = Float.POSITIVE_INFINITY)
        val nodes = listOf(
            PlantedNodeEntity(id = 1, plotId = 1, seedCode = "TOM-001", coordinateXM = 1f, coordinateYM = 1f),
            PlantedNodeEntity(id = 2, plotId = 1, seedCode = "TOM-001", coordinateXM = Float.NaN, coordinateYM = 1f))
        val text = PlanFileCodec.encode(PlanBundle(listOf(PlanPlot(plot, nodes, emptyList(), emptyList())), emptyList(), 0L), TestCatalog.lookup)
        val back = PlanFileCodec.decode(text)
        assertTrue("errors ${back.errors}", back.errors.isEmpty())
        assertEquals(1, back.bundle!!.plots[0].plants.size)
        assertNull(back.bundle!!.plots[0].plot.latitude); assertNull(back.bundle!!.plots[0].plot.soilPh)
    }
}
