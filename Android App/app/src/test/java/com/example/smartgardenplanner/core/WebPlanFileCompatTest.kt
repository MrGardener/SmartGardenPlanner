package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-platform evidence for the plan file (FR-029/FR-030): a file saved by the portable web planner
 * (web/dist/smart-garden-planner.html, produced by its browser smoke test) decodes on the JVM/Android side
 * with nothing lost, and the encoder's number formatting is the same on both platforms.
 */
class WebPlanFileCompatTest {

    private val webFile = """{"format":"smart-garden-plan","version":1,"exportedAt":"2026-09-27T13:26:04Z","app":"Smart Garden Planner web 1.0","units":"metres","plots":[{"name":"Test bed","lengthM":8,"widthM":5,"orientation":{"topFacesDeg":180,"set":true},"location":{"zip":"48104","latitude":42.269,"longitude":-83.728,"hardinessZone":"6a"},"soil":{},"createdAt":"2026-09-27T13:26:03Z","modifiedAt":"2026-09-27T13:26:04Z","plants":[{"code":"TOM-001","variety":"Tomato - Brandywine","x":4,"y":4,"plantedAt":"2026-09-27T13:26:03Z","germinationResolved":false},{"code":"GAR-001","variety":"Garlic - Music","x":7.6276,"y":4.7226,"plantedAt":"2026-09-27T13:26:04Z","germinationResolved":false},{"code":"GAR-001","variety":"Garlic - Music","x":7.3874,"y":4.7226,"plantedAt":"2026-09-27T13:26:04Z","germinationResolved":false},{"code":"GAR-001","variety":"Garlic - Music","x":7.1471,"y":4.7226,"plantedAt":"2026-09-27T13:26:04Z","germinationResolved":false},{"code":"BAS-001","variety":"Basil - Genovese","x":6.3463,"y":4.7226,"plantedAt":"2026-09-27T13:26:04Z","germinationResolved":false},{"code":"BAS-001","variety":"Basil - Genovese","x":5.3853,"y":4.7226,"plantedAt":"2026-09-27T13:26:04Z","germinationResolved":false},{"code":"BAS-001","variety":"Basil - Genovese","x":4.6645,"y":4.7226,"plantedAt":"2026-09-27T13:26:04Z","germinationResolved":false},{"code":"CHV-001","variety":"Chives - Common Chives","x":7.6276,"y":2.24,"plantedAt":"2026-09-27T13:26:04Z","germinationResolved":false},{"code":"CHV-001","variety":"Chives - Common Chives","x":6.6666,"y":2.24,"plantedAt":"2026-09-27T13:26:04Z","germinationResolved":false},{"code":"CHV-001","variety":"Chives - Common Chives","x":7.1471,"y":2.24,"plantedAt":"2026-09-27T13:26:04Z","germinationResolved":false}],"paths":[],"siteFeatures":[{"type":"TREE","points":[[1,1]],"heightM":6,"radiusM":2}]}],"customVarieties":[]}"""

    @Test
    fun fileSavedByTheWebPlanner_decodesOnAndroid() {
        val r = PlanFileCodec.decode(webFile)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        val bundle = assertNotNullAndGet(r.bundle)
        assertEquals(1, bundle.plots.size)
        val p = bundle.plots[0]
        assertEquals("Test bed", p.plot.name)
        assertEquals(180f, p.plot.northBearingDeg)
        assertTrue(p.plot.orientationSet)
        assertEquals("6a", p.plot.hardinessZone)
        assertEquals("48104", p.plot.locationZip)
        assertEquals(10, p.plants.size)
        assertEquals(1, p.features.size)
        assertEquals(SiteFeatureType.TREE.name, p.features[0].featureType)
        assertEquals(6f, p.features[0].heightM)
    }

    @Test
    fun reEncodingTheWebFile_keepsEveryPlant() {
        val bundle = assertNotNullAndGet(PlanFileCodec.decode(webFile).bundle)
        val again = PlanFileCodec.decode(PlanFileCodec.encode(bundle, { null }, "test"))
        assertEquals(bundle.plots[0].plants.map { it.coordinateXM to it.coordinateYM }, assertNotNullAndGet(again.bundle).plots[0].plants.map { it.coordinateXM to it.coordinateYM })
    }

    @Test
    fun numbersAreRoundedToFourDecimals() {
        val plot = PlotEntity(name = "R", lengthM = 2.5000001f, widthM = 3f)
        val text = PlanFileCodec.encode(PlanBundle(listOf(PlanPlot(plot, emptyList(), emptyList(), emptyList())), emptyList(), 0L), { null }, "test")
        assertTrue(text, text.contains("\"lengthM\":2.5,"))
        assertTrue(text, text.contains("\"widthM\":3,"))
    }

    private fun <T> assertNotNullAndGet(v: T?): T { assertNotNull(v); return v!! }
}
