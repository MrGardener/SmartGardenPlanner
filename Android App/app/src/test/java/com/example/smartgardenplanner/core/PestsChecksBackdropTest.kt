package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Host tests for FR-042 (pests), FR-043 (priority plants and pre-plan checks), FR-044 (disclaimer), FR-045 (watering) and FR-046 (satellite photo). */
class PestsChecksBackdropTest {

    private fun seed(code: String, name: String, family: String, radius: Float) =
        SeedEntity(botanicalCode = code, commonName = name, botanicalFamily = family, exclusionRadiusM = radius, plantType = "VEGETABLE")
    private val tomato = seed("TOM-001", "Tomato - Brandywine", "Solanaceae", 0.3f)
    private val pepper = seed("PEP-001", "Pepper - California Wonder", "Solanaceae", 0.25f)
    private val lettuce = seed("LET-001", "Lettuce - Buttercrunch", "Asteraceae", 0.15f)
    private val corn = seed("COR-001", "Sweet Corn - Silver Queen", "Poaceae", 0.2f)
    private val all = listOf(tomato, pepper, lettuce, corn)
    private val lookup: (String) -> SeedEntity? = { c -> all.firstOrNull { it.botanicalCode == c } }

    @Test
    fun pestsRoundTripOnThePlotAndPointAtThePlantsAtRisk() {
        val csv = Pest.encode(listOf(Pest.RABBIT, Pest.DEER, Pest.RABBIT))
        assertEquals("DEER,RABBIT", csv)
        assertEquals(listOf(Pest.DEER, Pest.RABBIT), Pest.parse("DEER, RABBIT,UNICORN"))
        assertNull(Pest.encode(emptyList()))
        val risks = PestAdvisor.risks(listOf(Pest.RACCOON, Pest.RABBIT), listOf(corn, lettuce, tomato))
        assertEquals(listOf("Sweet Corn", "Tomato"), risks.first { it.pest == Pest.RACCOON }.atRisk)
        assertEquals(listOf("Lettuce"), risks.first { it.pest == Pest.RABBIT }.atRisk)
        assertTrue(Pest.entries.all { it.tips.isNotEmpty() && it.signs.isNotBlank() })
        assertTrue(Pest.DEER.tips.first().contains("2.4 m"))
        assertTrue(Pest.entries.size >= 15)
    }

