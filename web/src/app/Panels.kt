package sgp.web

import com.example.smartgardenplanner.core.CarePlanner
import com.example.smartgardenplanner.core.CarePreference
import com.example.smartgardenplanner.core.CropReference
import com.example.smartgardenplanner.core.CropRotation
import com.example.smartgardenplanner.core.VarietyCatalogTraits
import com.example.smartgardenplanner.core.FoodPlanner
import com.example.smartgardenplanner.core.GuildCatalog
import com.example.smartgardenplanner.core.HardinessZones
import com.example.smartgardenplanner.core.HarmonyAnalyzer
import com.example.smartgardenplanner.core.PlantHeights
import com.example.smartgardenplanner.core.PlatformClock
import com.example.smartgardenplanner.core.PlotShape
import com.example.smartgardenplanner.core.RecommendationEngine
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.Severity
import com.example.smartgardenplanner.core.SiteFeatureType
import com.example.smartgardenplanner.core.SoilAnalyzer
import com.example.smartgardenplanner.core.SoilProfile
import com.example.smartgardenplanner.core.fmt
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.js.Date

enum class Tab(val label: String) { PLOT("Plot"), PLANTS("Plants"), HARMONY("Harmony"), CARE("Care"), FOOD("Food") }

/** The side panel: one tab at a time, rebuilt on every render (the data is small). */
object Panels {
    var tab = Tab.PLANTS
    var query = ""
    var typeFilter: String? = null
    private var keepFocus = false

    fun render(host: HTMLElement) {
        host.clear()
        host.add(h("div", "tabs", attrs = mapOf("role" to "tablist"), kids = Tab.entries.map { t ->
            button(t.label, if (t == tab) "tab on" else "tab") { tab = t; App.render() }.also { it.setAttribute("role", "tab") }
        }))
        val body = h("div", "panel-body")
        host.add(body)
        val wp = Store.plot()
        if (wp == null) {
            body.add(para("No plot open."), button("New plot", "btn primary") { Dialogs.plotDetails(null) }, button("Open a plan file…", "btn") { App.openFile() })
            return
        }
        when (tab) {
            Tab.PLOT -> plot(body, wp)
            Tab.PLANTS -> plants(body, wp)
            Tab.HARMONY -> harmony(body, wp)
            Tab.CARE -> care(body, wp)
            Tab.FOOD -> food(body, wp)
        }
    }

        /** Makes a line point out the plants of [species] on the layout when clicked (FR-036). */
    private fun findable(e: HTMLElement, species: String): HTMLElement {
        e.classList.add("findable")
        e.setAttribute("title", "Show where the $species plants are")
        e.on("click") { App.find(species, { n -> Catalog.get(n.seedCode)?.let { CropReference.speciesName(it).equals(species, true) } == true }) }
        return e
    }

    private fun kv(k: String, v: String) = h("div", "kv", kids = listOf(h("span", "k", k), h("span", "v", v)))

    private fun date(ms: Long) = Date(ms.toDouble()).toLocaleDateString()

    // ------------------------------------------------------------------ Plot

