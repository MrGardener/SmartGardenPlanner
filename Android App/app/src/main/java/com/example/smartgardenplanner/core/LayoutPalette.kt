package com.example.smartgardenplanner.core

/**
 * Sun bands for the shade overlay (FR-006): the same thresholds and colours on the phone and the computer.
 * Colours are ARGB (0xAARRGGBB) and are chosen to read on [LayoutPalette.PAPER] in both light and dark mode:
 * warm yellow for sun, mid blue for part shade and deep indigo for shade, which also differ in lightness so they
 * can be told apart without colour vision.
 */
enum class SunBand(val label: String, val minHours: Float, val overlayArgb: Long) {
    FULL_SUN("Full sun (6+ h)", 6f, 0x40FACC15),
    PART_SHADE("Part shade (3–6 h)", 3f, 0x6660A5FA),
    FULL_SHADE("Shade (under 3 h)", 0f, 0xA6312E81);

    companion object {
        fun of(hours: Float): SunBand = when {
            hours >= FULL_SUN.minHours -> FULL_SUN
            hours >= PART_SHADE.minHours -> PART_SHADE
            else -> FULL_SHADE
        }
    }
}

/**
 * Colours of the plot layout, shared by both clients. The layout is drawn on a light "paper" background in light
 * and dark mode alike, so shade, areas and plants stay visible (owner report 2026-09-27: shade could not be seen on
 * the dark background).
 */
object LayoutPalette {
    const val PAPER = 0xFFF5F1E6        // plot background
    const val GRID = 0xFFDDD5C3
    const val BORDER = 0xFF57534E
    const val INK = 0xFF292524          // labels drawn on the plot
    const val OUTSIDE_OUTLINE = 0x9978716C
    const val PATH_FILL = 0x99A8A29E
    const val PATH_EDGE = 0xFF78716C
    const val FENCE = 0xFF92400E
    const val WALL = 0xFF6B7280
    const val BUILDING_FILL = 0x80A8A29E
    const val BUILDING_EDGE = 0xFF44403C
    const val TREE_FILL = 0x4D16A34AL
    const val TREE_EDGE = 0xFF15803D
    const val TRUNK = 0xFF78350F

    /** Fill and edge for a marked site area. */
    fun area(type: SiteFeatureType): Pair<Long, Long> = when (type) {
        SiteFeatureType.FULL_SUN -> 0x40FACC15L to 0xFFCA8A04
        SiteFeatureType.PART_SHADE -> 0x6660A5FAL to 0xFF2563EB
        SiteFeatureType.FULL_SHADE -> 0xA6312E81 to 0xFF312E81
        SiteFeatureType.FLOOD -> 0x4D0EA5E9L to 0xFF0369A1
        else -> 0x4DD97706L to 0xFFB45309
    }

    /** "#RRGGBB" for an ARGB colour (web). */
    fun hex(argb: Long): String = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')

    /** Alpha 0–1 of an ARGB colour (web). */
    fun alpha(argb: Long): Double = ((argb shr 24) and 0xFF) / 255.0
}
