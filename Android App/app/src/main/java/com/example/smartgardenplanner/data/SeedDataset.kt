package com.example.smartgardenplanner.data

import com.example.smartgardenplanner.core.ClimateZoneEntity
import com.example.smartgardenplanner.core.SeedEntity

/**
 * [NEW] Replaces the raw-SQL seed pre-population block in MainActivity.onCreate(), which
 * inserted rows into a guessed table name ("seeds"/"SeedEntity"/"seed") that had no backing
 * Room entity and therefore always failed silently. This is now a real, typed dataset inserted
 * through SeedDao/ClimateZoneDao against the actual `seeds` / `climate_zones` tables.
 *
 * This starter set is intentionally small; expanding it is ordinary data work, not architecture
 * work, once the entity/DAO/migration plumbing (this revision) exists.
 */
object SeedDataset {

    val starterSeeds = listOf(
        SeedEntity(
            botanicalCode = "SOL-LYC",
            commonName = "Tomato",
            botanicalFamily = "Solanaceae",
            exclusionRadiusM = 1.25f,
            germinationDays = 7,
            daysToHarvest = 75,
            companionCodes = "PL-BAS,PL-MAR",
            antagonistCodes = "BRA-OLE",
            pestNotes = "Hornworms, aphids, whiteflies.",
            careNotes = "Deep, consistent watering; stake or cage for support.",
            fastTrackAlternateCode = "SOL-LYC-CHERRY",
            nurseryTransplantSuitable = true,
            catchCropAlternateCode = "PL-BAS"
        ),
        SeedEntity(
            botanicalCode = "SOL-LYC-CHERRY",
            commonName = "Cherry Tomato (fast-track)",
            botanicalFamily = "Solanaceae",
            exclusionRadiusM = 0.9f,
            germinationDays = 6,
            daysToHarvest = 55,
            companionCodes = "PL-BAS,PL-MAR",
            antagonistCodes = "BRA-OLE",
            pestNotes = "Hornworms, aphids.",
            careNotes = "Similar to standard tomato but faster maturity.",
            nurseryTransplantSuitable = true
        ),
        SeedEntity(
            botanicalCode = "PL-BAS",
            commonName = "Basil",
            botanicalFamily = "Lamiaceae",
            exclusionRadiusM = 0.40f,
            germinationDays = 6,
            daysToHarvest = 50,
            companionCodes = "SOL-LYC",
            antagonistCodes = "",
            pestNotes = "Japanese beetles, aphids.",
            careNotes = "Pinch flower buds to prolong leaf production.",
            nurseryTransplantSuitable = true,
            catchCropAlternateCode = "PL-MAR"
        ),
        SeedEntity(
            botanicalCode = "PL-MAR",
            commonName = "Marigold",
            botanicalFamily = "Asteraceae",
            exclusionRadiusM = 0.60f,
            germinationDays = 5,
            daysToHarvest = 45,
            companionCodes = "SOL-LYC,PL-BAS",
            antagonistCodes = "",
            pestNotes = "Natural pest deterrent; spider mites in dry conditions.",
            careNotes = "Full sun, tolerant of poor soil.",
            nurseryTransplantSuitable = true
        ),
        SeedEntity(
            botanicalCode = "BRA-OLE",
            commonName = "Cabbage",
            botanicalFamily = "Brassicaceae",
            exclusionRadiusM = 1.0f,
            germinationDays = 8,
            daysToHarvest = 70,
            companionCodes = "",
            antagonistCodes = "SOL-LYC",
            pestNotes = "Cabbage worms, aphids.",
            careNotes = "Consistent moisture; cool-season crop.",
            nurseryTransplantSuitable = true
        )
    )

    val starterClimateZones = listOf(
        ClimateZoneEntity(zipCode = "10001", hardinessZone = "7b", lastFrostDayOfYear = 105, firstFrostDayOfYear = 305),
        ClimateZoneEntity(zipCode = "90001", hardinessZone = "10b", lastFrostDayOfYear = 1, firstFrostDayOfYear = 365),
        ClimateZoneEntity(zipCode = "60601", hardinessZone = "6a", lastFrostDayOfYear = 120, firstFrostDayOfYear = 290),
        ClimateZoneEntity(zipCode = "73301", hardinessZone = "8b", lastFrostDayOfYear = 75, firstFrostDayOfYear = 320),
        ClimateZoneEntity(zipCode = "98101", hardinessZone = "8b", lastFrostDayOfYear = 90, firstFrostDayOfYear = 315)
    )
}
