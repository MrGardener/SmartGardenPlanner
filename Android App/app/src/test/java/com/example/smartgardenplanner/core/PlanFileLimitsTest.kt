package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every limit of LLR-PFILE-020 at, just inside and just outside its value (SGP-TCS-001). */
class PlanFileLimitsTest {

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun plot(extra: String = "", plants: String = """[{"code":"TOM-001","x":4,"y":4}]""", name: String = "Bed") =
        """{"name":${q(name)},"lengthM":8,"widthM":5,"plants":$plants$extra}"""
    private fun file(plots: String, custom: String = "") =
        """{"format":"smart-garden-plan","version":1,"plots":[$plots]${if (custom.isEmpty()) "" else ""","customVarieties":[$custom]"""}}"""
    private fun decode(plots: String, custom: String = "") = PlanFileCodec.decode(file(plots, custom))

    @Test
    fun plotCountAndFileSizeLimits() {
        assertNotNull(decode((1..100).joinToString(",") { plot(name = "P$it") }).bundle)
        val tooMany = decode((1..101).joinToString(",") { plot(name = "P$it") })
        assertNull(tooMany.bundle); assertTrue(tooMany.errors.any { it.contains("too many plots") })
        assertTrue(PlanFileCodec.decode(" ".repeat(20_000_001)).errors.any { it.contains("too large") })
        assertTrue(decode("").errors.isNotEmpty() || decode("").bundle?.plots?.isEmpty() == true)
    }

    @Test
    fun plantLimitPerPlotAndItemsOutsideThePlot() {
        val many = (1..5001).joinToString(",", "[", "]") { """{"code":"TOM-001","x":${it % 8},"y":1}""" }
        val r = decode(plot(plants = many))
        assertEquals(5000, r.bundle!!.plots[0].plants.size)
        assertTrue(r.warnings.any { it.contains("only the first 5000 of 5001") })
        // A plant without a code, with text coordinates or outside the plot is skipped and counted.
        val odd = """[{"x":1,"y":1},{"code":"","x":1,"y":1},{"code":"TOM-001","x":"1","y":1},{"code":"TOM-001","x":-0.01,"y":1},{"code":"TOM-001","x":8,"y":5}]"""
        val o = decode(plot(plants = odd)).bundle!!.plots[0]
        assertEquals("only the one at the exact corner is kept", 1, o.plants.size)
    }

    @Test
    fun locationSoilAndDirectionOutOfRangeAreDropped() {
        fun p(extra: String) = decode(plot(extra = extra)).bundle!!.plots[0].plot
        val ok = p(""","location":{"latitude":90,"longitude":-180},"soil":{"sandPct":0,"clayPct":100,"ph":3},"orientation":{"topFacesDeg":359.9}""")
        assertEquals(90.0, ok.latitude!!, 0.0); assertEquals(-180.0, ok.longitude!!, 0.0)
        assertEquals(0f, ok.soilSandPct!!, 0f); assertEquals(100f, ok.soilClayPct!!, 0f); assertEquals(3f, ok.soilPh!!, 0f)
        val bad = p(""","location":{"latitude":90.0001,"longitude":180.5,"zip":"4810a"},"soil":{"sandPct":-1,"clayPct":100.1,"ph":10.01}""")
        assertNull(bad.latitude); assertNull(bad.longitude); assertNull(bad.locationZip)
        assertNull(bad.soilSandPct); assertNull(bad.soilClayPct); assertNull(bad.soilPh)
        // The plot's direction is always a real angle in 0..360, even for huge or negative numbers (1e40 gave NaN).
        for ((deg, want) in listOf("720" to 0f, "-90" to 270f, "1e40" to 0f, "-1e40" to 0f, "\"east\"" to 0f)) {
            val b = p(""","orientation":{"topFacesDeg":$deg,"set":true}""").northBearingDeg
            assertTrue("$deg -> $b", b.isFinite() && b >= 0f && b < 360f); assertEquals(deg, want, b, 1e-3f)
        }
    }

    @Test
    fun namesAndTextsAreCutToTheirLimits() {
        val long = "N".repeat(250)
        val r = decode(plot(name = long, extra = ""","description":${q("d".repeat(600))},"location":{"address":${q("a".repeat(300))}}""")).bundle!!.plots[0].plot
        assertEquals(200, r.name.length); assertEquals(500, r.description.length); assertEquals(200, r.address!!.length)
        assertEquals("a blank name gets a default", "Imported plot 1", decode(plot(name = "   ")).bundle!!.plots[0].plot.name)
    }

