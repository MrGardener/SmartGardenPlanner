package sgp.web

import com.example.smartgardenplanner.core.Barrier
import com.example.smartgardenplanner.core.CompanionPlantingValidator
import com.example.smartgardenplanner.core.CropReference
import com.example.smartgardenplanner.core.CropRotation
import com.example.smartgardenplanner.core.VarietyCatalogTraits
import com.example.smartgardenplanner.core.HardinessZones
import com.example.smartgardenplanner.core.LayoutPalette
import com.example.smartgardenplanner.core.PathZoneEntity
import com.example.smartgardenplanner.core.PlantedNodeEntity
import com.example.smartgardenplanner.core.PlatformClock
import com.example.smartgardenplanner.core.PlotGeometry
import com.example.smartgardenplanner.core.PlotPoint
import com.example.smartgardenplanner.core.PlotShape
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.SiteFeatureEntity
import com.example.smartgardenplanner.core.SiteFeatureType
import com.example.smartgardenplanner.core.SunBand
import com.example.smartgardenplanner.core.SunlightEngine
import com.example.smartgardenplanner.core.fmt
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class Tool(val label: String, val hint: String) {
    SELECT("Select / move", "Click a plant, path or obstacle to select it; drag to move it. Drag empty space to pan; scroll to zoom. Delete removes the selection."),
    PLANT("Plant", "Click to plant the chosen variety. Choose varieties on the Plants tab."),
    PATH("No-plant path", "Drag a rectangle where nothing should be planted."),
    TREE("Tree", "Click where the trunk is, then give its height and crown size."),
    LINE_OBSTACLE("Fence / wall / building", "Click points along it, then press Enter or click Finish."),
    AREA("Sun / shade / flood / slope area", "Click the corners (3+), then press Enter or click Finish."),
        OUTLINE("Plot outline", "Drag a white corner to move it; double-click an edge to add a corner. Or click new corners in order (3+) and press Enter to redraw. “Delete outline” removes it."),
        PLAN("Plan an area for me", "Drag over the area you want planted; then list what to plant."),
    WATER("Irrigation", "Sprinkler or hose tap: click where it is. Drip line: click points along it, then Finish (Enter) or double-click. Choose the kind on the Plot or Care tab; click the tool again to stop."),
    PHOTO("Satellite photo", "Drag the photo to line it up with the plot. Add a photo, set its scale, turn it or hide it on the Plot tab → Satellite photo.")
}

/** What is currently selected on the layout. */
sealed interface Selection {
    data class Plant(val id: Long) : Selection
    data class Path(val id: Long) : Selection
    data class Feature(val id: Long) : Selection
    /** FR-061: several plants selected together (Shift+drag, or "Select its group"). */
    data class Group(val ids: Set<Long>) : Selection
}

/** The plot layout drawn as SVG, in metres, with the editing tools. */
object Canvas {
    var tool = Tool.SELECT
    var activeSeed: SeedEntity? = null
    var obstacleType = SiteFeatureType.FENCE
        var areaType = SiteFeatureType.FULL_SUN
    var waterType = SiteFeatureType.SPRINKLER
    private var hoverAt: PlotPoint? = null
    var selection: Selection? = null
        var showShade = false
    /** Plants to point out on the layout (legend / harvest "find"), or null. */
    var find: ((PlantedNodeEntity) -> Boolean)? = null
    private var dragVertex: Int? = null
    val points = mutableListOf<PlotPoint>()           // polygon/polyline being drawn
    private var dragStart: PlotPoint? = null           // rectangle tools, panning and moving
    private var dragNow: PlotPoint? = null
    private var dragMoved = false
    private var bandStart: PlotPoint? = null           // FR-061: Shift+drag selection box
    private var panOrigin: DoubleArray? = null
    private var vb = doubleArrayOf(0.0, 0.0, 10.0, 10.0) // viewBox x, y, w, h (metres)
    private var fittedFor: Long = -1
    private var shadeCache: Pair<String, FloatArray>? = null
    private lateinit var svg: Element

    fun mount(host: Element) {
        svg = s("svg", "id" to "sgp-svg", "xmlns" to SVGNS, "preserveAspectRatio" to "xMidYMid meet")
        host.appendChild(svg)
        svg.on("pointerdown") { onDown(it as MouseEvent) }
        svg.on("pointermove") { onMove(it as MouseEvent) }
        svg.on("pointerup") { onUp(it as MouseEvent) }
                        svg.on("dblclick") { e ->
            when {
                tool == Tool.WATER && waterType == SiteFeatureType.DRIP_LINE -> finishPoints()
                tool == Tool.OUTLINE && points.isEmpty() -> addOutlineCorner(toMetres(e as MouseEvent))
                tool in setOf(Tool.LINE_OBSTACLE, Tool.AREA, Tool.OUTLINE) -> finishPoints()
                tool == Tool.SELECT -> (selection as? Selection.Plant)?.let { sel -> Store.plot()?.plants?.firstOrNull { it.id == sel.id }?.let { Dialogs.plant(it) } }
            }
        }
        svg.on("wheel") { onWheel(it as WheelEvent) }
    }

        /** Irrigation buttons: pick a kind, or click the same kind again to stop placing (FR-053). */
    fun toggleWater(t: SiteFeatureType) {
        if (tool == Tool.WATER && waterType == t) { setTool(Tool.SELECT); return }
        waterType = t
        setTool(Tool.WATER)
        App.status(when (t) {
            SiteFeatureType.DRIP_LINE -> "Drip line / soaker hose: click points along it, then click Finish (Enter) or double-click the last point. Esc cancels. Click Drip line again to stop."
            SiteFeatureType.HOSE_BIB -> "Hose tap: click where the tap is, then give the hose length. Click Hose tap again to stop."
            else -> "Sprinkler: click where it stands, then set its throw and pattern. Click Sprinkler again to stop."
        })
    }

    fun setTool(t: Tool) {
        if (Store.viewSeason != null && t != Tool.SELECT) { App.status("You are looking at season ${Store.viewSeason} (read only). Choose the planning season on the Plot tab first."); return }
        if (t != Tool.PHOTO) Photo.calibrating = false
        tool = t; points.clear(); dragStart = null; App.status(t.hint); App.render()
    }

    fun fit() {
        val p = Store.plot()?.plot ?: return
        val pad = max(p.lengthM, p.widthM) * 0.08 + 0.4
        val comp = max(p.lengthM, p.widthM) / 38.0 * 3.8   // compass radius (see drawCompass)
        val top = max(pad * 1.6, comp * 1.75)
        vb = doubleArrayOf(-pad * 1.2, -top, p.lengthM + pad * 1.2 + comp * 2.6, p.widthM + pad + top)
        fittedFor = p.id
    }

    fun zoom(factor: Double, cx: Double = vb[0] + vb[2] / 2, cy: Double = vb[1] + vb[3] / 2) {
        val w = (vb[2] * factor).coerceIn(0.5, 5000.0)
        val h = vb[3] * w / vb[2]
        vb = doubleArrayOf(cx - (cx - vb[0]) * w / vb[2], cy - (cy - vb[1]) * h / vb[3], w, h)
        render()
    }

    private fun toMetres(e: MouseEvent): PlotPoint {
        val m = svg.asDynamic().getScreenCTM().inverse()
        val x = (m.a * e.clientX + m.c * e.clientY + m.e) as Double
        val y = (m.b * e.clientX + m.d * e.clientY + m.f) as Double
        return PlotPoint(x.toFloat(), y.toFloat())
    }

    // ----------------------------------------------------------------- drawing