    private fun plot(body: HTMLElement, wp: WebPlot) {
        val p = wp.plot
        body.add(heading(p.name).also { it.setAttribute("translate", "no") })
        body.add(kv("Size", "${p.lengthM.fmt(1)} × ${p.widthM.fmt(1)} m (${PlotShape.areaM2(p).fmt(1)} m²)"))
        body.add(kv("Top edge faces", if (p.orientationSet) compassName(p.northBearingDeg) else "not set (assumes north)"))
        body.add(kv("ZIP / zone", listOfNotNull(p.locationZip, p.hardinessZone?.let { "zone $it" }).joinToString(" · ").ifBlank { "not set" }))
        p.latitude?.let { body.add(kv("Latitude", "${it.fmt(2)}°")) }
        body.add(kv("Plants", wp.plants.size.toString()))
        body.add(h("div", "row", kids = listOf(
            button("Edit details…", "btn primary") { Dialogs.plotDetails(p) },
            button("Duplicate…", "btn", "Copy this plot (like duplicating a browser tab), with or without its plants and history") { Dialogs.duplicatePlot() },
            button("Delete plot", "btn danger") { Dialogs.confirm("Delete plot", "Delete “${p.name}” from this plan? (Undo can't bring back a deleted plot, but your saved file still has it.)", "Delete") { Store.removeCurrent(); App.render() } }
        )))
        body.add(h("div", "row wrap", kids = listOf(
            button("Open in Google Maps", "btn") { Photo.openMaps(wp, p.address.orEmpty()) },
            h("span", "hint", p.address?.let { "📍 $it" } ?: "Add the address in Edit details… to open the exact yard")
        )))
        if (!p.orientationSet) body.add(para("Set which way the plot faces so shade and “plan for me” are accurate.", "warn"))

        growingSeason(body, wp)
        Photo.section(body, wp)
        seasons(body, wp)
        irrigation(body, wp)

        body.add(heading("Draw what's on the site"))
        body.add(h("div", "row wrap", kids = listOf(SiteFeatureType.FENCE, SiteFeatureType.WALL, SiteFeatureType.BUILDING).map { t ->
            button(t.label, if (Canvas.tool == Tool.LINE_OBSTACLE && Canvas.obstacleType == t) "btn on" else "btn") { Canvas.obstacleType = t; Canvas.setTool(Tool.LINE_OBSTACLE) }
        } + button("Tree", if (Canvas.tool == Tool.TREE) "btn on" else "btn") { Canvas.setTool(Tool.TREE) }))
        body.add(h("div", "row wrap", kids = SiteFeatureType.entries.filter { it.isArea }.map { t ->
            button(t.label, if (Canvas.tool == Tool.AREA && Canvas.areaType == t) "btn on" else "btn") { Canvas.areaType = t; Canvas.setTool(Tool.AREA) }
        }))
        body.add(h("div", "row wrap", kids = listOf(
            button("Outline (odd shape)", if (Canvas.tool == Tool.OUTLINE) "btn on" else "btn") { Canvas.setTool(Tool.OUTLINE) },
                        button("Delete outline", "btn danger") { Canvas.deleteOutline() }
        )))
        if (wp.features.isNotEmpty()) {
            body.add(heading("On this plot"))
            wp.features.forEach { f ->
                val t = SiteFeatureType.of(f.featureType) ?: return@forEach
                body.add(h("div", "list-item", kids = listOf(
                    h("span", "grow", f.label.ifBlank { t.label } + if (t.isBarrier) " · ${f.heightM.fmt(1)} m" else ""),
                    button("Edit", "btn small") { Dialogs.feature(f, isNew = false) }
                )))
            }
        }
        body.add(heading("Soil"))
        SoilAnalyzer.guidance(SoilProfile.of(p)).forEach { body.add(para(it, "hint")) }

        body.add(heading("Settings (this browser)"))
        body.add(check("Enforce companion rules (keep antagonists apart)", Prefs.enforceCompanions) { Prefs.enforceCompanions = it })
        body.add(check("Guild planting (lets guild members sit together)", Prefs.guilds) { Prefs.guilds = it })
        body.add(label("Spacing margin", select(listOf("0.8" to "Tight (×0.8)", "1.0" to "Standard (×1.0)", "1.2" to "Roomy (×1.2)"), Prefs.margin.toString().let { if (it == "1") "1.0" else it }) { Prefs.margin = it.toFloat(); App.render() }))
        body.add(check("Organic care advice", Prefs.organic) { Prefs.organic = it })
    }

