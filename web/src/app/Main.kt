package sgp.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

/** The page shell: header toolbar, tool strip, layout, side panel and status line. */
object App {
    private lateinit var header: HTMLElement
    private lateinit var tools: HTMLElement
    private lateinit var panel: HTMLElement
    private lateinit var statusLine: HTMLElement
        private lateinit var legend: HTMLElement
    private lateinit var plantLegend: HTMLElement
    private var legendOpen = true

    fun status(text: String) { if (::statusLine.isInitialized) statusLine.textContent = text }

    fun applyTheme() {
        val root = document.documentElement ?: return
        if (Prefs.theme == "auto") root.removeAttribute("data-theme") else root.setAttribute("data-theme", Prefs.theme)
    }

    fun start() {
        applyTheme()
        val root = byId("app")
        root.clear()
        header = h("header", "top")
        tools = h("nav", "tools", attrs = mapOf("aria-label" to "Tools"))
        val canvasHost = h("div", "canvas", attrs = mapOf("id" to "sgp-canvas"))
        val preview = h("div", "preview hidden", attrs = mapOf("id" to "sgp-preview"))
        panel = h("aside", "panel")
        statusLine = h("footer", "status", attrs = mapOf("role" to "status", "aria-live" to "polite"))
        legend = h("div", "legend hidden", attrs = mapOf("id" to "sgp-legend", "aria-label" to "Shade legend"))
                plantLegend = h("div", "plant-legend", attrs = mapOf("id" to "sgp-plant-legend", "aria-label" to "Plants on this plot"))
        val stage = h("div", "stage", kids = listOf(canvasHost, legend, plantLegend, preview))
        root.add(header, h("main", "main", kids = listOf(tools, stage, panel)), statusLine)
        root.add(h("datalist", attrs = mapOf("id" to "sgp-seed-names"), kids = Catalog.seeds.map { h("option", attrs = mapOf("value" to it.commonName)) }))
        Canvas.mount(canvasHost)
        document.addEventListener("keydown", { e -> if (!Dialogs.isOpen()) Canvas.handleKey(e) })
        window.addEventListener("beforeunload", { e -> if (Store.dirty && Store.plots.isNotEmpty()) { e.preventDefault(); e.asDynamic().returnValue = "" } })
        window.addEventListener("resize", { Canvas.render() })
        document.addEventListener("dragover", { e -> e.preventDefault() })
        document.addEventListener("drop", { e ->
            e.preventDefault()
            val f = e.asDynamic().dataTransfer?.files?.get(0) as? File
            if (f != null) readFile(f)
        })

        val draft = Store.draft()
        if (draft != null) {
            val msgs = Store.load(draft.first, draft.second)
            if (Store.plots.isNotEmpty()) { Store.dirty = true; status("Restored your last session from this browser. Save to keep it as a file.") }
            else if (msgs.isNotEmpty()) status(msgs.first())
        }
        if (Store.plots.isEmpty()) status("Welcome! Create a plot or open a .sgp.json plan file. Press ? for help.")
        render()
        if (!Prefs.disclaimerAccepted) Dialogs.disclaimer { if (Store.plots.isEmpty()) Dialogs.help() } else if (Store.plots.isEmpty()) Dialogs.help()
    }

    fun render() {
        renderHeader()
        renderTools()
                renderLegend()
        renderPlantLegend()
        Panels.render(panel)
        Canvas.render()
    }

