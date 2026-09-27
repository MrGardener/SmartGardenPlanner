package sgp.web

import com.example.smartgardenplanner.core.Barrier
import com.example.smartgardenplanner.core.CompanionPlantingValidator
import com.example.smartgardenplanner.core.CropReference
import com.example.smartgardenplanner.core.HardinessZones
import com.example.smartgardenplanner.core.PathZoneEntity
import com.example.smartgardenplanner.core.PlantedNodeEntity
import com.example.smartgardenplanner.core.PlatformClock
import com.example.smartgardenplanner.core.PlotGeometry
import com.example.smartgardenplanner.core.PlotPoint
import com.example.smartgardenplanner.core.PlotShape
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.SiteFeatureEntity
import com.example.smartgardenplanner.core.SiteFeatureType
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
    OUTLINE("Plot outline", "Click the plot's corners in order (3+), then press Enter or click Finish."),
    PLAN("Plan an area for me", "Drag over the area you want planted; then list what to plant.")
}

/** What is currently selected on the layout. */
sealed interface Selection {
    data class Plant(val id: Long) : Selection
    data class Path(val id: Long) : Selection
    data class Feature(val id: Long) : Selection
}

/** The plot layout drawn as SVG, in metres, with the editing tools. */
object Canvas {
    var tool = Tool.SELECT
    var activeSeed: SeedEntity? = null
    var obstacleType = SiteFeatureType.FENCE
    var areaType = SiteFeatureType.FULL_SUN
    var selection: Selection? = null
    var showShade = false
    val points = mutableListOf<PlotPoint>()           // polygon/polyline being drawn
    private var dragStart: PlotPoint? = null           // rectangle tools, panning and moving
    private var dragNow: PlotPoint? = null
    private var dragMoved = false
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
        svg.on("dblclick") { if (tool in setOf(Tool.LINE_OBSTACLE, Tool.AREA, Tool.OUTLINE)) finishPoints() }
        svg.on("wheel") { onWheel(it as WheelEvent) }
    }

    fun setTool(t: Tool) { tool = t; points.clear(); dragStart = null; App.status(t.hint); App.render() }

    fun fit() {
        val p = Store.plot()?.plot ?: return
        val pad = max(p.lengthM, p.widthM) * 0.08 + 0.4
        vb = doubleArrayOf(-pad * 1.2, -pad * 1.6, p.lengthM + pad * 3.0, p.widthM + pad * 2.6)
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
        val wp = Store.plot()
        if (wp == null) {
            svg.setAttribute("viewBox", "0 0 10 6")
            svg.add(s("text", "x" to 5, "y" to 3, "text-anchor" to "middle", "font-size" to 0.45, "fill" to "#94a3b8").also { it.textContent = "Create a plot or open a plan file to start." })
            return
        }
        val p = wp.plot
        if (fittedFor != p.id) fit()
        svg.setAttribute("viewBox", "${num(vb[0])} ${num(vb[1])} ${num(vb[2])} ${num(vb[3])}")
        val fs = max(p.lengthM, p.widthM) / 38.0
        val outline = PlotShape.outline(p)

        svg.add(s("rect", "x" to 0, "y" to 0, "width" to p.lengthM, "height" to p.widthM, "fill" to "#0b1220", "stroke" to "#475569", "stroke-width" to 1.5, "vector-effect" to "non-scaling-stroke"))
        // Grid every metre (every 5 m on big plots).
        val step = if (max(p.lengthM, p.widthM) > 40) 5.0 else 1.0
        var g = step
        while (g < p.lengthM) { svg.add(line(g, 0.0, g, p.widthM.toDouble(), "#1e293b", 1.0)); g += step }
        g = step
        while (g < p.widthM) { svg.add(line(0.0, g, p.lengthM.toDouble(), g, "#1e293b", 1.0)); g += step }
        drawRulers(p.lengthM.toDouble(), p.widthM.toDouble(), fs, step)

        if (showShade) drawShade(wp)
        wp.features.forEach { drawFeature(it, fs) }
        wp.paths.forEach { drawPath(it) }
        if (outline.size >= 3) {
            val poly = outline.joinToString(" ") { "${num(it.x.toDouble())},${num(it.y.toDouble())}" }
            svg.add(s("path", "d" to "M0,0 H${p.lengthM} V${p.widthM} H0 Z M" + outline.joinToString(" L") { "${num(it.x.toDouble())},${num(it.y.toDouble())}" } + " Z",
                "fill" to "rgba(0,0,0,0.6)", "fill-rule" to "evenodd"))
            svg.add(s("polygon", "points" to poly, "fill" to "none", "stroke" to "#f8fafc", "stroke-width" to 2, "vector-effect" to "non-scaling-stroke"))
        }
        wp.plants.forEach { drawPlant(it, selected = (selection as? Selection.Plant)?.id == it.id) }
        Store.preview?.placed?.forEach { pl ->
            val c = Colors.of(pl.seed)
            svg.add(s("circle", "cx" to pl.x, "cy" to pl.y, "r" to pl.seed.exclusionRadiusM, "fill" to c, "fill-opacity" to 0.25, "stroke" to c, "stroke-dasharray" to "4 3", "stroke-width" to 1.5, "vector-effect" to "non-scaling-stroke"))
        }
        Store.previewArea?.let { a -> svg.add(s("polygon", "points" to a.joinToString(" ") { "${it.x},${it.y}" }, "fill" to "none", "stroke" to "#10b981", "stroke-width" to 2, "vector-effect" to "non-scaling-stroke")) }
        drawInProgress()
        drawCompass(p.lengthM.toDouble(), fs, p.northBearingDeg.toDouble(), p.orientationSet)
    }

    private fun line(x1: Double, y1: Double, x2: Double, y2: Double, colour: String, width: Double) =
        s("line", "x1" to x1, "y1" to y1, "x2" to x2, "y2" to y2, "stroke" to colour, "stroke-width" to width, "vector-effect" to "non-scaling-stroke")

    private fun drawRulers(l: Double, w: Double, fs: Double, step: Double) {
        var m = 0.0
        while (m <= l + 1e-6) {
            svg.add(s("text", "x" to m, "y" to -fs * 0.5, "font-size" to fs, "fill" to "#94a3b8", "text-anchor" to "middle").also { it.textContent = "${m.toInt()} m" })
            m += step
        }
        m = step
        while (m <= w + 1e-6) {
            svg.add(s("text", "x" to -fs * 0.4, "y" to m + fs * 0.35, "font-size" to fs, "fill" to "#94a3b8", "text-anchor" to "end").also { it.textContent = "${m.toInt()}" })
            m += step
        }
    }

    private fun drawCompass(l: Double, fs: Double, bearing: Double, set: Boolean) {
        val cx = l + fs * 3.4; val cy = -fs * 1.6; val r = fs * 1.1
        val a = -bearing * kotlin.math.PI / 180.0
        val tx = cx + sin(a) * r; val ty = cy - cos(a) * r
        svg.add(s("circle", "cx" to cx, "cy" to cy, "r" to r * 2.2, "fill" to "#0f172a", "stroke" to "#334155", "stroke-width" to 1, "vector-effect" to "non-scaling-stroke"))
        svg.add(s("line", "x1" to cx - sin(a) * r * 0.6, "y1" to cy + cos(a) * r * 0.6, "x2" to tx, "y2" to ty, "stroke" to if (set) "#ef4444" else "#64748b", "stroke-width" to 3, "stroke-linecap" to "round", "vector-effect" to "non-scaling-stroke"))
        svg.add(s("text", "x" to cx + sin(a) * r * 1.75, "y" to cy - cos(a) * r * 1.75 + fs * 0.32, "font-size" to fs * 0.9, "fill" to "#f8fafc", "text-anchor" to "middle", "font-weight" to "bold").also { it.textContent = if (set) "N" else "N?" })
    }

    private fun drawShade(wp: WebPlot) {
        val p = wp.plot
        val barriers = wp.features.mapNotNull { Barrier.from(it) }
        val key = "${p.lengthM},${p.widthM},${p.latitude},${p.northBearingDeg},${wp.features.hashCode()}"
        val cols = 30
        val rows = (cols * p.widthM / p.lengthM).toInt().coerceIn(4, 60)
        val grid = shadeCache?.takeIf { it.first == key }?.second ?: SunlightEngine.sunHoursGrid(
            p.lengthM, p.widthM, cols, rows, p.latitude ?: SunlightEngine.DEFAULT_LATITUDE,
            SunlightEngine.dayOfYear(PlatformClock.nowMillis()), p.northBearingDeg, barriers
        ).also { shadeCache = key to it }
        val cw = p.lengthM.toDouble() / cols; val ch = p.widthM.toDouble() / rows
        for (r in 0 until rows) for (c in 0 until cols) {
            val hrs = grid[r * cols + c]
            val alpha = if (hrs < 3f) 0.55 else if (hrs < 6f) 0.3 else 0.0
            if (alpha > 0) svg.add(s("rect", "x" to c * cw, "y" to r * ch, "width" to cw + 0.01, "height" to ch + 0.01, "fill" to "#000", "fill-opacity" to alpha))
        }
    }

    private fun featureColours(t: SiteFeatureType): Pair<String, String> = when (t) {
        SiteFeatureType.FULL_SUN -> "#facc15" to "#facc15"
        SiteFeatureType.PART_SHADE -> "#94a3b8" to "#94a3b8"
        SiteFeatureType.FULL_SHADE -> "#334155" to "#64748b"
        SiteFeatureType.FLOOD -> "#3b82f6" to "#3b82f6"
        SiteFeatureType.SLOPE -> "#a16207" to "#d97706"
        SiteFeatureType.TREE -> "#22c55e" to "#15803d"
        SiteFeatureType.FENCE -> "#a16207" to "#a16207"
        SiteFeatureType.WALL -> "#9ca3af" to "#9ca3af"
        SiteFeatureType.BUILDING -> "#e5e7eb" to "#e5e7eb"
    }

    private fun drawFeature(f: SiteFeatureEntity, fs: Double) {
        val t = SiteFeatureType.of(f.featureType) ?: return
        val pts = PlotGeometry.parsePoints(f.pointsJson)
        if (pts.isEmpty()) return
        val sel = (selection as? Selection.Feature)?.id == f.id
        val (fill, edge) = featureColours(t)
        val stroke = if (sel) "#f97316" else edge
        val g = s("g", "data-feature" to f.id)
        when {
            t.isArea && pts.size >= 3 -> {
                g.add(s("polygon", "points" to pts.joinToString(" ") { "${it.x},${it.y}" }, "fill" to fill, "fill-opacity" to 0.22, "stroke" to stroke, "stroke-width" to if (sel) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
                val cx = pts.map { it.x }.average(); val cy = pts.map { it.y }.average()
                g.add(s("text", "x" to cx, "y" to cy, "font-size" to fs * 0.8, "fill" to edge, "text-anchor" to "middle").also { it.textContent = f.label.ifBlank { t.label } })
                if (t == SiteFeatureType.SLOPE) {
                    val a = (f.slopeDirectionDeg - (Store.plot()?.plot?.northBearingDeg ?: 0f)) * kotlin.math.PI / 180.0
                    g.add(s("line", "x1" to cx, "y1" to cy + fs, "x2" to cx + sin(a) * fs * 2, "y2" to cy + fs - cos(a) * fs * 2, "stroke" to edge, "stroke-width" to 3, "vector-effect" to "non-scaling-stroke"))
                }
            }
            t == SiteFeatureType.TREE -> {
                val r = max(f.radiusM, 0.2f)
                g.add(s("circle", "cx" to pts[0].x, "cy" to pts[0].y, "r" to r, "fill" to fill, "fill-opacity" to 0.3, "stroke" to stroke, "stroke-width" to if (sel) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
                g.add(s("circle", "cx" to pts[0].x, "cy" to pts[0].y, "r" to r * 0.12, "fill" to "#78350f"))
            }
            else -> {
                val closed = t == SiteFeatureType.BUILDING && pts.size >= 3
                g.add(s(if (closed) "polygon" else "polyline", "points" to pts.joinToString(" ") { "${it.x},${it.y}" }, "fill" to if (closed) fill else "none", "fill-opacity" to 0.25, "stroke" to stroke, "stroke-width" to if (sel) 8 else 6, "stroke-linecap" to "round", "vector-effect" to "non-scaling-stroke"))
            }
        }
        if (t.isBarrier) g.add(s("text", "x" to pts[0].x, "y" to pts[0].y - fs * 0.3, "font-size" to fs * 0.7, "fill" to "#e2e8f0", "text-anchor" to "middle").also { it.textContent = "${f.heightM.fmt(1)} m" })
        svg.add(g)
    }

    private fun drawPath(z: PathZoneEntity) {
        val sel = (selection as? Selection.Path)?.id == z.id
        if (z.pathType == "POLYLINE") {
            val pts = PlotGeometry.parsePoints(z.pointsJson)
            svg.add(s("polyline", "points" to pts.joinToString(" ") { "${it.x},${it.y}" }, "fill" to "none", "stroke" to if (sel) "#f97316" else "#64748b", "stroke-opacity" to 0.6, "stroke-width" to z.widthM, "stroke-linecap" to "round"))
        } else {
            svg.add(s("rect", "x" to z.xM, "y" to z.yM, "width" to z.widthM, "height" to z.heightM, "fill" to "#2d3748", "fill-opacity" to 0.6, "stroke" to if (sel) "#f97316" else "#64748b", "stroke-width" to if (sel) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
        }
    }

    private fun drawPlant(n: PlantedNodeEntity, selected: Boolean) {
        val seed = Catalog.get(n.seedCode)
        val c = Colors.of(seed)
        val r = seed?.exclusionRadiusM ?: 0.3f
        svg.add(s("circle", "cx" to n.coordinateXM, "cy" to n.coordinateYM, "r" to r, "fill" to c, "fill-opacity" to 0.18, "stroke" to if (selected) "#f97316" else c, "stroke-width" to if (selected) 3 else 1.5, "vector-effect" to "non-scaling-stroke"))
        svg.add(s("circle", "cx" to n.coordinateXM, "cy" to n.coordinateYM, "r" to min(r * 0.25f, 0.08f), "fill" to c))
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
            else -> PlotGeometry.distanceToPolyline(pt.x, pt.y, pts) <= 0.3f
        }
    }

    private fun hitPath(wp: WebPlot, pt: PlotPoint): PathZoneEntity? = wp.paths.firstOrNull { z ->
        if (z.pathType == "POLYLINE") PlotGeometry.distanceToPolyline(pt.x, pt.y, PlotGeometry.parsePoints(z.pointsJson)) <= z.widthM / 2 + 0.1f
        else pt.x in z.xM..(z.xM + z.widthM) && pt.y in z.yM..(z.yM + z.heightM)
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
        svg.asDynamic().setPointerCapture(e.asDynamic().pointerId)
        dragMoved = false
        when (tool) {
            Tool.SELECT -> {
                val plant = hitPlant(wp, pt)
                val path = if (plant == null) hitPath(wp, pt) else null
                val feature = if (plant == null && path == null) hitFeature(wp, pt) else null
                selection = when {
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
            else -> {}
        }
    }

    private fun onMove(e: MouseEvent) {
        val start = dragStart ?: return
        val pt = toMetres(e)
        dragNow = pt
        if (dist(start.x, start.y, pt) > 0.02f) dragMoved = true
        if (tool == Tool.SELECT && selection == null) {
            val o = panOrigin ?: return
            val scale = vb[2] / (svg.asDynamic().clientWidth as Double).coerceAtLeast(1.0)
            vb[0] = o[2] - (e.clientX - o[0]) * scale
            vb[1] = o[3] - (e.clientY - o[1]) * scale
            render(); return
        }
        if (tool == Tool.SELECT && dragMoved) renderMovePreview(start, pt) else if (tool == Tool.PATH || tool == Tool.PLAN) render()
    }

    private fun renderMovePreview(start: PlotPoint, now: PlotPoint) {
        render()
        val wp = Store.plot() ?: return
        val dx = now.x - start.x; val dy = now.y - start.y
        when (val sel = selection) {
            is Selection.Plant -> wp.plants.firstOrNull { it.id == sel.id }?.let { n ->
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
            Tool.TREE -> Dialogs.feature(SiteFeatureEntity(plotId = wp.plot.id, featureType = SiteFeatureType.TREE.name, pointsJson = PlotGeometry.serializePoints(listOf(pt)), heightM = 6f, radiusM = 2f), isNew = true)
            Tool.LINE_OBSTACLE, Tool.AREA, Tool.OUTLINE -> { points += pt; App.render() }
        }
    }

    fun finishPoints() {
        val wp = Store.plot() ?: return
        when (tool) {
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
        if (!result.isValid) {
            val conflict = wp.plants.firstOrNull { it.id in (result.spacingViolations + result.antagonistViolations) }
            val other = conflict?.let { Catalog.get(it.seedCode) }
            App.status(if (conflict != null && conflict.id in result.antagonistViolations) "${CropReference.speciesName(seed)} doesn't grow well near ${other?.let { CropReference.speciesName(it) }}." else "Too close to ${other?.commonName ?: "another plant"}.")
            return
        }
        Store.change { it.plants = it.plants + node.copy(id = Store.newId()) }
        App.status("Planted ${seed.commonName}.")
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
                    !CompanionPlantingValidator().validatePlacement(moved, seed, others, { Catalog.get(it) }, Prefs.margin, Prefs.enforceCompanions, Store.guildsActive()).isValid ->
                        App.status("Can't move there: too close to another plant or a plant it dislikes.")
                    else -> { Store.change { it.plants = others + moved }; App.status("Moved.") }
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
            null -> {}
        }
        App.render()
    }

    fun deleteSelection() {
        val sel = selection ?: return
        Store.change { wp ->
            when (sel) {
                is Selection.Plant -> wp.plants = wp.plants.filter { it.id != sel.id }
                is Selection.Path -> wp.paths = wp.paths.filter { it.id != sel.id }
                is Selection.Feature -> wp.features = wp.features.filter { it.id != sel.id }
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
            k == "Escape" -> { cancelPoints(); selection = null; App.render() }
        }
    }
}
