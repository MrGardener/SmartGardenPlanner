package sgp.web

import com.example.smartgardenplanner.core.AutoPlanner
import com.example.smartgardenplanner.core.HardinessZones
import com.example.smartgardenplanner.core.PlantHeights
import com.example.smartgardenplanner.core.PlantRequest
import com.example.smartgardenplanner.core.PlantingLayout
import com.example.smartgardenplanner.core.CropReference
import com.example.smartgardenplanner.core.VarietyCatalogTraits
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

    fun close() {
        val o = open ?: return
        open = null
        releaseFocusIn(o)
        o.parentNode?.removeChild(o)
    }

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
        // FR-042: which pests and animals visit this yard; the Care tab then shows how to keep them out.
        val chosenPests = com.example.smartgardenplanner.core.Pest.parse(existing?.pests).toMutableSet()
        val pestBox = h("div", "field pests", kids = listOf(
            h("span", "lbl", "What pests or animals do you see regularly in your yard? (tick all that apply)"),
            h("div", "chips", kids = com.example.smartgardenplanner.core.Pest.entries.map { pest ->
                val cb = (h("input", attrs = mapOf("type" to "checkbox")) as HTMLInputElement).also { it.checked = pest in chosenPests }
                cb.on("change") { if (cb.checked) chosenPests += pest else chosenPests -= pest }
                h("label", "check chip-check", kids = listOf(cb, h("span", text = pest.label)))
            }),
            para("The Care tab then shows how to keep them away (fencing and other measures) and which of your plants they go for. You can change this later in Plot details.", "hint")
        ))
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
            , pestBox, soil
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
                    soilOrganicPct = organic.value.toFloatOrNull(), soilPh = ph.value.toFloatOrNull(),
                    pests = com.example.smartgardenplanner.core.Pest.encode(chosenPests)
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
                val arcWidth = select(listOf("360" to "Full circle", "270" to "Three quarters (270°)", "180" to "Half circle (180°)", "90" to "Quarter circle (90°)"),
            (if (entity.slopeGradePct <= 0f || entity.slopeGradePct >= 360f) 360 else entity.slopeGradePct.toInt()).toString()) {}
        when (t) {
            SiteFeatureType.SPRINKLER -> {
                body += label("How far it throws water (radius), m", radius)
                body += label("Pattern", arcWidth)
                body += h("div", "field", kids = listOf(h("span", "lbl", "For a part circle: which way the middle of the spray points"), compassPicker(downhill) { downhill = it }))
            }
            SiteFeatureType.DRIP_LINE -> body += label("Wetted strip each side of the line, m (drip about 0.3, soaker hose 0.2)", radius)
            SiteFeatureType.HOSE_BIB -> body += label("Hose length, m", radius)
            else -> {}
        }
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
                                radiusM = when {
                    t == SiteFeatureType.TREE -> (radius.value.toFloatOrNull() ?: 1f).coerceIn(0.2f, 40f)
                    t == SiteFeatureType.SPRINKLER -> (radius.value.toFloatOrNull() ?: 3f).coerceIn(0.5f, 30f)
                    t == SiteFeatureType.DRIP_LINE -> (radius.value.toFloatOrNull() ?: 0.3f).coerceIn(0.05f, 2f)
                    t == SiteFeatureType.HOSE_BIB -> (radius.value.toFloatOrNull() ?: 15f).coerceIn(1f, 60f)
                    else -> entity.radiusM
                },
                slopeGradePct = if (t == SiteFeatureType.SPRINKLER) arcWidth.value.toFloat() else grade.value.toFloatOrNull() ?: 0f, slopeDirectionDeg = downhill,
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

    private data class Row(var seed: SeedEntity?, var count: Int, var priority: Boolean = false)

    /**
     * "Plan an area for me" (FR-027): list what to plant; the shared AutoPlanner places it. The list starts from the
     * rows being edited (after "Change selections"), else the last list used, else the usual plants, else suggestions
     * (FR-034). Clumps or rows is chosen here (FR-032).
     */
        fun planForMe(area: List<PlotPoint>, mode: PreviewMode = PreviewMode.NORMAL) {
        val wp = Store.plot() ?: return
        val nextSeason = mode == PreviewMode.NEXT_SEASON
        // Next season: an empty plot, with this season's plants counted as history for the rotation (FR-037).
        val archivedNow = if (nextSeason && wp.plants.isNotEmpty()) com.example.smartgardenplanner.core.Seasons.archive(wp.plot.id, wp.plants, wp.season(), { Catalog.get(it) }) else emptyList()
        val planHistory = wp.history + archivedNow
        val planYear = if (nextSeason && wp.plants.isNotEmpty()) wp.season() + 1 else wp.season()
        val ctx = if (nextSeason) Store.context(wp).copy(nodes = emptyList()) else Store.context(wp)
        val rows = mutableListOf<Row>()
        val start = if (nextSeason) com.example.smartgardenplanner.core.RotationPlanner.lastList(wp.plants, wp.history, { Catalog.get(it) }).map { it.seed.botanicalCode to it.count }
            else Store.planRows.ifEmpty { Prefs.lastPlan }
        start.forEach { (code, n) -> Catalog.get(code)?.let { rows += Row(it, n, code in Store.planPriority) } }
        if (rows.isEmpty()) Store.usual(4).forEach { rows += Row(it.first, 3) }
        if (rows.isEmpty()) RecommendationEngine.recommend(Catalog.seeds, ctx, area, limit = 3, foodOnly = true).forEach { rows += Row(it.seed, 3) }
        if (rows.isEmpty()) rows += Row(null, 3)
        fun remember() {
            if (nextSeason) return
            Store.planRows.clear(); Store.planRows += rows.mapNotNull { r -> r.seed?.let { it.botanicalCode to r.count } }
            Store.planPriority.clear(); Store.planPriority += rows.filter { it.priority }.mapNotNull { it.seed?.botanicalCode }
        }
        // FR-043: checks shown before anything is placed, updated as the list changes.
        val checksBox = h("div", "plan-checks")
        var checksTimer = 0
        fun drawChecks() {
            checksBox.clear()
            val reqs = rows.mapNotNull { r -> r.seed?.let { PlantRequest(it, r.count, r.priority) } }.filter { it.count > 0 }
            if (reqs.isEmpty()) return
            val checks = com.example.smartgardenplanner.core.PlanChecks.check(ctx, area, reqs, planHistory, planYear, com.example.smartgardenplanner.core.Pest.parse(wp.plot.pests), Prefs.margin)
            checksBox.add(h("div", "lbl", "Checks before planning"))
            checks.forEach { c -> checksBox.add(h("div", "check-line sev-${c.severity.name.lowercase()}", (when (c.severity) { com.example.smartgardenplanner.core.Severity.HIGH -> "⚠ "; com.example.smartgardenplanner.core.Severity.MEDIUM -> "• "; else -> "✓ " }) + c.text)) }
        }
        fun checksSoon() { window.clearTimeout(checksTimer); checksTimer = window.setTimeout({ drawChecks() }, 250) }
        Store.previewArea = area
        val list = h("div", "plan-rows")
        val areaM2 = com.example.smartgardenplanner.core.PlotGeometry.polygonArea(area)
        fun draw() {
            list.clear()
            rows.forEachIndexed { i, row ->
                val search = input(row.seed?.commonName ?: "", placeholder = "Type a plant, e.g. cherry tomato, sweet pepper, spring onion")
                search.setAttribute("list", "sgp-seed-names")
                search.on("change") {
                    val s = Catalog.byName[search.value.trim().lowercase()] ?: Catalog.search(search.value, null, 1).firstOrNull()
                    row.seed = s; if (s != null) search.value = s.commonName
                    remember(); draw()
                }
                val count = input(row.count.toString(), "number").also { it.setAttribute("min", "1"); it.setAttribute("max", "200"); it.classList.add("count") }
                count.on("input") { row.count = count.value.toIntOrNull()?.coerceIn(0, 200) ?: 0; remember(); checksSoon() }
                val star = button(if (row.priority) "★" else "☆", if (row.priority) "btn icon star on" else "btn icon star",
                    if (row.priority) "Most important: placed first, in the sunniest spots that suit it (click to unmark)" else "Mark as most important: placed first, in the sunniest spots that suit it") {
                    row.priority = !row.priority; remember(); draw()
                }
                val info = row.seed?.let { s ->
                    val parts = mutableListOf<String>()
                    VarietyCatalogTraits.of(s)?.let { parts += it.details }
                    parts += "${PlantHeights.heightM(s).fmt(1)} m tall"
                    parts += "spacing ${(s.exclusionRadiusM * 2).fmt(2)} m"
                    RecommendationEngine.conflictReason(s, ctx)?.let { parts += "⚠ $it" }
                    parts.joinToString(" · ")
                } ?: "Not in the catalog yet"
                list.add(h("div", "plan-row", kids = listOf(
                    star, h("div", "grow", kids = listOf(search, h("div", "hint", info))), count,
                    button("✕", "btn icon", "Remove") { rows.removeAt(i); remember(); draw() }
                )))
            }
                        list.add(h("div", "row wrap", kids = listOf(
                button("+ Add a plant", "btn") { rows += Row(null, 3); draw() },
                button("How many fit?", "btn", "Keep the proportions of your list and fill the area with as many as fit in organised clumps") {
                    val reqs = rows.mapNotNull { r -> r.seed?.let { PlantRequest(it, r.count.coerceAtLeast(1)) } }
                    if (reqs.isEmpty()) { App.status("Add at least one plant first."); return@button }
                    val fit = com.example.smartgardenplanner.core.RotationPlanner.howManyFit(ctx, area, reqs, { x, y, r -> Canvas.blockedByPath(wp, x, y, r) }, Prefs.margin)
                    fit.forEach { f -> rows.firstOrNull { it.seed?.botanicalCode == f.seed.botanicalCode }?.count = f.count }
                    remember(); draw()
                    App.status("About ${fit.sumOf { it.count }} plants fit here: " + fit.joinToString(", ") { "${it.count} ${CropReference.speciesName(it.seed)}" } + ". Change any number, then Plan it.")
                }
            )))
            drawChecks()
        }
        draw()
        val usual = Store.usual()
        val usualBox = if (usual.isEmpty()) null else h("div", "field", kids = listOf(
            h("span", "lbl", "What you usually plant (tap to add)"),
            h("div", "chips", kids = usual.map { (s, n) ->
                button("+ ${CropReference.speciesName(s)}", "chip", "${VarietyCatalogTraits.displayName(s)} — planted $n time${if (n == 1) "" else "s"}") {
                    val i = rows.indexOfFirst { it.seed?.botanicalCode == s.botanicalCode }
                    if (i >= 0) rows[i].count += 1 else { rows.removeAll { it.seed == null }; rows += Row(s, 3) }
                    remember(); draw()
                }
            })
        ))
        var layout = Prefs.layout
        val layoutBox = h("div", "field")
        fun drawLayout() {
            layoutBox.clear()
            layoutBox.add(h("span", "lbl", "How should each crop be arranged?"))
            layoutBox.add(h("div", "chips", kids = PlantingLayout.entries.map { l ->
                button(l.label, if (l == layout) "chip on" else "chip") { layout = l; Prefs.layout = l; drawLayout() }
            }))
            layoutBox.add(para(layout.description, "hint"))
        }
        drawLayout()
        val warn = mutableListOf<HTMLElement>()
        if (!wp.plot.orientationSet) warn += para("The plot's direction isn't set, so the planner assumes the top edge faces north. Set it in Plot details for accurate shade and tall-plants-at-the-back.", "warn")
        if (wp.plot.latitude == null) warn += para("No ZIP/latitude: sun angles use 40° N.", "hint")
                if (planHistory.isNotEmpty()) warn += para("Past seasons on this plot are used for crop rotation: no crop goes where its family grew last season, and families stay away from their recent spots.", "hint")
        if (nextSeason) warn += para(if (wp.plants.isNotEmpty()) "Planning $planYear for the whole plot. When you keep the plan, this season's ${wp.plants.size} plants move to history (season ${wp.season()}) and the new layout is planted. Fences, buildings, trees, paths, areas and irrigation stay." else "Planning $planYear for the whole plot from your last season's list.", "p")
        modal(if (nextSeason) "Plan next season ($planYear) with crop rotation" else if (area == com.example.smartgardenplanner.core.PlotShape.effectiveOutline(wp.plot)) "Fill the whole plot" else "Plan this area for me", listOfNotNull(
            para("Area: ${areaM2.fmt(1)} m². List what you want and how many; the planner places them for sun, pollination and watering, with tall plants at the back. Tap ☆ on the plants that matter most: they're placed first, in the sunniest spots.", "hint"),
            usualBox
        ) + warn + list + checksBox + layoutBox, listOf(
            "Cancel" to { remember(); Store.previewArea = null; App.status("Plan cancelled. Your list is kept for next time."); App.render(); true },
            "Plan it" to plan@{
                val requests = rows.mapNotNull { r -> r.seed?.let { PlantRequest(it, r.count, r.priority) } }.filter { it.count > 0 }
                if (requests.isEmpty()) { App.status("Add at least one plant with a count."); return@plan false }
                remember()
                Prefs.lastPlan = Store.planRows.toList()
                                val result = AutoPlanner.plan(
                    ctx, area, requests,
                    isBlocked = { x, y, r -> Canvas.blockedByPath(wp, x, y, r) },
                    marginMultiplier = Prefs.margin, orientationKnown = wp.plot.orientationSet,
                    layout = layout, history = planHistory, seasonYear = planYear
                )
                Store.preview = result; Store.previewArea = area; Store.previewMode = mode
                previewCard()
                App.render(); true
            }
        ), wide = true)
    }

    /**
     * Shows the proposal: dashed plants on the layout plus a card to keep it, change the selections (back to the list,
     * same area) or discard it. Nothing on the plot changes until "Keep this plan".
     */
    fun previewCard() {
        val r = Store.preview ?: return
        val area = Store.previewArea
        val body = mutableListOf<HTMLElement>()
        val counts = r.placed.groupBy { it.seed.botanicalCode }.map { (_, l) -> "${l.size} × ${VarietyCatalogTraits.displayName(l.first().seed)}" }
        body += para(if (r.placed.isEmpty()) "Nothing fitted in that area." else "Placed: " + counts.joinToString(", ") + ".")
        if (r.unplaced.isNotEmpty()) body += para("Didn't fit: " + r.unplaced.entries.joinToString(", ") { "${it.value} × ${it.key}" } + ". Try a bigger area or fewer plants.", "warn")
        r.notes.forEach { body += para("• $it", "hint") }
        val card = byId("sgp-preview")
        fun hide() { card.clear(); card.classList.add("hidden") }
        card.clear()
                val wpNow = Store.plot()
        val rotation = Store.previewMode == PreviewMode.ROTATION
        card.add(h("h3", "h", when (Store.previewMode) {
            PreviewMode.NEXT_SEASON -> "Next season's plan (not planted yet)"
            PreviewMode.ROTATION -> "Rotation plan: ${Store.rotation.getOrNull(Store.rotationIndex)?.year} (${Store.rotationIndex + 1} of ${Store.rotation.size})"
            else -> "Proposed planting (not planted yet)"
        }))
        if (rotation) Store.rotation.getOrNull(Store.rotationIndex)?.summary?.forEach { card.add(para("• $it", "hint")) }
        body.forEach { card.add(it) }
        card.add(h("div", "row wrap", kids = listOfNotNull(
                        button(when (Store.previewMode) { PreviewMode.NEXT_SEASON -> "Start next season with this plan"; PreviewMode.ROTATION -> "Use ${Store.rotation.firstOrNull()?.year} now"; else -> "Keep this plan" }, "btn primary") {
                val now = PlatformClock.nowMillis()
                val plan = if (rotation) Store.rotation.firstOrNull()?.result ?: r else r
                val newSeason = Store.previewMode != PreviewMode.NORMAL
                Store.change { wp ->
                    val fresh = plan.placed.map { PlantedNodeEntity(id = Store.newId(), plotId = wp.plot.id, seedCode = it.seed.botanicalCode, coordinateXM = it.x, coordinateYM = it.y, datePlantedEpochMillis = now) }
                    if (newSeason) {
                        // Close this season (plants → history), then plant the new one: one undo step.
                        if (wp.plants.isNotEmpty()) wp.history = wp.history + com.example.smartgardenplanner.core.Seasons.archive(wp.plot.id, wp.plants, wp.season(), { Catalog.get(it) }).map { it.copy(id = Store.newId()) }
                        wp.plants = fresh
                    } else wp.plants = wp.plants + fresh
                }
                Store.preview = null; Store.previewArea = null; Store.previewMode = PreviewMode.NORMAL; Store.rotation = emptyList()
                hide()
                App.status(if (newSeason) "New season ${wpNow?.season()} started with ${plan.placed.size} plants; last season is in the history. Undo reverses this." else "Added ${plan.placed.size} plants. Undo removes them all in one step.")
                App.render()
            },
                        if (rotation) button("◀ Year", "btn", "Previous year of the plan") { showRotationYear(Store.rotationIndex - 1) } else null,
            if (rotation) button("Year ▶", "btn", "Next year of the plan") { showRotationYear(Store.rotationIndex + 1) } else null,
            if (rotation) null else button("Change selections", "btn", "Back to your list for the same area") {
                val mode = Store.previewMode
                Store.preview = null; hide()
                if (area != null) planForMe(area, mode)
                App.render()
            },
                        button(if (rotation) "Close" else "Discard", "btn", "Drop this proposal. Your plants and your list stay as they are.") {
                Store.preview = null; Store.previewArea = null; Store.previewMode = PreviewMode.NORMAL; Store.rotation = emptyList(); hide()
                App.status("Proposal discarded. Nothing on the plot changed, and your list is kept for next time.")
                App.render()
            }
        )))
        card.classList.remove("hidden")
    }

    // ------------------------------------------------------------------ planted plants

    /** Details of a planted plant, with change variety, planting date and delete (all undoable). */
    fun plant(node: PlantedNodeEntity) {
        val wp = Store.plot() ?: return
        val seed = Catalog.get(node.seedCode)
        val variety = input(seed?.commonName ?: node.seedCode, placeholder = "Type a variety")
        variety.setAttribute("list", "sgp-seed-names")
        val date = input(com.example.smartgardenplanner.core.CivilDate.isoUtc(node.datePlantedEpochMillis).take(10), "date")
        val body = mutableListOf<HTMLElement>()
        seed?.let { sd ->
            VarietyCatalogTraits.of(sd)?.let { body += para(it.details, "kind") }
            body += para("${sd.plantType.lowercase()} · ${sd.lifecycle.lowercase()} · spacing ${(sd.exclusionRadiusM * 2).fmt(2)} m · harvest about ${sd.daysToHarvest} days after planting", "hint")
            if (sd.careNotes.isNotBlank()) body += para(sd.careNotes, "hint")
        }
        body += para("Position: ${node.coordinateXM.fmt(2)} m from the left, ${node.coordinateYM.fmt(2)} m from the top (${com.example.smartgardenplanner.core.CropRotation.describeSpot(wp.plot, node.coordinateXM, node.coordinateYM)}). Drag it with Select / move to change it.", "hint")
        body += label("Variety", variety)
        body += label("Planted on", date)
        modal(seed?.let { VarietyCatalogTraits.displayName(it) } ?: node.seedCode, body, listOf(
            "Cancel" to { true },
            "Delete plant" to { Store.change { p -> p.plants = p.plants.filter { it.id != node.id } }; Canvas.selection = null; App.status("Plant deleted. Undo brings it back."); App.render(); true },
            "Save" to save@{
                val newSeed = Catalog.byName[variety.value.trim().lowercase()] ?: Catalog.search(variety.value, null, 1).firstOrNull()
                if (newSeed == null) { App.status("Choose a variety from the list."); return@save false }
                val day = date.value.split("-").mapNotNull { it.toIntOrNull() }
                val planted = if (day.size == 3) com.example.smartgardenplanner.core.CivilDate.localMidnight(day[0], day[1], day[2], com.example.smartgardenplanner.core.PlatformClock.localOffsetMillis(node.datePlantedEpochMillis)) + 12 * 3_600_000L else node.datePlantedEpochMillis
                val updated = node.copy(seedCode = newSeed.botanicalCode, datePlantedEpochMillis = planted)
                if (newSeed.botanicalCode != node.seedCode) {
                    val others = wp.plants.filter { it.id != node.id }
                    val ok = com.example.smartgardenplanner.core.CompanionPlantingValidator().validatePlacement(updated, newSeed, others, { Catalog.get(it) }, Prefs.margin, Prefs.enforceCompanions, Store.guildsActive()).isValid
                    if (!ok) { App.status("${newSeed.commonName} doesn't fit here (spacing or a plant it dislikes nearby). Move it first or choose another variety."); return@save false }
                }
                Store.change { p -> p.plants = p.plants.map { if (it.id == node.id) updated else it } }
                App.status("Saved ${VarietyCatalogTraits.displayName(newSeed)}.")
                App.render(); true
            }
        ))
    }

    // ------------------------------------------------------------------ seasons (FR-033)

    /** Closes the current season: plants move to history, everything else on the plot stays. One undo step. */
    fun newSeason() {
        val wp = Store.plot() ?: return
        val season = wp.season()
        val yearIn = input(season.toString(), "number").also { it.setAttribute("min", "1900"); it.setAttribute("max", "3000") }
        val body = listOf(
            para("This keeps the plot's fences, walls, buildings, trees, paths, sun/shade areas and outline, and moves this season's ${wp.plants.size} plant(s) into the plot's history."),
            para("History stays visible (Plot tab → Seasons, and “Show past season” on the layout) and is used for crop-rotation advice and by “Plan an area for me”. It is saved in the plan file.", "hint"),
            label("Season being closed", yearIn)
        )
        modal("Start a new season on “${wp.plot.name}”", body, listOf(
            "Cancel" to { true },
            "Start new season" to go@{
                val year = yearIn.value.toIntOrNull()?.takeIf { it in 1900..3000 } ?: run { App.status("Enter a year between 1900 and 3000."); return@go false }
                val archived = com.example.smartgardenplanner.core.Seasons.archive(wp.plot.id, wp.plants, year, { Catalog.get(it) }).map { it.copy(id = Store.newId()) }
                Store.change { p -> p.history = p.history + archived; p.plants = emptyList() }
                Store.historyYear = year
                Canvas.selection = null
                App.status("Season $year closed: ${archived.size} plant(s) kept in history (shown faded). Plan ${year + 1} with crop rotation in mind. Undo reverses this.")
                App.render(); true
            }
        ))
    }

    // ------------------------------------------------------------------ several seasons (FR-037)

    /** Plans the plot for several seasons in a row with strict rotation, and shows one year at a time on the layout. */
    fun rotationPlan() {
        val wp = Store.plot() ?: return
        val base = com.example.smartgardenplanner.core.RotationPlanner.lastList(wp.plants, wp.history, { Catalog.get(it) })
        if (base.isEmpty()) { message("Nothing to rotate yet", listOf("Plant this season first (or close a season). The rotation plan re-uses that list every year.")); return }
        val seasonsIn = select((2..10).map { it.toString() to "$it seasons" }, "5") {}
        val firstYear = if (wp.plants.isNotEmpty()) wp.season() + 1 else wp.season()
        modal("Rotation plan for the next seasons", listOf(
            para("Uses the same list every year (" + base.joinToString(", ") { "${it.count} ${CropReference.speciesName(it.seed)}" } + ") and plans the whole plot season after season, so no crop goes where its family grew the year before. Fences, buildings, trees, paths, areas and irrigation stay the same.", "p"),
            label("How many seasons, starting $firstYear", seasonsIn),
            para("This is a plan to look at: nothing changes until you choose “Use $firstYear now”. It is worked out again from your plot each time, so it follows any changes you make.", "hint")
        ), listOf(
            "Cancel" to { true },
            "Make the plan" to {
                val history = wp.history + (if (wp.plants.isNotEmpty()) com.example.smartgardenplanner.core.Seasons.archive(wp.plot.id, wp.plants, wp.season(), { Catalog.get(it) }) else emptyList())
                Store.rotation = com.example.smartgardenplanner.core.RotationPlanner.planSeasons(
                    Store.context(wp), com.example.smartgardenplanner.core.PlotShape.effectiveOutline(wp.plot), base, history, firstYear, seasonsIn.value.toInt(),
                    { x, y, r -> Canvas.blockedByPath(wp, x, y, r) }, Prefs.margin, wp.plot.orientationSet
                )
                showRotationYear(0); true
            }
        ))
    }

    fun showRotationYear(i: Int) {
        val wp = Store.plot() ?: return
        if (Store.rotation.isEmpty()) return
        Store.rotationIndex = i.coerceIn(0, Store.rotation.lastIndex)
        Store.preview = Store.rotation[Store.rotationIndex].result
        Store.previewArea = com.example.smartgardenplanner.core.PlotShape.effectiveOutline(wp.plot)
        Store.previewMode = PreviewMode.ROTATION
        previewCard()
        App.render()
    }

    // ------------------------------------------------------------------ templates (FR-041)

    /** Copies the plot, like duplicating a browser tab: same site, optionally the plants and the history. */
    fun duplicatePlot() {
        val wp = Store.plot() ?: return
        val name = input(wp.plot.name + " (copy)")
        fun cb(on: Boolean) = (h("input", attrs = mapOf("type" to "checkbox")) as HTMLInputElement).also { it.checked = on }
        val plants = cb(true); val history = cb(true)
        modal("Duplicate “${wp.plot.name}”", listOf(
            para("The copy keeps the plot's size, direction, ZIP, soil, outline, fences, buildings, trees, paths, areas and irrigation, so you can try another plan without touching the original.", "p"),
            label("Name of the copy", name),
            h("label", "check", kids = listOf(plants, h("span", text = "Copy this season's ${wp.plants.size} plants"))),
            h("label", "check", kids = listOf(history, h("span", text = "Copy the history (${com.example.smartgardenplanner.core.Seasons.years(wp.history).size} past seasons), so crop rotation continues")))
        ), listOf(
            "Cancel" to { true },
            "Duplicate" to dup@{
                if (name.value.isBlank()) { App.status("Give the copy a name."); return@dup false }
                val id = Store.newId()
                val copy = WebPlot(
                    wp.plot.copy(id = id, name = name.value.trim(), createdTimestamp = PlatformClock.nowMillis(), lastModifiedTimestamp = PlatformClock.nowMillis()),
                    if (plants.checked) wp.plants.map { it.copy(id = Store.newId(), plotId = id) } else emptyList(),
                    wp.paths.map { it.copy(id = Store.newId(), plotId = id) },
                    wp.features.map { it.copy(id = Store.newId(), plotId = id) },
                    if (history.checked) wp.history.map { it.copy(id = Store.newId(), plotId = id) } else emptyList()
                ).also { it.backdropImage = wp.backdropImage }
                Store.plots += copy
                Store.current = Store.plots.lastIndex
                Store.viewSeason = null; Store.historyYear = null; Canvas.selection = null
                Store.touched()
                App.status("Made “${copy.plot.name}”. The original is unchanged; switch between them with the plot list at the top.")
                App.render(); true
            }
        ))
    }

    // ------------------------------------------------------------------ help

    /** FR-044: shown before first use; the planner is an aid, not a guarantee. */
    fun disclaimer(then: (() -> Unit)? = null) = modal(com.example.smartgardenplanner.core.Disclaimer.TITLE, listOf(
        para(com.example.smartgardenplanner.core.Disclaimer.TEXT, "p"),
        para("You can read this again any time under Help (?).", "hint")
    ), listOf("I understand" to { Prefs.disclaimerAccepted = true; window.setTimeout({ then?.invoke() }, 0); true }))

    fun help() = modal("How to use Smart Garden Planner", listOf(
        para("This page is the whole app: it runs in your browser with no install and no account, and works offline. Your plan is kept in this browser as a draft, but the plan FILE is what you keep and share."),
        heading("Getting started"),
        para("1. New plot: give its size, ZIP code and which way the top edge faces."),
        para("2. Draw what's there: trees, fences, walls, buildings (with a rough height), and paths."),
        para("3. Plant: choose a variety on the Plants tab and click the layout. Or drag an area with “Plan an area for me” and list what you want."),
        para("4. Save: saves a .sgp.json file. Open it on the Android app (Plots → Import) or in this page on any computer."),
        para("5. When the season ends: Plot tab → Start a new season. Fences, buildings, trees and paths stay; this year's plants are kept as history so next year's plan can rotate crops. “Names” shows what each plant is (sweet or hot pepper, cherry or large tomato…)."),
        heading("Moving plans between computer and phone"),
        para("Save the file, then copy it to the phone (USB, email, Google Drive, OneDrive…). On the phone, use Import plan file. To bring phone plots here, use Export plan file on the phone and Open here."),
        heading("Seasons, shade and water"),
        para("Plot tab → Seasons: look back at any past season (read only), Plan next season (rotate) to re-plan the plot with the same crops rotated, or a Rotation plan for up to 10 seasons. Shade: choose the day and “Whole day” or “At a time of day” in the legend; tall plants can cast shade. Water: draw sprinklers, drip lines and hose taps (Plot tab → Irrigation) and turn on Water to see what gets watered. Plants tab → Fill the whole plot… with How many fit?; Plot tab → Duplicate plot… for templates."),
        heading("Pests, watering and the most important plants"),
        para("When you create a plot, tick the pests and animals you see in your yard (deer, rabbits, raccoons…); change them in Plot details. The Care tab shows how to keep each one out (fence heights, netting, buried wire…) and which of your plants they go for, plus watering advice for your plants and irrigation. In “Plan an area for me”, tap ☆ on the plants that matter most: the checks before planning show space, sun, neighbours, zone, rotation and pests, and the starred plants are placed first in the sunniest spots."),
        heading("Satellite photo"),
        para("Plot tab → Satellite photo: open Google Maps in satellite view, take a screenshot of your yard, and add it under the plot. Set the scale with two points a known distance apart, move and turn it to line up, then trace trees, fences and buildings on top."),
        heading("Finding and changing things"),
        para("“On this plot” (bottom-left of the layout) lists what is planted: click a line to circle those plants. Harvest lines on the Food tab do the same. Double-click a plant (or select it and click Edit plant…) to change its variety or planting date, or delete it. With the Plot outline tool, drag the white corners, double-click an edge to add a corner, or click Delete outline."),
        heading("Keyboard"),
        para("Ctrl+Z undo · Ctrl+Y redo · Delete removes the selection · Enter finishes a shape · Esc cancels · mouse wheel zooms · drag empty space to pan."),
        heading("Disclaimer"),
        para(com.example.smartgardenplanner.core.Disclaimer.TEXT),
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

