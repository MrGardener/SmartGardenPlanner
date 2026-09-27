package sgp.web

import com.example.smartgardenplanner.core.AutoPlanResult
import com.example.smartgardenplanner.core.GuildCatalog
import com.example.smartgardenplanner.core.PathZoneEntity
import com.example.smartgardenplanner.core.PlanBundle
import com.example.smartgardenplanner.core.PlanFileCodec
import com.example.smartgardenplanner.core.PlanPlot
import com.example.smartgardenplanner.core.PlantedNodeEntity
import com.example.smartgardenplanner.core.PlatformClock
import com.example.smartgardenplanner.core.PlotContext
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.PlantingHistoryEntity
import com.example.smartgardenplanner.core.PlantingLayout
import com.example.smartgardenplanner.core.PlotPoint
import com.example.smartgardenplanner.core.Seasons
import com.example.smartgardenplanner.core.SiteFeatureEntity
import com.example.smartgardenplanner.core.SunlightEngine
import kotlinx.browser.window

const val WEB_VERSION = "1.0"

enum class PreviewMode { NORMAL, NEXT_SEASON, ROTATION }

data class Snap(val plot: PlotEntity, val plants: List<PlantedNodeEntity>, val paths: List<PathZoneEntity>, val features: List<SiteFeatureEntity>, val history: List<PlantingHistoryEntity>, val backdropImage: String?)

/** One plot being edited, with its own undo/redo history (every change is one step, as on the phone). */
class WebPlot(var plot: PlotEntity, plants: List<PlantedNodeEntity>, paths: List<PathZoneEntity>, features: List<SiteFeatureEntity>, history: List<PlantingHistoryEntity> = emptyList()) {
    var plants = plants
    var paths = paths
    var features = features
    /** Plants of finished seasons (FR-033). */
    var history = history
    /** Satellite photo under the plot (FR-046), a data URL; its placement is plot.backdropJson. */
    var backdropImage: String? = null
    val undo = ArrayDeque<Snap>()
    val redo = ArrayDeque<Snap>()
    fun snap() = Snap(plot, plants, paths, features, history, backdropImage)
    fun restore(s: Snap) { plot = s.plot; plants = s.plants; paths = s.paths; features = s.features; history = s.history; backdropImage = s.backdropImage }
    /** The season being planned (FR-033). */
    fun season(): Int = Seasons.currentSeason(plants, history)
}

/** User preferences kept in this browser only. */
object Prefs {
    private fun get(key: String): String? = try { window.localStorage.getItem("sgp.$key") } catch (e: Throwable) { null }
    private fun set(key: String, v: String) { try { window.localStorage.setItem("sgp.$key", v) } catch (e: Throwable) {} }
    var guilds: Boolean
        get() = get("guilds") == "1"
        set(v) = set("guilds", if (v) "1" else "0")
    var enforceCompanions: Boolean
        get() = get("companions") != "0"
        set(v) = set("companions", if (v) "1" else "0")
    var margin: Float
        get() = get("margin")?.toFloatOrNull() ?: 1f
        set(v) = set("margin", v.toString())
    var household: Int
        get() = get("household")?.toIntOrNull() ?: 4
        set(v) = set("household", v.toString())
    /** "auto" (follow the system), "light" or "dark". */
    var theme: String
        get() = get("theme")?.takeIf { it == "light" || it == "dark" } ?: "auto"
        set(v) = set("theme", v)
    /** Clumps or rows for "Plan an area for me" (FR-032). */
    var layout: PlantingLayout
        get() = PlantingLayout.entries.firstOrNull { it.name == get("layout") } ?: PlantingLayout.CLUMPS
        set(v) = set("layout", v.name)
    /** The last "Plan an area for me" list, "CODE:count,CODE:count" (FR-034). */
    var lastPlan: List<Pair<String, Int>>
        get() = get("lastPlan").orEmpty().split(",").mapNotNull { e -> e.split(":").takeIf { it.size == 2 }?.let { (c, n) -> n.toIntOrNull()?.let { c to it } } }
        set(v) = set("lastPlan", v.joinToString(",") { "${it.first}:${it.second}" })
    var showLabels: Boolean
        get() = get("labels") != "0"
        set(v) = set("labels", if (v) "1" else "0")
    /** The disclaimer was read and accepted in this browser (FR-044). */
    var disclaimerAccepted: Boolean
        get() = get("disclaimer") == "1"
        set(v) = set("disclaimer", if (v) "1" else "0")
    /** Where the "On this plot" box was dragged to (pixels from the layout's top-left), or null for the corner. */
    var legendPos: Pair<Int, Int>?
        get() = get("legendPos")?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
        set(v) { if (v == null) set("legendPos", "") else set("legendPos", "${v.first},${v.second}") }
    var organic: Boolean
        get() = get("care") != "CONVENTIONAL"
        set(v) = set("care", if (v) "ORGANIC" else "CONVENTIONAL")
}

