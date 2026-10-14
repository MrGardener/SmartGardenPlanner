package sgp.web

import com.example.smartgardenplanner.core.DistanceUnit
import com.example.smartgardenplanner.core.Numbers
import com.example.smartgardenplanner.core.PhotoMeasure
import com.example.smartgardenplanner.core.PhotoMeasureResult
import com.example.smartgardenplanner.core.PhotoMeasurement
import com.example.smartgardenplanner.core.PixelPoint
import com.example.smartgardenplanner.core.ReferenceObject
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.MouseEvent
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * Measure a plot from a photo on the computer (T2-FUN-025, HLR-MEAS-020, LLR-PMEAS-060): the same core as the phone.
 * Click the four corners of the reference, then the plot's corners; "Use these sizes" opens New plot with them.
 */
object Measure {

    /** Asks for a photo, then opens the measuring dialog. [name] is what was typed in New plot so far. */
    fun pick(name: String) {
        val f = input(type = "file").also { it.setAttribute("accept", "image/png,image/jpeg,image/webp"); it.style.display = "none"; it.className = "measure-file" }
        f.on("change") {
            val file = f.files?.get(0) ?: return@on
            val reader = FileReader()
            reader.onload = { show(reader.result as String, name) }
            reader.readAsDataURL(file)
            f.parentNode?.removeChild(f)
        }
        document.body!!.appendChild(f)
        f.click()
    }

    fun show(dataUrl: String, name: String) {
        val img = document.createElement("img") as HTMLImageElement
        img.onload = { build(dataUrl, img.naturalWidth, img.naturalHeight, name) }
        img.onerror = { _, _, _, _, _ -> App.status("That photo couldn't be opened."); null }
        img.src = dataUrl
    }