    /** Seasons and crop rotation (FR-032, FR-033, FR-037). */
    private fun seasons(body: HTMLElement, wp: WebPlot) {
        val season = wp.season()
        val years = com.example.smartgardenplanner.core.Seasons.years(wp.history)
        body.add(heading("Seasons & crop rotation"))
        body.add(label("Season shown on the layout", select(
            listOf("" to "$season — planning (you can edit)") + years.map { it.toString() to "$it — look back (read only, ${wp.history.count { h -> h.seasonYear == it }} plants)" },
            Store.viewSeason?.toString() ?: ""
        ) { v -> Store.viewSeason = v.toIntOrNull(); Canvas.selection = null; Store.preview = null; App.render(); App.status(if (Store.viewSeason != null) "Looking back at season ${Store.viewSeason} (read only)." else "Back to planning season $season.") }))
        if (Store.viewSeason != null) {
            body.add(para("You are looking at ${Store.viewSeason}. Nothing can be changed here; choose $season above to plan.", "warn"))
        }
        body.add(para("Fences, buildings, trees, paths, areas and irrigation stay with the plot from year to year. Each finished season is kept as history and used for crop rotation.", "hint"))
        body.add(h("div", "row wrap", kids = listOf(
            button("Plan next season (rotate)…", "btn primary", "Re-plan the whole plot for next year with the same crops, rotated so nothing goes where its family grew") {
                Dialogs.planForMe(com.example.smartgardenplanner.core.PlotShape.effectiveOutline(wp.plot), PreviewMode.NEXT_SEASON)
            },
            button("Rotation plan for several seasons…", "btn", "Plan 1 to 30 years ahead with crop rotation and look at them one year at a time; you can swap varieties from any year on") { Dialogs.rotationPlan() },
            button("Start a new season (empty)…", "btn", "Move this season's plants into the history and start with an empty plot") { Dialogs.newSeason() }.also { if (wp.plants.isEmpty()) it.setAttribute("disabled", "") }
        )))
        if (years.isNotEmpty()) {
            body.add(label("Also show a past season faintly while planning", select(listOf("" to "None") + years.map { it.toString() to it.toString() }, Store.historyYear?.toString() ?: "") {
                Store.historyYear = it.toIntOrNull(); App.render()
            }))
            years.forEach { y ->
                val list = wp.history.filter { it.seasonYear == y }
                body.add(h("details", "pest", kids = listOf(
                    h("summary", text = "$y — ${list.size} plants"),
                    para(list.groupBy { it.speciesName }.entries.sortedByDescending { it.value.size }.joinToString(", ") { "${it.value.size} × ${it.key}" }, "hint"),
                    *com.example.smartgardenplanner.core.RotationPlanner.summary(wp.plot, list).map { para("• $it", "hint") }.toTypedArray()
                )))
            }
        }
        CropRotation.advice(wp.plot, wp.history, wp.plants, { Catalog.get(it) }, season).forEach { body.add(para(it, if (it.startsWith("⚠")) "warn" else "hint")) }
        body.add(heading("Templates"))
        body.add(para("Duplicate this plot to try another plan or keep a clean template: the copy carries the site and, if you like, the plants and history.", "hint"))
        body.add(button("Duplicate plot…", "btn") { Dialogs.duplicatePlot() })
    }

    /** FR-054: frost dates and growing season for the plot's location (NOAA 1991–2020 normals). */
    private fun growingSeason(body: HTMLElement, wp: WebPlot) {
        body.add(heading("Growing season"))
        val s = Frost.season(wp.plot)
        if (s == null) {
            body.add(para(if (wp.plot.latitude == null) "Set the plot's ZIP code (Edit details…) to see its frost dates and growing season." else "No NOAA weather station within 250 km of this plot, so frost dates aren't known.", "hint"))
            return
        }
        com.example.smartgardenplanner.core.GrowingSeason.describe(s).forEachIndexed { i, t -> body.add(para(t, if (i < 3) "p" else "hint")) }
        body.add(button("Planting calendar…", "btn", "When to start seeds indoors, plant out and sow a fall crop for each plant, from your frost dates") { Dialogs.plantingCalendar() })
    }

    /** Irrigation tools and summary (FR-039). */
    private fun irrigation(body: HTMLElement, wp: WebPlot) {
        body.add(heading("Irrigation"))
        body.add(h("div", "row wrap", kids = listOf(SiteFeatureType.SPRINKLER, SiteFeatureType.DRIP_LINE, SiteFeatureType.HOSE_BIB).map { t ->
            button(t.label, if (Canvas.tool == Tool.WATER && Canvas.waterType == t) "btn on" else "btn") { Canvas.toggleWater(t) }
        } + button(if (Store.showWater) "Water map: on" else "Water map: off", if (Store.showWater) "btn on" else "btn") { Store.showWater = !Store.showWater; App.render() }))
        body.add(para("Draw where your sprinklers, drip lines or soaker hoses and hose taps are. The water map shows what each reaches; plants circled in red need a watering can.", "hint"))
    }

