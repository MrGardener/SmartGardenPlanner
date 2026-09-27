package sgp.web

import com.example.smartgardenplanner.core.AutoPlanner
import com.example.smartgardenplanner.core.HardinessZones
import com.example.smartgardenplanner.core.PlantHeights
import com.example.smartgardenplanner.core.PlantRequest
import com.example.smartgardenplanner.core.PlantedNodeEntity
import com.example.smartgardenplanner.core.PlatformClock
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.PlotPoint
import com.example.smartgardenplanner.core.RecommendationEngine
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.SiteFeatureEntity
import com.example.smartgardenplanner.core.SiteFeatureType
import com.example.smartgardenplanner.core.fmt
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

val DIRECTIONS = listOf("N" to 0f, "NE" to 45f, "E" to 90f, "SE" to 135f, "S" to 180f, "SW" to 225f, "W" to 270f, "NW" to 315f)
fun compassName(deg: Float): String = DIRECTIONS[(((deg % 360f + 360f) % 360f + 22.5f) / 45f).toInt() % 8].first

/** Modal dialogs. Only one is open at a time. */
object Dialogs {
    private var open: HTMLElement? = null

    fun isOpen() = open != null

    /** Shows a modal with [title], [body] and action buttons (the last one is the primary action). */
    fun modal(title: String, body: List<HTMLElement>, actions: List<Pair<String, () -> Boolean>>, wide: Boolean = false) {
        close()
        val box = h("div", if (wide) "modal wide" else "modal", attrs = mapOf("role" to "dialog", "aria-modal" to "true", "aria-label" to title))
        box.add(h("h2", "modal-title", title))
        val content = h("div", "modal-body")
        body.forEach { content.appendChild(it) }
        box.add(content)
        val bar = h("div", "modal-actions")
        actions.forEachIndexed { i, (label, act) ->
            bar.add(button(label, if (i == actions.lastIndex) "btn primary" else "btn") { if (act()) close() })
        }
        box.add(bar)
        val back = h("div", "backdrop", kids = listOf(box))
        back.on("mousedown") { if (it.target == back) close() }
        back.on("keydown") { if (it.asDynamic().key == "Escape") { it.stopPropagation(); close() } }
        document.body!!.appendChild(back)
        open = back
        (box.querySelector("input, select, textarea") as? HTMLElement ?: box.querySelector("button") as? HTMLElement)?.focus()
    }

    fun close() { open?.let { it.parentNode?.removeChild(it) }; open = null }

    fun message(title: String, lines: List<String>) = modal(title, lines.map { para(it) }, listOf("OK" to { true }))

    fun confirm(title: String, text: String, yes: String, onYes: () -> Unit) =
        modal(title, listOf(para(text)), listOf("Cancel" to { true }, yes to { onYes(); true }))

    private fun numberField(value: Float?, step: String = "0.1"): HTMLInputElement =
        input(value?.let { if (it == kotlin.math.floor(it)) it.toInt().toString() else it.toString() } ?: "", "number").also { it.setAttribute("step", step) }

    private fun compassPicker(initial: Float?, onPick: (Float) -> Unit): HTMLElement {
        val row = h("div", "chips")
        var chosen = initial
        fun draw() {
            row.clear()
            DIRECTIONS.forEach { (name, deg) ->
                row.add(button(name, if (chosen == deg) "chip on" else "chip") { chosen = deg; onPick(deg); draw() })
            }
        }
        draw()
        return row
    }

    // ------------------------------------------------------------------ plots

