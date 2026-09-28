package com.example.smartgardenplanner.core

/**
 * [NEW] Requested feature: select a rectangular area and auto-populate it with a given seed
 * type, packed either in a simple line grid or a hexagonal (offset-row) grid for tighter
 * packing. Pure Kotlin, no Android dependency — unit-testable on the JVM
 * (see test/.../core/AutoPopulateEngineTest.kt).
 *
 * Coordinates returned are relative to the selected area's top-left corner (0,0), in meters.
 * The caller is responsible for translating them to plot-absolute coordinates and for
 * filtering out any point that falls inside a PathZoneEntity or violates spacing against
 * already-placed nodes (this engine only knows about the one seed type it's packing, not the
 * rest of the plot's state).
 */
class AutoPopulateEngine {

    enum class PackingPattern { LINE, HEXAGON }

    data class GridPoint(val xM: Float, val yM: Float)

    fun generatePositions(
        areaWidthM: Float,
        areaHeightM: Float,
        spacingM: Float,
        pattern: PackingPattern
    ): List<GridPoint> {
        if (!areaWidthM.isFinite() || !areaHeightM.isFinite() || !spacingM.isFinite()) return emptyList()
        if (areaWidthM <= 0f || areaHeightM <= 0f || spacingM <= 0f) return emptyList()
        // A huge area at a tiny spacing would mean millions of plants (and run out of memory): refuse to list more
        // than MAX_POINTS. estimateCount still reports the real number, so the dialog can say it's too many.
        if (roughCount(areaWidthM, areaHeightM, spacingM) > MAX_POINTS) return emptyList()

        return when (pattern) {
            PackingPattern.LINE -> generateLineGrid(areaWidthM, areaHeightM, spacingM)
            PackingPattern.HEXAGON -> generateHexGrid(areaWidthM, areaHeightM, spacingM)
        }
    }

    /** Simple rectangular grid: rows and columns spaced exactly `spacingM` apart. */
    private fun generateLineGrid(areaWidthM: Float, areaHeightM: Float, spacingM: Float): List<GridPoint> {
        val points = mutableListOf<GridPoint>()
        var y = spacingM / 2f
        while (y <= areaHeightM - spacingM / 2f || points.isEmpty()) {
            if (y > areaHeightM) break
            var x = spacingM / 2f
            while (x <= areaWidthM - spacingM / 2f || (x <= areaWidthM && points.isEmpty())) {
                if (x > areaWidthM) break
                points.add(GridPoint(x, y))
                x += spacingM
            }
            y += spacingM
            if (areaWidthM < spacingM || areaHeightM < spacingM) break
        }
        return points
    }

    /**
     * Hexagonal (triangular/offset-row) packing: rows are spaced at spacingM * sqrt(3)/2 apart
     * (the standard hex-packing row height for circles of a given center-to-center spacing),
     * and every other row is offset by half the horizontal spacing. This fits meaningfully more
     * plants in the same area than a line grid for circular exclusion zones.
     */
    private fun generateHexGrid(areaWidthM: Float, areaHeightM: Float, spacingM: Float): List<GridPoint> {
        val points = mutableListOf<GridPoint>()
        val rowHeight = spacingM * 0.8660254f // sqrt(3)/2
        var row = 0
        var y = spacingM / 2f
        while (y <= areaHeightM - spacingM / 2f + 0.0001f) {
            val rowOffset = if (row % 2 == 1) spacingM / 2f else 0f
            var x = spacingM / 2f + rowOffset
            while (x <= areaWidthM - spacingM / 2f + 0.0001f) {
                points.add(GridPoint(x, y))
                x += spacingM
            }
            y += rowHeight
            row++
        }
        return points
    }

    /** Upper bound on the number of positions, worked out without listing them (safe for any size). */
    private fun roughCount(w: Float, h: Float, s: Float): Double =
        (kotlin.math.floor(w.toDouble() / s) + 1.0) * (kotlin.math.floor(h.toDouble() / (s * 0.8660254)) + 1.0)

    /** How many plants would fit: exact for normal areas, an estimate (never listing them) for huge ones. */
    fun estimateCount(areaWidthM: Float, areaHeightM: Float, spacingM: Float, pattern: PackingPattern): Int {
        if (!areaWidthM.isFinite() || !areaHeightM.isFinite() || !spacingM.isFinite() || areaWidthM <= 0f || areaHeightM <= 0f || spacingM <= 0f) return 0
        val rough = roughCount(areaWidthM, areaHeightM, spacingM)
        if (rough > MAX_POINTS) return if (rough >= Int.MAX_VALUE) Int.MAX_VALUE else rough.toInt()
        return generatePositions(areaWidthM, areaHeightM, spacingM, pattern).size
    }

    companion object {
        /** Most positions ever listed at once. */
        const val MAX_POINTS = 20_000
    }
}