    fun render() {
        if (!::svg.isInitialized) return
                svg.clear()
        svg.add(s("defs").also { d -> d.add(s("marker", "id" to "sgp-arrow", "viewBox" to "0 0 10 10", "refX" to 8, "refY" to 5, "markerWidth" to 5, "markerHeight" to 5, "orient" to "auto-start-reverse").also { m -> m.add(s("path", "d" to "M0,0 L10,5 L0,10 z", "fill" to "#16a34a")) }) })
        val wp = Store.plot()
        if (wp == null) {
            svg.setAttribute("viewBox", "0 0 10 6")
            svg.add(s("text", "x" to 5, "y" to 3, "text-anchor" to "middle", "font-size" to 0.45, "fill" to "currentColor").also { it.textContent = "Create a plot or open a plan file to start." })
            return
        }
        val p = wp.plot
        if (fittedFor != p.id) fit()
        svg.setAttribute("viewBox", "${num(vb[0])} ${num(vb[1])} ${num(vb[2])} ${num(vb[3])}")
        val fs = max(p.lengthM, p.widthM) / 38.0
        val outline = PlotShape.outline(p)

        svg.add(s("rect", "x" to 0, "y" to 0, "width" to p.lengthM, "height" to p.widthM, "fill" to col(LayoutPalette.PAPER), "stroke" to col(LayoutPalette.BORDER), "stroke-width" to 1.5, "vector-effect" to "non-scaling-stroke"))
        drawPhoto(wp)
        // Grid every metre (every 5 m on big plots).
        val step = if (max(p.lengthM, p.widthM) > 40) 5.0 else 1.0
        var g = step
        while (g < p.lengthM) { svg.add(line(g, 0.0, g, p.widthM.toDouble(), col(LayoutPalette.GRID), 1.0)); g += step }
        g = step
        while (g < p.widthM) { svg.add(line(0.0, g, p.lengthM.toDouble(), g, col(LayoutPalette.GRID), 1.0)); g += step }
        drawRulers(p.lengthM.toDouble(), p.widthM.toDouble(), fs, step)

                if (showShade) drawShade(wp)
        if (Store.showWater) drawWater(wp, fs)
        wp.features.forEach { drawFeature(it, fs) }
        wp.paths.forEach { drawPath(it) }
        if (outline.size >= 3) {
            val poly = outline.joinToString(" ") { "${num(it.x.toDouble())},${num(it.y.toDouble())}" }
            svg.add(s("path", "d" to "M0,0 H${p.lengthM} V${p.widthM} H0 Z M" + outline.joinToString(" L") { "${num(it.x.toDouble())},${num(it.y.toDouble())}" } + " Z",
                "fill" to col(LayoutPalette.OUTSIDE_OUTLINE), "fill-opacity" to LayoutPalette.alpha(LayoutPalette.OUTSIDE_OUTLINE), "fill-rule" to "evenodd"))
                        svg.add(s("polygon", "points" to poly, "fill" to "none", "stroke" to col(LayoutPalette.BORDER), "stroke-width" to 2, "vector-effect" to "non-scaling-stroke"))
            if (tool == Tool.OUTLINE && points.isEmpty()) outline.forEach { v ->
                svg.add(s("circle", "cx" to v.x, "cy" to v.y, "r" to handleR(), "fill" to "#ffffff", "stroke" to col(LayoutPalette.BORDER), "stroke-width" to 2, "vector-effect" to "non-scaling-stroke", "class" to "handle"))
            }
        }
                val viewing = Store.viewSeason
        if (viewing != null) {
            // A past season, read-only: its plants drawn as normal plants instead of this season's.
            wp.history.filter { it.seasonYear == viewing }.forEach { h ->
                drawPlant(PlantedNodeEntity(id = -h.id, plotId = wp.plot.id, seedCode = h.seedCode, coordinateXM = h.coordinateXM, coordinateYM = h.coordinateYM, datePlantedEpochMillis = h.datePlantedEpochMillis), selected = false, fs = fs)
            }
        } else {
                        Store.historyYear?.let { y -> drawHistory(wp, y, fs) }
            // While previewing next season or a rotation year, this season's plants are hidden: that plan replaces them.
            val replacing = Store.preview != null && Store.previewMode != PreviewMode.NORMAL
            val groupIds = (selection as? Selection.Group)?.ids.orEmpty()
            if (!replacing) wp.plants.forEach { drawPlant(it, selected = (selection as? Selection.Plant)?.id == it.id || it.id in groupIds, fs = fs) }
            if (Store.showWater) drawDryRings(wp, fs)
        }
        Store.preview?.placed?.forEach { pl ->
            val c = Colors.of(pl.seed)
            svg.add(s("circle", "cx" to pl.x, "cy" to pl.y, "r" to pl.seed.exclusionRadiusM, "fill" to c, "fill-opacity" to 0.25, "stroke" to c, "stroke-dasharray" to "4 3", "stroke-width" to 1.5, "vector-effect" to "non-scaling-stroke"))
        }
                Store.preview?.guides?.forEach { gd ->
            svg.add(s("polygon", "points" to gd.area.joinToString(" ") { "${it.x},${it.y}" }, "fill" to "#16a34a", "fill-opacity" to 0.08, "stroke" to "#16a34a", "stroke-dasharray" to "5 4", "stroke-width" to 1.5, "vector-effect" to "non-scaling-stroke", "class" to "guide"))
            svg.add(s("line", "x1" to gd.from.x, "y1" to gd.from.y, "x2" to gd.to.x, "y2" to gd.to.y, "stroke" to "#16a34a", "stroke-width" to 3, "marker-end" to "url(#sgp-arrow)", "vector-effect" to "non-scaling-stroke"))
            svg.add(s("text", "x" to (gd.from.x + gd.to.x) / 2, "y" to (gd.from.y + gd.to.y) / 2, "font-size" to fs * 0.6, "fill" to "#166534", "text-anchor" to "middle", "font-style" to "italic").also { it.textContent = "${gd.species} runs this way" })
        }
        Store.previewArea?.let { a -> svg.add(s("polygon", "points" to a.joinToString(" ") { "${it.x},${it.y}" }, "fill" to "none", "stroke" to "#10b981", "stroke-width" to 2, "vector-effect" to "non-scaling-stroke")) }
        drawInProgress()
        drawCompass(p.lengthM.toDouble(), fs, p.northBearingDeg.toDouble(), p.orientationSet)
    }

    private fun line(x1: Double, y1: Double, x2: Double, y2: Double, colour: String, width: Double) =
        s("line", "x1" to x1, "y1" to y1, "x2" to x2, "y2" to y2, "stroke" to colour, "stroke-width" to width, "vector-effect" to "non-scaling-stroke")

    private fun drawRulers(l: Double, w: Double, fs: Double, step: Double) {
        // FR-057: numbers only on the ticks; the unit once, at the corner where the two rulers meet.
        svg.add(s("text", "x" to -fs * 0.9, "y" to -fs * 0.5, "font-size" to fs * 0.9, "fill" to "currentColor", "text-anchor" to "end", "font-style" to "italic", "class" to "ruler-unit").also { it.textContent = "(m)" })
        var m = 0.0
        while (m <= l + 1e-6) {
            svg.add(s("text", "x" to m, "y" to -fs * 0.5, "font-size" to fs, "fill" to "currentColor", "text-anchor" to "middle", "class" to "ruler-tick").also { it.textContent = "${m.toInt()}" })
            m += step
        }
        m = step
        while (m <= w + 1e-6) {
            svg.add(s("text", "x" to -fs * 0.4, "y" to m + fs * 0.35, "font-size" to fs, "fill" to "currentColor", "text-anchor" to "end").also { it.textContent = "${m.toInt()}" })
            m += step
        }
    }

