package com.example.smartgardenplanner.core

/** Days of the year to look at the sun on (FR-038). */
enum class ShadeDay(val label: String) {
    TODAY("Today"), SPRING("Spring equinox (Mar 20)"), MIDSUMMER("Midsummer (longest day)"), AUTUMN("Autumn equinox (Sep 22)"), MIDWINTER("Midwinter (shortest day)");

    fun dayOfYear(latitude: Double, today: Int): Int = when (this) {
        TODAY -> today
        SPRING -> 79
        AUTUMN -> 265
        MIDSUMMER -> if (latitude >= 0) 172 else 355
        MIDWINTER -> if (latitude >= 0) 355 else 172
    }
}

/**
 * Shade over the whole day and at a chosen time (FR-038). Adds the plot's own plants as shade casters at their mature
 * height, so the gardener can see how much tall crops shade their neighbours. Times are local solar time (noon = sun
 * due south / north). Pure Kotlin.
 */
object ShadeTools {

    /** A planted plant as a shade caster: a round crown at its mature height and spacing radius. */
    fun plantBarriers(nodes: List<PlantedNodeEntity>, seedLookup: (String) -> SeedEntity?, minHeightM: Float = 0.5f): List<Barrier> =
        nodes.mapNotNull { n ->
            val seed = seedLookup(n.seedCode) ?: return@mapNotNull null
            val h = PlantHeights.heightM(seed)
            if (h < minHeightM) null else Barrier(SiteFeatureType.TREE, listOf(PlotPoint(n.coordinateXM, n.coordinateYM)), h, (seed.exclusionRadiusM * 0.8f).coerceAtLeast(0.1f))
        }

    /** Solar hours of sunrise and sunset. */
    fun sunriseSunset(latitude: Double, dayOfYear: Int): Pair<Double, Double> {
        val len = SunlightEngine.dayLengthHours(latitude, dayOfYear)
        return (12.0 - len / 2.0) to (12.0 + len / 2.0)
    }

    /** True for cells in shade (or before sunrise / after sunset) at [solarHour]: cols × rows, row-major. */
    fun shadowGridAt(lengthM: Float, widthM: Float, cols: Int, rows: Int, latitude: Double, dayOfYear: Int, northBearingDeg: Float, barriers: List<Barrier>, solarHour: Double): BooleanArray {
        val sun = SunlightEngine.position(latitude, dayOfYear, solarHour)
        return BooleanArray(cols * rows) { i ->
            val c = i % cols; val r = i / cols
            SunlightEngine.isShaded((c + 0.5f) * lengthM / cols, (r + 0.5f) * widthM / rows, sun, northBearingDeg, barriers)
        }
    }

    /** When a spot gets direct sun, as solar-time intervals in 15-minute steps. */
    fun sunWindows(x: Float, y: Float, latitude: Double, dayOfYear: Int, northBearingDeg: Float, barriers: List<Barrier>): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        var start: Double? = null
        for (i in 0 until 96) {
            val h = (i + 0.5) * 0.25
            val sunny = SunlightEngine.position(latitude, dayOfYear, h).let { it.elevationDeg > 0.0 && !SunlightEngine.isShaded(x, y, it, northBearingDeg, barriers) }
            if (sunny && start == null) start = i * 0.25
            if (!sunny && start != null) { out += start to i * 0.25; start = null }
        }
        start?.let { out += it to 24.0 }
        return out
    }

    fun clock(h: Double): String { val m = (h * 60).toInt(); return "${m / 60}:${(m % 60).toString().padStart(2, '0')}" }

    /** "Sun 7:15–11:30 and 14:00–18:45" (solar time), or "No direct sun". */
    fun describeWindows(w: List<Pair<Double, Double>>): String =
        if (w.isEmpty()) "No direct sun" else "Sun " + w.joinToString(" and ") { "${clock(it.first)}–${clock(it.second)}" }
}
