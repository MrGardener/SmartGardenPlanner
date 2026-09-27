package sgp.web

import com.example.smartgardenplanner.core.Backdrop
import com.example.smartgardenplanner.core.PlotPoint
import com.example.smartgardenplanner.core.fmt
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * Satellite photo under the plot (FR-046): open Google Maps in satellite view, screenshot the yard, add the picture
 * here, set its scale from two points a known distance apart, then move / turn it to line up and trace on top.
 * Nothing is fetched from Google by this page; the user's own screenshot is stored with the plot.
 */
object Photo {
    /** Two-click scale setting in progress. */
    var calibrating = false
    var address = ""

    fun backdrop(wp: WebPlot): Backdrop? = if (wp.backdropImage == null) null else Backdrop.parse(wp.plot.backdropJson)

    fun section(body: HTMLElement, wp: WebPlot) {
        body.add(heading("Satellite photo"))
        val b = backdrop(wp)
        if (b == null) {
            body.add(para("See your real trees, fences and buildings under the plot: open Google Maps in satellite view, take a screenshot of your yard (Windows: Win+Shift+S · Mac: Cmd+Shift+4), save it, then add it here.", "hint"))
            val addr = input(address.ifBlank { wp.plot.address ?: wp.plot.locationZip.orEmpty() }, placeholder = "Your address, e.g. 123 Main St, Ann Arbor MI")
            addr.on("input") { address = addr.value }
            body.add(label("Address to look up", addr))
            body.add(h("div", "row wrap", kids = listOf(
                button("Open Google Maps (satellite)", "btn", "Opens Google Maps in a new tab; switch to Satellite if it isn't already, and zoom in on your yard") { openMaps(wp, addr.value) },
                button("Add photo…", "btn primary", "Choose the screenshot or aerial photo of your yard") { pick(wp) }
            )))
            return
        }
        body.add(para("Photo added: ${b.widthM.fmt(1)} m wide. Set the scale, then drag it to line up with your plot and trace trees, fences and buildings on top.", "hint"))
        body.add(h("div", "row wrap", kids = listOf(
            button("Set scale…", if (calibrating) "btn on" else "btn", "Click two points on the photo whose real distance you know (for example the Google Maps scale bar, or the length of a fence)") { startCalibration() },
            button("Move photo", if (Canvas.tool == Tool.PHOTO && !calibrating) "btn on" else "btn", "Drag the photo to line it up with the plot") { calibrating = false; Canvas.setTool(Tool.PHOTO) },
            button(if (b.visible) "Hide" else "Show", "btn") { update(wp) { it.copy(visible = !it.visible) } },
            button("Remove photo", "btn danger") { Store.change { it.backdropImage = null; it.plot = it.plot.copy(backdropJson = null) }; calibrating = false; App.render() }
        )))
        body.add(slider("See-through (%)", (b.opacity * 100).toInt(), 10, 100, "Lower = more see-through, so your drawing stays easy to see") { v -> update(wp) { it.copy(opacity = v / 100f) } })
        // FR-049: slider and number box move together; + turns clockwise, − counter-clockwise, 180° at most either way.
        body.add(slider("Turn (degrees: + clockwise, − counter-clockwise)", kotlin.math.round(b.rotationDeg).toInt(), -180, 180,
            "Turn the photo about its centre to line it up with your plot. Type a number or drag; 0 = not turned") { v -> update(wp) { it.turned(v.toFloat()) } })
        body.add(para("Now: " + Backdrop.describeTurn(b.rotationDeg) + ".", "hint"))
        if (Store.draftWithoutPhotos) body.add(para("The photo is too big to keep in this browser's draft; it's kept while the page is open and in your saved file. Save to keep it.", "warn"))
    }

    /** A range slider and a number box kept in step; the value is applied when either is released or confirmed. */
    private fun slider(text: String, value: Int, min: Int, max: Int, tip: String, onSet: (Int) -> Unit): HTMLElement {
        // min/max first: a range input clamps its value to 0–100 until they are set.
        val r = input(type = "range").also { it.setAttribute("min", "$min"); it.setAttribute("max", "$max"); it.setAttribute("step", "1"); it.value = value.toString(); it.title = tip }
        val box = input(type = "number").also { it.setAttribute("min", "$min"); it.setAttribute("max", "$max"); it.setAttribute("step", "1"); it.value = value.toString(); it.classList.add("num-small"); it.title = tip }
        fun clamp(t: String) = t.toIntOrNull()?.coerceIn(min, max)
        r.on("input") { box.value = r.value }
        r.on("change") { clamp(r.value)?.let(onSet) }
        box.on("input") { clamp(box.value)?.let { r.value = it.toString() } }
        box.on("change") { val v = clamp(box.value); if (v == null) box.value = r.value else { box.value = v.toString(); onSet(v) } }
        return h("label", "field", kids = listOf(h("span", "lbl", text), h("div", "row", kids = listOf(r, box))))
    }

