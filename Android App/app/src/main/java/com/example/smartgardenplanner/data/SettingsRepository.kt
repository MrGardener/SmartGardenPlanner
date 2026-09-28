package com.example.smartgardenplanner.data

import com.example.smartgardenplanner.core.AppSettings

/**
 * [NEW] Typed load/save for AppSettings on top of the existing generic AppConfig key-value
 * table (SecurityRepository/AppConfigDao) — no new database table needed. Each field is its own
 * row so a partial write (changing one setting) doesn't require re-serializing everything, and a
 * missing/corrupt key just falls back to that field's default rather than failing the whole load.
 */
class SettingsRepository(private val repository: SecurityRepository) {

    private object Keys {
        const val DISTANCE_UNIT = "settings.distanceUnit"
        const val CATALOG_TIER = "settings.catalogTier"
        const val SPACING_MARGIN = "settings.spacingMarginMultiplier"
        const val ENFORCE_RULES = "settings.enforceCompanionAntagonistRules"
        const val RULER_FONT_SP = "settings.rulerFontSizeSp"
        const val RULER_TICK_M = "settings.rulerTickIntervalM"
        const val ZOOM_MIN = "settings.zoomMin"
        const val ZOOM_MAX = "settings.zoomMax"
        const val ZOOM_STEP = "settings.zoomStep"
        const val UNDO_DEPTH = "settings.undoHistoryDepth"
        const val MIN_PLOT_DIM = "settings.minPlotDimensionM"
        const val MAX_PLOT_DIM = "settings.maxPlotDimensionM"
        const val TILT_ABORT = "settings.tiltAbortDegrees"
        const val LOW_LIGHT_LUX = "settings.lowLightLuxThreshold"
        const val GPS_ACCURACY = "settings.gpsAccuracyGateMeters"
        const val STORAGE_FLOOR = "settings.storageFloorPercent"
        const val GUILDS = "settings.guildsEnabled"
        const val HOUSEHOLD = "settings.householdSize"
        const val CARE_PREF = "settings.carePreference"
        const val REMINDERS = "settings.careRemindersEnabled"
        const val RAIN_MM = "settings.rainSkipThresholdMm"
        const val ONLINE = "settings.onlineFeaturesEnabled"
        const val USDA_KEY = "settings.usdaApiKey"
        const val VENDOR = "settings.preferredVendorId"
        const val PLAN_LAYOUT = "settings.planLayout"
        const val LAST_PLAN = "settings.lastPlanList"
        const val PLANT_LABELS = "settings.showPlantLabels"
        const val DISCLAIMER = "settings.disclaimerAccepted"
        const val LANGUAGE = "settings.language"
    }

    suspend fun load(): AppSettings {
        val defaults = AppSettings.DEFAULT
        return AppSettings(
            distanceUnit = unitOrDefault(Keys.DISTANCE_UNIT, defaults.distanceUnit),
            catalogTier = repository.fetchConfig(Keys.CATALOG_TIER)?.configValue ?: defaults.catalogTier,
            spacingMarginMultiplier = floatOrDefault(Keys.SPACING_MARGIN, defaults.spacingMarginMultiplier),
            enforceCompanionAntagonistRules = boolOrDefault(Keys.ENFORCE_RULES, defaults.enforceCompanionAntagonistRules),
            rulerFontSizeSp = floatOrDefault(Keys.RULER_FONT_SP, defaults.rulerFontSizeSp),
            rulerTickIntervalM = floatOrDefault(Keys.RULER_TICK_M, defaults.rulerTickIntervalM),
            zoomMin = floatOrDefault(Keys.ZOOM_MIN, defaults.zoomMin),
            zoomMax = floatOrDefault(Keys.ZOOM_MAX, defaults.zoomMax),
            zoomStep = floatOrDefault(Keys.ZOOM_STEP, defaults.zoomStep),
            undoHistoryDepth = intOrDefault(Keys.UNDO_DEPTH, defaults.undoHistoryDepth),
            minPlotDimensionM = floatOrDefault(Keys.MIN_PLOT_DIM, defaults.minPlotDimensionM),
            maxPlotDimensionM = floatOrDefault(Keys.MAX_PLOT_DIM, defaults.maxPlotDimensionM),
            tiltAbortDegrees = floatOrDefault(Keys.TILT_ABORT, defaults.tiltAbortDegrees),
            lowLightLuxThreshold = floatOrDefault(Keys.LOW_LIGHT_LUX, defaults.lowLightLuxThreshold),
            gpsAccuracyGateMeters = floatOrDefault(Keys.GPS_ACCURACY, defaults.gpsAccuracyGateMeters),
            storageFloorPercent = floatOrDefault(Keys.STORAGE_FLOOR, defaults.storageFloorPercent),
            guildsEnabled = boolOrDefault(Keys.GUILDS, defaults.guildsEnabled),
            householdSize = intOrDefault(Keys.HOUSEHOLD, defaults.householdSize),
            carePreference = repository.fetchConfig(Keys.CARE_PREF)?.configValue ?: defaults.carePreference,
            careRemindersEnabled = boolOrDefault(Keys.REMINDERS, defaults.careRemindersEnabled),
            rainSkipThresholdMm = floatOrDefault(Keys.RAIN_MM, defaults.rainSkipThresholdMm),
            onlineFeaturesEnabled = boolOrDefault(Keys.ONLINE, defaults.onlineFeaturesEnabled),
            usdaApiKey = repository.fetchConfig(Keys.USDA_KEY)?.configValue ?: defaults.usdaApiKey,
            preferredVendorId = repository.fetchConfig(Keys.VENDOR)?.configValue ?: defaults.preferredVendorId,
            planLayout = repository.fetchConfig(Keys.PLAN_LAYOUT)?.configValue ?: defaults.planLayout,
            lastPlanList = repository.fetchConfig(Keys.LAST_PLAN)?.configValue ?: defaults.lastPlanList,
            showPlantLabels = boolOrDefault(Keys.PLANT_LABELS, defaults.showPlantLabels),
            disclaimerAccepted = boolOrDefault(Keys.DISCLAIMER, defaults.disclaimerAccepted),
            language = repository.fetchConfig(Keys.LANGUAGE)?.configValue ?: defaults.language
        )
    }

