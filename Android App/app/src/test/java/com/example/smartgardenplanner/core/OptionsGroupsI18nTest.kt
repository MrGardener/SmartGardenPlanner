package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Host tests for FR-058 (antagonist retry), FR-059 (vine runways), FR-060 (layout options), FR-061 (groups), FR-062 (languages). */
class OptionsGroupsI18nTest {

    private fun seed(code: String, name: String, family: String, radius: Float, companions: String = "", antagonists: String = "") =
        SeedEntity(botanicalCode = code, commonName = name, botanicalFamily = family, exclusionRadiusM = radius, plantType = "VEGETABLE", companionCodes = companions, antagonistCodes = antagonists)
    private val corn = seed("COR-001", "Sweet Corn - Silver Queen", "Poaceae", 0.25f, antagonists = "TOM")
    private val tomato = seed("TOM-001", "Tomato - Brandywine", "Solanaceae", 0.3f, antagonists = "COR")
    private val squash = seed("WSQ-001", "Winter Squash - Spaghetti", "Cucurbitaceae", 0.9f)
    private val summer = seed("SSQ-001", "Summer Squash Mix - Mid-Season", "Cucurbitaceae", 0.64f)
    private val bean = seed("BEA-001", "Bush Bean - Provider", "Fabaceae", 0.15f)
    private val all = listOf(corn, tomato, squash, summer, bean)
    private val lookup: (String) -> SeedEntity? = { c -> all.firstOrNull { it.botanicalCode == c } }

    @Test
    fun tomatoesFindRoomAwayFromTheirAntagonist_inOneBlock() {
        val plot = PlotEntity(id = 1, name = "P", lengthM = 12f, widthM = 8f, latitude = 42.0, orientationSet = true)
        val ctx = PlotContext(plot, emptyList(), emptyList(), lookup)
        val r = AutoPlanner.plan(ctx, PlotShape.effectiveOutline(plot), listOf(PlantRequest(corn, 49), PlantRequest(tomato, 9), PlantRequest(bean, 20)))
        assertTrue("unplaced ${r.unplaced}", r.unplaced.isEmpty())
        assertTrue(r.notes.joinToString(), r.notes.none { it.startsWith("Tomato is in") })
    }

