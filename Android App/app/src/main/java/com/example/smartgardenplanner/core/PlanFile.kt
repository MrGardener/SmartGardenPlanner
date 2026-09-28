package com.example.smartgardenplanner.core

/**
 * Smart Garden Plan file, format version 1 (FR-029, T2-DAT-110, T2-PLT-020).
 *
 * A plain JSON file (extension `.sgp.json`) that any client can read and write: this Android app today, the
 * planned browser planner on a computer later (docs/CROSS_PLATFORM_PLAN.md). The full field-by-field
 * specification is docs/PLAN_FILE_FORMAT.md. Rules:
 *  - All lengths are metres, angles are compass degrees, dates are ISO-8601 UTC.
 *  - Plants refer to varieties by catalog code and name; varieties not in the standard catalogs travel in
 *    `customVarieties`.
 *  - Readers must reject a file whose `format` isn't "smart-garden-plan" or whose `version` is newer than
 *    they understand, and must range-check every value before storing anything.
 */
data class PlanPlot(
    val plot: PlotEntity,
    val plants: List<PlantedNodeEntity>,
    val paths: List<PathZoneEntity>,
    val features: List<SiteFeatureEntity>,
    /** Plants of finished seasons (FR-033). Optional in the file; older readers ignore it. */
    val history: List<PlantingHistoryEntity> = emptyList(),
    /** Satellite photo under the plot (FR-046), as a data URL; placement is in [PlotEntity.backdropJson]. */
    val backdropImage: String? = null
)

data class PlanBundle(val plots: List<PlanPlot>, val customVarieties: List<SeedEntity>, val exportedAtMillis: Long = 0L)

/** A decoded plant with the variety name, so a reader can match it when the code isn't in its catalog. */
data class PlanDecodeResult(val bundle: PlanBundle?, val varietyNames: Map<String, String>, val errors: List<String>, val warnings: List<String>)

object PlanFileCodec {

    const val FORMAT = "smart-garden-plan"
    const val VERSION = 1
    const val MAX_PLOTS = 100
    const val MAX_PLANTS_PER_PLOT = 5000
    const val MAX_ITEMS_PER_PLOT = 2000
    const val MAX_HISTORY_PER_PLOT = 20000
    const val MAX_DIMENSION_M = 1000f

    // ------------------------------------------------------------------ writing

    private class W {
        val sb = StringBuilder()
        fun str(s: String) {
            sb.append('"')
            for (c in s) when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u" + c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
            sb.append('"')
        }
        fun num(v: Double) {
            if (!v.isFinite()) { sb.append("null"); return }
            val r = kotlin.math.floor(v * 10000.0 + 0.5) / 10000.0
            if (r == kotlin.math.floor(r) && kotlin.math.abs(r) < 1e15) sb.append(r.toLong()) else sb.append(r.toString())
        }
        fun value(v: Any?) {
            when (v) {
                null -> sb.append("null")
                is String -> str(v)
                is Boolean -> sb.append(v)
                // Float/Double before Int: on Kotlin/JS every number passes `is Int`, so this order keeps
                // the 4-decimal rounding on both platforms (integers still print without a decimal point).
                is Float -> num(v.toDouble())
                is Double -> num(v)
                is Int -> sb.append(v)
                is Long -> sb.append(v)
                is Map<*, *> -> {
                    sb.append('{')
                    var first = true
                    for ((k, x) in v) {
                        if (x == null) continue
                        if (!first) sb.append(',')
                        first = false
                        str(k.toString()); sb.append(':'); value(x)
                    }
                    sb.append('}')
                }
                is List<*> -> {
                    sb.append('[')
                    v.forEachIndexed { i, x -> if (i > 0) sb.append(','); value(x) }
                    sb.append(']')
                }
                else -> str(v.toString())
            }
        }
    }

    private fun iso(millis: Long): String = CivilDate.isoUtc(millis)

    private fun parseIso(s: String?): Long? = CivilDate.parseIsoUtc(s)

