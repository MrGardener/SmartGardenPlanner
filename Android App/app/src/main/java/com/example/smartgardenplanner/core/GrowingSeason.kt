package com.example.smartgardenplanner.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Growing season by location (FR-054), from NOAA NCEI U.S. Climate Normals 1991–2020 (annual/seasonal, by station):
 * the dates after which (spring) and before which (fall) a frost of 32 °F or lower has a 50 % and a 10 % chance.
 * The plot's latitude/longitude (from its ZIP) picks the nearest station. Days are day-of-year (non-leap, 1–365);
 * 0 in every field marks a frost-free station. Pure Kotlin.
 */
data class FrostStation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevationFt: Int,
    /** Last spring frost: 50 % chance of a later frost / only 10 % chance of a later frost. */
    val lastSpring50: Int,
    val lastSpring10: Int,
    /** First fall frost: 50 % chance of an earlier frost / only 10 % chance of an earlier frost. */
    val firstFall50: Int,
    val firstFall10: Int
) {
    val frostFree: Boolean get() = lastSpring50 == 0 && firstFall50 == 0
}

data class Season(val station: FrostStation, val distanceKm: Double) {
    val frostFree: Boolean get() = station.frostFree
    val lastFrost: Int get() = station.lastSpring50
    val lastFrostSafe: Int get() = station.lastSpring10
    /** First fall frost as a day after the last spring frost (adds 365 when it falls in the next calendar year). */
    val firstFrost: Int get() = wrap(station.firstFall50)
    val firstFrostEarly: Int get() = wrap(station.firstFall10)
    /** Median frost-free days (365 for frost-free places). */
    val frostFreeDays: Int get() = if (frostFree) 365 else max(0, firstFrost - lastFrost)
    private fun wrap(d: Int) = if (d != 0 && d < station.lastSpring50) d + 365 else d
}

/** When to sow or plant a crop, as days of the year (may run past 365 into the next year). */
data class PlantingWindow(
    val startIndoors: IntRange?,
    val plantOut: IntRange?,
    val fallSowing: IntRange?,
    val note: String
)

object GrowingSeason {

    /** Frost-hardy crops that can go out before the last spring frost (weeks before it). */
    private val HARDY_WEEKS = mapOf(
        "pea" to 6, "spinach" to 6, "onion" to 4, "spring onion" to 4, "shallot" to 4, "garlic" to 6, "leek" to 4,
        "lettuce" to 3, "arugula" to 4, "radish" to 5, "carrot" to 3, "beet" to 3, "turnip" to 4, "parsnip" to 3,
        "kale" to 4, "cabbage" to 3, "broccoli" to 2, "cauliflower" to 2, "brussels sprouts" to 3, "collard greens" to 4,
        "swiss chard" to 2, "potato" to 2, "cilantro" to 3, "parsley" to 3, "dill" to 1, "fava bean" to 6, "chives" to 4,
        "strawberry" to 4, "celery" to 0, "asparagus" to 4
    )

    /** Crops usually started indoors (weeks before the last frost) and transplanted. */
    private val START_INDOORS_WEEKS = mapOf(
        "tomato" to 7, "paste tomato" to 7, "pepper" to 9, "shishito type pepper" to 9, "eggplant" to 9, "tomatillo" to 7,
        "broccoli" to 6, "cabbage" to 6, "cauliflower" to 6, "brussels sprouts" to 6, "kale" to 5, "collard greens" to 5,
        "onion" to 10, "leek" to 10, "celery" to 10, "basil" to 6, "lettuce" to 4, "parsley" to 8
    )

    fun parseStation(line: String): FrostStation? {
        val f = line.split('|')
        if (f.size < 8) return null
        val n = f.drop(1).map { it.trim() }
        val lat = n[0].toDoubleOrNull() ?: return null
        val lon = n[1].toDoubleOrNull() ?: return null
        val nums = n.drop(2).take(5).map { it.toIntOrNull() ?: return null }
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (nums.drop(1).any { it !in 0..366 }) return null
        return FrostStation(f[0].trim(), lat, lon, nums[0], nums[1], nums[2], nums[3], nums[4])
    }

    private fun km(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        val p = PI / 180.0
        val dLon = ((o2 - o1 + 540.0) % 360.0) - 180.0   // across the 180th meridian the short way
        val x = dLon * p * cos((a1 + a2) / 2.0 * p)
        val y = (a2 - a1) * p
        return 6371.0 * hypot(x, y)
    }

    /** The nearest station within [maxKm], or null. */
    fun seasonAt(stations: List<FrostStation>, latitude: Double?, longitude: Double?, maxKm: Double = 250.0): Season? {
        if (latitude == null || longitude == null) return null
        var best: FrostStation? = null; var bd = Double.MAX_VALUE
        for (s in stations) { val d = km(latitude, longitude, s.latitude, s.longitude); if (d < bd) { bd = d; best = s } }
        return best?.takeIf { bd <= maxKm }?.let { Season(it, bd) }
    }

