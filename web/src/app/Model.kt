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
import com.example.smartgardenplanner.core.PlotPoint
import com.example.smartgardenplanner.core.SiteFeatureEntity
import com.example.smartgardenplanner.core.SunlightEngine
import kotlinx.browser.window

const val WEB_VERSION = "1.0"

data class Snap(val plot: PlotEntity, val plants: List<PlantedNodeEntity>, val paths: List<PathZoneEntity>, val features: List<SiteFeatureEntity>)

/** One plot being edited, with its own undo/redo history (every change is one step, as on the phone). */
class WebPlot(var plot: PlotEntity, plants: List<PlantedNodeEntity>, paths: List<PathZoneEntity>, features: List<SiteFeatureEntity>) {
    var plants = plants
    var paths = paths
    var features = features
    val undo = ArrayDeque<Snap>()
    val redo = ArrayDeque<Snap>()
    fun snap() = Snap(plot, plants, paths, features)
    fun restore(s: Snap) { plot = s.plot; plants = s.plants; paths = s.paths; features = s.features }
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
    private var nextId = 1L

    fun newId(): Long = nextId++
    fun plot(): WebPlot? = plots.getOrNull(current)

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

    fun encode(): String = PlanFileCodec.encode(
        PlanBundle(plots.map { PlanPlot(it.plot, it.plants, it.paths, it.features) }, emptyList(), PlatformClock.nowMillis()),
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
                pp.paths.map { it.copy(id = newId()) }, pp.features.map { it.copy(id = newId()) }
            )
        }
        if (unknown > 0) messages += "$unknown plant(s) use varieties this planner doesn't know and were skipped."
        current = if (plots.isEmpty()) -1 else 0
        fileName = name
        dirty = false
        preview = null
        autosave()
        return messages
    }

    fun autosave() {
        try { window.localStorage.setItem("sgp.draft", encode()); window.localStorage.setItem("sgp.draftName", fileName) } catch (e: Throwable) {}
    }

    fun draft(): Pair<String, String>? = try {
        val t = window.localStorage.getItem("sgp.draft")
        if (t.isNullOrBlank()) null else t to (window.localStorage.getItem("sgp.draftName") ?: "my-garden.sgp.json")
    } catch (e: Throwable) { null }
}
