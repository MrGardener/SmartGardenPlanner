package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host tests for the roadmap features FR-002 to FR-026 (pure Kotlin parts). */
class RoadmapFeaturesTest {

    private fun seed(code: String, name: String, radius: Float = 0.3f, companions: String = "", antagonists: String = "",
                     type: String = "VEGETABLE", life: String = "ANNUAL", zMin: Int = 3, zMax: Int = 11, pests: String = "", days: Int = 70) =
        SeedEntity(
            botanicalCode = code, commonName = name, botanicalFamily = "F", exclusionRadiusM = radius,
            companionCodes = companions, antagonistCodes = antagonists, plantType = type, lifecycle = life,
            hardinessZoneMin = zMin, hardinessZoneMax = zMax, pestNotes = pests, daysToHarvest = days
        )

    private val tomato = seed("TOM-001", "Tomato - Brandywine", 0.6f, companions = "BAS,MAR", antagonists = "CAB,FEN", pests = "Hornworms, aphids")
    private val basil = seed("BAS-001", "Basil - Genovese", 0.2f, companions = "TOM", type = "HERB")
    private val cabbage = seed("CAB-001", "Cabbage - Golden Acre", 0.45f, antagonists = "TOM", pests = "Cabbage worms, aphids")
    private val corn = seed("COR-001", "Sweet Corn - Silver Queen", 0.3f)
    private val bean = seed("POL-001", "Pole Bean - Kentucky Wonder", 0.15f)
    private val fig = seed("FIG-001", "Fig - Brown Turkey", 3f, type = "FRUIT", life = "PERENNIAL", zMin = 7, zMax = 11)
    private val potato = seed("POT-001", "Potato - Yukon Gold", 0.3f)
    private val all = listOf(tomato, basil, cabbage, corn, bean, fig, potato)
    private val lookup: (String) -> SeedEntity? = { c -> all.firstOrNull { it.botanicalCode == c } }

    private fun plot(boundary: String? = null, zone: String? = null, ph: Float? = null) =
        PlotEntity(id = 1, name = "Test", lengthM = 10f, widthM = 10f, boundaryJson = boundary, hardinessZone = zone, soilPh = ph)

    private fun node(id: Long, s: SeedEntity, x: Float, y: Float, planted: Long = 0L) =
        PlantedNodeEntity(id = id, plotId = 1, seedCode = s.botanicalCode, coordinateXM = x, coordinateYM = y, datePlantedEpochMillis = planted)

    // ---------------- FR-002 plot outline

    @Test
    fun lShapedOutline_containsOnlyTheL() {
        val l = "0,0;10,0;10,4;4,4;4,10;0,10"
        val p = plot(boundary = l)
        assertTrue(PlotShape.contains(p, 2f, 8f))
        assertTrue(PlotShape.contains(p, 8f, 2f))
        assertFalse(PlotShape.contains(p, 8f, 8f)) // the cut-out corner
        assertEquals(64f, PlotShape.areaM2(p), 0.01f)
    }

    @Test
    fun noOutline_usesRectangle() {
        val p = plot()
        assertTrue(PlotShape.contains(p, 9.9f, 9.9f))
        assertFalse(PlotShape.contains(p, 10.1f, 5f))
        assertEquals(100f, PlotShape.areaM2(p), 0.01f)
    }

    @Test
    fun outlineValidation_rejectsFigureEightAndTooFewCorners() {
        assertNotNull(PlotGeometry.validateOutline(PlotGeometry.parsePoints("0,0;1,1")))
        assertNotNull(PlotGeometry.validateOutline(PlotGeometry.parsePoints("0,0;4,4;4,0;0,4")))
        assertNull(PlotGeometry.validateOutline(PlotGeometry.parsePoints("0,0;4,0;4,4;0,4")))
    }

    @Test
    fun pointsRoundTrip_andMalformedPairsSkipped() {
        val pts = PlotGeometry.parsePoints("1.5,2;x,3;4,5")
        assertEquals(listOf(PlotPoint(1.5f, 2f), PlotPoint(4f, 5f)), pts)
        assertEquals(pts, PlotGeometry.parsePoints(PlotGeometry.serializePoints(pts)))
    }