    /** "May 6". Day numbers beyond 365 wrap into the next year. */
    fun date(dayOfYear: Int): String {
        val months = listOf("Jan" to 31, "Feb" to 28, "Mar" to 31, "Apr" to 30, "May" to 31, "Jun" to 30, "Jul" to 31, "Aug" to 31, "Sep" to 30, "Oct" to 31, "Nov" to 30, "Dec" to 31)
        var d = ((dayOfYear - 1) % 365 + 365) % 365 + 1
        for ((m, len) in months) { if (d <= len) return "$m $d"; d -= len }
        return "Dec 31"
    }

    fun describe(s: Season): List<String> {
        val where = "Nearest NOAA weather station: ${stationName(s.station)} (${s.distanceKm.toInt()} km away, ${s.station.elevationFt} ft). 1991–2020 averages."
        if (s.frostFree) return listOf("Frost is rare here: you can grow all year, though summer heat limits cool-season crops.", where)
        return listOf(
            "Last spring frost: around ${date(s.lastFrost)} (1 year in 10 as late as ${date(s.lastFrostSafe)}).",
            "First fall frost: around ${date(s.firstFrost)} (1 year in 10 as early as ${date(s.firstFrostEarly)}).",
            "Growing season: about ${s.frostFreeDays} frost-free days.",
            "Frost-tender crops (tomatoes, peppers, squash, beans, corn, basil) go out after ${date(s.lastFrost + 7)}; hardy crops (peas, spinach, onions, lettuce) can go in several weeks earlier.",
            where
        )
    }

    /** "E GRAND RAPIDS, MI US" → "E Grand Rapids, MI". */
    fun stationName(st: FrostStation): String {
        val (place, rest) = st.name.split(",", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val nice = place.trim().lowercase().split(' ').joinToString(" ") { w -> if (w.length <= 2 && w.all { it.isLetter() } && w != "of") w.uppercase() else w.replaceFirstChar { it.uppercase() } }
        val region = rest.trim().split(' ').firstOrNull().orEmpty()
        return if (region.isEmpty()) nice else "$nice, $region"
    }

    fun isHardy(seed: SeedEntity): Boolean = CropReference.speciesKey(seed) in HARDY_WEEKS

    /** Sowing and planting windows for [seed] in [s] (FR-054). */
    fun window(seed: SeedEntity, s: Season): PlantingWindow {
        val key = CropReference.speciesKey(seed)
        val dth = seed.daysToHarvest.coerceIn(20, 400)
        if (s.frostFree) return PlantingWindow(null, 1..365, null, "Frost-free: plant in the cooler months for cool-season crops, any time for warm-season crops.")
        val indoor = START_INDOORS_WEEKS[key]
        val hardy = HARDY_WEEKS[key]
        val outStart = if (hardy != null) s.lastFrost - hardy * 7 else s.lastFrost + 7
        // Last day to plant out and still harvest before the first fall frost (transplants save the indoor weeks).
        val grow = if (indoor != null) max(30, dth - indoor * 7 / 2) else dth
        val outEnd = (if (hardy != null) s.firstFrost + 14 else s.firstFrost) - grow - 7
        val indoors = indoor?.let { (s.lastFrost - it * 7)..(s.lastFrost - it * 7 + 14) }
        val fall = if (hardy != null && seed.lifecycle != "PERENNIAL") (s.firstFrost - dth - 28).let { a -> if (a > outEnd + 20) a..(s.firstFrost - dth + 7) else null } else null
        val note = when {
            outEnd < outStart -> "The season here (${s.frostFreeDays} frost-free days) is short for ${CropReference.speciesName(seed)} ($dth days): choose a faster variety, start it indoors, or use a cold frame or row cover."
            hardy != null -> "Hardy: can go out about $hardy weeks before the last frost."
            else -> "Frost-tender: plant out after the last frost, when the soil has warmed."
        }
        return PlantingWindow(indoors, if (outEnd >= outStart) outStart..outEnd else null, fall, note)
    }

    fun describeWindow(w: PlantingWindow): String = listOfNotNull(
        w.startIndoors?.let { "start indoors ${date(it.first)}–${date(it.last)}" },
        w.plantOut?.let { "plant out from ${date(it.first)} (last chance ${date(it.last)})" },
        w.fallSowing?.let { "fall crop ${date(it.first)}–${date(it.last)}" }
    ).joinToString(" · ").ifBlank { "—" }

    /**
     * Days used to judge sun when planning (FR-055): three days across the growing season (a quarter, half and three
     * quarters of the way from the last spring to the first fall frost), or mid-May, midsummer and early August
     * (hemisphere-adjusted) without frost data. Sun on the day the plan is made (e.g. late September) is not typical.
     */
    fun sunDays(latitude: Double, s: Season?): List<Int> {
        if (s != null && !s.frostFree && s.frostFreeDays >= 30) {
            val a = s.lastFrost; val b = s.firstFrost
            return listOf(a + (b - a) / 4, (a + b) / 2, a + 3 * (b - a) / 4).map { ((it - 1) % 365) + 1 }
        }
        val north = listOf(135, 172, 213)
        return if (latitude >= 0) north else north.map { ((it + 182 - 1) % 365) + 1 }
    }

    /** The middle of the growing season, for the shade display's "Growing season" day. */
    fun midSeasonDay(latitude: Double, s: Season?): Int = sunDays(latitude, s)[1]
}

/** A Plan B choice (FR-056): a variety that can replace a lost plant and still produce with the others. */
data class BackupOption(val seed: SeedEntity, val readyDay: Int, val sameSpecies: Boolean, val reason: String)

/**
 * Plan B (FR-056): when a plant dies, suggest varieties that, planted today, will be ready at about the same time as
 * the plants that survived (and before the first fall frost), fastest-fitting first: other varieties of the same crop,
 * then quick crops of the same family, then quick catch crops. Pure Kotlin.
 */
object BackupPlanner {

