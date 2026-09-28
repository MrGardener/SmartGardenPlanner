package com.example.smartgardenplanner.core

/**
 * [NEW] Display-only unit conversion. Every distance is still stored internally as meters
 * everywhere in the database and in every calculation engine (PlotEntity.lengthM, SeedEntity.
 * exclusionRadiusM, etc.) — converting the actual storage unit would mean migrating every table
 * and touching every formula in the app, for zero real benefit. Instead, this converts ONLY at
 * the UI boundary: format meters for display in the user's chosen unit, and parse user input
 * back to meters before it's ever stored or calculated with.
 */
enum class DistanceUnit(val suffix: String) {
    METERS("m"),
    INCHES("in")
}

object DistanceFormatter {
    private const val METERS_TO_INCHES = 39.3701f

    fun metersToDisplay(meters: Float, unit: DistanceUnit): Float {
        return when (unit) {
            DistanceUnit.METERS -> meters
            DistanceUnit.INCHES -> meters * METERS_TO_INCHES
        }
    }

    fun displayToMeters(displayValue: Float, unit: DistanceUnit): Float {
        return when (unit) {
            DistanceUnit.METERS -> displayValue
            DistanceUnit.INCHES -> displayValue / METERS_TO_INCHES
        }
    }

    /** Formats a meters value for display, e.g. "3.25m" or "128.0in", respecting the chosen unit. */
    fun format(meters: Float, unit: DistanceUnit, decimals: Int = 2): String {
        val value = metersToDisplay(meters, unit)
        return value.fmt(decimals) + unit.suffix
    }

    /** Parses user-entered text (already in the given display unit) back to meters, or null if invalid. */
    fun parseToMeters(text: String, unit: DistanceUnit): Float? {
        // A decimal comma ("3,5") is accepted, as typed in many languages; anything that isn't a finite number is null.
        val t = text.trim().let { if (it.count { c -> c == ',' } == 1 && '.' !in it) it.replace(',', '.') else it }
        val value = t.toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
        return displayToMeters(value, unit).takeIf { it.isFinite() }
    }
}
