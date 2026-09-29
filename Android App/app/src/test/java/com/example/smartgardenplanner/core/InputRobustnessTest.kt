package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Adversarial tests for everything that reads user or file input (SGP-TCS-001 §3.1): minimum and maximum values,
 * random and malformed data, unexpected types and characters. Random cases use fixed seeds so a failure reproduces.
 */
class InputRobustnessTest {

    // ------------------------------------------------------------------ JSON reader (LLR-PFILE, TC-PFILE-*)

    @Test
    fun json_deepNestingIsRejectedNotCrashing() {
        for (depth in listOf(1, 63, 64, 65, 1_000, 100_000)) {
            val text = "[".repeat(depth) + "]".repeat(depth)
            val v = MiniJson.parse(text)
            if (depth <= 64) assertNotNull("depth $depth", v) else assertNull("depth $depth", v)
        }
        assertNull(MiniJson.parse("{\"a\":".repeat(50_000) + "1" + "}".repeat(50_000)))
    }

    @Test
    fun json_everyTruncationOfAValidDocumentIsRejectedCleanly() {
        val doc = """{"a":[1,-2.5e3,true,false,null,"x\"y\\z\u00e9\n"],"b":{"c":{}}}"""
        assertNotNull(MiniJson.parse(doc))
        for (i in 0 until doc.length) assertNull("prefix $i", MiniJson.parse(doc.substring(0, i)))
    }

    @Test
    fun json_malformedValuesAreRejected() {
        listOf("", " ", "-", "--1", "1-2", "01x", "tru", "nul", "\"\\u12\"", "\"\\uZZZZ\"", "{1:2}", "[1,]x", "{\"a\" 1}", "[1 2]", "NaN", "Infinity", "\"abc")
            .forEach { assertNull(it, MiniJson.parse(it)) }
        assertEquals(1e308, MiniJson.parse("1e308") as Double, 0.0)
        assertEquals(Double.POSITIVE_INFINITY, MiniJson.parse("1e999") as Double, 0.0)   // accepted by the reader; callers drop non-finite numbers
    }

