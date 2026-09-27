package com.example.smartgardenplanner.data

import com.example.smartgardenplanner.core.AppSettings
import com.example.smartgardenplanner.core.Feature
import com.example.smartgardenplanner.core.GuildCatalog
import com.example.smartgardenplanner.core.PlotContext
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.SunlightEngine
import com.example.smartgardenplanner.core.currentAppTier

/** Everything the plot insights screen and the reminder worker need, read in one go. */
data class PlotSnapshot(
    val context: PlotContext,
    val catalog: List<SeedEntity>,
    val history: List<com.example.smartgardenplanner.core.PlantingHistoryEntity> = emptyList() // FR-033
)

object PlotInsightsLoader {

    /** Guilds are active only when the user switched them on and the tier allows it (FR-009, Pro). */
    fun activeGuilds(settings: AppSettings) =
        if (settings.guildsEnabled && Feature.isEnabled(Feature.INTERPLANTING_GUILDS, settings.currentAppTier())) GuildCatalog.ALL else emptyList()

    /** Same tier rule as the canvas: below Pro, companion rules are always enforced (FR-012). */
    fun enforceRules(settings: AppSettings) =
        if (Feature.isEnabled(Feature.COMPANION_RULE_TOGGLE, settings.currentAppTier())) settings.enforceCompanionAntagonistRules else true

    suspend fun load(database: AppDatabase, plotId: Long, settings: AppSettings): PlotSnapshot? {
        val plot = database.plotDao().getById(plotId) ?: return null
        val nodes = database.plantedNodeDao().getByPlotId(plotId)
        val features = database.siteFeatureDao().getByPlotId(plotId)
        val catalog = database.seedDao().getAllSeeds()
        val byCode = catalog.associateBy { it.botanicalCode }
        val context = PlotContext(
            plot = plot,
            nodes = nodes,
            features = features,
            seedLookup = { code -> byCode[code] },
            guilds = activeGuilds(settings),
            enforceCompanionRules = enforceRules(settings),
            dayOfYear = SunlightEngine.dayOfYear(System.currentTimeMillis())
        )
                return PlotSnapshot(context, catalog, database.plantingHistoryDao().getByPlotId(plotId))
    }
}
