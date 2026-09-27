package com.example.smartgardenplanner.core

/**
 * [NEW] Every previously-hardcoded numeric/behavioral constant in the app, in one place.
 * Defaults match what was hardcoded before, so nothing changes behavior until a user actually
 * opens Settings and changes something. Persisted via SettingsRepository (data/SettingsRepository.kt)
 * on top of the existing AppConfig key-value table.
 */
data class AppSettings(
    // --- Units ---
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,

    // --- Catalog ---
    // [NEW] Which bundled seed catalog tier is active. Switching this replaces all non-custom
    // (bundled) seed entries with the new tier's set — anything the user personally added or
    // edited (SeedEntity.isCustom = true) is never touched.
    val catalogTier: String = "BASIC", // "BASIC" | "STANDARD" | "PRO"

    // --- Spacing & Placement ---
    // Multiplies the strict "sum of both radii" spacing requirement. 1.0 = the original strict
    // rule. Below 1.0 allows tighter packing than the textbook non-overlap rule (user explicitly
    // accepts the risk); above 1.0 is more conservative than the default.
    val spacingMarginMultiplier: Float = 1.0f,
    val enforceCompanionAntagonistRules: Boolean = true,

    // --- Canvas Display ---
    val rulerFontSizeSp: Float = 14f,          // was hardcoded 20f/18f (px) — now user-configurable, in sp
    val rulerTickIntervalM: Float = 1.0f,       // was hardcoded to always be every 1 meter
    val zoomMin: Float = 0.5f,
    val zoomMax: Float = 3.0f,
    val zoomStep: Float = 0.25f,
    val undoHistoryDepth: Int = 25,

    // --- Plot Validation ---
    val minPlotDimensionM: Float = 0.05f,
    val maxPlotDimensionM: Float = 1000.0f,

    // --- Sensors & Hardware ---
    // Scaffolded now even though the camera/sensor screens aren't built yet (deferred to last,
    // per prior discussion) — so the settings plumbing already exists when they land.
    val tiltAbortDegrees: Float = 5.0f,
    val lowLightLuxThreshold: Float = 10f,
    val gpsAccuracyGateMeters: Float = 15f,
    val storageFloorPercent: Float = 5f,

    // --- Roadmap features ---
    val guildsEnabled: Boolean = false,          // FR-009 (Pro): interplanting guilds, off by default
    val householdSize: Int = 4,                  // FR-016/022: people the garden should feed
    val carePreference: String = "ORGANIC",      // FR-017/018: "ORGANIC" | "CONVENTIONAL"
    val careRemindersEnabled: Boolean = false,   // FR-019 (Pro): daily watering/fertilizing notifications
    val rainSkipThresholdMm: Float = 5f,         // FR-019: this much rain counts as a watering
    val onlineFeaturesEnabled: Boolean = false,  // FR-026: master switch for network access, off by default
    val usdaApiKey: String = "DEMO_KEY",         // FR-020: FoodData Central key (DEMO_KEY is rate-limited)
    val preferredVendorId: String = "",          // FR-024 (Pro)
    val planLayout: String = "CLUMPS",           // FR-032: "CLUMPS" | "ROWS" for Plan an area for me
    val lastPlanList: String = "",               // FR-034: last Plan-an-area list, "CODE:count,CODE:count"
    val showPlantLabels: Boolean = true          // FR-031: short names (e.g. "Bell red", "Cherry") on the layout
) {
    val carePreferenceEnum: CarePreference
        get() = if (carePreference == CarePreference.CONVENTIONAL.name) CarePreference.CONVENTIONAL else CarePreference.ORGANIC

    val planLayoutEnum: PlantingLayout
        get() = PlantingLayout.entries.firstOrNull { it.name == planLayout } ?: PlantingLayout.CLUMPS

    /** The remembered plan list as (variety code, count) pairs. */
    val lastPlanRows: List<Pair<String, Int>>
        get() = lastPlanList.split(",").mapNotNull { e -> e.split(":").takeIf { it.size == 2 }?.let { (c, n) -> n.toIntOrNull()?.let { c to it } } }

    companion object {
        val DEFAULT = AppSettings()

        fun encodePlanRows(rows: List<Pair<String, Int>>): String = rows.joinToString(",") { "${it.first}:${it.second}" }
    }
}
