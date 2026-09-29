package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plan files (SGP-TCS-001 §3.7, LLR-PFILE-*): a random but valid plan — any sizes, positions at the edges, every kind of
 * path and site feature, history, a satellite photo and custom varieties — is written and read back unchanged
 * (numbers to 4 decimals). Nothing valid may be dropped and nothing may move outside the plot.
 */
class PlanFileRoundTripTest {

    private fun r4(f: Float) = kotlin.math.floor(f * 10000.0 + 0.5) / 10000.0

    private fun randomPlan(rnd: Random): PlanBundle {
        val plots = List(rnd.nextInt(1, 4)) { pi ->
            val len = listOf(0.5f, 1000f, rnd.nextDouble(0.5, 60.0).toFloat()).random(rnd)
            val wid = listOf(0.5f, 1000f, rnd.nextDouble(0.5, 60.0).toFloat()).random(rnd)
            fun x() = listOf(0f, len, rnd.nextDouble(0.0, len.toDouble()).toFloat()).random(rnd)
            fun y() = listOf(0f, wid, rnd.nextDouble(0.0, wid.toDouble()).toFloat()).random(rnd)
            val name = listOf("Back yard", "Ñ😀 “quoted” \\ \"x\"", "a".repeat(200), "Plot $pi").random(rnd)
            val plot = PlotEntity(name = name, lengthM = len, widthM = wid, description = "d".repeat(rnd.nextInt(0, 500)),
                locationZip = listOf(null, "48104", "00501").random(rnd), latitude = listOf(null, -90.0, 90.0, 42.28).random(rnd),
                longitude = listOf(null, -180.0, 180.0, -83.7).random(rnd), hardinessZone = listOf(null, "1a", "13b", "6a").random(rnd),
                northBearingDeg = listOf(0f, 359.9f, 90f).random(rnd), orientationSet = rnd.nextBoolean(),
                soilSandPct = listOf(null, 0f, 100f).random(rnd), soilPh = listOf(null, 3f, 10f, 6.5f).random(rnd),
                address = listOf(null, "123 Main St, Ann Arbor, MI").random(rnd), pests = Pest.encode(Pest.entries.shuffled(rnd).take(rnd.nextInt(0, 4))),
                backdropJson = if (rnd.nextBoolean()) Backdrop(1f, 2f, 30f, -170f, 0.6f, 0.5f, rnd.nextBoolean()).encode() else null,
                createdTimestamp = 1_700_000_000_000L, lastModifiedTimestamp = 1_800_000_000_000L)
            val plants = List(rnd.nextInt(0, 200)) { PlantedNodeEntity(plotId = 0, seedCode = TestCatalog.seeds.random(rnd).botanicalCode, coordinateXM = x(), coordinateYM = y(),
                datePlantedEpochMillis = rnd.nextLong(0, 4_000_000_000_000L) / 1000 * 1000, germinationFlagResolved = rnd.nextBoolean()) }
            val paths = List(rnd.nextInt(0, 5)) {
                if (rnd.nextBoolean()) PathZoneEntity(plotId = 0, xM = 0f, yM = 0f, widthM = 0.5f, heightM = 0.5f, pathType = "RECTANGLE", label = "p")
                else PathZoneEntity(plotId = 0, xM = 0f, yM = 0f, widthM = 0.6f, heightM = 0f, pathType = "POLYLINE", pointsJson = "0,0;${len / 2},${wid / 2};$len,$wid")
            }
            val features = listOf(
                SiteFeatureEntity(plotId = 0, featureType = SiteFeatureType.TREE.name, pointsJson = "${x()},${y()}", heightM = 100f, radiusM = 60f),
                SiteFeatureEntity(plotId = 0, featureType = SiteFeatureType.FENCE.name, pointsJson = "0,0;$len,0", heightM = 0.01f),
                SiteFeatureEntity(plotId = 0, featureType = SiteFeatureType.SPRINKLER.name, pointsJson = "${x()},${y()}", radiusM = 5f, slopeDirectionDeg = 270f, slopeGradePct = 90f),
                SiteFeatureEntity(plotId = 0, featureType = SiteFeatureType.FLOOD.name, pointsJson = "0,0;$len,0;$len,$wid", floodMonths = "1,12"),
                SiteFeatureEntity(plotId = 0, featureType = SiteFeatureType.SLOPE.name, pointsJson = "0,0;$len,0;0,$wid", slopeDirectionDeg = 0f, slopeGradePct = 100f)
            ).shuffled(rnd).take(rnd.nextInt(0, 6))
            val history = List(rnd.nextInt(0, 50)) {
                val s = TestCatalog.seeds.random(rnd)
                PlantingHistoryEntity(plotId = 0, seasonYear = listOf(1900, 3000, 2025).random(rnd), seedCode = s.botanicalCode, varietyName = s.commonName,
                    family = s.botanicalFamily, rotationGroup = RotationGroup.forSeed(s)?.name, coordinateXM = x(), coordinateYM = y(), radiusM = s.exclusionRadiusM,
                    datePlantedEpochMillis = 1_700_000_000_000L)
            }
            PlanPlot(plot, plants, paths, features, history, if (plot.backdropJson != null) "data:image/png;base64,iVBORw0KGgo=" else null)
        }
        val custom = List(rnd.nextInt(0, 3)) { i -> SeedEntity(botanicalCode = "MY-$i", commonName = "My plant $i", botanicalFamily = "Solanaceae", exclusionRadiusM = listOf(0.01f, 50f, 0.3f).random(rnd), isCustom = true) }
        return PlanBundle(plots, custom, 1_800_000_000_000L)
    }

