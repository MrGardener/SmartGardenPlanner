package com.example.smartgardenplanner.ui

import androidx.compose.ui.graphics.Color
import com.example.smartgardenplanner.core.SeedEntity
import kotlin.math.abs

/**
 * [UPDATED] Requested feature: differentiate vegetable types on the canvas by color, AND allow
 * manually overriding a variety's color when the automatic assignment collides with another
 * variety (a real report: marigold and tomato ended up the same color — hash-mod-360 has no
 * collision protection, and even without an exact collision, nearby hues can look identical).
 *
 * Priority order: SeedEntity.colorHex (user-assigned) > deterministic hash-derived color.
 */
object VegetableColorPalette {

    /**
     * A curated set of 16 colors chosen to be pairwise distinguishable (spread around the hue
     * wheel, alternating saturation/value) — offered as quick-pick swatches in the variety editor
     * so users aren't stuck typing hex codes to resolve a collision.
     */
    val PRESET_SWATCHES: List<Color> = listOf(
        Color(0xFFEF4444), // red
        Color(0xFFF97316), // orange
        Color(0xFFF59E0B), // amber
        Color(0xFFEAB308), // yellow
        Color(0xFF84CC16), // lime
        Color(0xFF22C55E), // green
        Color(0xFF10B981), // emerald
        Color(0xFF14B8A6), // teal
        Color(0xFF06B6D4), // cyan
        Color(0xFF0EA5E9), // sky
        Color(0xFF3B82F6), // blue
        Color(0xFF6366F1), // indigo
        Color(0xFF8B5CF6), // violet
        Color(0xFFA855F7), // purple
        Color(0xFFD946EF), // fuchsia
        Color(0xFFEC4899)  // pink
    )

    /** Preferred entry point: uses the seed's manual override if set, else falls back to the deterministic hash. */
    fun colorFor(seed: SeedEntity?): Color {
        seed?.colorHex?.let { hex -> parseHex(hex)?.let { return it } }
        return colorFor(seed?.botanicalCode ?: "UNKNOWN")
    }

    /** Fallback path for call sites that only have the code, not the full seed record (e.g. a failed lookup). */
    fun colorFor(botanicalCode: String): Color {
        val hash = abs(botanicalCode.hashCode())
        val hue = (hash % 360).toFloat()
        // Fixed saturation/value keeps every color readable on the light layout background (LayoutPalette.PAPER)
        // and on the dark screens around it.
        return Color.hsv(hue = hue, saturation = 0.75f, value = 0.72f)
    }

    fun exclusionRingColorFor(seed: SeedEntity?): Color = colorFor(seed).copy(alpha = 0.22f)

    fun exclusionRingColorFor(botanicalCode: String): Color = colorFor(botanicalCode).copy(alpha = 0.22f)

    fun toHex(color: Color): String {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }

    private fun parseHex(hex: String): Color? {
        return try {
            val normalized = if (hex.startsWith("#")) hex else "#$hex"
            Color(android.graphics.Color.parseColor(normalized))
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