    private fun renderHeader() {
        header.clear()
        header.add(h("div", "brand", kids = listOf(h("span", "logo", "🌱"), h("span", text = "Smart Garden Planner"))))
        val plotPicker = if (Store.plots.isNotEmpty()) select(Store.plots.mapIndexed { i, p -> i.toString() to p.plot.name }, Store.current.toString()) {
                        Store.current = it.toInt(); Canvas.selection = null; Store.preview = null; Store.previewArea = null
            Store.viewSeason = null; Store.historyYear = null; Store.rotation = emptyList(); Store.previewMode = PreviewMode.NORMAL; render()
        }.also { it.setAttribute("aria-label", "Plot") } else null
        val file = h("span", "file", (if (Store.dirty) "● " else "") + Store.fileName, mapOf("title" to if (Store.dirty) "Unsaved changes" else "Saved"))
        val wp = Store.plot()
        header.add(h("div", "actions", kids = listOfNotNull(
            button("New plot", "btn") { Dialogs.plotDetails(null) },
            button("Open…", "btn", "Open a .sgp.json plan file") { openFile() },
            button("Save", "btn primary", "Save the plan file (Ctrl+S)") { save(false) },
            button("Save as…", "btn") { save(true) },
            plotPicker,
            button("↶", "btn icon", "Undo (Ctrl+Z)") { if (Store.undo()) render() }.also { if (wp?.undo?.isEmpty() != false) it.setAttribute("disabled", "") },
            button("↷", "btn icon", "Redo (Ctrl+Y)") { if (Store.redo()) render() }.also { if (wp?.redo?.isEmpty() != false) it.setAttribute("disabled", "") },
                        button(if (Canvas.showShade) "Shade: on" else "Shade: off", if (Canvas.showShade) "btn on" else "btn", "Show sun and shade over the whole day or at a chosen time, from obstacles and tall plants") { Canvas.showShade = !Canvas.showShade; render() },
            button(if (Store.showWater) "Water: on" else "Water: off", if (Store.showWater) "btn on" else "btn", "Show what sprinklers, drip lines and hoses reach, and which plants need a watering can") { Store.showWater = !Store.showWater; render() },
            button(when (Prefs.theme) { "light" -> "☀ Light"; "dark" -> "☾ Dark"; else -> "◐ Auto" }, "btn", "Page colours: follow the system, light or dark. The layout itself stays light so shade is easy to see.") {
                Prefs.theme = when (Prefs.theme) { "auto" -> "light"; "light" -> "dark"; else -> "auto" }
                applyTheme(); render()
            },
            button("?", "btn icon", "Help") { Dialogs.help() },
            file
        )))
    }

