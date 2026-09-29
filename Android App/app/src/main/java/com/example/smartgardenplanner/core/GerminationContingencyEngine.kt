package com.example.smartgardenplanner.core


/**
 * [NEW] Implements T2-FUN-080 / T2-DAT-070 / HLR-DAT-080 / HLR-DAT-090: on user-declared
 * germination failure, search the seed dictionary and present up to 3 concurrent fallback
 * ("Plan B") pathways:
 *   1. Fast-track variety   — a short-maturity substitute from the same botanical family.
 *   2. Nursery transplant    — skip direct-sow, start again as a nursery transplant instead.
 *   3. Alternate catch-crop  — a different, unrelated crop suited to the remaining season length.
 *
 * Pure Kotlin, no Android framework dependency — trivially unit-testable on the JVM
 * (see test/.../core/GerminationContingencyEngineTest.kt).
 */
class GerminationContingencyEngine {

    sealed class ContingencyOption {
        data class FastTrackVariety(val alternateBotanicalCode: String, val alternateCommonName: String) : ContingencyOption()
        data class NurseryTransplant(val originalBotanicalCode: String) : ContingencyOption()
        data class CatchCrop(val alternateBotanicalCode: String, val alternateCommonName: String) : ContingencyOption()
    }

    /** Returns whether a node has exceeded its expected germination window and should surface an alert. */
    fun isGerminationOverdue(node: PlantedNodeEntity, seed: SeedEntity, nowEpochMillis: Long = PlatformClock.nowMillis()): Boolean {
        val elapsedDays = (nowEpochMillis - node.datePlantedEpochMillis) / 86_400_000L
        return elapsedDays > seed.germinationDays && !node.germinationFlagResolved
    }

    /**
     * Builds the concurrent 3-path option set for a failed seed. Any path whose data isn't
     * available on the seed record is simply omitted (never fabricated), so callers may see
     * fewer than 3 options.
     */
    fun buildContingencyOptions(
        failedSeed: SeedEntity,
        seedDictionary: List<SeedEntity>
    ): List<ContingencyOption> {
        val options = mutableListOf<ContingencyOption>()

        failedSeed.fastTrackAlternateCode?.let { code ->
            seedDictionary.find { it.botanicalCode == code }?.let { alt ->
                options.add(ContingencyOption.FastTrackVariety(alt.botanicalCode, alt.commonName))
            }
        }

        if (failedSeed.nurseryTransplantSuitable) {
            options.add(ContingencyOption.NurseryTransplant(failedSeed.botanicalCode))
        }

        failedSeed.catchCropAlternateCode?.let { code ->
            seedDictionary.find { it.botanicalCode == code }?.let { alt ->
                options.add(ContingencyOption.CatchCrop(alt.botanicalCode, alt.commonName))
            }
        }

        return options
    }
}
