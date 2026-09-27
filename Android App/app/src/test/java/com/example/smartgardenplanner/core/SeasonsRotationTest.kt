package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** Host tests for FR-031 (variety details), FR-032 (rotation, clumps), FR-033 (seasons) and FR-034 (usual plants). */
class SeasonsRotationTest {

    private fun seed(code: String, name: String, family: String, radius: Float = 0.3f, type: String = "VEGETABLE", lifecycle: String = "ANNUAL") =
        SeedEntity(botanicalCode = code, commonName = name, botanicalFamily = family, exclusionRadiusM = radius, plantType = type, lifecycle = lifecycle)

    private val redBell = seed("PEP-001", "Pepper - California Wonder", "Solanaceae")
    private val yellowBell = seed("PEP-020", "Pepper - Golden California Wonder", "Solanaceae")
    private val habanero = seed("PEP-030", "Pepper - Habanero", "Solanaceae")
    private val cherry = seed("TOM-017", "Tomato - Sweet 100", "Solanaceae")
    private val beef = seed("TOM-001", "Tomato - Brandywine", "Solanaceae")
    private val spring = seed("ONI-101", "Spring Onion - Evergreen Hardy White", "Amaryllidaceae", 0.04f)
    private val bulb = seed("ONI-002", "Onion - Red Burgundy", "Amaryllidaceae", 0.09f)
    private val bean = seed("BEA-001", "Bush Bean - Provider", "Fabaceae", 0.15f)
    private val lettuce = seed("LET-001", "Lettuce - Buttercrunch", "Asteraceae", 0.15f)
    private val corn = seed("COR-001", "Sweet Corn - Silver Queen", "Poaceae", 0.2f)
    private val marigold = seed("MAR-001", "Marigold - French", "Asteraceae", 0.15f, type = "FLOWER")
    private val asparagus = seed("ASP-001", "Asparagus - Mary Washington", "Asparagaceae", lifecycle = "PERENNIAL")
    private val all = listOf(redBell, yellowBell, habanero, cherry, beef, spring, bulb, bean, lettuce, corn, marigold, asparagus)
    private val lookup: (String) -> SeedEntity? = { c -> all.firstOrNull { it.botanicalCode == c } }

    private val plot = PlotEntity(id = 1, name = "P", lengthM = 6f, widthM = 6f, latitude = 40.0, orientationSet = true)
    private val wholePlot = listOf(PlotPoint(0f, 0f), PlotPoint(6f, 0f), PlotPoint(6f, 6f), PlotPoint(0f, 6f))
    private fun ctx() = PlotContext(plot, emptyList(), emptyList(), lookup, dayOfYear = 172)

    // ------------------------------------------------------------------ FR-031

    @Test
    fun peppersSaySweetOrSpicyAndTheirColour() {
        val red = VarietyCatalogTraits.of(redBell)!!
        assertEquals(Heat.SWEET, red.heat); assertEquals(FruitColour.RED, red.colour)
        assertEquals("Sweet bell pepper", red.kind)
        assertEquals(FruitColour.YELLOW, VarietyCatalogTraits.of(yellowBell)!!.colour)
        val hab = VarietyCatalogTraits.of(habanero)!!
        assertEquals(Heat.VERY_HOT, hab.heat)
        assertTrue(hab.kind.contains("spicy"))
        assertTrue(VarietyCatalogTraits.displayName(redBell).contains("sweet bell pepper"))
        // Red and yellow bells get different dot colours on the layout.
        assertTrue(VarietyCatalogTraits.dotArgb(redBell) != VarietyCatalogTraits.dotArgb(yellowBell))
    }

    @Test
    fun tomatoesSayCherryOrLarge_onionsSaySpringOrBulb() {
        assertEquals("Cherry tomato", VarietyCatalogTraits.of(cherry)!!.kind)
        assertEquals("Beefsteak tomato", VarietyCatalogTraits.of(beef)!!.kind)
        assertTrue(VarietyCatalogTraits.of(spring)!!.kind.startsWith("Spring onion"))
        assertEquals("Bulb onion", VarietyCatalogTraits.of(bulb)!!.kind)
        assertNull(VarietyCatalogTraits.of(lettuce))
    }

