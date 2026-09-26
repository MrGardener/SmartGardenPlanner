package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrrigationRouteCalculatorTest {

    private val calculator = IrrigationRouteCalculator()

    @Test
    fun calculateDripRoute_emptyInput_returnsEmptyRoute() {
        assertEquals(0, calculator.calculateDripRoute(emptyList()).size)
    }

    @Test
    fun calculateDripRoute_singleNode_returnsSingleStop() {
        val nodes = listOf(PlantedNodeEntity(id = 1, plotId = 1, seedCode = "SOL-LYC", coordinateXM = 3f, coordinateYM = 4f))

        val route = calculator.calculateDripRoute(nodes)

        assertEquals(1, route.size)
        assertEquals(1L, route.first().nodeId)
    }

    @Test
    fun calculateDripRoute_visitsAllNodesExactlyOnce() {
        val nodes = listOf(
            PlantedNodeEntity(id = 1, plotId = 1, seedCode = "A", coordinateXM = 0f, coordinateYM = 0f),
            PlantedNodeEntity(id = 2, plotId = 1, seedCode = "A", coordinateXM = 5f, coordinateYM = 0f),
            PlantedNodeEntity(id = 3, plotId = 1, seedCode = "A", coordinateXM = 1f, coordinateYM = 1f)
        )

        val route = calculator.calculateDripRoute(nodes)

        assertEquals(3, route.size)
        assertEquals(setOf(1L, 2L, 3L), route.map { it.nodeId }.toSet())
    }

    @Test
    fun calculateDripRoute_startsFromNodeClosestToOrigin() {
        val nodes = listOf(
            PlantedNodeEntity(id = 1, plotId = 1, seedCode = "A", coordinateXM = 10f, coordinateYM = 10f),
            PlantedNodeEntity(id = 2, plotId = 1, seedCode = "A", coordinateXM = 0.5f, coordinateYM = 0.5f)
        )

        val route = calculator.calculateDripRoute(nodes)

        assertEquals(2L, route.first().nodeId)
    }

    @Test
    fun totalRouteLengthM_matchesKnownGeometry() {
        // Simple 3-4-5 right triangle style path for an exact, checkable expected length.
        val nodes = listOf(
            PlantedNodeEntity(id = 1, plotId = 1, seedCode = "A", coordinateXM = 0f, coordinateYM = 0f),
            PlantedNodeEntity(id = 2, plotId = 1, seedCode = "A", coordinateXM = 3f, coordinateYM = 4f)
        )
        val route = calculator.calculateDripRoute(nodes)

        val length = calculator.totalRouteLengthM(route)

        assertTrue(Math.abs(length - 5.0f) < 0.01f)
    }
}