    @Test
    fun checksBeforePlanningWarnAboutSpaceSunAndPests() {
        val plot = PlotEntity(id = 1, name = "P", lengthM = 4f, widthM = 3f, latitude = 40.0, orientationSet = true)
        // A tall wall along the south edge (bottom, y = 3) shades much of the plot.
        val wall = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.WALL.name, pointsJson = "0,3;4,3", heightM = 3f)
        val ctx = PlotContext(plot, emptyList(), listOf(wall), lookup)
        val area = PlotShape.effectiveOutline(plot)
        val checks = PlanChecks.check(ctx, area, listOf(PlantRequest(tomato, 40), PlantRequest(corn, 10, priority = true)), pests = listOf(Pest.RACCOON))
        val text = checks.joinToString("\n") { it.text }
        assertTrue(text, checks.first().severity == Severity.HIGH)
        assertTrue(text, checks.any { it.severity == Severity.HIGH && it.text.startsWith("Space:") })
        assertTrue(text, checks.any { it.text.startsWith("Sun:") && it.text.contains("Tomato") })
        assertTrue(text, checks.any { it.text.startsWith("Pests:") && it.text.contains("Sweet Corn") })
        assertTrue(text, checks.any { it.text.startsWith("Most important: Sweet Corn") })
        // No obstacles: sun can't be checked, and the user is told so.
        val open = PlanChecks.check(PlotContext(plot, emptyList(), emptyList(), lookup), area, listOf(PlantRequest(lettuce, 4)))
        assertTrue(open.any { it.text.contains("treated as full sun") })
        assertTrue(open.any { it.text.startsWith("Most important: none marked") })
    }

    @Test
    fun mostImportantPlantsGetTheSunniestSpots() {
        val plot = PlotEntity(id = 1, name = "P", lengthM = 6f, widthM = 4f, latitude = 40.0, orientationSet = true)
        // A tree in the south-west corner: part of the plot is shaded for much of the day.
        val tree = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.TREE.name, pointsJson = "1,4.5", heightM = 7f, radiusM = 2f)
        val ctx = PlotContext(plot, emptyList(), listOf(tree), lookup)
        val area = PlotShape.effectiveOutline(plot)
        fun meanSun(result: AutoPlanResult, s: SeedEntity) = result.placed.filter { it.seed == s }.map { ctx.sunHoursAt(it.x, it.y) ?: 8.0 }.average()
        val normal = AutoPlanner.plan(ctx, area, listOf(PlantRequest(tomato, 8), PlantRequest(pepper, 8)))
        val peppersFirst = AutoPlanner.plan(ctx, area, listOf(PlantRequest(tomato, 8), PlantRequest(pepper, 8, priority = true)))
        assertEquals(8, peppersFirst.placed.count { it.seed == pepper })
        assertTrue("peppers ${meanSun(peppersFirst, pepper)} vs ${meanSun(normal, pepper)}", meanSun(peppersFirst, pepper) >= meanSun(normal, pepper) - 1e-6)
        assertTrue(peppersFirst.notes.any { it.startsWith("Most important first: Pepper") })
    }

    @Test
    fun wateringAdviceNamesUncoveredAndThirstyPlants() {
        val plot = PlotEntity(id = 1, name = "P", lengthM = 10f, widthM = 10f, latitude = 40.0, orientationSet = true)
        val nodes = listOf(PlantedNodeEntity(plotId = 1, seedCode = "TOM-001", coordinateXM = 1f, coordinateYM = 1f), PlantedNodeEntity(plotId = 1, seedCode = "LET-001", coordinateXM = 9f, coordinateYM = 9f))
        assertTrue(WateringAdvice.forPlot(plot, nodes, emptyList(), lookup).first().startsWith("Nothing waters this plot"))
        val sprinkler = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.SPRINKLER.name, pointsJson = "1,1", radiusM = 2f, slopeGradePct = 360f)
        val advice = WateringAdvice.forPlot(plot, nodes, listOf(sprinkler), lookup)
        assertTrue(advice.joinToString(), advice.any { it.startsWith("1 plant (Lettuce) aren't reached") })
        assertTrue(advice.any { it.contains("drip line or soaker hose") })
        assertTrue(WateringAdvice.needLabel(tomato).startsWith("moderate"))
        assertTrue(WateringAdvice.GENERAL.isNotEmpty() && WateringAdvice.SYSTEMS.isNotEmpty())
        assertTrue(Disclaimer.TEXT.contains("does not guarantee"))
    }

    @Test
    fun satellitePhotoCalibratesAndTravelsInThePlanFile() {
        val plot = PlotEntity(id = 1, name = "Yard", lengthM = 10f, widthM = 8f)
        val b = Backdrop.fresh(plot, 1600, 1200)
        assertEquals(10f, b.widthM, 1e-4f); assertEquals(7.5f, b.heightM, 1e-4f)
        // Two points 2 m apart on the photo as drawn are really 5 m apart: the photo grows 2.5×, the first point stays put.
        val a = PlotPoint(2f, 2f); val c = PlotPoint(4f, 2f)
        val cal = b.calibrate(a, c, 5f)!!
        assertEquals(25f, cal.widthM, 1e-3f)
        assertEquals(-3f, cal.xM, 1e-3f); assertEquals(-3f, cal.yM, 1e-3f)
        assertNull(b.calibrate(a, a, 5f))
        assertEquals(cal.copy(rotationDeg = 15f, visible = false), Backdrop.parse(cal.copy(rotationDeg = 15f, visible = false).encode()))
        assertNull(Backdrop.parse("1;2;0;0;0.5;1"))
        assertTrue(Backdrop.isImageDataUrl("data:image/png;base64,iVBORw0KGgo="))
        assertTrue(!Backdrop.isImageDataUrl("data:text/html;base64,PHNjcmlwdD4="))
        assertTrue(Backdrop.googleMapsUrl(null, null, "1 Main St, Ann Arbor")!!.endsWith("query=1%20Main%20St%2C%20Ann%20Arbor"))
        assertTrue(Backdrop.googleMapsUrl(42.27, -83.73, null)!!.contains("basemap=satellite"))

        val img = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ=="
        val withAll = plot.copy(pests = "DEER,SLUGS", backdropJson = cal.encode())
        val text = PlanFileCodec.encode(PlanBundle(listOf(PlanPlot(withAll, emptyList(), emptyList(), emptyList(), backdropImage = img)), emptyList()), lookup)
        val back = PlanFileCodec.decode(text).bundle!!.plots.single()
        assertEquals("DEER,SLUGS", back.plot.pests)
        assertEquals(img, back.backdropImage)
        val bb = Backdrop.parse(back.plot.backdropJson)!!
        assertTrue(abs(bb.widthM - 25f) < 1e-3f && abs(bb.xM + 3f) < 1e-3f)
        // A bad image is dropped with a warning; the plot still loads.
        val bad = text.replace(img, "data:text/html;base64,PHNjcmlwdD4=")
        val decoded = PlanFileCodec.decode(bad)
        assertNull(decoded.bundle!!.plots.single().backdropImage)
        assertNull(decoded.bundle!!.plots.single().plot.backdropJson)
        assertTrue(decoded.warnings.any { it.contains("satellite photo") })
    }
}