    @Test
    fun everyCatalogPepperTomatoAndOnionHasDetails() {
        val file = listOf("app/src/main/assets/seed_catalog_pro.txt", "Android App/app/src/main/assets/seed_catalog_pro.txt", "src/main/assets/seed_catalog_pro.txt")
            .map { java.io.File(it) }.first { it.exists() }
        val seeds = file.readLines().mapNotNull { l -> l.split("|").takeIf { it.size >= 14 }?.let { seed(it[0], it[1], it[2]) } }
        val missing = seeds.filter { CropReference.speciesKey(it) in setOf("pepper", "tomato", "onion", "spring onion", "paste tomato", "shishito type pepper") }
            .filter { VarietyCatalogTraits.of(it) == null }.map { it.commonName }
        assertTrue("No details for: $missing", missing.isEmpty())
        assertEquals(3, seeds.count { CropReference.speciesKey(it) == "spring onion" })
    }

    // ------------------------------------------------------------------ FR-032 rotation

    @Test
    fun rotationGroupsFollowTheFamily_andSkipFlowersAndPerennials() {
        assertEquals(RotationGroup.NIGHTSHADES, RotationGroup.forSeed(redBell))
        assertEquals(RotationGroup.LEGUMES, RotationGroup.forSeed(bean))
        assertEquals(RotationGroup.ALLIUMS, RotationGroup.forSeed(spring))
        assertNull(RotationGroup.forSeed(marigold))
        assertNull(RotationGroup.forSeed(asparagus))
        assertEquals(RotationGroup.BRASSICAS, CropRotation.successor(RotationGroup.LEGUMES))
        assertEquals(RotationGroup.LEGUMES, CropRotation.successor(RotationGroup.ALLIUMS))
    }

    private fun past(s: SeedEntity, year: Int, x: Float, y: Float) =
        Seasons.archive(1, listOf(PlantedNodeEntity(plotId = 1, seedCode = s.botanicalCode, coordinateXM = x, coordinateYM = y)), year, lookup).single()

    @Test
    fun conflictFindsTheSameFamilyInTheSameSpotWithinTheWait() {
        val history = listOf(past(beef, 2025, 1f, 1f))
        assertNotNull(CropRotation.conflict(1.1f, 1f, redBell, history, 2026))     // pepper after tomato: same family
        assertNull(CropRotation.conflict(1.1f, 1f, bean, history, 2026))           // beans are fine there
        assertNull(CropRotation.conflict(5f, 5f, redBell, history, 2026))          // far away is fine
        assertNull(CropRotation.conflict(1.1f, 1f, redBell, history, 2029))        // nightshades wait 3 years
        assertTrue(CropRotation.warning(CropRotation.conflict(1.1f, 1f, redBell, history, 2026)!!, "Pepper").contains("last season"))
    }

    @Test
    fun autoPlannerKeepsCropsAwayFromLastYearsFamily() {
        // Last year tomatoes filled the left half.
        val history = (0 until 6).flatMap { i -> (0 until 3).map { j -> past(beef, 2025, 0.5f + j, 0.5f + i) } }
        val r = AutoPlanner.plan(ctx(), wholePlot, listOf(PlantRequest(redBell, 6)), history = history, seasonYear = 2026)
        assertEquals(6, r.placed.size)
        assertTrue("peppers should go right of x=3: ${r.placed.map { it.x }}", r.placed.all { it.x > 2.8f })
        assertTrue(r.notes.any { it.startsWith("Crop rotation") })
    }

    // ------------------------------------------------------------------ FR-032 clumps vs rows

    private fun spread(r: AutoPlanResult, s: SeedEntity): Double {
        val pts = r.placed.filter { it.seed == s }
        val cx = pts.map { it.x }.average(); val cy = pts.map { it.y }.average()
        return pts.map { sqrt((it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy)) }.average()
    }

    @Test
    fun clumpsAreMoreCompactThanRows_andBothKeepTallPlantsBehind() {
        val requests = listOf(PlantRequest(corn, 8), PlantRequest(lettuce, 8), PlantRequest(bean, 8))
        val clumps = AutoPlanner.plan(ctx(), wholePlot, requests, layout = PlantingLayout.CLUMPS)
        val rows = AutoPlanner.plan(ctx(), wholePlot, requests, layout = PlantingLayout.ROWS)
        assertEquals(24, clumps.placed.size); assertEquals(24, rows.placed.size)
        val lettuceClumps = spread(clumps, lettuce); val lettuceRows = spread(rows, lettuce)
        assertTrue("clump spread $lettuceClumps should be below row spread $lettuceRows", lettuceClumps < lettuceRows)
        fun meanY(r: AutoPlanResult, s: SeedEntity) = r.placed.filter { it.seed == s }.map { it.y }.average()
        assertTrue(meanY(clumps, corn) < meanY(clumps, lettuce))
        assertTrue(meanY(rows, corn) < meanY(rows, lettuce))
        assertTrue(clumps.notes.any { it.contains("clump") })
    }