    @Test
    fun customVarietyLimits() {
        fun v(radius: String, color: String = "#FF6B35") = """{"code":"MY-1","name":"Mine","radiusM":$radius,"colorHex":${q(color)},"germinationDays":9999,"daysToHarvest":-5,"zoneMin":0,"zoneMax":99}"""
        for (r in listOf("0", "-1", "50.01", "\"x\"")) {
            val d = decode(plot(), v(r))
            assertTrue(r, d.bundle!!.customVarieties.isEmpty() && d.warnings.any { it.contains("invalid spacing") })
        }
        val s = decode(plot(), v("50")).bundle!!.customVarieties.single()
        assertEquals(50f, s.exclusionRadiusM, 0f); assertEquals(365, s.germinationDays); assertEquals(1, s.daysToHarvest)
        assertEquals(1, s.hardinessZoneMin); assertEquals(13, s.hardinessZoneMax)
        for (c in listOf("red", "#12345", "#GGGGGG", "#FF6B35FF00")) assertNull(c, decode(plot(), v("0.3", c)).bundle!!.customVarieties.single().colorHex)
        assertEquals("#ff6b35", decode(plot(), v("0.3", "#ff6b35")).bundle!!.customVarieties.single().colorHex)
    }

    @Test
    fun siteFeaturesAndIrrigationLimits() {
        fun feats(json: String) = decode(plot(extra = ""","siteFeatures":[$json]""")).let { it.bundle!!.plots[0].features to it.warnings }
        // Inside, on the edge, and outside the 8 × 5 m plot (a tree at 500 m used to be accepted).
        val (f, w) = feats("""{"type":"TREE","points":[[1,1]],"heightM":6,"radiusM":2},{"type":"TREE","points":[[8,5]],"heightM":6,"radiusM":2},""" +
            """{"type":"TREE","points":[[500,500]],"heightM":6,"radiusM":2},{"type":"FENCE","points":[[0,0],[9,0]],"heightM":1}""")
        assertEquals(2, f.size); assertTrue(w.any { it.contains("2 site feature(s) skipped") })
        // Barrier height 0 and over 100 m are refused; 100 m is kept.
        assertEquals(1, feats("""{"type":"WALL","points":[[0,0],[2,0]],"heightM":100},{"type":"WALL","points":[[0,0],[2,0]],"heightM":100.5},{"type":"WALL","points":[[0,0],[2,0]],"heightM":0}""").first.size)
        // Sprinkler: arc limited to 10–360, throw to 0–60 m, a huge direction becomes 0 (it was NaN).
        val s = feats("""{"type":"SPRINKLER","points":[[4,2]],"radiusM":99,"arcCentreDeg":1e40,"arcWidthDeg":2}""").first.single()
        assertEquals(60f, s.radiusM, 0f); assertEquals(10f, s.slopeGradePct, 0f); assertEquals(0f, s.slopeDirectionDeg, 0f)
        assertEquals(360f, feats("""{"type":"SPRINKLER","points":[[4,2]],"radiusM":3}""").first.single().slopeGradePct, 0f)
        // A drip line needs two points; a sprinkler one.
        assertEquals(0, feats("""{"type":"DRIP_LINE","points":[[1,1]],"radiusM":0.3}""").first.size)
        assertEquals(1, feats("""{"type":"DRIP_LINE","points":[[1,1],[3,1]],"radiusM":0.3}""").first.size)
        assertEquals(0, feats("""{"type":"SPRINKLER","points":[],"radiusM":3}""").first.size)
    }

    @Test
    fun historyLimits() {
        fun hist(json: String) = decode(plot(extra = ""","history":[$json]""")).let { it.bundle!!.plots[0].history to it.warnings }
        val (h, w) = hist("""{"season":1900,"code":"TOM-001","x":1,"y":1},{"season":3000,"code":"TOM-001","x":8,"y":5},""" +
            """{"season":1899,"code":"TOM-001","x":1,"y":1},{"season":3001,"code":"TOM-001","x":1,"y":1},{"season":2025,"x":1,"y":1},""" +
            """{"season":2025,"code":"TOM-001","x":9,"y":1},{"season":"2025","code":"TOM-001","x":1,"y":1}""")
        assertEquals(2, h.size); assertTrue(w.any { it.contains("5 past planting(s) skipped") })
        assertEquals("missing radius defaults to 0.3 m", 0.3f, h[0].radiusM, 0f)
        val many = (1..20_001).joinToString(",") { """{"season":2025,"code":"TOM-001","x":1,"y":1}""" }
        val (hm, wm) = hist(many)
        assertEquals(20_000, hm.size); assertTrue(wm.any { it.contains("only the first 20000") })
    }
}