    private fun build(url: String, w: Int, h: Int, name: String) {
        if (w <= 0 || h <= 0) { App.status("That photo couldn't be opened."); return }
        val ref = mutableListOf<PixelPoint>()
        val plot = mutableListOf<PixelPoint>()
        var refObj = ReferenceObject.RAISED_BED_4X8
        var measured: PhotoMeasurement? = null

        val customLong = input("", "number").also { it.setAttribute("step", "0.01") }
        val customShort = input("", "number").also { it.setAttribute("step", "0.01") }
        val customRow = h("div", "grid2", kids = listOf(label("Long side, m", customLong), label("Short side, m", customShort)))
        val step = para("", "hint measure-step")
        val result = h("div", "measure-result")
        val svg = s("svg", "class" to "measure-photo", "viewBox" to "0 0 $w $h", "preserveAspectRatio" to "xMidYMid meet")
        svg.add(s("image", "href" to url, "x" to 0, "y" to 0, "width" to w, "height" to h))
        val marks = s("g", "class" to "measure-marks")
        svg.add(marks)
        val holder = h("div", "measure-holder", kids = listOf(svg))
        val dot = maxOf(w, h) / 120.0
        val line = maxOf(w, h) / 350.0

        fun redraw() {
            marks.clear()
            fun shape(points: List<PixelPoint>, color: String, closed: Boolean) {
                for (i in points.indices) {
                    if (i > 0) marks.add(s("line", "x1" to points[i - 1].x, "y1" to points[i - 1].y, "x2" to points[i].x, "y2" to points[i].y, "stroke" to color, "stroke-width" to line))
                }
                if (closed && points.size > 2) marks.add(s("line", "x1" to points.last().x, "y1" to points.last().y, "x2" to points[0].x, "y2" to points[0].y, "stroke" to color, "stroke-width" to line))
                points.forEach { p -> marks.add(s("circle", "cx" to p.x, "cy" to p.y, "r" to dot, "fill" to "white", "stroke" to color, "stroke-width" to line * 1.5)) }
            }
            shape(ref, "#2563eb", ref.size == 4)
            shape(plot, "#ea580c", plot.size >= 3)
        }

        fun sizeM(): Pair<Double, Double>? = if (refObj == ReferenceObject.CUSTOM) {
            val a = Numbers.parse(customLong.value)?.toDouble(); val b = Numbers.parse(customShort.value)?.toDouble()
            if (a != null && b != null && a > 0 && b > 0) maxOf(a, b) to minOf(a, b) else null
        } else refObj.longM to refObj.shortM

        fun update() {
            customRow.style.display = if (refObj == ReferenceObject.CUSTOM) "" else "none"
            step.textContent = Lang.tr(when {
                ref.size < 4 -> "Step 1: click the 4 corners of the reference, in order around it, starting along a long side (${ref.size} of 4)."
                plot.isEmpty() -> "Step 2: click the plot's corners in order around it. Two points measure one distance."
                else -> "Keep clicking the plot's corners, or read the result below (${plot.size} points)."
            })
            result.clear(); measured = null
            val size = sizeM()
            if (ref.size == 4 && plot.size >= 2) {
                if (size == null) { result.add(para("Type the reference's long and short sides.", "hint")); return }
                val shownWidth = (svg.asDynamic().getBoundingClientRect().width as Double).takeIf { it > 0 } ?: w.toDouble()
                when (val r = PhotoMeasure.measure(ref, size.first, size.second, plot, w, h, tapErrorPx = 4.0 * w / shownWidth)) {
                    is PhotoMeasureResult.Failed -> result.add(para(r.reason, "warn"))
                    is PhotoMeasureResult.Ok -> {
                        measured = r.measurement
                        result.add(para(PhotoMeasure.describe(r.measurement, DistanceUnit.METERS), "measure-size"))
                        r.measurement.warnings.forEach { result.add(para("⚠ $it", "warn")) }
                    }
                }
            }
            Lang.apply(result)
        }

        val refSel = select(ReferenceObject.entries.map { it.name to it.label }, refObj.name) { v ->
            refObj = ReferenceObject.entries.firstOrNull { it.name == v } ?: ReferenceObject.RAISED_BED_4X8; update()
        }
        customLong.on("input") { update() }; customShort.on("input") { update() }
        val zoomSel = select(listOf("1" to "Fit", "2" to "2×", "4" to "4×"), "1") { v -> (svg.asDynamic().style).width = "${(v.toIntOrNull() ?: 1) * 100}%" }
        svg.on("click") { e ->
            e as MouseEvent
            val m = svg.asDynamic().getScreenCTM().inverse()
            val x = (m.a * e.clientX + m.c * e.clientY + m.e) as Double
            val y = (m.b * e.clientX + m.d * e.clientY + m.f) as Double
            if (x in 0.0..w.toDouble() && y in 0.0..h.toDouble()) {
                if (ref.size < 4) ref += PixelPoint(x, y) else if (plot.size < PhotoMeasure.MAX_CORNERS) plot += PixelPoint(x, y)
                redraw(); update()
            }
        }
        val tools = h("div", "row", kids = listOf(
            button("Undo last click") { if (plot.isNotEmpty()) plot.removeAt(plot.lastIndex) else if (ref.isNotEmpty()) ref.removeAt(ref.lastIndex); redraw(); update() },
            button("Start over") { ref.clear(); plot.clear(); redraw(); update() },
            label("Zoom", zoomSel)
        ))
        val body = listOf(
            para("Lay something of known size flat on the ground, take one photo from as high as you can, then click its corners and the plot's corners. Valid for flat ground.", "hint"),
            label("Reference", refSel), customRow, step, holder, tools, result
        )
        Dialogs.modal("Measure from a photo", body, listOf(
            "Cancel" to { true },
            "Use these sizes" to use@{
                val m = measured ?: run { App.status("Click the reference and at least 3 plot corners first."); return@use false }
                if (m.widthM == 0.0) { App.status("Two points measure one distance. Click at least 3 plot corners to get a size."); return@use false }
                window.setTimeout({ Dialogs.plotDetails(null, name, m) }, 0)
                true
            }
        ), wide = true)
        update()
    }
}
