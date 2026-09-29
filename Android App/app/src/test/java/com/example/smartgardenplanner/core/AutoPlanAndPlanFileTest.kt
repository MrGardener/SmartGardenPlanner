package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host tests for FR-027 (plan an area for me), FR-028 (orientation, ZIP) and FR-029 (plan file). */
class AutoPlanAndPlanFileTest {

    private fun seed(code: String, name: String, radius: Float, companions: String = "", antagonists: String = "", type: String = "VEGETABLE") =
        SeedEntity(botanicalCode = code, commonName = name, botanicalFamily = "F", exclusionRadiusM = radius,
            companionCodes = companions, antagonistCodes = antagonists, plantType = type)

    private val corn = seed("COR-001", "Sweet Corn - Silver Queen", 0.2f)
    private val lettuce = seed("LET-001", "Lettuce - Buttercrunch", 0.15f)
    private val tomato = seed("TOM-001", "Tomato - Brandywine", 0.3f, companions = "BAS", antagonists = "CAB")
    private val basil = seed("BAS-001", "Basil - Genovese", 0.15f, companions = "TOM", type = "HERB")
    private val cabbage = seed("CAB-001", "Cabbage - Golden Acre", 0.3f, antagonists = "TOM")
    private val squash = seed("ZUC-001", "Zucchini - Black Beauty", 0.4f)
    private val marigold = seed("MAR-001", "Marigold - French", 0.15f, type = "FLOWER")
    private val all = listOf(corn, lettuce, tomato, basil, cabbage, squash, marigold)
    private val lookup: (String) -> SeedEntity? = { c -> all.firstOrNull { it.botanicalCode == c } }

    private fun plot(bearing: Float = 0f, lat: Double? = 40.0) =
        PlotEntity(id = 1, name = "P", lengthM = 6f, widthM = 6f, northBearingDeg = bearing, latitude = lat, orientationSet = true)

    private val wholePlot = listOf(PlotPoint(0f, 0f), PlotPoint(6f, 0f), PlotPoint(6f, 6f), PlotPoint(0f, 6f))

    private fun ctx(p: PlotEntity = plot(), features: List<SiteFeatureEntity> = emptyList()) =
        PlotContext(p, emptyList(), features, lookup, dayOfYear = 172)

    private fun meanY(r: AutoPlanResult, s: SeedEntity) = r.placed.filter { it.seed == s }.map { it.y }.average()
    private fun meanX(r: AutoPlanResult, s: SeedEntity) = r.placed.filter { it.seed == s }.map { it.x }.average()

    @Test
    fun tallPlantsGoNorth_whenTopFacesNorth() {
        val r = AutoPlanner.plan(ctx(), wholePlot, listOf(PlantRequest(corn, 6), PlantRequest(lettuce, 6)))
        assertEquals(12, r.placed.size)
        // Top edge (y = 0) faces north, so corn (tall) should sit nearer y = 0 than lettuce.
        assertTrue(meanY(r, corn) < meanY(r, lettuce))
    }

    @Test
    fun tallPlantsFollowTheCompass_whenPlotIsRotated() {
        // Top edge faces east (90°): north is now the plot's left side (x = 0).
        val r = AutoPlanner.plan(ctx(plot(bearing = 90f)), wholePlot, listOf(PlantRequest(corn, 6), PlantRequest(lettuce, 6)))
        assertTrue(meanX(r, corn) < meanX(r, lettuce))
    }

    @Test
    fun southernHemisphere_putsTallPlantsSouth() {
        val r = AutoPlanner.plan(ctx(plot(lat = -33.9)), wholePlot, listOf(PlantRequest(corn, 6), PlantRequest(lettuce, 6)))
        assertTrue(meanY(r, corn) > meanY(r, lettuce))
    }

    @Test
    fun everyPlacementPassesTheNormalRules() {
        val r = AutoPlanner.plan(ctx(), wholePlot, listOf(PlantRequest(tomato, 4), PlantRequest(cabbage, 4), PlantRequest(basil, 4)))
        val v = CompanionPlantingValidator()
        val nodes = r.placed.mapIndexed { i, p -> PlantedNodeEntity(id = i + 1L, plotId = 1, seedCode = p.seed.botanicalCode, coordinateXM = p.x, coordinateYM = p.y) }
        nodes.forEach { n -> assertTrue(v.validatePlacement(n, lookup(n.seedCode)!!, nodes, lookup).isValid) }
    }

    @Test
    fun cornIsPlantedAsABlock() {
        val r = AutoPlanner.plan(ctx(), wholePlot, listOf(PlantRequest(corn, 9)))
        val xs = r.placed.map { it.x }; val ys = r.placed.map { it.y }
        // A 3 × 3-ish block, not a single 9-plant line.
        assertTrue(xs.max() - xs.min() > 0.3f && ys.max() - ys.min() > 0.3f)
        assertTrue(r.notes.any { it.contains("block") })
    }

    @Test
    fun pollinatorsAreSpreadNearInsectPollinatedCrops_andSuggestedWhenMissing() {
        val without = AutoPlanner.plan(ctx(), wholePlot, listOf(PlantRequest(squash, 4)))
        assertTrue(without.notes.any { it.contains("pollinator plants such as") })
        val with = AutoPlanner.plan(ctx(), wholePlot, listOf(PlantRequest(squash, 4), PlantRequest(marigold, 2)))
        assertEquals(6, with.placed.size)
        assertTrue(with.notes.any { it.contains("spread among") })
    }