    private fun check(text: String, value: Boolean, onChange: (Boolean) -> Unit): HTMLElement {
        val cb = h("input", attrs = mapOf("type" to "checkbox")) as HTMLInputElement
        cb.checked = value
        cb.on("change") { onChange(cb.checked); App.render() }
        return h("label", "check", kids = listOf(cb, h("span", text = text)))
    }

    // ------------------------------------------------------------------ Plants

    private fun plants(body: HTMLElement, wp: WebPlot) {
        val ctx = Store.context(wp)
        Canvas.activeSeed?.let { s ->
            body.add(h("div", "active", kids = listOf(
                h("span", "dot", attrs = mapOf("style" to "background:${Colors.of(s)}")),
                h("div", "grow", kids = listOfNotNull(h("b", text = s.commonName), VarietyCatalogTraits.of(s)?.let { h("div", "kind", it.details) }, h("div", "hint", seedLine(s)))),
            )))
            HardinessZones.describe(s, wp.plot.hardinessZone)?.let { body.add(para(it, "warn")) }
            if (s.careNotes.isNotBlank()) body.add(para(s.careNotes, "hint"))
        }
        body.add(h("div", "row", kids = listOf(
            button("Plant tool", if (Canvas.tool == Tool.PLANT) "btn on" else "btn") { Canvas.setTool(Tool.PLANT) },
                        button("Plan an area for me", if (Canvas.tool == Tool.PLAN) "btn on" else "btn primary") { Canvas.setTool(Tool.PLAN) },
            button("Fill the whole plot…", "btn", "List what you want this year; the planner fills the plot (use How many fit? for the numbers)") { Dialogs.planForMe(com.example.smartgardenplanner.core.PlotShape.effectiveOutline(wp.plot)) }
        )))
        val search = input(query, "search", "Search ${Catalog.seeds.size} varieties…")
        search.on("input") { query = search.value; keepFocus = true; App.render() }
        body.add(search)
        if (keepFocus) { keepFocus = false; kotlinx.browser.window.setTimeout({ search.focus(); search.setSelectionRange(search.value.length, search.value.length) }, 0) }
        body.add(select(listOf("" to "All types") + Catalog.types.map { it to it.lowercase().replaceFirstChar { c -> c.uppercase() } }, typeFilter ?: "") { typeFilter = it.ifBlank { null }; App.render() })

        if (query.isBlank() && typeFilter == null) {
            body.add(heading("Suggested for this plot"))
            RecommendationEngine.recommend(Catalog.seeds, ctx, null, limit = 10).forEach { r ->
                body.add(seedItem(r.seed, ctx, r.reasons.take(2).joinToString(" · ")))
            }
        } else {
            val results = Catalog.search(query, typeFilter, 120)
            body.add(para("${results.size}${if (results.size == 120) "+" else ""} matches", "hint"))
            results.forEach { body.add(seedItem(it, ctx, null)) }
        }
    }

    private fun seedLine(s: SeedEntity) =
        "${s.plantType.lowercase()} · ${s.lifecycle.lowercase()} · ${PlantHeights.heightM(s).fmt(1)} m tall · spacing ${(s.exclusionRadiusM * 2).fmt(2)} m · ${s.daysToHarvest} days"

    private fun seedItem(s: SeedEntity, ctx: com.example.smartgardenplanner.core.PlotContext, why: String?): HTMLElement {
        val conflict = RecommendationEngine.conflictReason(s, ctx)
        val guild = if (Prefs.guilds) GuildCatalog.guildsFor(s, Store.guildsActive()).firstOrNull()?.name else null
        val item = h("button", if (Canvas.activeSeed?.botanicalCode == s.botanicalCode) "seed on" else if (conflict != null) "seed dim" else "seed", attrs = mapOf("type" to "button"), kids = listOf(
            h("span", "dot", attrs = mapOf("style" to "background:${Colors.of(s)}")),
            h("span", "grow", kids = listOfNotNull(
                h("span", "name", s.commonName),
                VarietyCatalogTraits.of(s)?.let { h("span", "kind", it.details) },
                h("span", "hint", conflict ?: why ?: seedLine(s)),
                guild?.let { h("span", "badge", it) }
            ))
        ))
        item.on("click") { Canvas.activeSeed = s; if (Canvas.tool != Tool.PLANT) Canvas.setTool(Tool.PLANT) else App.render() }
        return item
    }

