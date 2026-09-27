package com.example.smartgardenplanner.core

/**
 * [NEW — FR-025] Central registry of which app tier (Basic/Standard/Pro) each gated feature
 * requires. This is infrastructure other roadmap items lean on (see FEATURE_ROADMAP.md) rather
 * than a user-facing screen itself — the Settings screen and any future feature-specific UI
 * check against this instead of hardcoding tier logic in multiple places.
 *
 * Reuses the existing catalog tier setting (AppSettings.catalogTier) as the one source of truth
 * for "what tier is this user on" — there is deliberately no separate subscription/account
 * concept yet. Tier assignments below match the "Proposed Tier" column in FEATURE_ROADMAP.md at
 * the time each entry was added; update both together if a tier assignment changes.
 */
enum class AppTier { BASIC, STANDARD, PRO }

enum class Feature(val minimumTier: AppTier, val roadmapId: String) {
    COMPANION_RULE_TOGGLE(AppTier.PRO, "FR-012"), // Settings -> Spacing & Placement -> Enforce companion/antagonist rules
    POLYGON_AREA_SELECT(AppTier.STANDARD, "FR-001"),
    POLYGON_PLOT_SHAPE(AppTier.PRO, "FR-002"),
    SLOPE_CONFIGURATION(AppTier.PRO, "FR-003"),
    FLOODING_ZONES(AppTier.PRO, "FR-004"),
    SUN_SHADE_ZONES(AppTier.STANDARD, "FR-005"),
    SUNLIGHT_BARRIERS(AppTier.PRO, "FR-006"),
    HISTORICAL_SUNLIGHT(AppTier.PRO, "FR-007"),
    GREY_OUT_INCOMPATIBLE(AppTier.STANDARD, "FR-010"),
    HARMONY_REPORT(AppTier.STANDARD, "FR-011"),
    INTERPLANTING_GUILDS(AppTier.PRO, "FR-009"),
    SOIL_TEST(AppTier.PRO, "FR-013"),
    ZONE_AWARE_RECOMMENDATIONS(AppTier.STANDARD, "FR-014"),
    RECOMMEND_AND_AUTOPOPULATE(AppTier.PRO, "FR-015"),
    HOMESTEAD_STARTER_LIST(AppTier.STANDARD, "FR-016"),
    FERTILIZING_PLAN(AppTier.STANDARD, "FR-017"),
    PEST_MANAGEMENT_PLAN(AppTier.STANDARD, "FR-018"),
    CARE_REMINDERS(AppTier.PRO, "FR-019"),
    NUTRITION_GUIDE(AppTier.BASIC, "FR-020"),
    RECIPE_SUGGESTIONS(AppTier.STANDARD, "FR-021"),
    YIELD_ESTIMATES(AppTier.STANDARD, "FR-022"),
    VENDOR_LINKS(AppTier.BASIC, "FR-023"),
    VENDOR_TARGETING(AppTier.PRO, "FR-024"),
    ONLINE_FEATURES(AppTier.BASIC, "FR-026"); // the switch itself is available to everyone; off by default

    /** Tier name for "needs Standard" style messages. */
    val tierLabel: String get() = minimumTier.name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        private val tierRank = mapOf(AppTier.BASIC to 0, AppTier.STANDARD to 1, AppTier.PRO to 2)

        fun isEnabled(feature: Feature, currentTier: AppTier): Boolean {
            return tierRank.getValue(currentTier) >= tierRank.getValue(feature.minimumTier)
        }
    }
}

/** Convenience: resolves AppSettings.catalogTier (a String) to the AppTier enum, defaulting safely. */
fun AppSettings.currentAppTier(): AppTier {
    return try {
        AppTier.valueOf(this.catalogTier)
    } catch (e: IllegalArgumentException) {
        AppTier.BASIC
    }
}