    /** Compass rose with four arrowheads and N / E / S / W, turned to the plot's direction (FR-028). */
    private fun drawCompass(l: Double, fs: Double, bearing: Double, set: Boolean) {
        val r = fs * 3.8
        val cx = l + r * 1.3; val cy = -r * 0.45
        val g = s("g", "class" to "compass", "transform" to "translate(${num(cx)},${num(cy)}) rotate(${num(-bearing)})")
        g.add(s("circle", "cx" to 0, "cy" to 0, "r" to r * 1.12, "fill" to "var(--card)", "stroke" to "var(--line)", "stroke-width" to 1, "vector-effect" to "non-scaling-stroke"))
        listOf(0.0 to "N", 90.0 to "E", 180.0 to "S", 270.0 to "W").forEach { (deg, letter) ->
            val a = deg * kotlin.math.PI / 180.0
            fun pt(dist: Double, side: Double): String {
                val x = sin(a) * dist + cos(a) * side; val y = -cos(a) * dist + sin(a) * side
                return "${num(x)},${num(y)}"
            }
            val north = letter == "N"
            val ink = if (north && set) "#dc2626" else if (north) "#9ca3af" else "currentColor"
            val tip = pt(r * 1.02, 0.0); val base = pt(r * 0.6, 0.0)
            // Half-filled arrowhead, like a classic compass rose.
            g.add(s("polygon", "points" to "$tip ${pt(r * 0.52, -r * 0.2)} $base", "fill" to ink, "stroke" to ink, "stroke-width" to 1, "vector-effect" to "non-scaling-stroke"))
            g.add(s("polygon", "points" to "$tip ${pt(r * 0.52, r * 0.2)} $base", "fill" to "var(--card)", "stroke" to ink, "stroke-width" to 1.5, "vector-effect" to "non-scaling-stroke"))
            val lx = sin(a) * r * 0.34; val ly = -cos(a) * r * 0.34
            g.add(s("text", "x" to lx, "y" to ly, "font-size" to fs * (if (north) 1.35 else 1.1), "font-weight" to "bold", "fill" to ink, "text-anchor" to "middle", "dominant-baseline" to "central",
                "transform" to "rotate(${num(bearing)} ${num(lx)} ${num(ly)})").also { it.textContent = if (north && !set) "N?" else letter })
        }
        svg.add(g)
    }

    /** Barriers for the shade display: obstacles, plus the plants at mature height when switched on (FR-038). */
    fun shadeBarriers(wp: WebPlot): List<Barrier> =
        wp.features.mapNotNull { Barrier.from(it) } + (if (Store.shadePlants) com.example.smartgardenplanner.core.ShadeTools.plantBarriers(wp.plants, { Catalog.get(it) }) else emptyList())

    fun shadeDayOfYear(wp: WebPlot): Int {
        val lat = wp.plot.latitude ?: SunlightEngine.DEFAULT_LATITUDE
        return Store.shadeDay.dayOfYear(lat, SunlightEngine.dayOfYear(PlatformClock.nowMillis()), com.example.smartgardenplanner.core.GrowingSeason.midSeasonDay(lat, Frost.season(wp.plot)))
    }

    private fun drawShade(wp: WebPlot) {
        val p = wp.plot
        val barriers = shadeBarriers(wp)
        val lat = p.latitude ?: SunlightEngine.DEFAULT_LATITUDE
        val day = shadeDayOfYear(wp)
        val cols = 30
        val rows = (cols * p.widthM / p.lengthM).toInt().coerceIn(4, 60)
        val cw = p.lengthM.toDouble() / cols; val ch = p.widthM.toDouble() / rows
        val g = s("g", "class" to "shade")
        val hour = Store.shadeHour
        if (hour != null) {
            // Shade at one moment: dark where the sun doesn't reach right now.
            val shadow = com.example.smartgardenplanner.core.ShadeTools.shadowGridAt(p.lengthM, p.widthM, cols, rows, lat, day, p.northBearingDeg, barriers, hour)
            for (r in 0 until rows) for (c in 0 until cols) if (shadow[r * cols + c]) {
                g.add(s("rect", "x" to c * cw, "y" to r * ch, "width" to cw + 0.01, "height" to ch + 0.01, "fill" to "#312e81", "fill-opacity" to 0.55, "data-shadow" to "1"))
            }
        } else {
            val key = "${p.lengthM},${p.widthM},${p.latitude},${p.northBearingDeg},$day,${barriers.hashCode()}"
            val grid = shadeCache?.takeIf { it.first == key }?.second ?: SunlightEngine.sunHoursGrid(p.lengthM, p.widthM, cols, rows, lat, day, p.northBearingDeg, barriers).also { shadeCache = key to it }
            // Shared SunBand colours: yellow = full sun, blue = part shade, indigo = shade (same as the phone).
            for (r in 0 until rows) for (c in 0 until cols) {
                val band = SunBand.of(grid[r * cols + c])
                g.add(s("rect", "x" to c * cw, "y" to r * ch, "width" to cw + 0.01, "height" to ch + 0.01, "fill" to col(band.overlayArgb),
                    "fill-opacity" to LayoutPalette.alpha(band.overlayArgb), "data-band" to band.name).also { it.add(s("title").also { t -> t.textContent = "${band.label}: about ${grid[r * cols + c].fmt(1)} h of direct sun over the day" }) })
            }
        }
        svg.add(g)
    }

    /** Irrigation coverage (FR-039): wet areas coloured by source; plants out of reach get a red dashed ring. */
    private fun drawWater(wp: WebPlot, fs: Double) {
        val p = wp.plot
        val cols = 40
        val rows = (cols * p.widthM / p.lengthM).toInt().coerceIn(4, 80)
        val cw = p.lengthM.toDouble() / cols; val ch = p.widthM.toDouble() / rows
        val irrigation = wp.features.filter { SiteFeatureType.of(it.featureType)?.isIrrigation == true }
        val grid = com.example.smartgardenplanner.core.Irrigation.grid(p, irrigation, cols, rows)
        val g = s("g", "class" to "water")
        for (i in grid.indices) {
            val src = grid[i]
            if (src == com.example.smartgardenplanner.core.WaterSource.MANUAL) continue
            val c = i % cols; val r = i / cols
            g.add(s("rect", "x" to c * cw, "y" to r * ch, "width" to cw + 0.01, "height" to ch + 0.01, "fill" to col(src.argb), "fill-opacity" to LayoutPalette.alpha(src.argb), "data-water" to src.name))
        }
        svg.add(g)
    }

    private fun drawDryRings(wp: WebPlot, fs: Double) {
        val irrigation = wp.features.filter { SiteFeatureType.of(it.featureType)?.isIrrigation == true }
        if (irrigation.isEmpty()) return
        com.example.smartgardenplanner.core.Irrigation.plants(wp.plot, wp.plants, irrigation, { Catalog.get(it) })
            .filter { it.source == com.example.smartgardenplanner.core.WaterSource.MANUAL }.forEach { pw ->
                val r = (pw.seed?.exclusionRadiusM ?: 0.3f) + fs * 0.25
                svg.add(s("circle", "cx" to pw.node.coordinateXM, "cy" to pw.node.coordinateYM, "r" to r, "fill" to "none", "stroke" to "#dc2626", "stroke-dasharray" to "3 3", "stroke-width" to 2.5, "vector-effect" to "non-scaling-stroke", "class" to "dry-ring"))
            }
    }

        private fun col(argb: Long) = LayoutPalette.hex(argb)

    private fun featureColours(t: SiteFeatureType): Triple<String, Double, String> = when {
        t.isIrrigation -> Triple("#3b82f6", 0.1, "#2563eb")
        t.isArea -> LayoutPalette.area(t).let { Triple(col(it.first), LayoutPalette.alpha(it.first), col(it.second)) }
        t == SiteFeatureType.TREE -> Triple(col(LayoutPalette.TREE_FILL), LayoutPalette.alpha(LayoutPalette.TREE_FILL), col(LayoutPalette.TREE_EDGE))
        t == SiteFeatureType.FENCE -> Triple("none", 0.0, col(LayoutPalette.FENCE))
        t == SiteFeatureType.WALL -> Triple("none", 0.0, col(LayoutPalette.WALL))
        else -> Triple(col(LayoutPalette.BUILDING_FILL), LayoutPalette.alpha(LayoutPalette.BUILDING_FILL), col(LayoutPalette.BUILDING_EDGE))
    }

