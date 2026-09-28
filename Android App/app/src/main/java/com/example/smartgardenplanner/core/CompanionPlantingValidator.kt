package com.example.smartgardenplanner.core

import kotlin.math.sqrt

/**
 * [NEW] Implements T2-INT-030 / HLR-DAT-050: continuously evaluate node coordinates and flag a
 * conflict warning if a new node would fall within an existing node's exclusion radius (spacing
 * violation) OR is listed as a botanical antagonist of a nearby node (companion-planting conflict).
 * Pure Kotlin, no Android dependency — unit-testable on the JVM.
 *
 * [UPDATED] Added marginMultiplier and enforceCompanionRules — previously the "sum of both radii"
 * spacing rule and the antagonist check were both unconditional and non-negotiable. Real feedback:
 * the strict rule can reject placements a user considers acceptable in practice, with no way to
 * adjust it. Both are now driven by Settings (core/AppSettings.kt) rather than hardcoded.
 */
class CompanionPlantingValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val spacingViolations: List<Long>,     // IDs of existing nodes whose spacing radius is violated
        val antagonistViolations: List<Long>   // IDs of existing nodes that are botanical antagonists
    )

    /**
     * @param candidate the node being placed (coordinates in meters, plot-relative)
     * @param candidateSeed the seed profile for the candidate node
     * @param existingNodes already-placed nodes on the same plot
     * @param seedLookup function resolving a botanicalCode to its SeedEntity
     * @param marginMultiplier scales the strict "sum of both radii" requirement — 1.0 is the
     *   original unconditional rule; lower values allow tighter placement.
     * @param enforceCompanionRules when false, antagonist-proximity conflicts are never raised
     *   (spacing is still enforced regardless).
     * @param guilds active interplanting guilds (FR-009); empty when the feature is off. Two members of the
     *   same guild only need the larger radius between centres, and skip the antagonist check.
     */
    fun validatePlacement(
        candidate: PlantedNodeEntity,
        candidateSeed: SeedEntity,
        existingNodes: List<PlantedNodeEntity>,
        seedLookup: (String) -> SeedEntity?,
        marginMultiplier: Float = 1.0f,
        enforceCompanionRules: Boolean = true,
        guilds: List<Guild> = emptyList()
    ): ValidationResult {
        val spacingViolations = mutableListOf<Long>()
        val antagonistViolations = mutableListOf<Long>()

        for (existing in existingNodes) {
            // Only a saved plant (id != 0) can be "itself". Unsaved candidates all have id 0, e.g. the
            // positions accepted earlier in the same auto-populate batch, and must still be compared.
            if (candidate.id != 0L && existing.id == candidate.id) continue
            val existingSeed = seedLookup(existing.seedCode) ?: continue

            // Double precision plus a small tolerance, so that two spacing circles that exactly touch are
            // allowed (T2-VAL-040). In Float, 0.6f + 0.3f = 0.90000004f, which wrongly rejected a 0.90 m gap.
            val dx = candidate.coordinateXM.toDouble() - existing.coordinateXM.toDouble()
            val dy = candidate.coordinateYM.toDouble() - existing.coordinateYM.toDouble()
            val distance = sqrt(dx * dx + dy * dy)

            val inSameGuild = guilds.isNotEmpty() && GuildCatalog.sharedGuilds(candidateSeed, existingSeed, guilds).isNotEmpty()
            val fullSpacing = (candidateSeed.exclusionRadiusM.toDouble() + existingSeed.exclusionRadiusM.toDouble()) * marginMultiplier
            val requiredSpacing = if (inSameGuild) {
                maxOf(candidateSeed.exclusionRadiusM.toDouble(), existingSeed.exclusionRadiusM.toDouble()) * marginMultiplier
            } else {
                fullSpacing
            }
            if (distance < requiredSpacing - SPACING_TOLERANCE_M) {
                spacingViolations.add(existing.id)
            }

            if (enforceCompanionRules && !inSameGuild) {
                // [UPDATED] Companion/antagonist codes are matched by SPECIES PREFIX (the part of
                // a botanicalCode before its cultivar suffix, e.g. "MAR" for any "MAR-###"
                // cultivar of Marigold), not exact botanicalCode. This is actually the more
                // correct behavior — a companion/antagonist relationship is a property of the
                // species, not one specific named cultivar (any Marigold cultivar deters pests
                // near any Tomato cultivar, not just one specific pairing) — and it's what makes
                // the relationship data usable at all against the cultivar-level tiered catalog,
                // where hundreds of distinct botanicalCodes share a small set of species prefixes.
                val candidatePrefix = speciesPrefix(candidateSeed.botanicalCode)
                val existingPrefix = speciesPrefix(existingSeed.botanicalCode)
                // Codes are compared ignoring case and spaces: custom varieties may be typed as " tom , cor ".
                val candidateAntagonists = candidateSeed.antagonistCodes.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                val existingAntagonists = existingSeed.antagonistCodes.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
                if (existingPrefix in candidateAntagonists || candidatePrefix in existingAntagonists) {
                    // Antagonist conflicts matter within a wider "nearby" radius, not just the spacing circle.
                    val nearbyRadius = fullSpacing * 2.0
                    if (distance < nearbyRadius - SPACING_TOLERANCE_M) {
                        antagonistViolations.add(existing.id)
                    }
                }
            }
        }

        return ValidationResult(
            isValid = spacingViolations.isEmpty() && antagonistViolations.isEmpty(),
            spacingViolations = spacingViolations,
            antagonistViolations = antagonistViolations
        )
    }

    companion object {
        /** Distances within 0.1 mm of the required spacing count as meeting it (TBC-20). */
        const val SPACING_TOLERANCE_M = 0.0001
    }

    /** Extracts the species-level prefix from a cultivar botanicalCode, e.g. "MAR-002" -> "MAR". */
    private fun speciesPrefix(botanicalCode: String): String {
        return botanicalCode.substringBefore("-").trim().uppercase()
    }
}
