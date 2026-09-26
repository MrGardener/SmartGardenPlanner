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
    val storageFloorPercent: Float = 5f
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}