    // ------------------------------------------------------------------ FR-033 seasons, FR-034 usual plants

    @Test
    fun archiveKeepsNameFamilyAndGroup_andAdviceDescribesLastSeason() {
        val nodes = listOf(PlantedNodeEntity(plotId = 1, seedCode = beef.botanicalCode, coordinateXM = 1f, coordinateYM = 1f, datePlantedEpochMillis = CivilDate.localMidnight(2025, 5, 1, 0L) + 12 * 3_600_000L))
        assertEquals(2025, Seasons.seasonOf(nodes))
        val h = Seasons.archive(1, nodes, 2025, lookup).single()
        assertEquals("Tomato - Brandywine", h.varietyName); assertEquals("Solanaceae", h.family); assertEquals("NIGHTSHADES", h.rotationGroup)
        val advice = CropRotation.advice(plot, listOf(h), emptyList(), lookup, 2026)
        assertTrue(advice.first(), advice.first().startsWith("2025: nightshades (Tomato) in the north-west corner"))
        assertTrue(advice.any { it.contains("plant onion family next") }) // nightshades → roots/onions → legumes
        val clash = CropRotation.advice(plot, listOf(h), listOf(PlantedNodeEntity(plotId = 1, seedCode = redBell.botanicalCode, coordinateXM = 1f, coordinateYM = 1f)), lookup, 2026)
        assertTrue(clash.any { it.startsWith("⚠ 1 Pepper plant is where nightshades grew in 2025") })
    }

    @Test
    fun currentSeasonComesAfterTheLastClosedSeason() {
        val h = past(beef, 2026, 1f, 1f)
        val planted2026 = listOf(PlantedNodeEntity(plotId = 1, seedCode = "BEA-001", coordinateXM = 2f, coordinateYM = 2f, datePlantedEpochMillis = CivilDate.localMidnight(2026, 10, 1, 0L) + 12 * 3_600_000L))
        assertEquals(2027, Seasons.currentSeason(planted2026, listOf(h)))
        assertEquals(2026, Seasons.currentSeason(planted2026, emptyList()))
    }

    @Test
    fun longRowsAreDetected_forTheRotationTip() {
        val row = (0 until 6).map { PlotPoint(0.5f + it, 0.5f) }
        val clump = listOf(PlotPoint(1f, 1f), PlotPoint(1.5f, 1f), PlotPoint(1f, 1.5f), PlotPoint(1.5f, 1.5f))
        assertTrue(CropRotation.isRow(row, plot))
        assertTrue(!CropRotation.isRow(clump, plot))
    }

    @Test
    fun usualVarietiesAreTheMostPlantedSpecies() {
        val usual = Seasons.usualVarieties(
            currentCodes = listOf("TOM-001", "TOM-001", "BEA-001"),
            historyCodes = listOf("TOM-017", "TOM-001", "BEA-001", "BEA-001", "LET-001"),
            seedLookup = lookup
        )
        assertEquals("TOM-001", usual[0].first.botanicalCode); assertEquals(4, usual[0].second)
        assertEquals("BEA-001", usual[1].first.botanicalCode)
    }

    @Test
    fun planFileRoundTripsHistory() {
        val h = past(beef, 2025, 1f, 2f)
        val text = PlanFileCodec.encode(PlanBundle(listOf(PlanPlot(plot, emptyList(), emptyList(), emptyList(), listOf(h))), emptyList(), 0L), lookup, "t")
        val back = PlanFileCodec.decode(text).bundle!!.plots[0].history.single()
        assertEquals(2025, back.seasonYear); assertEquals("TOM-001", back.seedCode); assertEquals("Tomato - Brandywine", back.varietyName)
        assertEquals("NIGHTSHADES", back.rotationGroup); assertEquals(1f, back.coordinateXM); assertEquals(2f, back.coordinateYM)
        // A file without history still reads (older writers).
        val none = PlanFileCodec.encode(PlanBundle(listOf(PlanPlot(plot, emptyList(), emptyList(), emptyList())), emptyList(), 0L), lookup, "t")
        assertTrue(!none.contains("\"history\""))
        assertTrue(PlanFileCodec.decode(none).bundle!!.plots[0].history.isEmpty())
    }
}