/** Everything open in the planner: the plots of one plan file. */
object Store {
    val plots = mutableListOf<WebPlot>()
    var current = -1
    var fileName = "my-garden.sgp.json"
    var fileHandle: dynamic = null
    var dirty = false
    var preview: AutoPlanResult? = null
    var previewArea: List<PlotPoint>? = null
    /** The rows of the plan being edited (variety code, count); kept when a proposal is discarded (FR-034). */
    val planRows = mutableListOf<Pair<String, Int>>()
    /** Varieties marked "most important" in that list (FR-043). */
    val planPriority = mutableSetOf<String>()
    /** Clump arrangements chosen in that list (FR-047): variety code → plants per row. */
    val planShapes = mutableMapOf<String, List<Int>>()
    /** True when the browser draft had to be kept without the satellite photos (not enough browser storage). */
    var draftWithoutPhotos = false
        /** Past season shown faintly under this season's plants (null = none). */
    var historyYear: Int? = null
    /** Past season shown instead of this season, read-only (FR-037); null = the season being planned. */
    var viewSeason: Int? = null
    /** What "Keep this plan" does with the proposal (FR-037). */
    var previewMode = PreviewMode.NORMAL
    /** A multi-season rotation plan being looked at, and which year is on the layout. */
    var rotation: List<com.example.smartgardenplanner.core.SeasonPlan> = emptyList()
    var rotationIndex = 0
    /** What the rotation plan was made from, and the user's variety changes: year → (from code → to code) (FR-048). */
    var rotationBase: List<com.example.smartgardenplanner.core.PlantRequest> = emptyList()
    var rotationFirstYear = 0
    var rotationSeasons = 5
    val rotationChanges = mutableMapOf<Int, MutableMap<String, String>>()
    /** Shade display (FR-038): which day, whole day (null) or a solar hour, and whether plants cast shade. */
    var shadeDay = com.example.smartgardenplanner.core.ShadeDay.TODAY
    var shadeHour: Double? = null
    var shadePlants = true
    /** Irrigation overlay (FR-039). */
    var showWater = false
    private var nextId = 1L

    fun newId(): Long = nextId++
    fun plot(): WebPlot? = plots.getOrNull(current)

    /** Varieties this gardener plants most, from every open plot and its history (FR-034). */
    fun usual(limit: Int = 8) = Seasons.usualVarieties(plots.flatMap { p -> p.plants.map { it.seedCode } }, plots.flatMap { p -> p.history.map { it.seedCode } }, { Catalog.get(it) }, limit)

    fun guildsActive() = if (Prefs.guilds) GuildCatalog.ALL else emptyList()

    fun context(p: WebPlot): PlotContext = PlotContext(
        p.plot, p.plants, p.features, { Catalog.get(it) }, guildsActive(), Prefs.enforceCompanions,
        SunlightEngine.dayOfYear(PlatformClock.nowMillis())
    )

