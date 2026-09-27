package com.example.smartgardenplanner.core

import kotlin.math.sqrt

/**
 * A satellite or aerial photo shown under the plot (FR-046), for example a screenshot of Google Maps in satellite view,
 * so trees, fences and buildings can be traced in the right place. The photo's top-left corner sits at ([xM], [yM])
 * in plot metres, it is [widthM] wide (height = width × [aspect]) and turned [rotationDeg] clockwise about that corner.
 * Stored on the plot as "x;y;width;rotation;opacity;aspect" ([PlotEntity.backdropJson]); the image itself is kept
 * next to the plot (a file on the phone, a data URL in the computer planner and the plan file).
 */
data class Backdrop(
    val xM: Float = 0f,
    val yM: Float = 0f,
    val widthM: Float,
    val rotationDeg: Float = 0f,
    val opacity: Float = 0.6f,
    /** Image height ÷ width. */
    val aspect: Float,
    val visible: Boolean = true
) {
    val heightM: Float get() = widthM * aspect

    fun encode(): String = listOf(xM, yM, widthM, rotationDeg, opacity, aspect).joinToString(";") { it.fmt(4) } + if (visible) "" else ";h"

    /**
     * Rescales the photo so that two points picked on it ([a], [b], in plot metres as currently drawn) are
     * [distanceM] apart, keeping [a] where it is. Returns null when the points are too close or the distance is invalid.
     */
    fun calibrate(a: PlotPoint, b: PlotPoint, distanceM: Float): Backdrop? {
        val dx = b.x - a.x; val dy = b.y - a.y
        val current = sqrt(dx * dx + dy * dy)
        if (current < 0.01f || distanceM !in 0.1f..MAX_WIDTH_M) return null
        val k = distanceM / current
        val w = widthM * k
        if (w !in MIN_WIDTH_M..MAX_WIDTH_M) return null
        return copy(xM = a.x + (xM - a.x) * k, yM = a.y + (yM - a.y) * k, widthM = w)
    }

    fun moved(dx: Float, dy: Float) = copy(xM = xM + dx, yM = yM + dy)

    companion object {
        const val MIN_WIDTH_M = 1f
        const val MAX_WIDTH_M = 2000f
        /** Largest image kept (as a data URL in the plan file / browser): about 4 MB of text. */
        const val MAX_IMAGE_CHARS = 4_000_000
        /** Longest side an imported photo is scaled down to. */
        const val MAX_IMAGE_PX = 1600

        /** A new backdrop covering the plot's width, top-left at the plot's corner. */
        fun fresh(plot: PlotEntity, imageWidthPx: Int, imageHeightPx: Int): Backdrop =
            Backdrop(widthM = plot.lengthM.coerceIn(MIN_WIDTH_M, MAX_WIDTH_M), aspect = (imageHeightPx.toFloat() / imageWidthPx.coerceAtLeast(1)).coerceIn(0.05f, 20f))

        fun parse(s: String?): Backdrop? {
            if (s.isNullOrBlank()) return null
            val f = s.split(';')
            if (f.size < 6) return null
            val n = f.take(6).map { it.trim().toFloatOrNull() ?: return null }
            if (n[2] !in MIN_WIDTH_M..MAX_WIDTH_M || n[5] !in 0.05f..20f || n.any { it.isNaN() }) return null
            if (kotlin.math.abs(n[0]) > 10_000f || kotlin.math.abs(n[1]) > 10_000f) return null
            return Backdrop(n[0], n[1], n[2], ((n[3] % 360f) + 360f) % 360f, n[4].coerceIn(0.1f, 1f), n[5], f.getOrNull(6) != "h")
        }

        /** True for a data URL of a JPEG, PNG or WebP image within the size limit. */
        fun isImageDataUrl(s: String?): Boolean =
            s != null && s.length <= MAX_IMAGE_CHARS && Regex("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$").matches(s)

        /** Google Maps in satellite view at a location, to take a screenshot from. */
        fun googleMapsUrl(latitude: Double?, longitude: Double?, query: String?): String? = when {
            // An address finds the actual yard; the ZIP's centre point is only roughly nearby.
            !query.isNullOrBlank() -> "https://www.google.com/maps/search/?api=1&query=" + encode(query.trim())
            latitude != null && longitude != null ->
                "https://www.google.com/maps/@?api=1&map_action=map&center=${latitude.fmt(6)},${longitude.fmt(6)}&zoom=18&basemap=satellite"
            else -> null
        }

        private fun encode(s: String): String = buildString {
            for (c in s) when {
                c.isLetterOrDigit() && c.code < 128 || c in "-_.~" -> append(c)
                c == ' ' -> append("%20")
                else -> c.toString().encodeToByteArray().forEach { b -> append('%'); append(((b.toInt() and 0xff) or 0x100).toString(16).substring(1).uppercase()) }
            }
        }
    }
}