    // ------------------------------------------------------------------ Harmony

    private fun harmony(body: HTMLElement, wp: WebPlot) {
        val r = HarmonyAnalyzer.analyze(Store.context(wp), Catalog.seeds, Prefs.margin)
        body.add(heading("Harmony score: ${r.score} / 100"))
        if (r.plantCounts.isEmpty()) body.add(para("Nothing planted yet.", "hint"))
                r.plantCounts.forEach { (n, c) -> body.add(findable(kv(n, c.toString()), n)) }
        if (r.issues.isNotEmpty()) body.add(heading("To look at"))
        r.issues.forEach { i -> body.add(h("div", "issue ${i.severity.name.lowercase()}", kids = listOf(h("b", text = i.severity.label + ": "), h("span", text = i.text)))) }
        if (r.goodPairs.isNotEmpty()) { body.add(heading("Good neighbors")); r.goodPairs.forEach { body.add(para("✓ $it", "hint")) } }
        if (r.recommendations.isNotEmpty()) { body.add(heading("Ideas")); r.recommendations.forEach { body.add(para("• $it", "hint")) } }
        if (r.issues.none { it.severity == Severity.HIGH } && r.plantCounts.isNotEmpty()) body.add(para("No serious problems found.", "ok"))
    }

    // ------------------------------------------------------------------ Care

    private fun care(body: HTMLElement, wp: WebPlot) {
        val ctx = Store.context(wp)
        val pref = if (Prefs.organic) CarePreference.ORGANIC else CarePreference.CONVENTIONAL
        body.add(para(com.example.smartgardenplanner.core.Disclaimer.SHORT, "hint disclaimer"))
        wildlife(body, wp)
        watering(body, wp)
        if (wp.plants.isEmpty()) { body.add(para("Plant something to see a watering schedule, feeding plan and plant pests to watch.", "hint")); return }
        whenToPlant(body, wp)
        planB(body, wp)
        body.add(heading("Watering schedule"))
        val days = CarePlanner.wateringIntervalDays(ctx)
        body.add(para("Deep-water about every $days day${if (days == 1) "" else "s"} (thirstiest crop, adjusted for your soil). Water at the base in the morning; skip after a good rain.", "p"))
        CarePlanner.dueTasks(ctx, emptyList(), pref, PlatformClock.nowMillis(), null, 10.0).forEach { t ->
            body.add(kv(t.title, date(t.dueEpochMillis)))
        }
        body.add(heading("Feeding plan (${if (Prefs.organic) "organic" else "conventional"})"))
        CarePlanner.fertilizingPlan(ctx, pref).forEach { e ->
            body.add(h("div", "list-item col", kids = listOf(h("b", text = date(e.dueEpochMillis) + " · " + e.species.joinToString(", ")), h("span", "hint", e.action))))
        }
        val pests = CarePlanner.pestPlan(ctx, pref)
        if (pests.isNotEmpty()) body.add(heading("Pests and diseases to watch"))
        pests.forEach { a ->
            body.add(h("details", "pest", kids = listOf(
                h("summary", text = "${a.pest} — ${a.affects.joinToString(", ")}"),
                para("Look for: ${a.scouting}", "hint"), para("Prevent: ${a.prevention}", "hint"), para("Treat: ${a.control}", "hint")
            )))
        }
    }

    /** FR-054: planting windows for what's planted, from the plot's frost dates. */
    private fun whenToPlant(body: HTMLElement, wp: WebPlot) {
        val s = Frost.season(wp.plot) ?: return
        body.add(heading("When to plant (your frost dates)"))
        body.add(para("Last frost around ${com.example.smartgardenplanner.core.GrowingSeason.date(s.lastFrost)}, first frost around ${com.example.smartgardenplanner.core.GrowingSeason.date(s.firstFrost)} (${s.frostFreeDays} days).", "hint"))
        wp.plants.mapNotNull { Catalog.get(it.seedCode) }.distinctBy { it.botanicalCode }.forEach { sd ->
            val w = com.example.smartgardenplanner.core.GrowingSeason.window(sd, s)
            body.add(h("div", "list-item col", kids = listOf(h("b", text = sd.commonName), h("span", "hint", com.example.smartgardenplanner.core.GrowingSeason.describeWindow(w) + " — " + w.note))))
        }
    }