    private fun renderLegend() {
        legend.clear()
        val wp = Store.plot()
        val viewing = Store.viewSeason
        if (wp == null || (!Canvas.showShade && !Store.showWater && viewing == null)) { legend.classList.add("hidden"); return }
        legend.classList.remove("hidden")
        if (viewing != null) legend.add(h("div", "legend-row", kids = listOf(h("b", text = "Looking back at $viewing (read only)"),
            button("Back to ${wp.season()}", "btn small") { Store.viewSeason = null; render() })))
        if (Canvas.showShade) {
            val lat = wp.plot.latitude ?: com.example.smartgardenplanner.core.SunlightEngine.DEFAULT_LATITUDE
            val day = Canvas.shadeDayOfYear(wp)
            val (rise, set) = com.example.smartgardenplanner.core.ShadeTools.sunriseSunset(lat, day)
            val row = h("div", "legend-row")
            row.add(select(com.example.smartgardenplanner.core.ShadeDay.entries.map { it.name to it.label }, Store.shadeDay.name) { Store.shadeDay = com.example.smartgardenplanner.core.ShadeDay.valueOf(it); render() }.also { it.setAttribute("aria-label", "Day") })
            row.add(select(listOf("day" to "Whole day (hours of sun)", "time" to "At a time of day"), if (Store.shadeHour == null) "day" else "time") {
                Store.shadeHour = if (it == "time") 9.0.coerceIn(rise, set) else null; render()
            }.also { it.setAttribute("aria-label", "Shade mode") })
            val plants = h("input", attrs = mapOf("type" to "checkbox")) as HTMLInputElement
            plants.checked = Store.shadePlants
            plants.on("change") { Store.shadePlants = plants.checked; render() }
            row.add(h("label", "check", kids = listOf(plants, h("span", text = "Tall plants cast shade"))))
            legend.add(row)
            val hour = Store.shadeHour
            if (hour != null) {
                val sun = com.example.smartgardenplanner.core.SunlightEngine.position(lat, day, hour)
                val slider = h("input", attrs = mapOf("type" to "range", "min" to rise.toString(), "max" to set.toString(), "step" to "0.25", "aria-label" to "Time of day")) as HTMLInputElement
                slider.value = hour.toString()
                val readout = h("span", text = "")
                fun describe(hh: Double) {
                    val sp = com.example.smartgardenplanner.core.SunlightEngine.position(lat, day, hh)
                    readout.textContent = "${com.example.smartgardenplanner.core.ShadeTools.clock(hh)} solar time · sun ${compassName(sp.azimuthDeg.toFloat())}, ${sp.elevationDeg.toInt()}° up · dark = shade now"
                }
                describe(sun.let { hour })
                slider.on("input") { Store.shadeHour = slider.value.toDouble(); describe(Store.shadeHour!!); Canvas.render() }
                legend.add(h("div", "legend-row", kids = listOf(h("span", text = "🌅 ${com.example.smartgardenplanner.core.ShadeTools.clock(rise)}"), slider, h("span", text = "🌇 ${com.example.smartgardenplanner.core.ShadeTools.clock(set)}"))))
                legend.add(h("div", "legend-row", kids = listOf(readout)))
            } else {
                val bands = h("div", "legend-row")
                bands.add(h("b", text = "Direct sun over the day:"))
                com.example.smartgardenplanner.core.SunBand.entries.forEach { b ->
                    val sw = h("span", "sw", attrs = mapOf("style" to "background:linear-gradient(${cssRgba(b.overlayArgb)},${cssRgba(b.overlayArgb)}),#f5f1e6"))
                    bands.add(h("span", kids = listOf(sw, h("span", text = b.label))))
                }
                legend.add(bands)
                legend.add(para("Point at a spot to see when it gets sun. Choose “At a time of day” to watch the shade move.", "hint"))
            }
        }
        if (Store.showWater) {
            val row = h("div", "legend-row")
            row.add(h("b", text = "Water:"))
            com.example.smartgardenplanner.core.WaterSource.entries.forEach { w ->
                val sw = if (w == com.example.smartgardenplanner.core.WaterSource.MANUAL) h("span", "sw", attrs = mapOf("style" to "background:#f5f1e6;border:2px dashed #dc2626"))
                    else h("span", "sw", attrs = mapOf("style" to "background:linear-gradient(${cssRgba(w.argb)},${cssRgba(w.argb)}),#f5f1e6"))
                row.add(h("span", kids = listOf(sw, h("span", text = w.label))))
            }
            legend.add(row)
        }
    }

    /** Points out plants matching [test] on the layout ("find"); null clears. */
    fun find(label: String?, test: ((com.example.smartgardenplanner.core.PlantedNodeEntity) -> Boolean)?) {
        Canvas.find = test
        if (test != null && label != null) {
            val n = Store.plot()?.plants?.count(test) ?: 0
            status(if (n == 0) "No $label on this plot." else "Showing $n × $label (circled in orange). Click it again or press Esc to clear.")
        }
        render()
    }

