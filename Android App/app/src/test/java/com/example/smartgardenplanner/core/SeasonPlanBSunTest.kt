package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host tests for FR-054 (growing season), FR-055 (planning with growing-season sun) and FR-056 (Plan B). */
class SeasonPlanBSunTest {

    private fun seed(code: String, name: String, family: String, radius: Float, days: Int, type: String = "VEGETABLE") =
        SeedEntity(botanicalCode = code, commonName = name, botanicalFamily = family, exclusionRadiusM = radius, plantType = type, daysToHarvest = days)

    private val stationsFile = listOf("src/main/assets/frost_stations.txt", "app/src/main/assets/frost_stations.txt", "Android App/app/src/main/assets/frost_stations.txt")
        .map { java.io.File(it) }.first { it.exists() }
    private val stations = stationsFile.readLines().mapNotNull { GrowingSeason.parseStation(it) }

    @Test
    fun frostDatesComeFromTheNearestNoaaStation() {
        assertEquals(6500, stations.size)
        // Jenison, MI (ZIP 49428): nearest station East Grand Rapids.
        val s = GrowingSeason.seasonAt(stations, 42.91, -85.828)!!
        assertTrue(s.station.name, s.station.name.startsWith("E GRAND RAPIDS"))
        assertEquals("May 6", GrowingSeason.date(s.lastFrost))
        assertEquals("Oct 10", GrowingSeason.date(s.firstFrost))
        assertEquals(157, s.frostFreeDays)
        assertTrue(GrowingSeason.describe(s).first().startsWith("Last spring frost: around May 6"))
        // Miami is frost-free; Phoenix's first frost wraps into the next year.
        assertTrue(GrowingSeason.seasonAt(stations, 25.77, -80.19)!!.frostFree)
        val phx = GrowingSeason.seasonAt(stations, 33.45, -112.07)!!
        assertTrue("phoenix ${phx.frostFreeDays}", phx.frostFreeDays > 300)
        assertNull(GrowingSeason.seasonAt(stations, 48.85, 2.35)) // Paris: no station within 250 km
        assertNull(GrowingSeason.parseStation("bad|x|1|2|3|4|5|6"))
    }

    @Test
    fun plantingWindowsFollowTheFrostDates() {
        val s = GrowingSeason.seasonAt(stations, 42.91, -85.828)!!
        val tomato = seed("TOM-001", "Tomato - Brandywine", "Solanaceae", 0.3f, 80)
        val pea = seed("PEA-001", "Pea - Sugar Snap", "Fabaceae", 0.05f, 60)
        val w = GrowingSeason.window(tomato, s)
        assertEquals(s.lastFrost + 7, w.plantOut!!.first)
        assertNotNull(w.startIndoors)
        assertTrue(w.startIndoors!!.first < s.lastFrost)
        val p = GrowingSeason.window(pea, s)
        assertTrue(p.plantOut!!.first < s.lastFrost)
        assertTrue(p.note.startsWith("Hardy"))
        // A 150-day melon doesn't fit a 157-day season once planted a week after frost: told so.
        val slow = seed("WAT-009", "Watermelon - Slow", "Cucurbitaceae", 1f, 150, "FRUIT")
        assertTrue(GrowingSeason.window(slow, s).note.contains("short for Watermelon"))
        assertEquals(listOf(135, 172, 213), GrowingSeason.sunDays(42.0, null))
        val days = GrowingSeason.sunDays(42.0, s)
        assertTrue(days.all { it in s.lastFrost..s.firstFrost })
    }

    @Test
    fun planBOffersFasterVarietiesThatCatchUpWithTheSurvivors() {
        val s = GrowingSeason.seasonAt(stations, 42.91, -85.828)!!
        val original = seed("TOM-001", "Tomato - Brandywine", "Solanaceae", 0.3f, 85)
        val quick = seed("TOM-050", "Tomato - Early Girl", "Solanaceae", 0.3f, 55)
        val cherry = seed("TOM-060", "Tomato - Sungold", "Solanaceae", 0.3f, 60)
        val slow = seed("TOM-070", "Tomato - Big Slow", "Solanaceae", 0.3f, 110)
        val pepper = seed("PEP-001", "Pepper - California Wonder", "Solanaceae", 0.25f, 70)
        val catalog = listOf(original, quick, cherry, slow, pepper)
        // Planted May 15 (day 135); died June 10 (day 161): the others are ready around Aug 8 (day 220).
        val opts = BackupPlanner.options(original, 135, 161, catalog, s)
        assertEquals("TOM-060", opts.first().seed.botanicalCode) // 161 + 60 = 221, closest to 220
        assertTrue(opts.first().reason, opts.first().reason.contains("ready with the others"))
        assertTrue(opts.none { it.seed == original })
        assertTrue(opts.first().sameSpecies)
        // Late in the season nothing that ripens after the first frost is offered.
        val late = BackupPlanner.options(original, 135, 250, catalog, s)
        assertTrue(late.joinToString { it.seed.commonName }, late.none { 250 + it.seed.daysToHarvest > s.firstFrost })
        val ahead = BackupPlanner.planAhead(listOf(original), catalog, s)
        assertEquals(listOf("TOM-050", "TOM-060", "TOM-001").take(2), ahead.single().second.map { it.botanicalCode }.take(2))
    }

    @Test
    fun plannerJudgesSunOverTheGrowingSeasonNotOnTheDayItIsRun() {
        // A tall tree south-east: in midwinter it shades much of the plot, in summer far less.
        val plot = PlotEntity(id = 1, name = "P", lengthM = 12f, widthM = 10f, latitude = 42.9, orientationSet = true)
        val tree = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.TREE.name, pointsJson = "10,12", heightM = 15f, radiusM = 4f)
        val winter = PlotContext(plot, emptyList(), listOf(tree), { null }, dayOfYear = 355)
        assertTrue(winter.forPlanning().sunDays.isNotEmpty())
        val corn = seed("COR-001", "Sweet Corn - Silver Queen", "Poaceae", 0.2f, 80)
        val lettuce = seed("LET-001", "Lettuce - Buttercrunch", "Asteraceae", 0.15f, 50)
        val lookup: (String) -> SeedEntity? = { c -> listOf(corn, lettuce).firstOrNull { it.botanicalCode == c } }
        val ctx = PlotContext(plot, emptyList(), listOf(tree), lookup, dayOfYear = 355)
        val area = PlotShape.effectiveOutline(plot)
        val r = AutoPlanner.plan(ctx, area, listOf(PlantRequest(corn, 16), PlantRequest(lettuce, 16)))
        val season = ctx.forPlanning()
        fun mean(s: SeedEntity) = r.placed.filter { it.seed == s }.map { season.sunHoursAt(it.x, it.y)!! }.average()
        var total = 0.0; var n = 0
        for (x in 0 until 12) for (y in 0 until 10) { total += season.sunHoursAt(x + 0.5f, y + 0.5f)!!; n++ }
        assertTrue("corn ${mean(corn)} vs plot ${total / n}", mean(corn) >= total / n)
        assertTrue("corn ${mean(corn)} lettuce ${mean(lettuce)}", mean(corn) >= mean(lettuce))
    }
}