    // ---------------- FR-013 soil / FR-014 zone

    @Test
    fun soilTriangle_classifiesReferencePoints() {
        assertEquals(SoilTexture.LOAM, SoilAnalyzer.classify(40f, 40f, 20f))
        assertEquals(SoilTexture.SAND, SoilAnalyzer.classify(92f, 5f, 3f))
        assertEquals(SoilTexture.CLAY, SoilAnalyzer.classify(20f, 20f, 60f))
        assertEquals(SoilTexture.SILT_LOAM, SoilAnalyzer.classify(20f, 65f, 15f))
        assertEquals(SoilTexture.SANDY_LOAM, SoilAnalyzer.classify(65f, 25f, 10f))
        assertNotNull(SoilAnalyzer.validateTexture(50f, 20f, 10f))
        assertNull(SoilAnalyzer.validateTexture(40f, 40f, 20f))
    }

    @Test
    fun soilGuidance_mentionsLimeForAcidSoil() {
        val tips = SoilAnalyzer.guidance(SoilProfile(null, null, null, 1.5f, 5.0f))
        assertTrue(tips.any { it.contains("lime") })
        assertTrue(tips.any { it.contains("Organic matter is low") })
    }

    @Test
    fun hardiness_blocksOnlyPerennialsOutsideZone() {
        assertTrue(HardinessZones.blocksPlacement(fig, "5a"))
        assertFalse(HardinessZones.blocksPlacement(fig, "8b"))
        assertFalse(HardinessZones.blocksPlacement(tomato, "2a")) // annual: warn only
        assertFalse(HardinessZones.blocksPlacement(fig, null))    // unknown zone: allow
        assertEquals(10, HardinessZones.number("10b"))
    }

    // ---------------- FR-009 guilds

    @Test
    fun guilds_letMembersPlantCloserButNotOnTop() {
        val v = CompanionPlantingValidator()
        val existing = listOf(node(1, corn, 5f, 5f))
        // 0.35 m apart: full spacing is 0.45 m, guild spacing is max radius 0.3 m.
        val candidate = node(0, bean, 5.35f, 5f)
        assertFalse(v.validatePlacement(candidate, bean, existing, lookup).isValid)
        assertTrue(v.validatePlacement(candidate, bean, existing, lookup, guilds = GuildCatalog.ALL).isValid)
        val onTop = node(0, bean, 5.1f, 5f)
        assertFalse(v.validatePlacement(onTop, bean, existing, lookup, guilds = GuildCatalog.ALL).isValid)
    }

    @Test
    fun guilds_doNotRelaxSpacingForNonMembers() {
        val v = CompanionPlantingValidator()
        val existing = listOf(node(1, corn, 5f, 5f))
        val candidate = node(0, potato, 5.4f, 5f)
        assertFalse(v.validatePlacement(candidate, potato, existing, lookup, guilds = GuildCatalog.ALL).isValid)
    }

    // ---------------- FR-010 / FR-014 / FR-015 recommendations

    @Test
    fun pickerConflict_flagsAntagonistOnPlotAndTenderPerennial() {
        val ctx = PlotContext(plot(zone = "5a"), listOf(node(1, tomato, 2f, 2f)), emptyList(), lookup)
        assertNotNull(RecommendationEngine.conflictReason(cabbage, ctx))
        assertNotNull(RecommendationEngine.conflictReason(fig, ctx))
        assertNull(RecommendationEngine.conflictReason(basil, ctx))
    }

    @Test
    fun recommend_excludesAntagonistsAndPrefersCompanions() {
        val ctx = PlotContext(plot(zone = "7a"), listOf(node(1, tomato, 2f, 2f)), emptyList(), lookup)
        val area = listOf(PlotPoint(1f, 1f), PlotPoint(4f, 1f), PlotPoint(4f, 4f), PlotPoint(1f, 4f))
        val recs = RecommendationEngine.recommend(all, ctx, area)
        assertTrue(recs.none { it.seed == cabbage })
        assertEquals(basil, recs.first().seed)
    }