    /** FR-056: Plan B — faster varieties to keep in mind in case plants are lost. */
    private fun planB(body: HTMLElement, wp: WebPlot) {
        val ahead = com.example.smartgardenplanner.core.BackupPlanner.planAhead(wp.plants.mapNotNull { Catalog.get(it.seedCode) }, Catalog.seeds, Frost.season(wp.plot))
        body.add(heading("Plan B: if a plant dies"))
        body.add(para("Double-click a plant that died (or select it → Edit plant…) and choose “Plan B…”: it suggests varieties that, planted today, will be ready with the plants that survived and before the first frost, so the harvest stays as planned.", "hint"))
        if (ahead.isNotEmpty()) body.add(h("details", "pest", kids = listOf(h("summary", text = "Faster varieties to keep seed of")) + ahead.map { (sp, list) ->
            para("$sp: " + list.joinToString(", ") { "${it.commonName.substringAfter(" - ")} (${it.daysToHarvest} days)" }, "hint")
        }))
    }

    /** FR-042: pests and animals the user sees in the yard, with prevention and the plants at risk. */
    private fun wildlife(body: HTMLElement, wp: WebPlot) {
        val chosen = com.example.smartgardenplanner.core.Pest.parse(wp.plot.pests)
        body.add(heading("Pests and animals in your yard"))
        body.add(para("Tick what you see regularly; the tips below change to match.", "hint"))
        body.add(h("div", "chips", kids = com.example.smartgardenplanner.core.Pest.entries.map { p ->
            val on = p in chosen
            button(p.label, if (on) "chip on" else "chip", if (on) "Seen in my yard (click to remove)" else "Click if you see this in your yard") {
                val next = if (on) chosen - p else chosen + p
                Store.change { it.plot = it.plot.copy(pests = com.example.smartgardenplanner.core.Pest.encode(next)) }
                App.render()
            }
        }))
        val seeds = wp.plants.mapNotNull { Catalog.get(it.seedCode) }
        val risks = com.example.smartgardenplanner.core.PestAdvisor.risks(chosen, seeds)
        chosen.forEach { p ->
            val atRisk = risks.first { it.pest == p }.atRisk
            val summary = p.label + when {
                atRisk.isNotEmpty() -> " — goes for your ${atRisk.take(5).joinToString(", ")}"
                p.targets.isEmpty() -> " — can damage most plants"
                else -> ""
            }
            body.add(h("details", "pest", attrs = mapOf("open" to ""), kids = listOf(
                h("summary", text = summary),
                para("Signs: ${p.signs}", "hint")
            ) + p.tips.map { para("• $it", "hint") }))
        }
        if (chosen.isEmpty()) body.add(para("None ticked yet. Deer, rabbits, groundhogs, raccoons and squirrels are the most common garden visitors in North America.", "hint"))
        body.add(h("details", "pest", kids = listOf(h("summary", text = "Keeping animals out: general tips")) + com.example.smartgardenplanner.core.Pest.GENERAL_TIPS.map { para("• $it", "hint") }))
    }

