package com.example.smartgardenplanner.data

import androidx.room.withTransaction
import com.example.smartgardenplanner.core.PlanBundle
import com.example.smartgardenplanner.core.PlanFileCodec
import com.example.smartgardenplanner.core.PlanPlot

/** Result of an import, in words the user can read. */
data class ImportReport(val plotsImported: Int, val plantsImported: Int, val messages: List<String>, val firstPlotId: Long?)

/**
 * Export and import of Smart Garden plan files (FR-029, T2-DAT-110). Export reads plots from the database
 * and encodes them; import decodes, validates, and stores everything as NEW plots in one transaction, so a
 * failure leaves the database unchanged. Existing plots are never overwritten.
 */
class PlanFileRepository(private val database: AppDatabase) {

    suspend fun export(plotIds: List<Long>, appVersion: String): String {
        val seeds = database.seedDao().getAllSeeds().associateBy { it.botanicalCode }
        val plots = plotIds.mapNotNull { id ->
            val plot = database.plotDao().getById(id) ?: return@mapNotNull null
            PlanPlot(
                plot,
                database.plantedNodeDao().getByPlotId(id),
                database.pathZoneDao().getByPlotId(id),
                database.siteFeatureDao().getByPlotId(id),
                database.plantingHistoryDao().getByPlotId(id)
            )
        }
        return PlanFileCodec.encode(PlanBundle(plots, emptyList(), System.currentTimeMillis()), { seeds[it] }, appVersion)
    }

    suspend fun import(text: String): ImportReport {
        val decoded = PlanFileCodec.decode(text)
        val bundle = decoded.bundle ?: return ImportReport(0, 0, decoded.errors, null)
        val messages = decoded.warnings.toMutableList()
        var plants = 0
        var firstId: Long? = null
        database.withTransaction {
            val seedDao = database.seedDao()
            val known = seedDao.getAllSeeds().associateBy { it.botanicalCode }.toMutableMap()
            val byName = known.values.associateBy { it.commonName.lowercase() }
            // Custom varieties from the file are added if their code is new here.
            bundle.customVarieties.filter { it.botanicalCode !in known }.forEach { v ->
                seedDao.insert(v)
                known[v.botanicalCode] = v
            }
            val existingNames = database.plotDao().getAllPlots().map { it.name }.toMutableSet()
            var unknown = 0
            for (pp in bundle.plots) {
                var name = pp.plot.name
                if (name in existingNames) name = "$name (imported)"
                existingNames += name
                val plotId = database.plotDao().insert(pp.plot.copy(id = 0, name = name))
                if (firstId == null) firstId = plotId
                val nodes = pp.plants.mapNotNull { n ->
                    val code = when {
                        n.seedCode in known -> n.seedCode
                        else -> decoded.varietyNames[n.seedCode]?.lowercase()?.let { byName[it]?.botanicalCode }
                    }
                    if (code == null) { unknown++; null } else n.copy(id = 0, plotId = plotId, seedCode = code)
                }
                if (nodes.isNotEmpty()) database.plantedNodeDao().insertAll(nodes)
                plants += nodes.size
                if (pp.paths.isNotEmpty()) database.pathZoneDao().insertAll(pp.paths.map { it.copy(id = 0, plotId = plotId) })
                pp.features.forEach { database.siteFeatureDao().insert(it.copy(id = 0, plotId = plotId)) }
                // Season history keeps its own variety names, so it is stored even for varieties not in this catalog.
                if (pp.history.isNotEmpty()) database.plantingHistoryDao().insertAll(pp.history.map { it.copy(id = 0, plotId = plotId) })
            }
            if (unknown > 0) messages += "$unknown plant(s) use varieties that aren't in this device's catalog and were skipped. Switch to a larger catalog tier (Settings → Catalog) and import again to include them."
        }
        return ImportReport(bundle.plots.size, plants, messages, firstId)
    }
}