    @Test
    fun recommend_respectsFloodAndShadeZones() {
        val flood = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.FLOOD.name, pointsJson = "0,0;10,0;10,10;0,10")
        val ctx = PlotContext(plot(), emptyList(), listOf(flood), lookup)
        val recs = RecommendationEngine.recommend(all, ctx)
        assertTrue(recs.none { it.seed == tomato }) // tomatoes don't tolerate flooding
        val shade = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.FULL_SHADE.name, pointsJson = "0,0;10,0;10,10;0,10")
        val shaded = RecommendationEngine.recommend(all, PlotContext(plot(), emptyList(), listOf(shade), lookup))
        assertTrue(shaded.none { it.seed == corn })
    }

    // ---------------- FR-011 harmony

    @Test
    fun harmony_reportsCloseAntagonistsAndScoresLower() {
        val bad = PlotContext(plot(), listOf(node(1, tomato, 2f, 2f), node(2, cabbage, 3f, 2f)), emptyList(), lookup)
        val good = PlotContext(plot(), listOf(node(1, tomato, 2f, 2f), node(2, basil, 3f, 2f)), emptyList(), lookup)
        val badReport = HarmonyAnalyzer.analyze(bad, all)
        val goodReport = HarmonyAnalyzer.analyze(good, all)
        assertTrue(badReport.issues.any { it.severity == Severity.HIGH })
        assertTrue(goodReport.goodPairs.isNotEmpty())
        assertTrue(badReport.score < goodReport.score)
    }

    @Test
    fun harmony_flagsPlantsOutsideOutline() {
        val ctx = PlotContext(plot(boundary = "0,0;10,0;10,4;4,4;4,10;0,10"), listOf(node(1, basil, 8f, 8f)), emptyList(), lookup)
        assertTrue(HarmonyAnalyzer.analyze(ctx, all).issues.any { it.text.contains("outside the plot outline") })
    }

    // ---------------- FR-006 / FR-007 sunlight

    @Test
    fun sunPosition_noonIsSouthAndHigherInSummer() {
        val summer = SunlightEngine.position(40.0, 172, 12.0)
        val winter = SunlightEngine.position(40.0, 355, 12.0)
        assertEquals(180.0, summer.azimuthDeg, 1.0)
        assertEquals(73.4, summer.elevationDeg, 1.0)
        assertEquals(26.6, winter.elevationDeg, 1.0)
        assertTrue(SunlightEngine.position(40.0, 172, 9.0).azimuthDeg < 180.0) // morning sun in the east
    }

    @Test
    fun dayLength_matchesKnownValues() {
        assertEquals(15.0, SunlightEngine.dayLengthHours(40.0, 172), 0.3)
        assertEquals(12.0, SunlightEngine.dayLengthHours(0.0, 80), 0.2)
        assertEquals(24.0, SunlightEngine.dayLengthHours(80.0, 172), 0.01)
    }

    @Test
    fun wallSouthOfPoint_castsShadeInWinter() {
        // Plot top faces north, so a wall along y = 6 is south of a point at y = 5.
        val wall = Barrier(SiteFeatureType.WALL, listOf(PlotPoint(0f, 6f), PlotPoint(10f, 6f)), 2f, 0f)
        val open = SunlightEngine.directSunHours(5f, 5f, 40.0, 355, 0f, emptyList())
        val shaded = SunlightEngine.directSunHours(5f, 5f, 40.0, 355, 0f, listOf(wall))
        assertTrue(open > 8.0)
        assertTrue(shaded < 1.0)
        // A wall to the north casts no shade at mid-latitudes.
        val north = Barrier(SiteFeatureType.WALL, listOf(PlotPoint(0f, 4f), PlotPoint(10f, 4f)), 2f, 0f)
        assertEquals(open, SunlightEngine.directSunHours(5f, 5f, 40.0, 355, 0f, listOf(north)), 0.3)
    }

    @Test
    fun treeCrown_shadesPointsUnderIt() {
        val tree = Barrier(SiteFeatureType.TREE, listOf(PlotPoint(5f, 5f)), 6f, 2f)
        assertEquals(0.0, SunlightEngine.directSunHours(5.5f, 5f, 40.0, 172, 0f, listOf(tree)), 0.01)
    }

    @Test
    fun rotatedPlot_movesTheShade() {
        // Rotate the plot so its top faces south: now a wall at y = 4 (above the point) is to the south.
        val wall = Barrier(SiteFeatureType.WALL, listOf(PlotPoint(0f, 4f), PlotPoint(10f, 4f)), 2f, 0f)
        assertTrue(SunlightEngine.directSunHours(5f, 5f, 40.0, 355, 180f, listOf(wall)) < 1.0)
    }

    // ---------------- FR-017 / FR-018 / FR-019 care

    @Test
    fun fertilizingPlan_sideDressesHeavyFeedersAndSkipsNitrogenForBeans() {
        val ctx = PlotContext(plot(ph = 5.5f), listOf(node(1, tomato, 2f, 2f), node(2, bean, 5f, 5f)), emptyList(), lookup)
        val plan = CarePlanner.fertilizingPlan(ctx, CarePreference.ORGANIC)
        assertTrue(plan.first().action.contains("lime"))
        assertTrue(plan.count { it.species.contains("Tomato") && it.action.contains("Heavy feeder") } >= 2)
        assertTrue(plan.any { it.species.contains("Pole Bean") && it.action.contains("no nitrogen") })
    }

    @Test
    fun pestPlan_groupsSharedPestsAndFollowsPreference() {
        val ctx = PlotContext(plot(), listOf(node(1, tomato, 2f, 2f), node(2, cabbage, 8f, 8f)), emptyList(), lookup)
        val organic = CarePlanner.pestPlan(ctx, CarePreference.ORGANIC)
        val aphids = organic.first { it.pest == "Aphids" }
        assertEquals(listOf("Cabbage", "Tomato"), aphids.affects)
        val conventional = CarePlanner.pestPlan(ctx, CarePreference.CONVENTIONAL)
        assertTrue(conventional.first { it.pest == "Aphids" }.control.contains("label"))
    }

    @Test
    fun reminders_waterWhenDueUnlessItRained() {
        val day = 86_400_000L
        val ctx = PlotContext(plot(), listOf(node(1, tomato, 2f, 2f, planted = 0L)), emptyList(), lookup)
        val log = listOf(CareLogEntity(plotId = 1, taskType = "WATER", doneAtEpochMillis = 10 * day))
        val dry = CarePlanner.dueTasks(ctx, log, CarePreference.ORGANIC, 14 * day, rainMm = 0.0, rainThresholdMm = 5.0)
        assertTrue(dry.first { it.type == CareTaskType.WATER }.isDue)
        val wet = CarePlanner.dueTasks(ctx, log, CarePreference.ORGANIC, 14 * day, rainMm = 12.0, rainThresholdMm = 5.0)
        val water = wet.first { it.type == CareTaskType.WATER }
        assertTrue(water.rainSkip)
        assertFalse(water.isDue)
        val notYet = CarePlanner.dueTasks(ctx, log, CarePreference.ORGANIC, 11 * day, null, 5.0)
        assertFalse(notYet.first { it.type == CareTaskType.WATER }.isDue)
    }

    @Test
    fun wateringInterval_shorterOnSand() {
        val sandy = plot().copy(soilSandPct = 90f, soilSiltPct = 5f, soilClayPct = 5f)
        val clay = plot().copy(soilSandPct = 20f, soilSiltPct = 20f, soilClayPct = 60f)
        val nodes = listOf(node(1, potato, 2f, 2f))
        assertTrue(CarePlanner.wateringIntervalDays(PlotContext(sandy, nodes, emptyList(), lookup)) <
            CarePlanner.wateringIntervalDays(PlotContext(clay, nodes, emptyList(), lookup)))
    }

    // ---------------- FR-016 / FR-020 / FR-021 / FR-022 food

    @Test
    fun cropTable_parsesEveryLine() {
        assertTrue(CropReference.allKeys().size > 150)
        assertNotNull(CropReference.lookup("tomato")?.nutrients)
        assertEquals(FeedingClass.NITROGEN_FIXER, CropReference.forSeed(bean).feeding)
    }

    @Test
    fun homesteadList_coversRolesAndScalesWithHousehold() {
        val two = FoodPlanner.homesteadList(all, "7a", SoilProfile(null, null, null, null, null), 2)
        val six = FoodPlanner.homesteadList(all, "7a", SoilProfile(null, null, null, null, null), 6)
        assertTrue(two.any { it.role == HomesteadRole.CALORIE && it.seed == potato })
        assertTrue(six.first { it.seed == potato }.plants > two.first { it.seed == potato }.plants)
    }

    @Test
    fun yieldAndNutrition_totalsScaleWithPlantCount() {
        val ctx = PlotContext(plot(), listOf(node(1, tomato, 2f, 2f), node(2, tomato, 4f, 2f)), emptyList(), lookup)
        val lines = FoodPlanner.yieldLines(ctx)
        assertEquals(9f, lines.first().totalKg, 0.01f)
        val totals = FoodPlanner.nutritionTotals(ctx)
        assertEquals(9000.0 * 0.18, totals.energyKcal, 1.0) // 9 kg × 18 kcal/100 g
    }

    @Test
    fun recipes_rankFullMatchesFirst() {
        val matches = FoodPlanner.recipeMatches(setOf("basil", "garlic"))
        assertEquals("Pesto", matches.first().recipe.name)
        assertTrue(matches.all { it.grown.isNotEmpty() })
    }

    // ---------------- FR-023 / FR-024 vendors

    @Test
    fun vendors_arePlaceholdersAndPreferenceNeedsPro() {
        assertNull(VendorRegistry.purchaseLink(tomato, VendorRegistry.VENDORS.first()))
        assertEquals("vendor_b", VendorRegistry.effectiveVendor("vendor_b", canChoose = true).id)
        assertEquals("vendor_a", VendorRegistry.effectiveVendor("vendor_b", canChoose = false).id)
    }

    // ---------------- FR-026 online data

    @Test
    fun onlineData_onlyAllowsListedHttpsHosts() {
        assertTrue(OnlineData.isAllowed(OnlineData.rainUrl(40.7, -74.0)))
        assertFalse(OnlineData.isAllowed("http://api.open-meteo.com/v1/forecast"))
        assertFalse(OnlineData.isAllowed("https://example.com/"))
        assertFalse(OnlineData.isAllowed("https://api.open-meteo.com.evil.test/"))
    }

    @Test
    fun onlineData_parsesResponses() {
        assertEquals(7.5, OnlineData.parseRainMm("""{"daily":{"time":["2026-09-26","2026-09-27"],"precipitation_sum":[2.5,5.0]}}""")!!, 0.001)
        val sun = OnlineData.parseMonthlySunshineHours("""{"daily":{"time":["2025-01-01","2025-01-02","2025-07-01"],"sunshine_duration":[3600,7200,36000]}}""")!!
        assertEquals(1.5, sun[0], 0.001)
        assertEquals(10.0, sun[6], 0.001)
        val fdc = OnlineData.parseFdcSearch("""{"foods":[{"fdcId":123,"description":"Tomatoes, red, raw","foodNutrients":[{"nutrientNumber":"208","value":18},{"nutrientNumber":"401","value":13.7}]}]}""")!!
        assertEquals(123L, fdc.fdcId)
        assertEquals(13.7f, fdc.nutrients.vitaminCMg, 0.01f)
        assertNull(OnlineData.parseRainMm("not json"))
        assertNull(MiniJson.parse("{\"a\":1,}"))
    }
}