    /** FR-045: watering recommendations, irrigation coverage and the tools to draw sprinklers, drip lines and hoses. */
    private fun watering(body: HTMLElement, wp: WebPlot) {
        body.add(heading("Watering and irrigation"))
        body.add(para("Draw your sprinklers, drip lines or soaker hoses and hose taps, then turn on the water map to see which areas get wet and which need watering by hand.", "hint"))
        body.add(h("div", "row wrap", kids = listOf(SiteFeatureType.SPRINKLER, SiteFeatureType.DRIP_LINE, SiteFeatureType.HOSE_BIB).map { t ->
            button("+ " + t.label, if (Canvas.tool == Tool.WATER && Canvas.waterType == t) "btn on" else "btn") { Canvas.toggleWater(t) }
        } + button(if (Store.showWater) "Water map: on" else "Water map: off", if (Store.showWater) "btn on" else "btn") { Store.showWater = !Store.showWater; App.render() }))
        val lookup = { c: String -> Catalog.get(c) }
        val specific = com.example.smartgardenplanner.core.WateringAdvice.forPlot(wp.plot, wp.plants, wp.features, lookup)
        if (wp.features.any { SiteFeatureType.of(it.featureType)?.isIrrigation == true }) {
            com.example.smartgardenplanner.core.Irrigation.report(wp.plot, wp.plants, wp.features, lookup).forEach { body.add(para(it, if (it.startsWith("⚠")) "warn" else "hint")) }
        }
        specific.forEach { body.add(para("→ $it", "p")) }
        val seeds = wp.plants.mapNotNull { Catalog.get(it.seedCode) }.distinctBy { CropReference.speciesKey(it) }
        if (seeds.isNotEmpty()) body.add(h("details", "pest", kids = listOf(h("summary", text = "How much water your plants need")) +
            seeds.sortedBy { CropReference.forSeed(it).waterIntervalDays }.map { s -> para("${CropReference.speciesName(s)}: ${com.example.smartgardenplanner.core.WateringAdvice.needLabel(s)}", "hint") }))
        body.add(h("details", "pest", kids = listOf(h("summary", text = "Watering tips")) + com.example.smartgardenplanner.core.WateringAdvice.GENERAL.map { para("• $it", "hint") }))
        body.add(h("details", "pest", kids = listOf(h("summary", text = "Choosing sprinklers, drip or hose")) + com.example.smartgardenplanner.core.WateringAdvice.SYSTEMS.map { para("• $it", "hint") }))
    }

    // ------------------------------------------------------------------ Food

    private fun food(body: HTMLElement, wp: WebPlot) {
        val ctx = Store.context(wp)
        val lines = FoodPlanner.yieldLines(ctx)
        body.add(heading("Expected harvest"))
        if (lines.isEmpty()) body.add(para("No food crops planted yet.", "hint"))
                if (lines.isNotEmpty()) body.add(para("Click a line to see where those plants are.", "hint"))
        lines.forEach { l -> body.add(findable(kv("${l.species} × ${l.plants}", "${l.totalKg.fmt(1)} kg"), l.species)) }
        if (lines.isNotEmpty()) {
            val t = FoodPlanner.nutritionTotals(ctx)
            body.add(heading("What that feeds (one adult)"))
            body.add(kv("Calories", "${t.kcalDays.fmt(0)} days"))
            body.add(kv("Protein", "${t.proteinDays.fmt(0)} days"))
            body.add(kv("Vitamin C", "${t.vitaminCDays.fmt(0)} days"))
            body.add(kv("Vitamin A", "${t.vitaminADays.fmt(0)} days"))
            val keys = ctx.plantedSeeds().map { CropReference.speciesKey(it) }.toSet()
            val matches = FoodPlanner.recipeMatches(keys).take(6)
            if (matches.isNotEmpty()) body.add(heading("Recipes from your garden"))
            matches.forEach { m ->
                body.add(h("details", "pest", kids = listOf(
                    h("summary", text = m.recipe.name + if (m.missing.isEmpty()) " ✓" else " (missing ${m.missing.joinToString(", ")})"),
                    para("Pantry: ${m.recipe.pantry}", "hint"), para(m.recipe.method, "hint")
                )))
            }
        }
        body.add(heading("Homestead starter list"))
        val household = input(Prefs.household.toString(), "number").also { it.setAttribute("min", "1"); it.setAttribute("max", "20") }
        household.on("change") { Prefs.household = household.value.toIntOrNull()?.coerceIn(1, 20) ?: 4; App.render() }
        body.add(label("People in the household", household))
        FoodPlanner.homesteadList(Catalog.seeds, ctx.zone, ctx.soil, Prefs.household).forEach { i ->
            val item = h("button", "seed", attrs = mapOf("type" to "button"), kids = listOf(
                h("span", "dot", attrs = mapOf("style" to "background:${Colors.of(i.seed)}")),
                h("span", "grow", kids = listOf(h("span", "name", "${i.plants} × ${i.seed.commonName}"), h("span", "hint", "${i.role.label} · ~${i.expectedKg.fmt(0)} kg · ${i.note}")))
            ))
            item.on("click") { Canvas.activeSeed = i.seed; tab = Tab.PLANTS; Canvas.setTool(Tool.PLANT) }
            body.add(item)
        }
    }
}