    /** Applies one change to the current plot as one undo step. */
    fun change(update: (WebPlot) -> Unit) {
        val p = plot() ?: return
        p.undo.addLast(p.snap())
        while (p.undo.size > 100) p.undo.removeFirst()
        p.redo.clear()
        update(p)
        p.plot = p.plot.copy(lastModifiedTimestamp = PlatformClock.nowMillis())
        touched()
    }

    fun undo(): Boolean {
        val p = plot() ?: return false
        val s = p.undo.removeLastOrNull() ?: return false
        p.redo.addLast(p.snap()); p.restore(s); touched(); return true
    }

    fun redo(): Boolean {
        val p = plot() ?: return false
        val s = p.redo.removeLastOrNull() ?: return false
        p.undo.addLast(p.snap()); p.restore(s); touched(); return true
    }

    fun touched() { dirty = true; autosave() }

    fun addPlot(plot: PlotEntity) {
        plots += WebPlot(plot.copy(id = newId()), emptyList(), emptyList(), emptyList())
        current = plots.size - 1
        touched()
    }

    fun removeCurrent() {
        if (current < 0) return
        plots.removeAt(current)
        current = if (plots.isEmpty()) -1 else 0
        touched()
    }

    fun encode(withPhotos: Boolean = true): String = PlanFileCodec.encode(
        PlanBundle(plots.map { PlanPlot(it.plot, it.plants, it.paths, it.features, it.history, if (withPhotos) it.backdropImage else null) }, emptyList(), PlatformClock.nowMillis()),
        { Catalog.get(it) }, "web $WEB_VERSION"
    )

    /** Loads a plan file (replacing what's open). Returns messages for the user; empty plots list means failure. */
    fun load(text: String, name: String): List<String> {
        val decoded = PlanFileCodec.decode(text)
        val bundle = decoded.bundle ?: return decoded.errors
        val messages = decoded.warnings.toMutableList()
        bundle.customVarieties.forEach { Catalog.addCustom(it) }
        var unknown = 0
        plots.clear()
        for (pp in bundle.plots) {
            val plants = pp.plants.mapNotNull { n ->
                val code = if (Catalog.get(n.seedCode) != null) n.seedCode
                else decoded.varietyNames[n.seedCode]?.lowercase()?.let { Catalog.byName[it]?.botanicalCode }
                if (code == null) { unknown++; null } else n.copy(id = newId(), plotId = 0, seedCode = code)
            }
            plots += WebPlot(
                pp.plot.copy(id = newId()), plants,
                pp.paths.map { it.copy(id = newId()) }, pp.features.map { it.copy(id = newId()) },
                pp.history.map { it.copy(id = newId()) }
            ).also { it.backdropImage = pp.backdropImage }
        }
        if (unknown > 0) messages += "$unknown plant(s) use varieties this planner doesn't know and were skipped."
        current = if (plots.isEmpty()) -1 else 0
                fileName = name
        dirty = false
        preview = null
        previewMode = PreviewMode.NORMAL; rotation = emptyList(); viewSeason = null; historyYear = null
        autosave()
        return messages
    }

    fun autosave() {
        try {
            window.localStorage.setItem("sgp.draftName", fileName)
            try { window.localStorage.setItem("sgp.draft", encode()); draftWithoutPhotos = false }
            catch (e: Throwable) {
                // Photos can be bigger than the browser allows; keep everything else, and the file keeps the photos.
                window.localStorage.setItem("sgp.draft", encode(withPhotos = false)); draftWithoutPhotos = plots.any { it.backdropImage != null }
            }
        } catch (e: Throwable) {}
    }

    fun draft(): Pair<String, String>? = try {
        val t = window.localStorage.getItem("sgp.draft")
        if (t.isNullOrBlank()) null else t to (window.localStorage.getItem("sgp.draftName") ?: "my-garden.sgp.json")
    } catch (e: Throwable) { null }
}