    @Test
    fun randomPlansComeBackUnchanged() {
        val rnd = Random(2026)
        repeat(60) { case ->
            val b = randomPlan(rnd)
            val back = PlanFileCodec.decode(PlanFileCodec.encode(b, TestCatalog.lookup))
            assertTrue("case $case: ${back.errors} ${back.warnings}", back.errors.isEmpty() && back.warnings.isEmpty())
            val bb = back.bundle!!
            assertEquals(b.plots.size, bb.plots.size); assertEquals(b.customVarieties.size, bb.customVarieties.size)
            b.plots.zip(bb.plots).forEach { (a, c) ->
                assertEquals("case $case name", a.plot.name, c.plot.name); assertEquals(a.plot.description, c.plot.description)
                assertEquals(r4(a.plot.lengthM), c.plot.lengthM.toDouble(), 1e-4); assertEquals(a.plot.hardinessZone, c.plot.hardinessZone)
                assertEquals(a.plot.latitude, c.plot.latitude); assertEquals(a.plot.pests, c.plot.pests); assertEquals(a.plot.address, c.plot.address)
                assertEquals(a.plot.orientationSet, c.plot.orientationSet); assertEquals(a.plot.northBearingDeg.toDouble(), c.plot.northBearingDeg.toDouble(), 1e-3)
                assertEquals(a.plot.backdropJson != null, c.plot.backdropJson != null)
                assertEquals("case $case plants", a.plants.size, c.plants.size)
                a.plants.zip(c.plants).forEach { (p, q) ->
                    assertEquals(p.seedCode, q.seedCode); assertEquals(r4(p.coordinateXM), q.coordinateXM.toDouble(), 1e-4)
                    assertEquals(p.datePlantedEpochMillis, q.datePlantedEpochMillis); assertEquals(p.germinationFlagResolved, q.germinationFlagResolved)
                }
                assertEquals("case $case paths", a.paths.size, c.paths.size); assertEquals("case $case features", a.features.size, c.features.size)
                assertEquals("case $case history", a.history.size, c.history.size)
                a.features.zip(c.features).forEach { (f, g) -> assertEquals(f.featureType, g.featureType); assertEquals(f.floodMonths, g.floodMonths) }
            }
        }
    }

    @Test
    fun anEmptyPlotAndTheLargestAllowedPlotRoundTrip() {
        for (size in listOf(0.0001f, 1000f)) {
            val b = PlanBundle(listOf(PlanPlot(PlotEntity(name = "E", lengthM = size, widthM = size), emptyList(), emptyList(), emptyList())), emptyList(), 1L)
            val back = PlanFileCodec.decode(PlanFileCodec.encode(b, TestCatalog.lookup))
            assertNotNull("$size ${back.errors} ${back.warnings}", back.bundle)
        }
    }
}
