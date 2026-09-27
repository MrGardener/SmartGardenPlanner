package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shade overlay bands and layout colours shared by the phone and the web planner (FR-006). */
class LayoutPaletteTest {

    @Test
    fun bandsFollowTheSunHourThresholds() {
        assertEquals(SunBand.FULL_SHADE, SunBand.of(0f))
        assertEquals(SunBand.FULL_SHADE, SunBand.of(2.99f))
        assertEquals(SunBand.PART_SHADE, SunBand.of(3f))
        assertEquals(SunBand.PART_SHADE, SunBand.of(5.99f))
        assertEquals(SunBand.FULL_SUN, SunBand.of(6f))
        assertEquals(SunBand.FULL_SUN, SunBand.of(14f))
    }

    @Test
    fun bandThresholdsMatchTheMarkedSunAreas() {
        // Marked areas count as 8 / 4.5 / 2 h (SunlightEngine.effectiveSunHours); they must land in the matching band.
        assertEquals(SunBand.FULL_SUN, SunBand.of(8f))
        assertEquals(SunBand.PART_SHADE, SunBand.of(4.5f))
        assertEquals(SunBand.FULL_SHADE, SunBand.of(2f))
    }

    /** Relative luminance (WCAG) of an ARGB colour composited over the paper background. */
    private fun luminanceOnPaper(argb: Long): Double {
        val a = LayoutPalette.alpha(argb)
        fun ch(shift: Int): Double {
            val over = ((argb shr shift) and 0xFF) / 255.0
            val under = ((LayoutPalette.PAPER shr shift) and 0xFF) / 255.0
            val c = over * a + under * (1 - a)
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    @Test
    fun bandsDifferInLightness_soTheyReadWithoutColourVision() {
        val sun = luminanceOnPaper(SunBand.FULL_SUN.overlayArgb)
        val part = luminanceOnPaper(SunBand.PART_SHADE.overlayArgb)
        val shade = luminanceOnPaper(SunBand.FULL_SHADE.overlayArgb)
        assertTrue("sun $sun > part $part > shade $shade", sun > part && part > shade)
        assertTrue("shade must contrast with sun at least 3:1", (sun + 0.05) / (shade + 0.05) >= 3.0)
    }

    @Test
    fun hexAndAlpha() {
        assertEquals("#312e81", LayoutPalette.hex(SunBand.FULL_SHADE.overlayArgb))
        assertEquals(0xA6 / 255.0, LayoutPalette.alpha(SunBand.FULL_SHADE.overlayArgb), 1e-9)
        assertEquals("#f5f1e6", LayoutPalette.hex(LayoutPalette.PAPER))
    }
}