    /** New plot, or edit the current plot's details when [existing] is given. */
    fun plotDetails(existing: PlotEntity?) {
        val name = input(existing?.name ?: "My garden")
        val length = numberField(existing?.lengthM ?: 6f)
        val width = numberField(existing?.widthM ?: 4f)
        val zip = input(existing?.locationZip ?: "", placeholder = "e.g. 48104")
        val zoneInfo = para("", "hint")
        val zone = select(listOf("" to "Unknown") + HardinessZones.LABELS.map { it to "Zone $it" }, existing?.hardinessZone) {}
        var bearing: Float? = existing?.takeIf { it.orientationSet }?.northBearingDeg
        var lat = existing?.latitude
        var lon = existing?.longitude
        fun lookupZip() {
            val z = zip.value.trim()
            if (z.length != 5) { zoneInfo.textContent = ""; return }
            val loc = Zips.location(z)
            val zn = Zips.zone(z)
            if (loc != null) { lat = loc.latitude; lon = loc.longitude }
            if (zn != null) zone.value = zn
            zoneInfo.textContent = when {
                loc == null && zn == null -> "ZIP not found in the offline tables. Pick the zone yourself."
                else -> listOfNotNull(zn?.let { "USDA zone $it" }, loc?.let { "latitude ${it.latitude.fmt(2)}°" }, loc?.state).joinToString(" · ")
            }
        }
        zip.on("input") { lookupZip() }
        if (existing?.locationZip != null) lookupZip()
        val sand = numberField(existing?.soilSandPct, "1"); val silt = numberField(existing?.soilSiltPct, "1"); val clay = numberField(existing?.soilClayPct, "1")
        val organic = numberField(existing?.soilOrganicPct); val ph = numberField(existing?.soilPh)
        val soil = h("details", "soil", kids = listOf(
            h("summary", text = "Soil (optional)"),
            h("div", "grid3", kids = listOf(label("Sand %", sand), label("Silt %", silt), label("Clay %", clay), label("Organic matter %", organic), label("pH", ph)))
        ))
        val body = listOf(
            label("Name", name),
            h("div", "grid2", kids = listOf(label("Length (left–right), m", length), label("Width (top–bottom), m", width))),
            label("ZIP code (fills in zone and latitude, offline)", zip), zoneInfo,
            label("USDA hardiness zone", zone),
            h("div", "field", kids = listOf(h("span", "lbl", "Which way does the TOP edge of the plot face?"),
                compassPicker(bearing) { bearing = it },
                para("Stand at the bottom edge looking across the plot: the direction you face. This lets the planner work out shade and put tall plants at the back.", "hint")))
            , soil
        )
        modal(if (existing == null) "New plot" else "Plot details", body, listOf(
            "Cancel" to { true },
            (if (existing == null) "Create plot" else "Save") to save@{
                val l = length.value.toFloatOrNull(); val w = width.value.toFloatOrNull()
                if (name.value.isBlank()) { App.status("Give the plot a name."); return@save false }
                if (l == null || w == null || l < 0.5f || w < 0.5f || l > 1000f || w > 1000f) { App.status("Length and width must be between 0.5 and 1000 m."); return@save false }
                val s = sand.value.toFloatOrNull(); val si = silt.value.toFloatOrNull(); val c = clay.value.toFloatOrNull()
                if (s != null && si != null && c != null) {
                    com.example.smartgardenplanner.core.SoilAnalyzer.validateTexture(s, si, c)?.let { App.status(it); return@save false }
                }
                val base = existing ?: PlotEntity(name = "", lengthM = l, widthM = w)
                val updated = base.copy(
                    name = name.value.trim(), lengthM = l, widthM = w,
                    locationZip = zip.value.trim().ifBlank { null }, hardinessZone = zone.value.ifBlank { null },
                    latitude = lat, longitude = lon,
                    northBearingDeg = bearing ?: base.northBearingDeg, orientationSet = bearing != null || base.orientationSet,
                    soilSandPct = s, soilSiltPct = si, soilClayPct = c,
                    soilOrganicPct = organic.value.toFloatOrNull(), soilPh = ph.value.toFloatOrNull()
                )
                if (existing == null) Store.addPlot(updated) else Store.change { it.plot = updated }
                Canvas.selection = null
                App.render(); true
            }
        ))
    }

    // ------------------------------------------------------------------ site features