    @Test
    fun vinesKeepTheirRunwayClearOfOtherPlantsAndFences() {
        val plot = PlotEntity(id = 1, name = "P", lengthM = 14f, widthM = 10f, latitude = 42.0, orientationSet = true)
        val fence = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.FENCE.name, pointsJson = "0,9.6;14,9.6", heightM = 1.2f)
        val ctx = PlotContext(plot, emptyList(), listOf(fence), lookup)
        val r = AutoPlanner.plan(ctx, PlotShape.effectiveOutline(plot), listOf(PlantRequest(squash, 3), PlantRequest(summer, 3), PlantRequest(bean, 20)))
        assertTrue("unplaced ${r.unplaced}", r.unplaced.isEmpty())
        for (g in r.guides) {
            val poly = g.area
            val inside = r.placed.filter { p -> CropReference.speciesName(p.seed) != g.species && PlotGeometry.pointInPolygon(p.x, p.y, poly) }
            assertTrue("${inside.map { it.seed.commonName }} inside ${g.species}'s runway", inside.isEmpty())
            assertTrue("${g.species} runway reaches the fence", poly.all { it.y < 9.6f - 0.2f || it.y <= g.from.y })
        }
    }

    @Test
    fun severalDifferentLayoutsAreOffered() {
        val plot = PlotEntity(id = 1, name = "P", lengthM = 10f, widthM = 8f, latitude = 42.0, orientationSet = true)
        val ctx = PlotContext(plot, emptyList(), emptyList(), lookup)
        val opts = AutoPlanner.options(ctx, PlotShape.effectiveOutline(plot), listOf(PlantRequest(corn, 16), PlantRequest(bean, 12), PlantRequest(summer, 3)), count = 8)
        assertTrue("only ${opts.size} options", opts.size >= 5)
        assertEquals(opts.size, opts.map { AutoPlanner.signature(it.result) }.distinct().size)
        assertEquals("Suggested", opts.first().label)
        assertTrue(AutoPlanner.summarize(ctx, opts.first().result).contains("plants placed"))
        assertEquals("Long rows", AutoPlanner.variantLabel(AutoPlanner.VARIANT_COUNT - 1))
    }

    @Test
    fun aGroupCanBeFoundMovedAndRearranged() {
        val block = (0 until 20).map { i -> PlantedNodeEntity(id = i.toLong() + 1, plotId = 1, seedCode = "BEA-001", coordinateXM = 2f + (i % 5) * 0.3f, coordinateYM = 2f + (i / 5) * 0.3f) }
        val far = PlantedNodeEntity(id = 99, plotId = 1, seedCode = "BEA-001", coordinateXM = 8f, coordinateYM = 8f)
        val g = GroupTools.groupOf(block + far, block[7], lookup)
        assertEquals(20, g.size)
        assertEquals(20, GroupTools.inRect(block + far, 1.9f, 1.9f, 3.3f, 3.0f).size)
        val moved = GroupTools.moved(g, 1f, 0.5f)
        assertEquals(block[0].coordinateXM + 1f, moved.first { it.id == 1L }.coordinateXM, 1e-5f)
        val two = GroupTools.rearranged(g, listOf(10, 10), lookup)!!
        assertEquals(2, two.map { (it.coordinateYM * 1000).toInt() }.distinct().size)
        assertEquals(10, two.map { (it.coordinateXM * 1000).toInt() }.distinct().size)
        // Same center.
        assertEquals(g.map { it.coordinateXM }.average(), two.map { it.coordinateXM }.average(), 1e-3)
        assertNull(GroupTools.rearranged(g, listOf(3, 3), lookup))
        val plot = PlotEntity(id = 1, name = "P", lengthM = 10f, widthM = 10f)
        assertEquals(0, GroupTools.problems(two, listOf(far), plot, lookup))
        assertTrue(GroupTools.problems(GroupTools.moved(two, 7f, 0f), listOf(far), plot, lookup) > 0) // off the plot
    }

    @Test
    fun theInterfaceTranslatesFromADictionary_andFallsBackToEnglish() {
        val dict = I18n.parse(listOf("# comment", "New plot\tNueva parcela", "{0} plants selected\t{0} plantas seleccionadas", "Option {0}: {1}\tOpción {0}: {1}", "Suggested\tSugerida", "bad line"))
        assertEquals(4, dict.size)
        I18n.use("es", dict)
        try {
            assertEquals("Nueva parcela", I18n.tr("New plot"))
            assertEquals(" Nueva parcela ", I18n.tr(" New plot "))
            assertEquals("12 plantas seleccionadas", I18n.tr("12 plants selected"))
            assertEquals("Opción 2: Sugerida", I18n.tr("Option 2: Suggested"))
            assertEquals("Save", I18n.tr("Save"))
            assertEquals(0.5, I18n.coverage(listOf("New plot", "Save")), 1e-9)
            val file = listOf("src/main/assets/i18n/es.txt", "app/src/main/assets/i18n/es.txt", "Android App/app/src/main/assets/i18n/es.txt").map { java.io.File(it) }.first { it.exists() }
            val es = I18n.parse(file.readLines())
            assertTrue(es.size > 150)
            assertTrue("every template keeps its placeholders", es.all { (k, v) -> Regex("\\{\\d\\}").findAll(k).map { it.value }.toSet() == Regex("\\{\\d\\}").findAll(v).map { it.value }.toSet() })
        } finally { I18n.use("en", emptyMap()) }
        assertEquals("New plot", I18n.tr("New plot"))
    }
}