    suspend fun save(settings: AppSettings) {
        repository.saveConfig(Keys.DISTANCE_UNIT, settings.distanceUnit.name)
        repository.saveConfig(Keys.CATALOG_TIER, settings.catalogTier)
        repository.saveConfig(Keys.SPACING_MARGIN, settings.spacingMarginMultiplier.toString())
        repository.saveConfig(Keys.ENFORCE_RULES, settings.enforceCompanionAntagonistRules.toString())
        repository.saveConfig(Keys.RULER_FONT_SP, settings.rulerFontSizeSp.toString())
        repository.saveConfig(Keys.RULER_TICK_M, settings.rulerTickIntervalM.toString())
        repository.saveConfig(Keys.ZOOM_MIN, settings.zoomMin.toString())
        repository.saveConfig(Keys.ZOOM_MAX, settings.zoomMax.toString())
        repository.saveConfig(Keys.ZOOM_STEP, settings.zoomStep.toString())
        repository.saveConfig(Keys.UNDO_DEPTH, settings.undoHistoryDepth.toString())
        repository.saveConfig(Keys.MIN_PLOT_DIM, settings.minPlotDimensionM.toString())
        repository.saveConfig(Keys.MAX_PLOT_DIM, settings.maxPlotDimensionM.toString())
        repository.saveConfig(Keys.TILT_ABORT, settings.tiltAbortDegrees.toString())
        repository.saveConfig(Keys.LOW_LIGHT_LUX, settings.lowLightLuxThreshold.toString())
        repository.saveConfig(Keys.GPS_ACCURACY, settings.gpsAccuracyGateMeters.toString())
        repository.saveConfig(Keys.STORAGE_FLOOR, settings.storageFloorPercent.toString())
        repository.saveConfig(Keys.GUILDS, settings.guildsEnabled.toString())
        repository.saveConfig(Keys.HOUSEHOLD, settings.householdSize.toString())
        repository.saveConfig(Keys.CARE_PREF, settings.carePreference)
        repository.saveConfig(Keys.REMINDERS, settings.careRemindersEnabled.toString())
        repository.saveConfig(Keys.RAIN_MM, settings.rainSkipThresholdMm.toString())
        repository.saveConfig(Keys.ONLINE, settings.onlineFeaturesEnabled.toString())
        repository.saveConfig(Keys.USDA_KEY, settings.usdaApiKey)
        repository.saveConfig(Keys.VENDOR, settings.preferredVendorId)
        repository.saveConfig(Keys.PLAN_LAYOUT, settings.planLayout)
        repository.saveConfig(Keys.LAST_PLAN, settings.lastPlanList)
        repository.saveConfig(Keys.PLANT_LABELS, settings.showPlantLabels.toString())
        repository.saveConfig(Keys.DISCLAIMER, settings.disclaimerAccepted.toString())
        repository.saveConfig(Keys.LANGUAGE, settings.language)
    }

    suspend fun resetToDefaults() {
        save(AppSettings.DEFAULT)
    }

    private suspend fun floatOrDefault(key: String, default: Float): Float {
        return repository.fetchConfig(key)?.configValue?.toFloatOrNull() ?: default
    }

    private suspend fun intOrDefault(key: String, default: Int): Int {
        return repository.fetchConfig(key)?.configValue?.toIntOrNull() ?: default
    }

    private suspend fun boolOrDefault(key: String, default: Boolean): Boolean {
        return repository.fetchConfig(key)?.configValue?.toBooleanStrictOrNull() ?: default
    }

    private suspend fun unitOrDefault(key: String, default: com.example.smartgardenplanner.core.DistanceUnit): com.example.smartgardenplanner.core.DistanceUnit {
        val raw = repository.fetchConfig(key)?.configValue ?: return default
        return try {
            com.example.smartgardenplanner.core.DistanceUnit.valueOf(raw)
        } catch (e: IllegalArgumentException) {
            default
        }
    }
}