    @Test
    fun json_randomBytesNeverThrow() {
        val rnd = Random(20260928)
        val alphabet = "{}[]\":,\\-+.eE0123456789tfnrulsa \n\tüé€😀"
        repeat(5_000) {
            val s = buildString { repeat(rnd.nextInt(0, 60)) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            MiniJson.parse(s)   // must return a value or null, never throw
        }
    }

    // ------------------------------------------------------------------ Plan file decoder (LLR-PFILE-*)

    private fun validFile(name: String = "Bed", extra: String = "") = """{"format":"smart-garden-plan","version":1,"plots":[{"name":${q(name)},"lengthM":8,"widthM":5,
        "plants":[{"code":"TOM-001","variety":"Tomato - Brandywine","x":4,"y":4}],"paths":[],"siteFeatures":[{"type":"TREE","points":[[1,1]],"heightM":6,"radiusM":2}]$extra}]}"""
    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun planFile_everyTruncationIsRejectedWithAMessage() {
        val f = validFile()
        assertTrue(PlanFileCodec.decode(f).errors.isEmpty())
        for (i in 0 until f.length step 3) {
            val r = PlanFileCodec.decode(f.substring(0, i))
            assertTrue("prefix $i", r.bundle == null && r.errors.isNotEmpty())
        }
    }

    @Test
    fun planFile_randomMutationsNeverThrowAndNeverYieldOutOfPlotData() {
        val rnd = Random(7)
        val f = validFile()
        repeat(3_000) {
            val chars = f.toCharArray()
            repeat(rnd.nextInt(1, 4)) { chars[rnd.nextInt(chars.size)] = "0123456789-.e\",:{}[]x ".random(rnd) }
            val r = PlanFileCodec.decode(String(chars))
            r.bundle?.plots?.forEach { p ->
                assertTrue(p.plot.lengthM > 0f && p.plot.widthM > 0f && p.plot.lengthM <= PlanFileCodec.MAX_DIMENSION_M)
                p.plants.forEach { n -> assertTrue(n.coordinateXM in 0f..p.plot.lengthM && n.coordinateYM in 0f..p.plot.widthM) }
            }
        }
    }

    @Test
    fun planFile_wrongTypesAndExtremeNumbersAreSkippedWithWarnings() {
        val cases = listOf(
            """"lengthM":"8"""" to "size", """"lengthM":-1""" to "size", """"lengthM":0""" to "size", """"lengthM":1e999""" to "size",
            """"lengthM":1000.0001""" to "size"
        )
        for ((field, _) in cases) {
            val r = PlanFileCodec.decode(validFile().replace(""""lengthM":8""", field))
            assertNull(field, r.bundle)
            assertTrue(field, r.errors.any { it.contains("No usable plots") } && r.warnings.any { it.contains("size") })
        }
        // Minimum and maximum legal sizes are kept.
        for (size in listOf("0.0001", "1000")) {
            val r = PlanFileCodec.decode(validFile().replace(""""lengthM":8""", """"lengthM":$size""").replace(""""x":4""", """"x":0"""))
            assertNotNull(size, r.bundle)
        }
        // A plant outside the plot, at the exact edge, and with non-numeric coordinates.
        val edge = PlanFileCodec.decode(validFile().replace(""""x":4,"y":4""", """"x":8,"y":5""")).bundle!!
        assertEquals(1, edge.plots[0].plants.size)
        val out = PlanFileCodec.decode(validFile().replace(""""x":4,"y":4""", """"x":8.01,"y":5"""))
        assertEquals(0, out.bundle!!.plots[0].plants.size); assertTrue(out.warnings.any { it.contains("1 plant(s) skipped") })
        assertEquals(0, PlanFileCodec.decode(validFile().replace(""""x":4""", """"x":"4"""")).bundle!!.plots[0].plants.size)
    }

    @Test
    fun planFile_hardinessZoneMustBeARealZone() {
        for ((zone, ok) in listOf("7b" to true, "13b" to true, "1a" to true, "7" to false, "7zz" to false, "14a" to false, "0a" to false, "b7" to false, "7B" to false)) {
            val f = validFile(extra = ""","location":{"hardinessZone":${q(zone)}}""")
            assertEquals(zone, if (ok) zone else null, PlanFileCodec.decode(f).bundle!!.plots[0].plot.hardinessZone)
        }
    }

    @Test
    fun planFile_namesSurviveAnyCharactersInARoundTrip() {
        val rnd = Random(99)
        val tricky = listOf("", "\"", "\\", "\n\t\r", "\u0000\u0001\u001f", "😀🌱", "Ñandú ñ", "<script>", "a".repeat(80), "Tomato - Brandywine", "{0}")
        for (name in tricky + List(50) { buildString { repeat(rnd.nextInt(1, 30)) { append(Char(rnd.nextInt(1, 0xD7FF))) } } }) {
            val plot = PlotEntity(id = 0, name = name.ifBlank { "x" }.take(80), lengthM = 5f, widthM = 4f)
            val text = PlanFileCodec.encode(PlanBundle(listOf(PlanPlot(plot, emptyList(), emptyList(), emptyList(), emptyList(), null)), emptyList(), 0L), { null })
            val back = PlanFileCodec.decode(text)
            assertTrue("name ${name.map { it.code }}", back.errors.isEmpty())
            assertEquals(plot.name, back.bundle!!.plots[0].plot.name)
        }
    }

    // ------------------------------------------------------------------ Points, units, dates (LLR-GEO, LLR-UNIT)

    @Test
    fun points_malformedPairsAreSkipped() {
        assertEquals(listOf(PlotPoint(1f, 2f)), PlotGeometry.parsePoints("1,2;;x,1;1,2,3;NaN,1;1e40,2; 3 , ;"))
        assertEquals(emptyList<PlotPoint>(), PlotGeometry.parsePoints(null))
        assertEquals(emptyList<PlotPoint>(), PlotGeometry.parsePoints("  "))
        val rnd = Random(3)
        repeat(2_000) { PlotGeometry.parsePoints(String(CharArray(rnd.nextInt(40)) { ",;.-0123456789eE xN".random(rnd) })).forEach { assertTrue(it.x.isFinite() && it.y.isFinite()) } }
    }

    @Test
    fun distances_invalidTextGivesNull_andCommaDecimalsAreAccepted() {
        for (bad in listOf("", " ", "abc", "NaN", "Infinity", "-Infinity", "1e40", "--1", "1..2", "1,2,3")) assertNull(bad, DistanceFormatter.parseToMeters(bad, DistanceUnit.METERS))
        assertEquals(3.5f, DistanceFormatter.parseToMeters("3,5", DistanceUnit.METERS)!!, 1e-6f)
        assertEquals(3.5f, DistanceFormatter.parseToMeters(" 3.5 ", DistanceUnit.METERS)!!, 1e-6f)
        assertEquals(0.0254f, DistanceFormatter.parseToMeters("1", DistanceUnit.INCHES)!!, 1e-5f)
        // Round trip at the extremes of what the app accepts.
        for (m in listOf(0.05f, 1f, 1000f)) for (u in DistanceUnit.entries)
            assertEquals(m, DistanceFormatter.displayToMeters(DistanceFormatter.metersToDisplay(m, u), u), m * 1e-5f)
    }

    @Test
    fun isoDates_edgeCases() {
        assertEquals(0L, CivilDate.parseIsoUtc("1970-01-01T00:00:00Z"))
        assertNotNull(CivilDate.parseIsoUtc("2024-02-29T23:59:59Z"))
        for (bad in listOf("2023-02-29T00:00:00Z", "2026-13-01T00:00:00Z", "2026-00-10T00:00:00Z", "2026-04-31T00:00:00Z", "2026-01-01T24:00:00Z",
            "2026-01-01 00:00:00Z", "2026-01-01T00:00:00", "", "yesterday", "９９９９-01-01T00:00:00Z")) assertNull(bad, CivilDate.parseIsoUtc(bad))
        val rnd = Random(11)
        repeat(2_000) {
            val ms = rnd.nextLong(-10_000_000_000_000L, 40_000_000_000_000L) / 1000 * 1000
            assertEquals(ms, CivilDate.parseIsoUtc(CivilDate.isoUtc(ms)))
        }
    }

    // ------------------------------------------------------------------ ZIP, zones, photo, frost data

    @Test
    fun zip_alphanumericAndOddInputsFindNothing() {
        val lines = listOf("10001|40.75|-73.99|NY|7b", "10002|||||", "99999||||8a")
        for (bad in listOf("", "1000", "100011", "1000a", "abcde", "10 01", "-1000", "１０００１", "10001\n", "１")) {
            assertNull(bad, ZipTable.find(lines, bad)); assertNull(bad, ZipTable.findZone(lines, bad))
        }
        assertEquals("7b", ZipTable.findZone(lines, "10001")); assertNull(ZipTable.find(lines, "99999")); assertEquals("8a", ZipTable.findZone(lines, "99999"))
        assertNull(ZipTable.find(emptyList(), "10001"))
    }

    @Test
    fun zones_onlyRealLabels() {
        for (z in HardinessZones.LABELS) assertTrue(z, HardinessZones.isValid(z))
        for (z in listOf(null, "", "7", "7zz", "14a", "0b", " 7b", "7b ", "b7", "7B", "10ab")) assertFalse("$z", HardinessZones.isValid(z))
        assertNull(HardinessZones.number("0a")); assertNull(HardinessZones.number("14b")); assertEquals(13, HardinessZones.number("13b"))
    }

    @Test
    fun backdrop_parseAndTurnNeverProduceNonFiniteValues() {
        for (deg in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1e30f, -1e30f, 180f, -180f, 540f, -540f)) {
            val d = Backdrop.normalizeDeg(deg)
            assertTrue("$deg -> $d", d.isFinite() && d in -180f..180f)
        }
        assertNull(Backdrop.parse("0;0;10;Infinity;0.5;0.5"))
        assertNull(Backdrop.parse("0;0;0.99;0;0.5;0.5")); assertNotNull(Backdrop.parse("0;0;1;0;0.5;0.5")); assertNotNull(Backdrop.parse("0;0;2000;0;0.5;20"))
        assertNull(Backdrop.parse("0;0;2000.1;0;0.5;0.5")); assertNull(Backdrop.parse("1;2;3")); assertNull(Backdrop.parse("a;b;c;d;e;f"))
        val rnd = Random(5)
        repeat(3_000) {
            val b = Backdrop.parse(List(rnd.nextInt(4, 8)) { listOf("0", "-1", "1e9", "NaN", "Infinity", "10", "0.3", "h", "", rnd.nextDouble(-5000.0, 5000.0).toString()).random(rnd) }.joinToString(";"))
            if (b != null) assertTrue(listOf(b.xM, b.yM, b.widthM, b.rotationDeg, b.opacity, b.aspect).all { it.isFinite() })
        }
        assertNull(Backdrop(0f, 0f, 10f, aspect = 1f).calibrate(PlotPoint(1f, 1f), PlotPoint(1f, 1f), 5f))
        assertNull(Backdrop(0f, 0f, 10f, aspect = 1f).calibrate(PlotPoint(1f, 1f), PlotPoint(2f, 1f), Float.NaN))
        assertFalse(Backdrop.isImageDataUrl("data:image/svg+xml;base64,AAAA")); assertFalse(Backdrop.isImageDataUrl("data:image/png;base64,AA AA"))
    }

    @Test
    fun frostStations_malformedLinesAreRejected_andDistanceWrapsAt180() {
        for (bad in listOf("", "X|1|2", "X|91|0|0|100|110|280|270", "X|0|181|0|100|110|280|270", "X|a|0|0|100|110|280|270", "X|0|0|0|367|110|280|270", "X|0|0|0|-1|110|280|270"))
            assertNull(bad, GrowingSeason.parseStation(bad))
        val east = GrowingSeason.parseStation("ADAK, AK US|51.9|179.9|10|120|140|300|290")!!
        val s = GrowingSeason.seasonAt(listOf(east), 51.9, -179.9)
        assertNotNull("a station 14 km away across the 180th meridian is found", s)
        assertTrue(s!!.distanceKm < 20.0)
    }
}
