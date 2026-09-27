package com.example.smartgardenplanner.core

/**
 * "Replace all" (FR-051): swap every plant of one variety on the plot for another variety in one step, keeping their
 * places and planting dates. The result says how many plants now crowd a neighbour (the new variety needs more room,
 * or doesn't get along with a neighbour), so the gardener can decide; nothing is moved automatically. Pure Kotlin.
 */
object PlantSwap {

    data class Result(val nodes: List<PlantedNodeEntity>, val changed: Int, val crowded: Int, val messages: List<String>)

    fun replaceAll(
        nodes: List<PlantedNodeEntity>,
        fromCode: String,
        to: SeedEntity,
        lookup: (String) -> SeedEntity?,
        zone: String?,
        marginMultiplier: Float = 1f,
        enforceCompanionRules: Boolean = true,
        guilds: List<Guild> = emptyList()
    ): Result {
        val from = lookup(fromCode)
        if (fromCode == to.botanicalCode) return Result(nodes, 0, 0, listOf("That's the same variety; nothing changed."))
        if (HardinessZones.blocksPlacement(to, zone)) return Result(nodes, 0, 0, listOf(HardinessZones.describe(to, zone) ?: "${to.commonName} won't survive winters here."))
        val out = nodes.map { if (it.seedCode == fromCode) it.copy(seedCode = to.botanicalCode) else it }
        val changed = nodes.count { it.seedCode == fromCode }
        if (changed == 0) return Result(nodes, 0, 0, listOf("No ${from?.commonName ?: fromCode} on this plot."))
        val combinedLookup: (String) -> SeedEntity? = { c -> if (c == to.botanicalCode) to else lookup(c) }
        val validator = CompanionPlantingValidator()
        val crowded = out.count { n ->
            n.seedCode == to.botanicalCode &&
                !validator.validatePlacement(n, to, out.filter { it !== n }, combinedLookup, marginMultiplier, enforceCompanionRules, guilds).isValid
        }
        val messages = mutableListOf("Replaced $changed ${from?.commonName ?: fromCode} with ${to.commonName}.")
        if (from != null && to.exclusionRadiusM > from.exclusionRadiusM + 0.005f) {
            messages += "${to.commonName} needs ${(to.exclusionRadiusM * 200).toInt()} cm between plants (was ${(from.exclusionRadiusM * 200).toInt()} cm)."
        }
        if (crowded > 0) messages += "$crowded of them now crowd a neighbour (spacing or a plant they don't get along with). Harmony lists them; move or remove some, or Undo."
        HardinessZones.describe(to, zone)?.let { messages += it }
        return Result(out, changed, crowded, messages)
    }
}