    /** Height, crown, slope or flood details for a site feature; saves it (new) or updates it. */
    fun feature(entity: SiteFeatureEntity, isNew: Boolean) {
        val t = SiteFeatureType.of(entity.featureType) ?: return
        val labelIn = input(entity.label, placeholder = "optional, e.g. Neighbour's oak")
        val height = numberField(entity.heightM)
        val radius = numberField(entity.radiusM)
        val grade = numberField(entity.slopeGradePct, "1")
        var downhill = entity.slopeDirectionDeg
        val months = (1..12).map { m ->
            val cb = h("input", attrs = mapOf("type" to "checkbox")) as HTMLInputElement
            cb.checked = entity.floodMonths.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(m)
            m to cb
        }
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val body = mutableListOf(label("Label", labelIn))
        if (t.isBarrier) body += label("Height, m (roughly is fine: fence 1.8, 1-storey house 5, 2-storey 8)", height)
        if (t == SiteFeatureType.TREE) body += label("Crown radius, m", radius)
        if (t == SiteFeatureType.SLOPE) {
            body += label("Grade %", grade)
            body += h("div", "field", kids = listOf(h("span", "lbl", "Downhill direction"), compassPicker(downhill) { downhill = it }))
        }
        if (t == SiteFeatureType.FLOOD) body += h("div", "field", kids = listOf(h("span", "lbl", "Months it floods"),
            h("div", "months", kids = months.map { (m, cb) -> h("label", "month", kids = listOf(cb, h("span", text = monthNames[m - 1]))) })))
        val actions = mutableListOf<Pair<String, () -> Boolean>>("Cancel" to { true })
        if (!isNew) actions += "Delete" to { Store.change { wp -> wp.features = wp.features.filter { it.id != entity.id } }; Canvas.selection = null; App.render(); true }
        actions += (if (isNew) "Add" else "Save") to save@{
            val hgt = height.value.toFloatOrNull() ?: 0f
            if (t.isBarrier && (hgt <= 0f || hgt > 150f)) { App.status("Height must be between 0 and 150 m."); return@save false }
            val updated = entity.copy(
                label = labelIn.value.trim(), heightM = if (t.isBarrier) hgt else 0f,
                radiusM = if (t == SiteFeatureType.TREE) (radius.value.toFloatOrNull() ?: 1f).coerceIn(0.2f, 40f) else entity.radiusM,
                slopeGradePct = grade.value.toFloatOrNull() ?: 0f, slopeDirectionDeg = downhill,
                floodMonths = months.filter { it.second.checked }.joinToString(",") { it.first.toString() }
            )
            Store.change { wp ->
                wp.features = if (isNew) wp.features + updated.copy(id = Store.newId()) else wp.features.map { if (it.id == entity.id) updated else it }
            }
            if (Canvas.tool == Tool.TREE && isNew) App.status("Tree added. Click again to add another, or choose Select to move it.")
            App.render(); true
        }
        modal(if (isNew) "Add ${t.label.lowercase()}" else t.label, body, actions)
    }

    // ------------------------------------------------------------------ plan for me

    private data class Row(var seed: SeedEntity?, var count: Int)

    /** "Plan an area for me" (FR-027): list what to plant; the shared AutoPlanner places it. */
    fun planForMe(area: List<PlotPoint>) {
        val wp = Store.plot() ?: return
        val rows = mutableListOf<Row>()
        val ctx = Store.context(wp)
        RecommendationEngine.recommend(Catalog.seeds, ctx, area, limit = 3, foodOnly = true).forEach { rows += Row(it.seed, 3) }
        if (rows.isEmpty()) rows += Row(null, 3)
        val list = h("div", "plan-rows")
        val areaM2 = com.example.smartgardenplanner.core.PlotGeometry.polygonArea(area)
        fun draw() {
            list.clear()
            rows.forEachIndexed { i, row ->
                val search = input(row.seed?.commonName ?: "", placeholder = "Type a plant, e.g. tomato")
                search.setAttribute("list", "sgp-seed-names")
                search.on("change") {
                    val s = Catalog.byName[search.value.trim().lowercase()] ?: Catalog.search(search.value, null, 1).firstOrNull()
                    row.seed = s; if (s != null) search.value = s.commonName
                    draw()
                }
                val count = input(row.count.toString(), "number").also { it.setAttribute("min", "1"); it.setAttribute("max", "200"); it.classList.add("count") }
                count.on("input") { row.count = count.value.toIntOrNull()?.coerceIn(0, 200) ?: 0 }
                val info = row.seed?.let { s ->
                    val parts = mutableListOf("${PlantHeights.heightM(s).fmt(1)} m tall", "spacing ${(s.exclusionRadiusM * 2).fmt(2)} m")
                    RecommendationEngine.conflictReason(s, ctx)?.let { parts += "⚠ $it" }
                    parts.joinToString(" · ")
                } ?: "Not in the catalog yet"
                list.add(h("div", "plan-row", kids = listOf(
                    h("div", "grow", kids = listOf(search, h("div", "hint", info))), count,
                    button("✕", "btn icon", "Remove") { rows.removeAt(i); draw() }
                )))
            }
            list.add(button("+ Add a plant", "btn") { rows += Row(null, 3); draw() })
        }
        draw()
        val warn = mutableListOf<HTMLElement>()
        if (!wp.plot.orientationSet) warn += para("The plot's direction isn't set, so the planner assumes the top edge faces north. Set it in Plot details for accurate shade and tall-plants-at-the-back.", "warn")
        if (wp.plot.latitude == null) warn += para("No ZIP/latitude: sun angles use 40° N.", "hint")
        modal("Plan this area for me", listOf(
            para("Area: ${areaM2.fmt(1)} m². List what you want and how many; the planner places them for sun, pollination and watering, with tall plants at the back.", "hint")
        ) + warn + list, listOf(
            "Cancel" to { Store.previewArea = null; App.render(); true },
            "Plan it" to plan@{
                val requests = rows.mapNotNull { r -> r.seed?.let { PlantRequest(it, r.count) } }.filter { it.count > 0 }
                if (requests.isEmpty()) { App.status("Add at least one plant with a count."); return@plan false }
                val result = AutoPlanner.plan(
                    ctx, area, requests,
                    isBlocked = { x, y, r -> Canvas.blockedByPath(wp, x, y, r) },
                    marginMultiplier = Prefs.margin, orientationKnown = wp.plot.orientationSet
                )
                Store.preview = result; Store.previewArea = area
                previewCard()
                App.render(); true
            }
        ), wide = true)
    }

