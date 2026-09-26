package com.example.smartgardenplanner.core

import kotlin.math.sqrt

/**
 * [NEW] Implements T2-FUN-060 / HLR-DAT-100: computes an optimized drip-irrigation routing path
 * across all planted nodes on a plot using a greedy nearest-neighbor polyline (a reasonable,
 * cheap approximation of a minimum-spanning traversal — sufficient for garden-scale node counts,
 * where an exact TSP solve would be overkill). Pure Kotlin, unit-testable on the JVM. The caller
 * (Canvas screen) is responsible for rendering the returned ordered coordinate list as a polyline.
 */
class IrrigationRouteCalculator {

    data class RoutePoint(val nodeId: Long, val xM: Float, val yM: Float)

    fun calculateDripRoute(nodes: List<PlantedNodeEntity>): List<RoutePoint> {
        if (nodes.isEmpty()) return emptyList()

        val remaining = nodes.map { RoutePoint(it.id, it.coordinateXM, it.coordinateYM) }.toMutableList()
        val route = mutableListOf<RoutePoint>()

        // Start from the node closest to the origin (hose-bib assumption: water source at (0,0)).
        var current = remaining.minByOrNull { distance(0f, 0f, it.xM, it.yM) }!!
        route.add(current)
        remaining.remove(current)

        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { distance(current.xM, current.yM, it.xM, it.yM) }!!
            route.add(next)
            remaining.remove(next)
            current = next
        }

        return route
    }

    /** Total pipe/hose length in meters for the calculated route — useful for a materials estimate. */
    fun totalRouteLengthM(route: List<RoutePoint>): Float {
        if (route.size < 2) return 0f
        var total = 0f
        for (i in 0 until route.size - 1) {
            total += distance(route[i].xM, route[i].yM, route[i + 1].xM, route[i + 1].yM)
        }
        return total
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}