    private fun points(json: String?): List<List<Float>> = PlotGeometry.parsePoints(json).map { listOf(it.x, it.y) }

    /** Encodes plots to the version-1 JSON text. [seedLookup] supplies variety names and custom varieties. */
    fun encode(bundle: PlanBundle, seedLookup: (String) -> SeedEntity?, appVersion: String = ""): String {
        val plots = bundle.plots.map { pp ->
            val p = pp.plot
            mapOf(
                "name" to p.name,
                "description" to p.description.ifBlank { null },
                "lengthM" to p.lengthM,
                "widthM" to p.widthM,
                "outline" to PlotGeometry.parsePoints(p.boundaryJson).takeIf { it.size >= 3 }?.map { listOf(it.x, it.y) },
                "orientation" to mapOf("topFacesDeg" to p.northBearingDeg, "set" to p.orientationSet),
                "location" to mapOf("zip" to p.locationZip, "latitude" to p.latitude, "longitude" to p.longitude, "hardinessZone" to p.hardinessZone, "address" to p.address?.ifBlank { null }),
                "soil" to mapOf("sandPct" to p.soilSandPct, "siltPct" to p.soilSiltPct, "clayPct" to p.soilClayPct, "organicPct" to p.soilOrganicPct, "ph" to p.soilPh),
                "pests" to Pest.parse(p.pests).map { it.name }.takeIf { it.isNotEmpty() },
                "backdrop" to Backdrop.parse(p.backdropJson)?.takeIf { Backdrop.isImageDataUrl(pp.backdropImage) }?.let { b ->
                    mapOf("image" to pp.backdropImage, "x" to b.xM, "y" to b.yM, "widthM" to b.widthM, "rotationDeg" to b.rotationDeg, "opacity" to b.opacity, "aspect" to b.aspect, "visible" to b.visible)
                },
                "createdAt" to iso(p.createdTimestamp),
                "modifiedAt" to iso(p.lastModifiedTimestamp),
                "plants" to pp.plants.map { n ->
                    mapOf(
                        "code" to n.seedCode,
                        "variety" to (seedLookup(n.seedCode)?.commonName ?: n.seedCode),
                        "x" to n.coordinateXM, "y" to n.coordinateYM,
                        "plantedAt" to iso(n.datePlantedEpochMillis),
                        "germinationResolved" to n.germinationFlagResolved
                    )
                },
                "paths" to pp.paths.map { z ->
                    if (z.pathType == "POLYLINE") mapOf("type" to "POLYLINE", "points" to points(z.pointsJson), "widthM" to z.widthM, "label" to z.label.ifBlank { null })
                    else mapOf("type" to "RECTANGLE", "x" to z.xM, "y" to z.yM, "widthM" to z.widthM, "heightM" to z.heightM, "label" to z.label.ifBlank { null })
                },
                "siteFeatures" to pp.features.map { f ->
                    mapOf(
                        "type" to f.featureType, "points" to points(f.pointsJson), "label" to f.label.ifBlank { null },
                        "heightM" to f.heightM.takeIf { it > 0f }, "radiusM" to f.radiusM.takeIf { it > 0f },
                                                "slopeDirectionDeg" to f.slopeDirectionDeg.takeIf { f.featureType == SiteFeatureType.SLOPE.name },
                        "slopeGradePct" to f.slopeGradePct.takeIf { f.featureType == SiteFeatureType.SLOPE.name },
                        "arcCentreDeg" to f.slopeDirectionDeg.takeIf { f.featureType == SiteFeatureType.SPRINKLER.name },
                        "arcWidthDeg" to f.slopeGradePct.takeIf { f.featureType == SiteFeatureType.SPRINKLER.name },
                        "floodMonths" to f.floodMonths.split(",").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.isNotEmpty() }
                    )
                },
                "history" to pp.history.takeIf { it.isNotEmpty() }?.map { h ->
                    mapOf(
                        "season" to h.seasonYear, "code" to h.seedCode, "variety" to h.varietyName,
                        "family" to h.family.ifBlank { null }, "rotationGroup" to h.rotationGroup,
                        "x" to h.coordinateXM, "y" to h.coordinateYM, "radiusM" to h.radiusM,
                        "plantedAt" to iso(h.datePlantedEpochMillis)
                    )
                }
            )
        }
        val usedCodes = bundle.plots.flatMap { it.plants.map { n -> n.seedCode } }.toSet()
        val custom = (bundle.customVarieties + usedCodes.mapNotNull { seedLookup(it) }.filter { it.isCustom })
            .distinctBy { it.botanicalCode }
            .map { s ->
                mapOf(
                    "code" to s.botanicalCode, "name" to s.commonName, "family" to s.botanicalFamily, "plantType" to s.plantType,
                    "lifecycle" to s.lifecycle, "zoneMin" to s.hardinessZoneMin, "zoneMax" to s.hardinessZoneMax,
                    "radiusM" to s.exclusionRadiusM, "germinationDays" to s.germinationDays, "daysToHarvest" to s.daysToHarvest,
                    "companions" to s.companionCodes, "antagonists" to s.antagonistCodes, "pests" to s.pestNotes, "care" to s.careNotes,
                    "colorHex" to s.colorHex
                )
            }
        val root = linkedMapOf<String, Any?>(
            "format" to FORMAT,
            "version" to VERSION,
            "exportedAt" to iso(if (bundle.exportedAtMillis > 0) bundle.exportedAtMillis else PlatformClock.nowMillis()),
            "app" to "Smart Garden Planner $appVersion".trim(),
            "units" to "metres",
            "plots" to plots,
            "customVarieties" to custom
        )
        val w = W()
        w.value(root)
        return w.sb.toString()
    }

    // ------------------------------------------------------------------ reading

    private fun Map<*, *>.num(key: String): Double? = (this[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun Map<*, *>.text(key: String, max: Int = 200): String? = (this[key] as? String)?.take(max)

    private fun readPoints(v: Any?): List<PlotPoint>? {
        val list = v as? List<*> ?: return null
        return list.mapNotNull { p ->
            val xy = p as? List<*> ?: return@mapNotNull null
            val x = (xy.getOrNull(0) as? Number)?.toFloat()
            val y = (xy.getOrNull(1) as? Number)?.toFloat()
            if (x != null && y != null && x.isFinite() && y.isFinite()) PlotPoint(x, y) else null
        }
    }

    /**
     * Decodes and validates a plan file. Returns errors (the whole file is rejected) or a bundle plus
     * warnings (items that were skipped). Plot, plant and item ids in the result are 0 (new rows).
     */
    fun decode(text: String): PlanDecodeResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val names = mutableMapOf<String, String>()
        if (text.length > 20_000_000) return PlanDecodeResult(null, names, listOf("The file is too large (over 20 MB)."), warnings)
        val root = MiniJson.parse(text) as? Map<*, *>
            ?: return PlanDecodeResult(null, names, listOf("This isn't a valid plan file (not readable JSON)."), warnings)
        if (root["format"] != FORMAT) errors += "This isn't a Smart Garden plan file."
        val version = (root["version"] as? Number)?.toInt()
        if (version == null) errors += "The file has no format version."
        else if (version > VERSION) errors += "The file was made by a newer version of the app (format $version). Update the app to open it."
        val plotsJson = root["plots"] as? List<*>
        if (plotsJson == null) errors += "The file contains no plots."
        else if (plotsJson.size > MAX_PLOTS) errors += "The file has too many plots (${plotsJson.size}, limit $MAX_PLOTS)."
        if (errors.isNotEmpty()) return PlanDecodeResult(null, names, errors, warnings)

        val custom = (root["customVarieties"] as? List<*>).orEmpty().mapNotNull { v ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            val code = m.text("code", 40)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val radius = m.num("radiusM")?.toFloat()?.takeIf { it > 0f && it <= 50f } ?: run { warnings += "Custom variety $code skipped: invalid spacing."; return@mapNotNull null }
            SeedEntity(
                botanicalCode = code,
                commonName = m.text("name", 100) ?: code,
                botanicalFamily = m.text("family", 60) ?: "",
                exclusionRadiusM = radius,
                germinationDays = m.num("germinationDays")?.toInt()?.coerceIn(1, 365) ?: 10,
                daysToHarvest = m.num("daysToHarvest")?.toInt()?.coerceIn(1, 3650) ?: 60,
                companionCodes = m.text("companions", 400) ?: "",
                antagonistCodes = m.text("antagonists", 400) ?: "",
                pestNotes = m.text("pests", 500) ?: "",
                careNotes = m.text("care", 500) ?: "",
                colorHex = m.text("colorHex", 9)?.takeIf { Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$").matches(it) },
                plantType = m.text("plantType", 20)?.takeIf { it in setOf("VEGETABLE", "FRUIT", "HERB", "FLOWER", "ORNAMENTAL") } ?: "VEGETABLE",
                lifecycle = if (m.text("lifecycle", 20) == "PERENNIAL") "PERENNIAL" else "ANNUAL",
                hardinessZoneMin = m.num("zoneMin")?.toInt()?.coerceIn(1, 13) ?: 3,
                hardinessZoneMax = m.num("zoneMax")?.toInt()?.coerceIn(1, 13) ?: 11,
                isCustom = true
            )
        }

        val plots = mutableListOf<PlanPlot>()
        plotsJson!!.forEachIndexed { index, v ->
            val m = v as? Map<*, *> ?: run { warnings += "Plot ${index + 1} skipped: not an object."; return@forEachIndexed }
            val name = m.text("name", 200)?.takeIf { it.isNotBlank() } ?: "Imported plot ${index + 1}"
            val length = m.num("lengthM")?.toFloat()
            val width = m.num("widthM")?.toFloat()
            if (length == null || width == null || length <= 0f || width <= 0f || length > MAX_DIMENSION_M || width > MAX_DIMENSION_M) {
                warnings += "Plot '$name' skipped: its size is missing or outside 0–${MAX_DIMENSION_M.toInt()} m."
                return@forEachIndexed
            }
            fun inside(p: PlotPoint) = p.x in 0f..length && p.y in 0f..width
            val outline = readPoints(m["outline"])?.takeIf { it.size >= 3 }
            val outlineOk = outline != null && outline.all(::inside) && PlotGeometry.validateOutline(outline) == null
            if (outline != null && !outlineOk) warnings += "Plot '$name': its outline was invalid and was dropped (full rectangle used)."
            val orientation = m["orientation"] as? Map<*, *>
            val location = m["location"] as? Map<*, *>
            val soil = m["soil"] as? Map<*, *>
            val lat = location?.num("latitude")?.takeIf { it in -90.0..90.0 }
            val lon = location?.num("longitude")?.takeIf { it in -180.0..180.0 }
            val backdrop = (m["backdrop"] as? Map<*, *>)?.let { bm ->
                val image = bm["image"] as? String
                if (!Backdrop.isImageDataUrl(image)) { warnings += "Plot '$name': its satellite photo was missing, too large or not an image, and was dropped."; return@let null }
                val b = Backdrop.parse(listOf("x", "y", "widthM", "rotationDeg", "opacity", "aspect").joinToString(";") { k -> bm.num(k)?.toString() ?: "x" } + if (bm["visible"] == false) ";h" else "")
                if (b == null) { warnings += "Plot '$name': its satellite photo placement was invalid, and the photo was dropped."; null } else b to image!!
            }
            fun pct(key: String) = soil?.num(key)?.toFloat()?.takeIf { it in 0f..100f }
            val plot = PlotEntity(
                name = name,
                lengthM = length,
                widthM = width,
                description = m.text("description", 500) ?: "",
                locationZip = location?.text("zip", 10)?.takeIf { ZipTable.isValidZip(it) },
                boundaryJson = if (outlineOk) PlotGeometry.serializePoints(outline!!) else null,
                hardinessZone = location?.text("hardinessZone", 4)?.takeIf { HardinessZones.isValid(it) },
                latitude = lat,
                longitude = lon,
                northBearingDeg = (orientation?.num("topFacesDeg")?.toFloat() ?: 0f).let { ((it % 360f) + 360f) % 360f },
                orientationSet = orientation?.get("set") == true,
                soilSandPct = pct("sandPct"), soilSiltPct = pct("siltPct"), soilClayPct = pct("clayPct"),
                soilOrganicPct = pct("organicPct"),
                soilPh = soil?.num("ph")?.toFloat()?.takeIf { it in 3f..10f },
                address = location?.text("address", 200)?.trim()?.ifBlank { null },
                pests = Pest.encode((m["pests"] as? List<*>).orEmpty().mapNotNull { Pest.of(it as? String) }),
                backdropJson = backdrop?.first?.encode(),
                createdTimestamp = parseIso(m.text("createdAt", 30)) ?: PlatformClock.nowMillis(),
                lastModifiedTimestamp = parseIso(m.text("modifiedAt", 30)) ?: PlatformClock.nowMillis()
            )

            val plantsJson = (m["plants"] as? List<*>).orEmpty()
            if (plantsJson.size > MAX_PLANTS_PER_PLOT) warnings += "Plot '$name': only the first $MAX_PLANTS_PER_PLOT of ${plantsJson.size} plants were read."
            var badPlants = 0
            val plants = plantsJson.take(MAX_PLANTS_PER_PLOT).mapNotNull { pv ->
                val pm = pv as? Map<*, *> ?: return@mapNotNull null.also { badPlants++ }
                val code = pm.text("code", 40)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null.also { badPlants++ }
                val x = pm.num("x")?.toFloat(); val y = pm.num("y")?.toFloat()
                if (x == null || y == null || !inside(PlotPoint(x, y))) { badPlants++; return@mapNotNull null }
                pm.text("variety", 100)?.let { names[code] = it }
                PlantedNodeEntity(
                    plotId = 0, seedCode = code, coordinateXM = x, coordinateYM = y,
                    datePlantedEpochMillis = parseIso(pm.text("plantedAt", 30)) ?: PlatformClock.nowMillis(),
                    germinationFlagResolved = pm["germinationResolved"] == true
                )
            }
            if (badPlants > 0) warnings += "Plot '$name': $badPlants plant(s) skipped (missing variety or outside the plot)."

            val paths = (m["paths"] as? List<*>).orEmpty().take(MAX_ITEMS_PER_PLOT).mapNotNull { zv ->
                val zm = zv as? Map<*, *> ?: return@mapNotNull null
                val label = zm.text("label", 40) ?: ""
                if (zm["type"] == "POLYLINE") {
                    val pts = readPoints(zm["points"])?.filter(::inside)?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                    val w = zm.num("widthM")?.toFloat()?.takeIf { it > 0f && it <= 50f } ?: return@mapNotNull null
                    PathZoneEntity(plotId = 0, xM = 0f, yM = 0f, widthM = w, heightM = 0f, label = label, pathType = "POLYLINE", pointsJson = PlotGeometry.serializePoints(pts))
                } else {
                    val x = zm.num("x")?.toFloat(); val y = zm.num("y")?.toFloat()
                    val w = zm.num("widthM")?.toFloat(); val h = zm.num("heightM")?.toFloat()
                    if (x == null || y == null || w == null || h == null || w <= 0f || h <= 0f || !inside(PlotPoint(x, y)) || !inside(PlotPoint(x + w, y + h))) return@mapNotNull null
                    PathZoneEntity(plotId = 0, xM = x, yM = y, widthM = w, heightM = h, label = label)
                }
            }

            val features = (m["siteFeatures"] as? List<*>).orEmpty().take(MAX_ITEMS_PER_PLOT).mapNotNull { fv ->
                val fm = fv as? Map<*, *> ?: return@mapNotNull null
                val type = SiteFeatureType.of(fm.text("type", 20) ?: "") ?: return@mapNotNull null
                val pts = readPoints(fm["points"]) ?: return@mapNotNull null
                                val needed = when { type.isArea -> 3; type == SiteFeatureType.TREE || type == SiteFeatureType.SPRINKLER || type == SiteFeatureType.HOSE_BIB -> 1; else -> 2 }
                if (pts.size < needed) return@mapNotNull null
                val height = fm.num("heightM")?.toFloat() ?: 0f
                if (type.isBarrier && (height <= 0f || height > 100f)) return@mapNotNull null
                SiteFeatureEntity(
                    plotId = 0, featureType = type.name, pointsJson = PlotGeometry.serializePoints(pts),
                    label = fm.text("label", 40) ?: "",
                    heightM = height.coerceIn(0f, 100f),
                                        radiusM = (fm.num("radiusM")?.toFloat() ?: 0f).coerceIn(0f, 60f),
                    slopeDirectionDeg = (((if (type == SiteFeatureType.SPRINKLER) fm.num("arcCentreDeg") else fm.num("slopeDirectionDeg"))?.toFloat() ?: 0f) % 360f + 360f) % 360f,
                    slopeGradePct = if (type == SiteFeatureType.SPRINKLER) (fm.num("arcWidthDeg")?.toFloat() ?: 360f).coerceIn(10f, 360f)
                        else (fm.num("slopeGradePct")?.toFloat() ?: 0f).coerceIn(0f, 100f),
                    floodMonths = (fm["floodMonths"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt()?.takeIf { mo -> mo in 1..12 } }.joinToString(",")
                )
            }
            val historyJson = (m["history"] as? List<*>).orEmpty()
            if (historyJson.size > MAX_HISTORY_PER_PLOT) warnings += "Plot '$name': only the first $MAX_HISTORY_PER_PLOT of ${historyJson.size} past plantings were read."
            var badHistory = 0
            val history = historyJson.take(MAX_HISTORY_PER_PLOT).mapNotNull { hv ->
                val hm = hv as? Map<*, *> ?: return@mapNotNull null.also { badHistory++ }
                val code = hm.text("code", 40)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null.also { badHistory++ }
                val season = hm.num("season")?.toInt()?.takeIf { it in 1900..3000 } ?: return@mapNotNull null.also { badHistory++ }
                val x = hm.num("x")?.toFloat(); val y = hm.num("y")?.toFloat()
                if (x == null || y == null || !inside(PlotPoint(x, y))) { badHistory++; return@mapNotNull null }
                PlantingHistoryEntity(
                    plotId = 0, seasonYear = season, seedCode = code,
                    varietyName = hm.text("variety", 100) ?: code, family = hm.text("family", 60) ?: "",
                    rotationGroup = hm.text("rotationGroup", 20)?.takeIf { RotationGroup.of(it) != null },
                    coordinateXM = x, coordinateYM = y,
                    radiusM = hm.num("radiusM")?.toFloat()?.takeIf { it > 0f && it <= 50f } ?: 0.3f,
                    datePlantedEpochMillis = parseIso(hm.text("plantedAt", 30)) ?: 0L
                )
            }
            if (badHistory > 0) warnings += "Plot '$name': $badHistory past planting(s) skipped (invalid season, variety or position)."
            plots += PlanPlot(plot, plants, paths, features, history, backdrop?.second)
        }
        if (plots.isEmpty()) return PlanDecodeResult(null, names, listOf("No usable plots in the file.") + warnings, warnings)
        return PlanDecodeResult(PlanBundle(plots, custom, parseIso(root["exportedAt"] as? String) ?: 0L), names, emptyList(), warnings)
    }
}
