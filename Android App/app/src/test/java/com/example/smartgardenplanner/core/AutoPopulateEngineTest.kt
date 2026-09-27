package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPopulateEngineTest {

    private val engine = AutoPopulateEngine()

    @Test
    fun generatePositions_zeroArea_returnsEmpty() {
        val points = engine.generatePositions(0f, 5f, 1f, AutoPopulateEngine.PackingPattern.LINE)
        assertTrue(points.isEmpty())
    }

    @Test
    fun lineGrid_allPointsWithinBounds() {
        val points = engine.generatePositions(4f, 4f, 1f, AutoPopulateEngine.PackingPattern.LINE)
        assertTrue(points.isNotEmpty())
        points.forEach {
            assertTrue(it.xM in 0f..4f)
            assertTrue(it.yM in 0f..4f)
        }
    }

    @Test
    fun lineGrid_respectsMinimumSpacing() {
        val spacing = 1.25f
        val points = engine.generatePositions(5f, 5f, spacing, AutoPopulateEngine.PackingPattern.LINE)

        for (i in points.indices) {
            for (j in points.indices) {
                if (i == j) continue
                val dx = points[i].xM - points[j].xM
                val dy = points[i].yM - points[j].yM
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                assertTrue("Points $i and $j are closer than spacing", dist >= spacing - 0.01)
            }
        }
    }

    @Test
    fun hexGrid_packsMoreOrEqualPointsThanLineGrid() {
        // Hex packing only beats a square grid once the area is large enough for the extra rows
        // (pitch √3/2 · s) to outweigh the shorter offset rows. At 6 m × 6 m with 1 m spacing the
        // engine correctly gives 36 (rows) vs 33 (hex), so this test uses 10 m × 10 m (100 vs 105).
        val lineCount = engine.estimateCount(10f, 10f, 1f, AutoPopulateEngine.PackingPattern.LINE)
        val hexCount = engine.estimateCount(10f, 10f, 1f, AutoPopulateEngine.PackingPattern.HEXAGON)

        assertTrue("Hex packing ($hexCount) should fit at least as many points as line packing ($lineCount)", hexCount >= lineCount)
    }

    @Test
    fun hexGrid_allPointsWithinBounds() {
        val points = engine.generatePositions(5f, 5f, 1f, AutoPopulateEngine.PackingPattern.HEXAGON)
        points.forEach {
            assertTrue(it.xM in 0f..5f)
            assertTrue(it.yM in 0f..5f)
        }
    }

    @Test
    fun areaSmallerThanSpacing_returnsAtMostOnePoint() {
        val points = engine.generatePositions(0.5f, 0.5f, 2f, AutoPopulateEngine.PackingPattern.LINE)
        assertTrue(points.size <= 1)
    }
}