    private const val DAY_MS = 86_400_000L

    /**
     * @param plantedDay day of year the original was planted; @param today day of year now;
     * @param season frost data (null = no frost limit known).
     */
    fun options(original: SeedEntity, plantedDay: Int, today: Int, catalog: List<SeedEntity>, season: Season?, limitIn: Int = 6): List<BackupOption> {
        val limit = limitIn.coerceAtLeast(0)   // a negative limit made take() throw
        val targetReady = plantedDay + original.daysToHarvest
        val frostLimit = season?.takeIf { !it.frostFree }?.firstFrost
        val daysLeft = targetReady - today
        val key = CropReference.speciesKey(original)
        fun fits(s: SeedEntity): Boolean {
            if (s.botanicalCode == original.botanicalCode || s.lifecycle == "PERENNIAL") return false
            val ready = today + s.daysToHarvest
            if (frostLimit != null && ready > frostLimit + (if (GrowingSeason.isHardy(s)) 21 else 0)) return false
            return true
        }
        val same = catalog.filter { CropReference.speciesKey(it) == key && fits(it) }
        val family = catalog.filter { it.botanicalFamily == original.botanicalFamily && CropReference.speciesKey(it) != key && fits(it) && CropReference.forSeed(it).isFood }
        val out = mutableListOf<BackupOption>()
        fun add(list: List<SeedEntity>, sameSpecies: Boolean, n: Int) {
            list.sortedWith(compareBy<SeedEntity> { kotlin.math.abs((today + it.daysToHarvest) - targetReady) }.thenBy { it.daysToHarvest })
                .distinctBy { it.botanicalCode }.take(n).forEach { s ->
                    val ready = today + s.daysToHarvest
                    val diff = ready - targetReady
                    val timing = when {
                        kotlin.math.abs(diff) <= 7 -> "ready with the others (around ${GrowingSeason.date(ready)})"
                        diff < 0 -> "ready around ${GrowingSeason.date(ready)}, ${-diff} days before the others"
                        else -> "ready around ${GrowingSeason.date(ready)}, $diff days after the others"
                    }
                    out += BackupOption(s, ready, sameSpecies, "${s.daysToHarvest} days: $timing" + (frostLimit?.let { f -> if (ready > f) " (after the usual first frost: protect it)" else "" } ?: ""))
                }
        }
        add(same, true, limit)
        if (out.size < limit) add(family, false, limit - out.size)
        if (out.isEmpty() && daysLeft > 20) {
            add(catalog.filter { fits(it) && it.daysToHarvest <= max(25, min(daysLeft, 60)) && CropReference.forSeed(it).isFood }, false, 3)
        }
        return out.take(limit)
    }

    fun dayOfYear(epochMillis: Long): Int = SunlightEngine.dayOfYear(epochMillis)

    /** Plan ahead: for each planted species, the fastest catalog varieties (the ones to keep seed of as Plan B). */
    fun planAhead(planted: List<SeedEntity>, catalog: List<SeedEntity>, season: Season?, perSpecies: Int = 3): List<Pair<String, List<SeedEntity>>> =
        planted.distinctBy { CropReference.speciesKey(it) }.map { p ->
            val key = CropReference.speciesKey(p)
            val limit = season?.takeIf { !it.frostFree }?.frostFreeDays
            CropReference.speciesName(p) to catalog.filter { CropReference.speciesKey(it) == key && it.daysToHarvest < p.daysToHarvest && it.lifecycle != "PERENNIAL" && (limit == null || it.daysToHarvest < limit) }
                .sortedBy { it.daysToHarvest }.take(perSpecies)
        }.filter { it.second.isNotEmpty() }
}