    /** Legend of what's planted, with counts; clicking a line points those plants out (FR-036). */
    private fun renderPlantLegend() {
        plantLegend.clear()
        val wp = Store.plot()
        if (wp == null || wp.plants.isEmpty()) { plantLegend.classList.add("hidden"); return }
        plantLegend.classList.remove("hidden")
        plantLegend.add(h("div", "pl-head", kids = listOf(
            h("b", text = "On this plot (${wp.plants.size})"),
            button(if (legendOpen) "−" else "+", "btn small", if (legendOpen) "Hide the list" else "Show the list") { legendOpen = !legendOpen; render() }
        )))
        if (!legendOpen) return
        plantLegend.add(para("Click a line to find those plants.", "hint"))
        wp.plants.groupBy { it.seedCode }.entries.sortedByDescending { it.value.size }.forEach { (code, list) ->
            val seed = Catalog.get(code)
            val ring = Colors.of(seed)
            val dot = seed?.let { com.example.smartgardenplanner.core.VarietyCatalogTraits.dotArgb(it) }?.let { com.example.smartgardenplanner.core.LayoutPalette.hex(it) } ?: ring
            val name = seed?.commonName ?: code
            val kind = seed?.let { com.example.smartgardenplanner.core.VarietyCatalogTraits.of(it)?.details }
            val active = Canvas.find != null && Canvas.find!!(list.first()) && legendFindCode == code
            val row = h("button", if (active) "pl-row on" else "pl-row", attrs = mapOf("type" to "button", "title" to "Find ${list.size} × $name"), kids = listOfNotNull(
                h("span", "sw2", attrs = mapOf("style" to "border-color:$ring;background:radial-gradient(circle,$dot 0 38%,transparent 40%)")),
                h("span", "grow", kids = listOfNotNull(h("span", "name", "${list.size} × $name"), kind?.let { h("span", "hint", it) }))
            ))
            row.on("click") {
                if (active) { legendFindCode = null; find(null, null) } else { legendFindCode = code; find("$name", { it.seedCode == code }) }
            }
            plantLegend.add(row)
        }
    }
    private var legendFindCode: String? = null

    private fun cssRgba(argb: Long): String {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return "rgba($r,$g,$b,${com.example.smartgardenplanner.core.LayoutPalette.alpha(argb)})"
    }

    private fun renderTools() {
        tools.clear()
        Tool.entries.forEach { t ->
            tools.add(button(t.label, if (Canvas.tool == t) "tool on" else "tool", t.hint) {
                if (t == Tool.PLANT && Canvas.activeSeed == null) Panels.tab = Tab.PLANTS
                if (t == Tool.PHOTO || t == Tool.WATER) Panels.tab = Tab.PLOT
                if (t == Tool.PHOTO) Photo.calibrating = false
                Canvas.setTool(t)
            })
        }
        if (Canvas.tool in setOf(Tool.LINE_OBSTACLE, Tool.AREA, Tool.OUTLINE) && Canvas.points.isNotEmpty()) {
            tools.add(button("Finish (Enter)", "tool primary") { Canvas.finishPoints() })
            tools.add(button("Cancel (Esc)", "tool") { Canvas.cancelPoints() })
        }
        tools.add(h("span", "sep"))
        tools.add(button(if (Prefs.showLabels) "Names: on" else "Names: off", if (Prefs.showLabels) "tool on" else "tool", "Show what each plant is (sweet or hot pepper, cherry or large tomato, spring or bulb onion…)") { Prefs.showLabels = !Prefs.showLabels; render() })
        tools.add(button("＋", "tool icon", "Zoom in") { Canvas.zoom(1 / 1.3) })
        tools.add(button("－", "tool icon", "Zoom out") { Canvas.zoom(1.3) })
        tools.add(button("Fit", "tool", "Fit the plot to the window") { Canvas.fit(); Canvas.render() })
                if (Canvas.tool == Tool.OUTLINE) tools.add(button("Delete outline", "tool danger", "Remove the outline; the plot becomes the full rectangle (undoable)") { Canvas.deleteOutline() })
        (Canvas.selection as? Selection.Plant)?.let { sel ->
            Store.plot()?.plants?.firstOrNull { it.id == sel.id }?.let { n -> tools.add(button("Edit plant…", "tool primary", "Change variety or planting date, or delete (double-click a plant also works)") { Dialogs.plant(n) }) }
        }
        if (Canvas.selection is Selection.Feature) {
            val f = Store.plot()?.features?.firstOrNull { it.id == (Canvas.selection as Selection.Feature).id }
            if (f != null) tools.add(button("Edit selected…", "tool primary") { Dialogs.feature(f, isNew = false) })
        }
        if (Canvas.selection != null) tools.add(button("Delete selected", "tool danger") { Canvas.deleteSelection() })
    }

