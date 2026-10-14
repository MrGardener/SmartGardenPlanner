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

    /**
     * LLR-UNIT-030: the display value rounded half-up, a space, then the unit: "3.25 m" (2 decimals) or "128.0 in"
     * (1 decimal) unless [decimals] is given. The decimal sign is always "." (owner decision 2026-10-14).
     */
    fun format(meters: Float, unit: DistanceUnit, decimals: Int = if (unit == DistanceUnit.METERS) 2 else 1): String {
        val value = metersToDisplay(meters, unit).toDouble()
        // A Float such as 0.005f is stored as 0.00499999…; the tiny nudge makes it round half-up as it reads.
        val nudged = value + kotlin.math.sign(value) * kotlin.math.abs(value) * 1e-7
        return nudged.fmt(decimals) + " " + unit.suffix
    }

    /**
     * LLR-UNIT-040: parses what the user typed (in the display unit) to meters, or null. The text is trimmed and must
     * be at most [MAX_CHARS] characters of digits with at most one decimal sign ("." or ",") and at least one digit:
     * no sign, no exponent, no grouping separator, nothing else ("-2", "1e3", "1,000.5" and "12 m" are refused).
     */
    fun parseToMeters(text: String, unit: DistanceUnit): Float? {
        val t = text.trim()
        if (t.length > MAX_CHARS || !NUMBER.matches(t)) return null
        val value = t.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
        return displayToMeters(value, unit).takeIf { it.isFinite() }
    }

    /** Longest text [parseToMeters] reads. */
    const val MAX_CHARS = 12
    private val NUMBER = Regex("^(\\d+([.,]\\d*)?|[.,]\\d+)$")
}
