package com.example.smartgardenplanner.core

/**
 * [NEW — FR-008] Companion/antagonist fields store comma-separated species-prefix codes
 * (e.g. "BAS,MAR,CAR" for Basil/Marigold/Carrot), which is what the validator needs — but is
 * meaningless to a human reading the Encyclopedia, who has no reason to know the internal code
 * scheme. This resolves codes to real display names by looking up any seed in the given
 * dictionary that shares that species prefix and taking its species-level name (the part of its
 * commonName before " - ").
 */
object CompanionNameResolver {

    fun resolveToDisplayNames(codes: String, dictionary: List<SeedEntity>): String {
        if (codes.isBlank()) return "None on file."
        val prefixToName = dictionary.associate { seed ->
            seed.botanicalCode.substringBefore("-") to seed.commonName.substringBefore(" - ")
        }
        val names = codes.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { prefix -> prefixToName[prefix] ?: prefix } // fall back to the raw code only if truly unresolvable
        return if (names.isEmpty()) "None on file." else names.distinct().joinToString(", ")
    }
}