    @Test
    fun shadedSpotsAreAvoidedForSunLovers() {
        val shade = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.FULL_SHADE.name, pointsJson = "0,0;6,0;6,3;0,3")
        val r = AutoPlanner.plan(ctx(features = listOf(shade)), wholePlot, listOf(PlantRequest(tomato, 3)))
        assertTrue(r.placed.all { it.y > 3f })
    }

    @Test
    fun reportsWhatDoesNotFit() {
        val small = listOf(PlotPoint(0f, 0f), PlotPoint(1f, 0f), PlotPoint(1f, 1f), PlotPoint(0f, 1f))
        val r = AutoPlanner.plan(ctx(), small, listOf(PlantRequest(squash, 10)))
        assertTrue(r.placed.size < 10)
        assertEquals(10 - r.placed.size, r.unplaced["Zucchini"])
    }

    @Test
    fun zipTable_findsByBinarySearch() {
        val lines = listOf("00501|40.815|-73.045|NY", "10001|40.748|-73.997|NY", "60601|41.886|-87.618|IL", "90210|34.090|-118.406|CA")
        assertEquals(41.886, ZipTable.find(lines, "60601")!!.latitude, 1e-6)
        assertNull(ZipTable.find(lines, "12345"))
        assertNull(ZipTable.find(lines, "9021"))
    }

    @Test
    fun onlineZone_parsesAndIsAllowListed() {
        assertEquals("7b", OnlineData.parseZone("""{"zone":"7b","coordinates":{"lat":"40.7","lon":"-74.0"}}"""))
        assertNull(OnlineData.parseZone("""{"zone":"xx"}"""))
        assertTrue(OnlineData.isAllowed(OnlineData.zoneUrl("10001")))
    }

    @Test
    fun planFile_roundTripsEverything() {
        val p = plot(bearing = 45f).copy(name = "Back yard", boundaryJson = "0,0;6,0;6,4;0,6", hardinessZone = "6b", locationZip = "60601", soilPh = 6.5f)
        val custom = seed("MY-001", "My Bean - Heirloom", 0.2f).copy(isCustom = true)
        val bundle = PlanBundle(
            listOf(PlanPlot(
                p,
                listOf(PlantedNodeEntity(id = 5, plotId = 1, seedCode = "TOM-001", coordinateXM = 1f, coordinateYM = 2f, datePlantedEpochMillis = 1_700_000_000_000L),
                    PlantedNodeEntity(id = 6, plotId = 1, seedCode = "MY-001", coordinateXM = 2f, coordinateYM = 2f)),
                listOf(PathZoneEntity(plotId = 1, xM = 0f, yM = 5f, widthM = 6f, heightM = 0.5f)),
                listOf(SiteFeatureEntity(plotId = 1, featureType = "TREE", pointsJson = "5,1", heightM = 6f, radiusM = 2f))
            )),
            emptyList()
        )
        val text = PlanFileCodec.encode(bundle, { c -> (all + custom).firstOrNull { it.botanicalCode == c } })
        val back = PlanFileCodec.decode(text)
        assertTrue(back.errors.toString(), back.errors.isEmpty())
        val pp = back.bundle!!.plots.single()
        assertEquals("Back yard", pp.plot.name)
        assertEquals(45f, pp.plot.northBearingDeg, 0.001f)
        assertTrue(pp.plot.orientationSet)
        assertEquals("6b", pp.plot.hardinessZone)
        assertEquals(4, PlotGeometry.parsePoints(pp.plot.boundaryJson).size)
        assertEquals(2, pp.plants.size)
        assertEquals(1_700_000_000_000L, pp.plants.first().datePlantedEpochMillis)
        assertEquals(0L, pp.plants.first().id) // new rows on import
        assertEquals(1, pp.paths.size)
        assertEquals(6f, pp.features.single().heightM, 0.001f)
        assertEquals("MY-001", back.bundle!!.customVarieties.single().botanicalCode)
        assertEquals("Tomato - Brandywine", back.varietyNames["TOM-001"])
    }

    @Test
    fun planFile_rejectsWrongFormatAndNewerVersions_andSkipsBadItems() {
        assertFalse(PlanFileCodec.decode("""{"format":"other","version":1,"plots":[]}""").errors.isEmpty())
        assertFalse(PlanFileCodec.decode("""{"format":"smart-garden-plan","version":99,"plots":[]}""").errors.isEmpty())
        assertFalse(PlanFileCodec.decode("not json").errors.isEmpty())
        val r = PlanFileCodec.decode("""{"format":"smart-garden-plan","version":1,"plots":[
            {"name":"A","lengthM":5,"widthM":5,"plants":[{"code":"TOM-001","x":1,"y":1},{"code":"TOM-001","x":9,"y":1},{"x":1,"y":1}]},
            {"name":"Huge","lengthM":5000,"widthM":5}]}""")
        assertTrue(r.errors.isEmpty())
        assertEquals(1, r.bundle!!.plots.size)
        assertEquals(1, r.bundle!!.plots.single().plants.size)
        assertTrue(r.warnings.any { it.contains("2 plant(s) skipped") })
        assertTrue(r.warnings.any { it.contains("Huge") })
        assertNotNull(r.bundle)
    }
}