    /** Shows the proposal: dashed plants on the layout plus a card to keep or discard it. */
    fun previewCard() {
        val r = Store.preview ?: return
        val body = mutableListOf<HTMLElement>()
        val counts = r.placed.groupBy { it.seed.commonName }.map { "${it.value.size} × ${it.key}" }
        body += para(if (r.placed.isEmpty()) "Nothing fitted in that area." else "Placed: " + counts.joinToString(", ") + ".")
        if (r.unplaced.isNotEmpty()) body += para("Didn't fit: " + r.unplaced.entries.joinToString(", ") { "${it.value} × ${it.key}" } + ". Try a bigger area or fewer plants.", "warn")
        r.notes.forEach { body += para("• $it", "hint") }
        val card = byId("sgp-preview")
        card.clear()
        card.add(h("h3", "h", "Proposed planting"))
        body.forEach { card.add(it) }
        card.add(h("div", "row", kids = listOf(
            button("Discard", "btn") { Store.preview = null; Store.previewArea = null; card.clear(); card.classList.add("hidden"); App.render() },
            button("Keep this plan", "btn primary") {
                val now = PlatformClock.nowMillis()
                Store.change { wp ->
                    wp.plants = wp.plants + r.placed.map { PlantedNodeEntity(id = Store.newId(), plotId = wp.plot.id, seedCode = it.seed.botanicalCode, coordinateXM = it.x, coordinateYM = it.y, datePlantedEpochMillis = now) }
                }
                Store.preview = null; Store.previewArea = null
                card.clear(); card.classList.add("hidden")
                App.status("Added ${r.placed.size} plants. Undo removes them all in one step.")
                App.render()
            }
        )))
        card.classList.remove("hidden")
    }

    // ------------------------------------------------------------------ help

    fun help() = modal("How to use Smart Garden Planner", listOf(
        para("This page is the whole app: it runs in your browser with no install and no account, and works offline. Your plan is kept in this browser as a draft, but the plan FILE is what you keep and share."),
        heading("Getting started"),
        para("1. New plot: give its size, ZIP code and which way the top edge faces."),
        para("2. Draw what's there: trees, fences, walls, buildings (with a rough height), and paths."),
        para("3. Plant: choose a variety on the Plants tab and click the layout. Or drag an area with “Plan an area for me” and list what you want."),
        para("4. Save: saves a .sgp.json file. Open it on the Android app (Plots → Import) or in this page on any computer."),
        heading("Moving plans between computer and phone"),
        para("Save the file, then copy it to the phone (USB, email, Google Drive, OneDrive…). On the phone, use Import plan file. To bring phone plots here, use Export plan file on the phone and Open here."),
        heading("Keyboard"),
        para("Ctrl+Z undo · Ctrl+Y redo · Delete removes the selection · Enter finishes a shape · Esc cancels · mouse wheel zooms · drag empty space to pan."),
        para("Version $WEB_VERSION · uses the same planning rules as the Android app. Hardiness zones: USDA 2023 by ZIP (PRISM Group). ZIP locations: public-domain ZIP centroid data.", "hint")
    ), listOf("Close" to { true }), wide = true)
}

/** Download fallback for browsers without the File System Access API (Firefox, Safari, phones). */
fun downloadText(name: String, text: String) {
    val blob = org.w3c.files.Blob(arrayOf(text), org.w3c.files.BlobPropertyBag(type = "application/json"))
    val url = org.w3c.dom.url.URL.createObjectURL(blob)
    val a = h("a", attrs = mapOf("href" to url, "download" to name))
    document.body!!.appendChild(a); a.click(); a.parentNode?.removeChild(a)
    window.setTimeout({ org.w3c.dom.url.URL.revokeObjectURL(url) }, 2000)
}