    private fun drawFeature(f: SiteFeatureEntity, fs: Double) {
        val t = SiteFeatureType.of(f.featureType) ?: return
        val pts = PlotGeometry.parsePoints(f.pointsJson)
        if (pts.isEmpty()) return
        val sel = (selection as? Selection.Feature)?.id == f.id
        val (fill, fillAlpha, edge) = featureColours(t)
        val ink = col(LayoutPalette.INK)
        val stroke = if (sel) "#f97316" else edge
        val g = s("g", "data-feature" to f.id)
                when {
            t == SiteFeatureType.SPRINKLER -> {
                val c = pts[0]
                val r = if (f.radiusM > 0f) f.radiusM else com.example.smartgardenplanner.core.Irrigation.DEFAULT_THROW_M
                val arc = if (f.slopeGradePct <= 0f || f.slopeGradePct >= 360f) 360f else f.slopeGradePct
                if (arc >= 360f) g.add(s("circle", "cx" to c.x, "cy" to c.y, "r" to r, "fill" to "none", "stroke" to "#2563eb", "stroke-dasharray" to "6 4", "stroke-width" to if (sel) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
                else {
                    // Arc: compass bearings turned into plot directions.
                    val n = Store.plot()?.plot?.northBearingDeg ?: 0f
                    fun at(bearing: Double): PlotPoint { val (dx, dy) = SunlightEngine.sunDirectionInPlot(bearing, n); return PlotPoint((c.x + dx * r).toFloat(), (c.y + dy * r).toFloat()) }
                    val a0 = f.slopeDirectionDeg - arc / 2.0; val a1 = f.slopeDirectionDeg + arc / 2.0
                    val steps = (arc / 10f).toInt().coerceAtLeast(2)
                    val pts2 = (0..steps).map { at(a0 + (a1 - a0) * it / steps) }
                    g.add(s("polygon", "points" to (listOf(c) + pts2).joinToString(" ") { "${it.x},${it.y}" }, "fill" to "#3b82f6", "fill-opacity" to 0.08, "stroke" to "#2563eb", "stroke-dasharray" to "6 4", "stroke-width" to if (sel) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
                }
                g.add(s("circle", "cx" to c.x, "cy" to c.y, "r" to fs * 0.35, "fill" to "#2563eb", "stroke" to stroke, "stroke-width" to if (sel) 3 else 1, "vector-effect" to "non-scaling-stroke"))
                g.add(s("text", "x" to c.x, "y" to c.y - fs * 0.5, "font-size" to fs * 0.6, "fill" to "#1e3a8a", "text-anchor" to "middle").also { it.textContent = "💧 ${r.fmt(1)} m" })
            }
            t == SiteFeatureType.DRIP_LINE -> {
                g.add(s("polyline", "points" to pts.joinToString(" ") { "${it.x},${it.y}" }, "fill" to "none", "stroke" to if (sel) "#f97316" else "#0369a1", "stroke-width" to 3, "stroke-dasharray" to "1 6", "stroke-linecap" to "round", "vector-effect" to "non-scaling-stroke"))
                g.add(s("polyline", "points" to pts.joinToString(" ") { "${it.x},${it.y}" }, "fill" to "none", "stroke" to "#0369a1", "stroke-opacity" to 0.5, "stroke-width" to 1, "vector-effect" to "non-scaling-stroke"))
            }
            t == SiteFeatureType.HOSE_BIB -> {
                val c = pts[0]
                val len = if (f.radiusM > 0f) f.radiusM else com.example.smartgardenplanner.core.Irrigation.DEFAULT_HOSE_M
                g.add(s("circle", "cx" to c.x, "cy" to c.y, "r" to len, "fill" to "none", "stroke" to "#6366f1", "stroke-dasharray" to "2 6", "stroke-width" to 1, "vector-effect" to "non-scaling-stroke"))
                g.add(s("rect", "x" to c.x - fs * 0.3, "y" to c.y - fs * 0.3, "width" to fs * 0.6, "height" to fs * 0.6, "fill" to "#6366f1", "stroke" to stroke, "stroke-width" to if (sel) 3 else 1, "vector-effect" to "non-scaling-stroke"))
                g.add(s("text", "x" to c.x, "y" to c.y - fs * 0.5, "font-size" to fs * 0.6, "fill" to "#3730a3", "text-anchor" to "middle").also { it.textContent = "Tap · ${len.fmt(0)} m hose" })
            }
            t.isArea && pts.size >= 3 -> {
                g.add(s("polygon", "points" to pts.joinToString(" ") { "${it.x},${it.y}" }, "fill" to fill, "fill-opacity" to fillAlpha, "stroke" to stroke, "stroke-width" to if (sel) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
                val cx = pts.map { it.x }.average(); val cy = pts.map { it.y }.average()
                g.add(s("text", "x" to cx, "y" to cy, "font-size" to fs * 0.8, "fill" to ink, "text-anchor" to "middle", "font-weight" to "600").also { it.textContent = f.label.ifBlank { t.label } })
                if (t == SiteFeatureType.SLOPE) {
                    val a = (f.slopeDirectionDeg - (Store.plot()?.plot?.northBearingDeg ?: 0f)) * kotlin.math.PI / 180.0
                    g.add(s("line", "x1" to cx, "y1" to cy + fs, "x2" to cx + sin(a) * fs * 2, "y2" to cy + fs - cos(a) * fs * 2, "stroke" to edge, "stroke-width" to 3, "vector-effect" to "non-scaling-stroke"))
                }
            }
            t == SiteFeatureType.TREE -> {
                val r = max(f.radiusM, 0.2f)
                g.add(s("circle", "cx" to pts[0].x, "cy" to pts[0].y, "r" to r, "fill" to fill, "fill-opacity" to if (showShade) 0.0 else fillAlpha, "stroke" to stroke, "stroke-width" to if (sel) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
                g.add(s("circle", "cx" to pts[0].x, "cy" to pts[0].y, "r" to r * 0.12, "fill" to col(LayoutPalette.TRUNK)))
            }
            else -> {
                val closed = t == SiteFeatureType.BUILDING && pts.size >= 3
                g.add(s(if (closed) "polygon" else "polyline", "points" to pts.joinToString(" ") { "${it.x},${it.y}" }, "fill" to if (closed) fill else "none", "fill-opacity" to fillAlpha, "stroke" to stroke, "stroke-width" to if (sel) 8 else 6, "stroke-linecap" to "round", "vector-effect" to "non-scaling-stroke"))
            }
        }
        if (t.isBarrier) g.add(s("text", "x" to pts[0].x, "y" to pts[0].y - fs * 0.3 - (if (t == SiteFeatureType.TREE) max(f.radiusM, 0.2f) * 0.12f else 0f), "font-size" to fs * 0.7, "fill" to ink, "text-anchor" to "middle", "font-weight" to "600").also { it.textContent = "${f.heightM.fmt(1)} m" })
        svg.add(g)
    }

    private fun drawPath(z: PathZoneEntity) {
        val sel = (selection as? Selection.Path)?.id == z.id
        if (z.pathType == "POLYLINE") {
            val pts = PlotGeometry.parsePoints(z.pointsJson)
            svg.add(s("polyline", "points" to pts.joinToString(" ") { "${it.x},${it.y}" }, "fill" to "none", "stroke" to if (sel) "#f97316" else col(LayoutPalette.PATH_FILL), "stroke-opacity" to LayoutPalette.alpha(LayoutPalette.PATH_FILL), "stroke-width" to z.widthM, "stroke-linecap" to "round"))
        } else {
            svg.add(s("rect", "x" to z.xM, "y" to z.yM, "width" to z.widthM, "height" to z.heightM, "fill" to col(LayoutPalette.PATH_FILL), "fill-opacity" to LayoutPalette.alpha(LayoutPalette.PATH_FILL), "stroke" to if (sel) "#f97316" else col(LayoutPalette.PATH_EDGE), "stroke-width" to if (sel) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
        }
    }

    /** Short label under a plant: the variety's everyday kind ("Bell red", "Cherry red", "Spring") or its species. */
    fun shortLabel(seed: SeedEntity?): String = seed?.let { VarietyCatalogTraits.of(it)?.tag ?: CropReference.speciesName(it) } ?: "?"

    private fun drawPlant(n: PlantedNodeEntity, selected: Boolean, fs: Double) {
        val seed = Catalog.get(n.seedCode)
        val c = Colors.of(seed)
        val r = seed?.exclusionRadiusM ?: 0.3f
        val dot = seed?.let { VarietyCatalogTraits.dotArgb(it) }?.let { LayoutPalette.hex(it) } ?: c
                val f = find
        val found = f != null && f(n)
        val g = s("g", "class" to if (found) "plant found" else "plant", "data-code" to n.seedCode, "opacity" to if (f != null && !found) 0.25 else 1.0)
        g.add(s("title").also { it.textContent = seed?.let { sd -> VarietyCatalogTraits.displayName(sd) } ?: n.seedCode })
        g.add(s("circle", "cx" to n.coordinateXM, "cy" to n.coordinateYM, "r" to r, "fill" to c, "fill-opacity" to 0.16, "stroke" to if (selected) "#f97316" else c, "stroke-width" to if (selected) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
        g.add(s("circle", "cx" to n.coordinateXM, "cy" to n.coordinateYM, "r" to max(min(r * 0.3f, 0.12f), 0.05f), "fill" to dot, "stroke" to col(LayoutPalette.INK), "stroke-width" to 1, "vector-effect" to "non-scaling-stroke"))
                if (found) g.add(s("circle", "cx" to n.coordinateXM, "cy" to n.coordinateYM, "r" to r + fs * 0.35, "fill" to "none", "stroke" to "#f97316", "stroke-width" to 4, "vector-effect" to "non-scaling-stroke", "class" to "find-ring"))
        val label = shortLabel(seed)
        // Names shrink to fit tight spacing (e.g. corn at 40 cm) so neighbours' names don't overlap.
        val labelSize = min(fs * 0.5, r * 2.0 * 1.7 / max(4, label.length))
        if (Prefs.showLabels || found) g.add(s("text", "x" to n.coordinateXM, "y" to n.coordinateYM + max(r * 0.3f, 0.05f) + labelSize * 1.2, "font-size" to labelSize, "fill" to col(LayoutPalette.INK), "text-anchor" to "middle", "class" to "plant-label",
            "stroke" to col(LayoutPalette.PAPER), "stroke-width" to labelSize * 0.24, "paint-order" to "stroke", "stroke-linejoin" to "round").also { it.textContent = label })
        svg.add(g)
    }

    /** A past season (FR-033): faded dashed circles with a label, under this season's plants. */
    private fun drawHistory(wp: WebPlot, year: Int, fs: Double) {
        val g = s("g", "class" to "history", "opacity" to 0.75)
        wp.history.filter { it.seasonYear == year }.forEach { h ->
            g.add(s("circle", "cx" to h.coordinateXM, "cy" to h.coordinateYM, "r" to h.radiusM, "fill" to "none", "stroke" to "#57534e", "stroke-dasharray" to "3 3", "stroke-width" to 1.2, "vector-effect" to "non-scaling-stroke"))
            if (Prefs.showLabels) g.add(s("text", "x" to h.coordinateXM, "y" to h.coordinateYM + fs * 0.2, "font-size" to fs * 0.45, "fill" to "#57534e", "text-anchor" to "middle", "font-style" to "italic").also { it.textContent = h.speciesName })
        }
        svg.add(g)
    }

    /** FR-046: the satellite photo, drawn over the paper so the grid and everything else stays on top. */
    private fun drawPhoto(wp: WebPlot) {
        val img = wp.backdropImage ?: return
        var b = Photo.backdrop(wp) ?: return
        if (!b.visible) return
        val a = dragStart; val n = dragNow
        if (tool == Tool.PHOTO && !Photo.calibrating && a != null && n != null) b = b.moved(n.x - a.x, n.y - a.y)
        val el = s("image", "x" to b.xM, "y" to b.yM, "width" to b.widthM, "height" to b.heightM, "preserveAspectRatio" to "none",
            "opacity" to b.opacity, "transform" to "rotate(${b.rotationDeg} ${b.centreX} ${b.centreY})", "class" to "backdrop-photo")
        el.setAttribute("href", img)
        svg.add(el)
    }

    private fun drawInProgress() {
        if (points.isNotEmpty()) {
            svg.add(s("polyline", "points" to points.joinToString(" ") { "${it.x},${it.y}" }, "fill" to "none", "stroke" to "#10b981", "stroke-width" to 3, "vector-effect" to "non-scaling-stroke"))
            points.forEach { svg.add(s("circle", "cx" to it.x, "cy" to it.y, "r" to 0.06, "fill" to "#10b981")) }
        }
        val a = dragStart; val b = dragNow
        if (a != null && b != null && (tool == Tool.PATH || tool == Tool.PLAN)) {
            svg.add(s("rect", "x" to min(a.x, b.x), "y" to min(a.y, b.y), "width" to abs(b.x - a.x), "height" to abs(b.y - a.y), "fill" to "none", "stroke" to if (tool == Tool.PLAN) "#10b981" else "#64748b", "stroke-width" to 2, "stroke-dasharray" to "6 4", "vector-effect" to "non-scaling-stroke"))
        }
    }

    // ----------------------------------------------------------------- interaction

    private fun hitPlant(wp: WebPlot, pt: PlotPoint): PlantedNodeEntity? = wp.plants.minByOrNull { dist(it.coordinateXM, it.coordinateYM, pt) }
        ?.takeIf { n -> dist(n.coordinateXM, n.coordinateYM, pt) <= max(0.15f, (Catalog.get(n.seedCode)?.exclusionRadiusM ?: 0.3f)) }

    private fun hitFeature(wp: WebPlot, pt: PlotPoint): SiteFeatureEntity? = wp.features.reversed().firstOrNull { f ->
        val t = SiteFeatureType.of(f.featureType) ?: return@firstOrNull false
        val pts = PlotGeometry.parsePoints(f.pointsJson)
        when {
            t.isArea -> PlotGeometry.pointInPolygon(pt.x, pt.y, pts)
                        t == SiteFeatureType.TREE -> PlotGeometry.distanceToPolyline(pt.x, pt.y, pts) <= max(f.radiusM, 0.3f)
            t == SiteFeatureType.SPRINKLER || t == SiteFeatureType.HOSE_BIB -> PlotGeometry.distanceToPolyline(pt.x, pt.y, pts) <= max(0.3f, handleR().toFloat() * 1.5f)
            else -> PlotGeometry.distanceToPolyline(pt.x, pt.y, pts) <= 0.3f
        }
    }

    private fun hitPath(wp: WebPlot, pt: PlotPoint): PathZoneEntity? = wp.paths.firstOrNull { z ->
        if (z.pathType == "POLYLINE") PlotGeometry.distanceToPolyline(pt.x, pt.y, PlotGeometry.parsePoints(z.pointsJson)) <= z.widthM / 2 + 0.1f
        else pt.x in z.xM..(z.xM + z.widthM) && pt.y in z.yM..(z.yM + z.heightM)
    }

            private fun handleR(): Double = vb[2] / 90.0

    /** With shade on, pointing at a spot tells when it gets sun (FR-038); with water on, how it is watered. */
    private fun hover(pt: PlotPoint) {
        val wp = Store.plot() ?: return
        if (!showShade && !Store.showWater) return
        if (!PlotShape.contains(wp.plot, pt.x, pt.y)) return
        val last = hoverAt
        if (last != null && dist(last.x, last.y, pt) < 0.15f) return
        hoverAt = pt
        val parts = mutableListOf<String>()
        if (showShade) parts += com.example.smartgardenplanner.core.ShadeTools.describeWindows(
            com.example.smartgardenplanner.core.ShadeTools.sunWindows(pt.x, pt.y, wp.plot.latitude ?: SunlightEngine.DEFAULT_LATITUDE, shadeDayOfYear(wp), wp.plot.northBearingDeg, shadeBarriers(wp))
        ) + " (solar time, ${Store.shadeDay.label.lowercase()})"
        if (Store.showWater) parts += "Water: " + com.example.smartgardenplanner.core.Irrigation.sourceAt(pt.x, pt.y, wp.features.filter { SiteFeatureType.of(it.featureType)?.isIrrigation == true }, wp.plot.northBearingDeg).label
        App.status("At ${pt.x.fmt(1)}, ${pt.y.fmt(1)} m: " + parts.joinToString(" · "))
    }

    private fun dist(x: Float, y: Float, p: PlotPoint): Float { val dx = x - p.x; val dy = y - p.y; return sqrt(dx * dx + dy * dy) }

    private fun onWheel(e: WheelEvent) {
        e.preventDefault()
        val pt = toMetres(e)
        zoom(if (e.deltaY > 0) 1.15 else 1 / 1.15, pt.x.toDouble(), pt.y.toDouble())
    }

        private fun onDown(e: MouseEvent) {
        val wp = Store.plot() ?: return
        val pt = toMetres(e)
        if (Store.viewSeason != null) {
            // Read-only past season: only panning.
            selection = null; dragStart = pt; dragNow = pt; dragMoved = false
            panOrigin = doubleArrayOf(e.clientX.toDouble(), e.clientY.toDouble(), vb[0], vb[1])
            svg.asDynamic().setPointerCapture(e.asDynamic().pointerId)
            return
        }
        svg.asDynamic().setPointerCapture(e.asDynamic().pointerId)
        dragMoved = false
        when (tool) {
            Tool.SELECT -> {
                val plant = hitPlant(wp, pt)
                val path = if (plant == null) hitPath(wp, pt) else null
                val feature = if (plant == null && path == null) hitFeature(wp, pt) else null
                // FR-061: Shift+drag on empty ground draws a box that selects the plants inside it.
                if (plant == null && (e.asDynamic().shiftKey as? Boolean) == true) {
                    bandStart = pt; dragStart = pt; dragNow = pt; selection = null; App.render(); return
                }
                val group = selection as? Selection.Group
                selection = when {
                    plant != null && group != null && plant.id in group.ids -> group
                    plant != null -> Selection.Plant(plant.id)
                    path != null -> Selection.Path(path.id)
                    feature != null -> Selection.Feature(feature.id)
                    else -> null
                }
                dragStart = pt; dragNow = pt
                if (selection == null) panOrigin = doubleArrayOf(e.clientX.toDouble(), e.clientY.toDouble(), vb[0], vb[1])
                App.render()
            }
                        Tool.PATH, Tool.PLAN -> { dragStart = pt; dragNow = pt }
            Tool.PHOTO -> if (!Photo.calibrating) { dragStart = pt; dragNow = pt }
            Tool.OUTLINE -> if (points.isEmpty()) {
                val outline = PlotShape.outline(wp.plot)
                val i = outline.indices.minByOrNull { dist(outline[it].x, outline[it].y, pt) }
                if (i != null && dist(outline[i].x, outline[i].y, pt) <= handleR() * 2.2) { dragVertex = i; dragStart = pt; dragNow = pt }
            }
            else -> {}
        }
    }

        private fun onMove(e: MouseEvent) {
        val start = dragStart
        val pt = toMetres(e)
        if (start == null) { hover(pt); return }
        dragNow = pt
        if (dist(start.x, start.y, pt) > 0.02f) dragMoved = true
                if (bandStart != null) {
            render()
            val a = bandStart!!
            svg.add(s("rect", "x" to min(a.x, pt.x), "y" to min(a.y, pt.y), "width" to abs(pt.x - a.x), "height" to abs(pt.y - a.y), "fill" to "#f97316", "fill-opacity" to 0.08, "stroke" to "#f97316", "stroke-dasharray" to "6 4", "stroke-width" to 2, "vector-effect" to "non-scaling-stroke", "class" to "band"))
            return
        }
        if ((tool == Tool.SELECT && selection == null) || Store.viewSeason != null) {
            val o = panOrigin ?: return
            val scale = vb[2] / (svg.asDynamic().clientWidth as Double).coerceAtLeast(1.0)
            vb[0] = o[2] - (e.clientX - o[0]) * scale
            vb[1] = o[3] - (e.clientY - o[1]) * scale
            render(); return
        }
                if (tool == Tool.SELECT && dragMoved) renderMovePreview(start, pt) else if (tool == Tool.PATH || tool == Tool.PLAN || tool == Tool.PHOTO) render()
        else if (tool == Tool.OUTLINE && dragVertex != null) {
            render()
            val wp = Store.plot() ?: return
            val o = PlotShape.outline(wp.plot).toMutableList()
            o[dragVertex!!] = clampToPlot(wp, pt)
            svg.add(s("polygon", "points" to o.joinToString(" ") { "${it.x},${it.y}" }, "fill" to "none", "stroke" to "#f97316", "stroke-dasharray" to "6 4", "stroke-width" to 2, "vector-effect" to "non-scaling-stroke"))
        }
    }

    private fun renderMovePreview(start: PlotPoint, now: PlotPoint) {
        render()
        val wp = Store.plot() ?: return
        val dx = now.x - start.x; val dy = now.y - start.y
        when (val sel = selection) {
            is Selection.Plant -> wp.plants.firstOrNull { it.id == sel.id }?.let { n ->
                svg.add(s("circle", "cx" to n.coordinateXM + dx, "cy" to n.coordinateYM + dy, "r" to (Catalog.get(n.seedCode)?.exclusionRadiusM ?: 0.3f), "fill" to "none", "stroke" to "#f97316", "stroke-dasharray" to "4 3", "stroke-width" to 2, "vector-effect" to "non-scaling-stroke"))
            }
            is Selection.Group -> wp.plants.filter { it.id in sel.ids }.forEach { n ->
                svg.add(s("circle", "cx" to n.coordinateXM + dx, "cy" to n.coordinateYM + dy, "r" to (Catalog.get(n.seedCode)?.exclusionRadiusM ?: 0.3f), "fill" to "none", "stroke" to "#f97316", "stroke-dasharray" to "4 3", "stroke-width" to 2, "vector-effect" to "non-scaling-stroke"))
            }
            is Selection.Feature -> wp.features.firstOrNull { it.id == sel.id }?.let { f ->
                val pts = PlotGeometry.parsePoints(f.pointsJson).map { PlotPoint(it.x + dx, it.y + dy) }
                svg.add(s("polyline", "points" to (pts + pts.take(1)).joinToString(" ") { "${it.x},${it.y}" }, "fill" to "none", "stroke" to "#f97316", "stroke-dasharray" to "4 3", "stroke-width" to 2, "vector-effect" to "non-scaling-stroke"))
            }
            else -> {}
        }
    }

        private fun onUp(e: MouseEvent) {
        val wp = Store.plot()
        val start = dragStart
        val pt = toMetres(e)
        dragStart = null; dragNow = null; panOrigin = null
        if (wp == null) return
        Store.viewSeason?.let { y -> if (!dragMoved) App.status("You are looking at season $y (read only). Choose the planning season on the Plot tab to make changes."); return }
        val band = bandStart
        if (band != null) {
            bandStart = null
            val picked = com.example.smartgardenplanner.core.GroupTools.inRect(wp.plants, band.x, band.y, pt.x, pt.y)
            selection = if (picked.isEmpty()) null else Selection.Group(picked.map { it.id }.toSet())
            App.status(if (picked.isEmpty()) "No plants in that box." else "${picked.size} plants selected. Drag one of them to move them all, or Rearrange group… to change the rows.")
            App.render(); return
        }
        when (tool) {
            Tool.SELECT -> if (dragMoved && start != null) moveSelection(wp, pt.x - start.x, pt.y - start.y) else App.render()
            Tool.PLANT -> place(wp, pt)
            Tool.PATH -> if (start != null) {
                val x = min(start.x, pt.x); val y = min(start.y, pt.y); val w = abs(pt.x - start.x); val h = abs(pt.y - start.y)
                if (w > 0.05f && h > 0.05f) Store.change { it.paths = it.paths + PathZoneEntity(id = Store.newId(), plotId = it.plot.id, xM = x, yM = y, widthM = w, heightM = h) }
                App.render()
            }
            Tool.PLAN -> if (start != null) {
                val x0 = min(start.x, pt.x); val y0 = min(start.y, pt.y); val x1 = max(start.x, pt.x); val y1 = max(start.y, pt.y)
                if (x1 - x0 > 0.2f && y1 - y0 > 0.2f) Dialogs.planForMe(listOf(PlotPoint(x0, y0), PlotPoint(x1, y0), PlotPoint(x1, y1), PlotPoint(x0, y1)))
                App.render()
            }
                        Tool.WATER -> if (waterType == SiteFeatureType.DRIP_LINE) { points += pt; App.render() }
                else Dialogs.feature(SiteFeatureEntity(plotId = wp.plot.id, featureType = waterType.name, pointsJson = PlotGeometry.serializePoints(listOf(pt)),
                    radiusM = if (waterType == SiteFeatureType.SPRINKLER) com.example.smartgardenplanner.core.Irrigation.DEFAULT_THROW_M else com.example.smartgardenplanner.core.Irrigation.DEFAULT_HOSE_M,
                    slopeGradePct = if (waterType == SiteFeatureType.SPRINKLER) 360f else 0f), isNew = true)
            Tool.TREE -> Dialogs.feature(SiteFeatureEntity(plotId = wp.plot.id, featureType = SiteFeatureType.TREE.name, pointsJson = PlotGeometry.serializePoints(listOf(pt)), heightM = 6f, radiusM = 2f), isNew = true)
                        Tool.OUTLINE -> {
                val v = dragVertex
                dragVertex = null
                if (v != null) {
                    if (dragMoved) moveOutlineCorner(wp, v, clampToPlot(wp, pt)) else App.render()
                } else { points += pt; App.render() }
            }
            Tool.LINE_OBSTACLE, Tool.AREA -> { points += pt; App.render() }
            Tool.PHOTO -> when {
                Photo.backdrop(wp) == null -> { Panels.tab = Tab.PLOT; App.status("Add a satellite photo first: Plot tab → Satellite photo → Add photo…"); App.render() }
                Photo.calibrating -> {
                    points += pt
                    if (points.size >= 2) Photo.askDistance(wp, points[0], points[1])
                    else App.status("Now click the second point.")
                    App.render()
                }
                dragMoved && start != null -> {
                    val b = Photo.backdrop(wp)!!
                    Store.change { it.plot = it.plot.copy(backdropJson = b.moved(pt.x - start.x, pt.y - start.y).encode()) }
                    App.render()
                }
                else -> App.render()
            }
        }
    }

    private fun clampToPlot(wp: WebPlot, p: PlotPoint) = PlotPoint(p.x.coerceIn(0f, wp.plot.lengthM), p.y.coerceIn(0f, wp.plot.widthM))

    /** Moves one outline corner (one undo step), if the outline stays valid. */
    private fun moveOutlineCorner(wp: WebPlot, index: Int, to: PlotPoint) {
        val o = PlotShape.outline(wp.plot).toMutableList()
        o[index] = to
        PlotGeometry.validateOutline(o)?.let { App.status("Can't move the corner there: $it"); App.render(); return }
        Store.change { it.plot = it.plot.copy(boundaryJson = PlotGeometry.serializePoints(o)) }
        val outside = wp.plants.count { !PlotShape.contains(wp.plot, it.coordinateXM, it.coordinateYM) }
        App.status(if (outside > 0) "Corner moved. $outside plant(s) are now outside the outline." else "Corner moved. Undo puts it back.")
        App.render()
    }

    /** Double-click near an outline edge adds a corner there. */
    fun addOutlineCorner(pt: PlotPoint) {
        val wp = Store.plot() ?: return
        val o = PlotShape.outline(wp.plot)
        if (o.size < 3) return
        val i = o.indices.minBy { PlotGeometry.distanceToSegment(pt.x, pt.y, o[it], o[(it + 1) % o.size]) }
        if (PlotGeometry.distanceToSegment(pt.x, pt.y, o[i], o[(i + 1) % o.size]) > handleR() * 3) return
        val n = o.toMutableList().also { it.add(i + 1, clampToPlot(wp, pt)) }
        Store.change { it.plot = it.plot.copy(boundaryJson = PlotGeometry.serializePoints(n)) }
        App.status("Corner added. Drag it where you want it.")
        App.render()
    }

    fun deleteOutline() {
        if (Store.plot()?.plot?.boundaryJson.isNullOrBlank()) { App.status("This plot has no outline; it is the full rectangle."); return }
        Store.change { it.plot = it.plot.copy(boundaryJson = null) }
        points.clear()
        App.status("Outline deleted: the plot is the full rectangle again. Undo brings it back.")
        App.render()
    }

        fun finishPoints() {
        val wp = Store.plot() ?: return
        when (tool) {
            Tool.WATER -> if (waterType == SiteFeatureType.DRIP_LINE && dedupe(points).size >= 2) {
                val line = dedupe(points)
                points.clear()
                // One line per Finish: back to Select / move, so clicks stop adding points (FR-053).
                tool = Tool.SELECT
                Dialogs.feature(SiteFeatureEntity(plotId = wp.plot.id, featureType = SiteFeatureType.DRIP_LINE.name, pointsJson = PlotGeometry.serializePoints(line), radiusM = com.example.smartgardenplanner.core.Irrigation.DEFAULT_DRIP_HALF_WIDTH_M), isNew = true)
            } else if (waterType == SiteFeatureType.DRIP_LINE) App.status("Click at least 2 points along the drip line.")
            Tool.OUTLINE -> {
                val problem = PlotGeometry.validateOutline(points)
                if (problem != null) { App.status(problem); return }
                val outline = points.toList()
                Store.change { it.plot = it.plot.copy(boundaryJson = PlotGeometry.serializePoints(outline)) }
                val outside = wp.plants.count { !PlotShape.contains(wp.plot, it.coordinateXM, it.coordinateYM) }
                App.status(if (outside > 0) "Outline saved. $outside plant(s) are now outside it." else "Outline saved.")
                points.clear(); tool = Tool.SELECT
            }
            Tool.LINE_OBSTACLE -> if (points.size >= 2) {
                Dialogs.feature(SiteFeatureEntity(plotId = wp.plot.id, featureType = obstacleType.name, pointsJson = PlotGeometry.serializePoints(points), heightM = if (obstacleType == SiteFeatureType.BUILDING) 5f else 1.8f), isNew = true)
                points.clear()
            } else App.status("Click at least 2 points.")
            Tool.AREA -> if (points.size >= 3) {
                val f = SiteFeatureEntity(plotId = wp.plot.id, featureType = areaType.name, pointsJson = PlotGeometry.serializePoints(points))
                if (areaType == SiteFeatureType.SLOPE || areaType == SiteFeatureType.FLOOD) Dialogs.feature(f, isNew = true)
                else Store.change { it.features = it.features + f.copy(id = Store.newId()) }
                points.clear()
            } else App.status("Click at least 3 corners.")
            else -> {}
        }
        App.render()
    }

    /** Drops points that repeat the previous one (a double-click adds the same point twice). */
    private fun dedupe(pts: List<PlotPoint>): List<PlotPoint> = pts.fold(mutableListOf()) { acc, p -> if (acc.isEmpty() || dist(acc.last().x, acc.last().y, p) > 0.05f) acc += p; acc }

    fun cancelPoints() { points.clear(); Store.preview = null; Store.previewArea = null; App.render() }

    /** Places the active variety at [pt] if it passes the same rules as the phone app. */
    fun place(wp: WebPlot, pt: PlotPoint) {
        val seed = activeSeed ?: run { App.status("Choose a variety first (Plants tab)."); return }
        val p = wp.plot
        if (!PlotShape.contains(p, pt.x, pt.y)) { App.status("That spot is outside the plot."); return }
        if (HardinessZones.blocksPlacement(seed, p.hardinessZone)) { App.status(HardinessZones.describe(seed, p.hardinessZone) ?: "Not hardy in this zone."); return }
        if (blockedByPath(wp, pt.x, pt.y, seed.exclusionRadiusM)) { App.status("That spot overlaps a no-plant path."); return }
        val node = PlantedNodeEntity(id = 0, plotId = p.id, seedCode = seed.botanicalCode, coordinateXM = pt.x, coordinateYM = pt.y, datePlantedEpochMillis = PlatformClock.nowMillis())
        val result = CompanionPlantingValidator().validatePlacement(node, seed, wp.plants, { Catalog.get(it) }, Prefs.margin, Prefs.enforceCompanions, Store.guildsActive())
        if (result.unknownVarietyViolations.isNotEmpty()) {
            val n = wp.plants.first { it.id == result.unknownVarietyViolations.first() }
            Dialogs.unknownVariety(n.id, n.seedCode); return
        }
        if (!result.isValid) {
            val conflict = wp.plants.firstOrNull { it.id in (result.spacingViolations + result.antagonistViolations) }
            val other = conflict?.let { Catalog.get(it.seedCode) }
            App.status(if (conflict != null && conflict.id in result.antagonistViolations) "${CropReference.speciesName(seed)} doesn't grow well near ${other?.let { CropReference.speciesName(it) }}." else "Too close to ${other?.commonName ?: "another plant"}.")
            return
        }
        Store.change { it.plants = it.plants + node.copy(id = Store.newId()) }
        val hit = CropRotation.conflict(pt.x, pt.y, seed, wp.history, wp.season())
        App.status(if (hit != null) "Planted ${seed.commonName}. Rotation note: " + CropRotation.warning(hit, CropReference.speciesName(seed)) else "Planted ${VarietyCatalogTraits.displayName(seed)}.")
        App.render()
    }

    fun blockedByPath(wp: WebPlot, x: Float, y: Float, r: Float): Boolean = wp.paths.any { z ->
        if (z.pathType == "POLYLINE") PlotGeometry.distanceToPolyline(x, y, PlotGeometry.parsePoints(z.pointsJson)) < z.widthM / 2 + r
        else {
            val cx = x.coerceIn(z.xM, z.xM + z.widthM); val cy = y.coerceIn(z.yM, z.yM + z.heightM)
            (x - cx) * (x - cx) + (y - cy) * (y - cy) < r * r
        }
    }

    private fun moveSelection(wp: WebPlot, dx: Float, dy: Float) {
        val p = wp.plot
        when (val sel = selection) {
            is Selection.Plant -> {
                val n = wp.plants.firstOrNull { it.id == sel.id } ?: return
                val seed = Catalog.get(n.seedCode) ?: return
                val x = n.coordinateXM + dx; val y = n.coordinateYM + dy
                val moved = n.copy(coordinateXM = x, coordinateYM = y)
                val others = wp.plants.filter { it.id != n.id }
                when {
                    !PlotShape.contains(p, x, y) -> App.status("Can't move there: outside the plot.")
                    blockedByPath(wp, x, y, seed.exclusionRadiusM) -> App.status("Can't move there: it overlaps a no-plant path.")
                    else -> {
                        val r = CompanionPlantingValidator().validatePlacement(moved, seed, others, { Catalog.get(it) }, Prefs.margin, Prefs.enforceCompanions, Store.guildsActive())
                        when {
                            r.unknownVarietyViolations.isNotEmpty() -> others.first { it.id == r.unknownVarietyViolations.first() }.let { Dialogs.unknownVariety(it.id, it.seedCode) }
                            !r.isValid -> App.status("Can't move there: too close to another plant or a plant it dislikes.")
                            else -> {
                                Store.change { it.plants = others + moved }
                                val hit = CropRotation.conflict(x, y, seed, wp.history, wp.season())
                                App.status(if (hit != null) "Moved. Rotation note: " + CropRotation.warning(hit, CropReference.speciesName(seed)) else "Moved.")
                            }
                        }
                    }
                }
            }
            is Selection.Feature -> {
                val f = wp.features.firstOrNull { it.id == sel.id } ?: return
                val pts = PlotGeometry.parsePoints(f.pointsJson)
                val cdx = dx.coerceIn(-pts.minOf { it.x }, p.lengthM - pts.maxOf { it.x })
                val cdy = dy.coerceIn(-pts.minOf { it.y }, p.widthM - pts.maxOf { it.y })
                val moved = f.copy(pointsJson = PlotGeometry.serializePoints(pts.map { PlotPoint(it.x + cdx, it.y + cdy) }))
                Store.change { it.features = it.features.map { x -> if (x.id == f.id) moved else x } }
                App.status("Moved.")
            }
            is Selection.Path -> {
                val z = wp.paths.firstOrNull { it.id == sel.id } ?: return
                val moved = if (z.pathType == "POLYLINE") z.copy(pointsJson = PlotGeometry.serializePoints(PlotGeometry.parsePoints(z.pointsJson).map { PlotPoint(it.x + dx, it.y + dy) }))
                else z.copy(xM = (z.xM + dx).coerceIn(0f, p.lengthM - z.widthM), yM = (z.yM + dy).coerceIn(0f, p.widthM - z.heightM))
                Store.change { it.paths = it.paths.map { x -> if (x.id == z.id) moved else x } }
            }
            is Selection.Group -> {
                val group = wp.plants.filter { it.id in sel.ids }
                val moved = com.example.smartgardenplanner.core.GroupTools.moved(group, dx, dy)
                val others = wp.plants.filter { it.id !in sel.ids }
                val bad = com.example.smartgardenplanner.core.GroupTools.problems(moved, others, p, { Catalog.get(it) }, Prefs.margin, Prefs.enforceCompanions, Store.guildsActive()) +
                    moved.count { n -> blockedByPath(wp, n.coordinateXM, n.coordinateYM, Catalog.get(n.seedCode)?.exclusionRadiusM ?: 0.3f) }
                if (bad > 0) App.status("Can't move the group there: $bad plant(s) would be outside the plot, on a path, or too close to other plants.")
                else { Store.change { it.plants = others + moved }; App.status("Moved ${moved.size} plants. Future rotation plans start from where they are now.") }
            }
            null -> {}
        }
        App.render()
    }

    /** FR-061: select every plant of the selected plant's clump. */
    fun selectGroupOf(wp: WebPlot, id: Long) {
        val n = wp.plants.firstOrNull { it.id == id } ?: return
        val g = com.example.smartgardenplanner.core.GroupTools.groupOf(wp.plants, n) { Catalog.get(it) }
        selection = Selection.Group(g.map { it.id }.toSet())
        App.status("${g.size} plants selected. Drag one of them to move the whole group, or Rearrange group… to change its rows.")
        App.render()
    }

    fun deleteSelection() {
        val sel = selection ?: return
        Store.change { wp ->
            when (sel) {
                is Selection.Plant -> wp.plants = wp.plants.filter { it.id != sel.id }
                is Selection.Path -> wp.paths = wp.paths.filter { it.id != sel.id }
                is Selection.Feature -> wp.features = wp.features.filter { it.id != sel.id }
                is Selection.Group -> wp.plants = wp.plants.filter { it.id !in sel.ids }
            }
        }
        selection = null
        App.status("Deleted. Undo brings it back.")
        App.render()
    }

    fun handleKey(e: Event) {
        val k = e.asDynamic().key as String
        val ctrl = (e.asDynamic().ctrlKey as Boolean) || (e.asDynamic().metaKey as Boolean)
        val target = e.target?.asDynamic()?.tagName as? String
        if (target == "INPUT" || target == "SELECT" || target == "TEXTAREA") return
        when {
            ctrl && (k == "z" || k == "Z") && !(e.asDynamic().shiftKey as Boolean) -> { e.preventDefault(); if (Store.undo()) App.render() }
            ctrl && (k == "y" || k == "Y" || ((k == "z" || k == "Z") && (e.asDynamic().shiftKey as Boolean))) -> { e.preventDefault(); if (Store.redo()) App.render() }
            k == "Delete" || k == "Backspace" -> { e.preventDefault(); deleteSelection() }
            k == "Enter" -> finishPoints()
                        k == "Escape" -> { cancelPoints(); selection = null; find = null; App.render() }
        }
    }
}
