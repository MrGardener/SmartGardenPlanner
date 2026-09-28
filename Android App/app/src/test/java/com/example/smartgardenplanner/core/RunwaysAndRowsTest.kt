package com.example.smartgardenplanner.core

import org.junit.Assert.assertTrue
import org.junit.Test

/** Host tests for the owner's 2026-09-28 report: squash runways over other squash / past the plot edge, and long rows. */
class RunwaysAndRowsTest {

    private val catalog: List<SeedEntity> by lazy {
        val file = listOf("app/src/main/assets/seed_catalog_pro.txt", "Android App/app/src/main/assets/seed_catalog_pro.txt", "src/main/assets/seed_catalog_pro.txt")
            .map { java.io.File(it) }.first { it.exists() }
        file.readLines().mapNotNull { l ->
            val p = l.split("|"); if (p.size < 14) null else SeedEntity(
                botanicalCode = p[0], commonName = p[1], botanicalFamily = p[2], plantType = p[3], lifecycle = p[4],
                hardinessZoneMin = p[5].toInt(), hardinessZoneMax = p[6].toInt(), exclusionRadiusM = p[7].toFloat(),
                germinationDays = p[8].toInt(), daysToHarvest = p[9].toInt(), companionCodes = p[10], antagonistCodes = p[11])
        }
    }
    private fun v(name: String) = catalog.first { it.commonName == name }
    internal val lookup: (String) -> SeedEntity? = { c -> catalog.firstOrNull { it.botanicalCode == c } }

    /** The owner's list from the screenshots. */
    internal fun ownersList() = listOf(
        PlantRequest(v("Sweet Corn - Honey Select"), 49), PlantRequest(v("Tomato - San Marzano"), 12),
        PlantRequest(v("Pepper - Golden California Wonder"), 12), PlantRequest(v("Zucchini - Golden"), 3),
        PlantRequest(v("Summer Squash Mix - Mid-Season"), 3), PlantRequest(v("Garlic - Music"), 42),
        PlantRequest(v("Bush Bean - Blue Lake"), 20), PlantRequest(v("Winter Squash - Spaghetti"), 3),
        PlantRequest(v("Onion - Candy"), 42), PlantRequest(v("Sweet Pea - Golden Yellow Series"), 20),
        PlantRequest(v("Carrot - Danvers"), 64), PlantRequest(v("Cilantro - Mid-Season"), 30), PlantRequest(v("Basil - Lemon"), 6)
    )

    private fun distToPolygon(x: Float, y: Float, poly: List<PlotPoint>): Float =
        if (PlotGeometry.pointInPolygon(x, y, poly)) 0f else PlotGeometry.distanceToPolyline(x, y, poly + poly.first())

    @Test
    fun runwaysNeverCrossPlantsOtherRunwaysOrThePlotEdge_inEveryLayout() {
        for ((len, wid) in listOf(15f to 12f, 13f to 11f)) {
            val plot = PlotEntity(id = 1, name = "P", lengthM = len, widthM = wid, latitude = 42.3, orientationSet = true)
            val house = SiteFeatureEntity(plotId = 1, featureType = SiteFeatureType.BUILDING.name, pointsJson = "8,-0.6;10.5,-0.6;10.5,0;8,0", heightM = 4f)
            val ctx = PlotContext(plot, emptyList(), listOf(house), lookup)
            val area = PlotShape.effectiveOutline(plot)
            for (variant in 0 until AutoPlanner.VARIANT_COUNT) {
                val r = AutoPlanner.planVariant(variant, ctx, area, ownersList())
                val blockedNote = r.notes.any { it.startsWith("There was no completely free ground") }
                for (g in r.guides) {
                    val where = "${len}x$wid variant $variant ${g.species}"
                    if (blockedNote) continue
                    g.area.forEach { p -> assertTrue("$where: runway leaves the plot at $p", p.x >= -0.01f && p.y >= -0.01f && p.x <= len + 0.01f && p.y <= wid + 0.01f) }
                    r.placed.forEach { pl ->
                        assertTrue("$where: ${pl.seed.commonName} at (${pl.x}, ${pl.y}) sits in the runway", distToPolygon(pl.x, pl.y, g.area) >= pl.seed.exclusionRadiusM * 0.79f)
                    }
                    r.guides.filter { it !== g }.forEach { o ->
                        o.area.forEach { p -> assertTrue("$where overlaps ${o.species}'s runway", distToPolygon(p.x, p.y, g.area) > 0.01f || distToPolygon(g.area.first().x, g.area.first().y, o.area) > 0.01f) }
                    }
                }
            }
        }
    }

    @Test
    fun longRowsAreRealRowsWithWalkways() {
        val plot = PlotEntity(id = 1, name = "P", lengthM = 15f, widthM = 12f, latitude = 42.3, orientationSet = true)
        val ctx = PlotContext(plot, emptyList(), emptyList(), lookup)
        val r = AutoPlanner.plan(ctx, PlotShape.effectiveOutline(plot), ownersList(), layout = PlantingLayout.ROWS)
        assertTrue(r.notes.joinToString(), r.notes.first().startsWith("Long rows:"))
        // No two plants of different crops closer than their spacing plus the walkway (no crowding or mixing).
        r.placed.forEachIndexed { i, a ->
            r.placed.drop(i + 1).forEach { b ->
                val d = kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                val need = (a.seed.exclusionRadiusM + b.seed.exclusionRadiusM).toDouble() - 1e-3
                assertTrue("${a.seed.commonName} and ${b.seed.commonName} overlap ($d < $need)", d >= need)
            }
        }
        // Carrots (64, spaced 10 cm) form long straight rows: few distinct row lines, many plants per line.
        val carrotRows = r.placed.filter { it.seed.commonName.startsWith("Carrot") }.groupBy { (it.y * 20).toInt() }
        assertTrue("carrot rows ${carrotRows.mapValues { it.value.size }}", carrotRows.size <= 2)
        // Tallest at the back (north = small y), shortest toward the sun.
        fun meanY(prefix: String) = r.placed.filter { it.seed.commonName.startsWith(prefix) }.map { it.y }.average()
        assertTrue(meanY("Sweet Corn") < meanY("Carrot"))
    }
}