    // ------------------------------------------------------------------ files

    fun openFile() {
        if (Store.dirty && Store.plots.isNotEmpty()) {
            Dialogs.confirm("Open another plan", "Opening a file replaces what's open here. Save first if you want to keep your changes.", "Open anyway") { pickAndRead() }
        } else pickAndRead()
    }

    private fun pickAndRead() {
        val w = window.asDynamic()
        if (w.showOpenFilePicker != null) {
            val opts: dynamic = js("({types:[{description:'Garden plan',accept:{'application/json':['.json']}}]})")
            w.showOpenFilePicker(opts).then({ handles: dynamic ->
                val handle = handles[0]
                handle.getFile().then({ f: File -> readFile(f, handle) })
            }).catch({ _: dynamic -> })
            return
        }
        val chooser = h("input", attrs = mapOf("type" to "file", "accept" to ".json,.sgp.json,application/json")) as HTMLInputElement
        chooser.on("change") { chooser.files?.get(0)?.let { readFile(it) } }
        chooser.click()
    }

    fun readFile(f: File, handle: dynamic = null) {
        val reader = FileReader()
        reader.onload = {
            val msgs = Store.load(reader.result as String, f.name)
            if (Store.plots.isEmpty()) Dialogs.message("Couldn't open ${f.name}", msgs.ifEmpty { listOf("The file has no plots.") })
            else {
                Store.fileHandle = handle
                Canvas.selection = null
                status("Opened ${f.name}: ${Store.plots.size} plot(s).")
                if (msgs.isNotEmpty()) Dialogs.message("Opened with notes", msgs)
            }
            render()
        }
        reader.readAsText(f)
    }

    fun save(asNew: Boolean) {
        if (Store.plots.isEmpty()) { status("Nothing to save yet."); return }
        val text = Store.encode()
        val w = window.asDynamic()
        val handle = Store.fileHandle
        if (!asNew && handle != null) { writeHandle(handle, text); return }
        if (w.showSaveFilePicker != null) {
            val opts: dynamic = js("({types:[{description:'Garden plan',accept:{'application/json':['.json']}}]})")
            opts.suggestedName = Store.fileName
            w.showSaveFilePicker(opts).then({ fh: dynamic -> Store.fileHandle = fh; Store.fileName = fh.name as String; writeHandle(fh, text) }).catch({ _: dynamic -> })
            return
        }
        var name = Store.fileName
        if (asNew) name = window.prompt("File name", name)?.trim()?.ifBlank { null } ?: return
        if (!name.endsWith(".json")) name += ".sgp.json"
        Store.fileName = name
        downloadText(name, text)
        Store.dirty = false
        status("Downloaded $name. Keep it anywhere; open it here or import it on the phone.")
        render()
    }

    private fun writeHandle(handle: dynamic, text: String) {
        handle.createWritable()
            .then({ stream: dynamic -> stream.write(text).then({ _: dynamic -> stream.close() }) })
            .then({ _: dynamic -> Store.dirty = false; status("Saved ${Store.fileName}."); render() })
            .catch({ e: dynamic -> status("Couldn't save: ${e?.message}") })
    }
}

fun main() {
    document.addEventListener("keydown", { e ->
        val k = e.asDynamic().key as String
        val ctrl = (e.asDynamic().ctrlKey as Boolean) || (e.asDynamic().metaKey as Boolean)
        if (ctrl && (k == "s" || k == "S")) { e.preventDefault(); App.save(e.asDynamic().shiftKey as Boolean) }
        else if (k == "?" && !Dialogs.isOpen() && (e.target?.asDynamic()?.tagName as? String) != "INPUT") Dialogs.help()
        else if (ctrl && (k == "o" || k == "O")) { e.preventDefault(); App.openFile() }
    })
    try {
        App.start()
    } catch (t: Throwable) {
        byId("app").textContent = "Smart Garden Planner couldn't start: ${t.message}"
        throw t
    }
}