    private fun update(wp: WebPlot, f: (Backdrop) -> Backdrop) {
        val b = backdrop(wp) ?: return
        Store.change { it.plot = it.plot.copy(backdropJson = f(b).encode()) }
        App.render()
    }

    fun openMaps(wp: WebPlot, query: String) {
        val q = query.trim()
        val url = Backdrop.googleMapsUrl(wp.plot.latitude, wp.plot.longitude, q.ifBlank { null })
        if (url == null) { App.status("Add the plot's address (Edit details…) or ZIP code first."); return }
        // FR-050: an address typed here is kept with the plot for next time (only digits = a ZIP, not kept).
        if (q.isNotEmpty() && q != wp.plot.address && !q.all { it.isDigit() }) { Store.change { it.plot = it.plot.copy(address = q.take(200)) } }
        window.open(url, "_blank", "noopener")
        App.status("Google Maps opened in a new tab. Switch to Satellite, zoom in on your yard, take a screenshot, then click Add photo….")
    }

    fun pick(wp: WebPlot) {
        val f = input(type = "file").also { it.setAttribute("accept", "image/png,image/jpeg,image/webp"); it.style.display = "none" }
        f.on("change") {
            val file = f.files?.get(0) ?: return@on
            val reader = FileReader()
            reader.onload = { load(wp, reader.result as String) }
            reader.readAsDataURL(file)
            f.parentNode?.removeChild(f)
        }
        document.body!!.appendChild(f)
        f.click()
    }

    /** Scales the picture down (longest side [Backdrop.MAX_IMAGE_PX]) and stores it as a JPEG with the plot. */
    fun load(wp: WebPlot, dataUrl: String) {
        val img = document.createElement("img") as HTMLImageElement
        img.onload = {
            val w0 = img.naturalWidth; val h0 = img.naturalHeight
            val k = minOf(1.0, Backdrop.MAX_IMAGE_PX.toDouble() / maxOf(w0, h0).coerceAtLeast(1))
            val w = (w0 * k).toInt().coerceAtLeast(1); val hgt = (h0 * k).toInt().coerceAtLeast(1)
            val c = document.createElement("canvas") as HTMLCanvasElement
            c.width = w; c.height = hgt
            c.getContext("2d").asDynamic().drawImage(img, 0, 0, w, hgt)
            var url = c.toDataURL("image/jpeg", 0.85)
            if (url.length > Backdrop.MAX_IMAGE_CHARS) url = c.toDataURL("image/jpeg", 0.6)
            if (!Backdrop.isImageDataUrl(url)) App.status("That picture couldn't be used. Try a PNG or JPEG screenshot.")
            else {
                Store.change { it.backdropImage = url; it.plot = it.plot.copy(backdropJson = Backdrop.fresh(it.plot, w, hgt).encode()) }
                startCalibration()
            }
            null
        }
        img.onerror = { _, _, _, _, _ -> App.status("That file isn't a picture this browser can read."); null }
        img.src = dataUrl
    }

    fun startCalibration() {
        calibrating = true
        Canvas.setTool(Tool.PHOTO)
        App.status("Set scale: click two points on the photo whose real distance you know (the ends of the Google Maps scale bar, or both ends of a fence).")
    }

    /** Second click of the scale setting: ask for the real distance and rescale. */
    fun askDistance(wp: WebPlot, a: PlotPoint, b: PlotPoint) {
        val d = input("", "number", placeholder = "e.g. 10").also { it.setAttribute("step", "0.1"); it.setAttribute("min", "0.1") }
        val shown = kotlin.math.sqrt(((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)).toDouble())
        Dialogs.modal("Set the photo's scale", listOf(
            para("On the photo these two points are ${shown.fmt(2)} m apart at the current scale. How far apart are they really?", "p"),
            label("Real distance, m (1 ft = 0.3048 m)", d)
        ), listOf(
            "Cancel" to { calibrating = false; Canvas.points.clear(); App.render(); true },
            "Set scale" to set@{
                val m = d.value.toFloatOrNull()
                val cal = m?.let { backdrop(wp)?.calibrate(a, b, it) }
                if (cal == null) { App.status("Enter a distance between 0.1 and 2000 m, with the two points not on top of each other."); return@set false }
                Store.change { it.plot = it.plot.copy(backdropJson = cal.encode()) }
                calibrating = false; Canvas.points.clear()
                App.status("Scale set: the photo is ${cal.widthM.fmt(1)} m wide. Drag it to line up with the plot, turn it if needed, then trace trees, fences and buildings.")
                App.render(); true
            }
        ))
    }

}
