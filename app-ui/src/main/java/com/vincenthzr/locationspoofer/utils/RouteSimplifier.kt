package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Iterative Douglas–Peucker simplification.
 *
 * Semantic waypoints (dwell, elevation or timestamp) split the route into independent
 * simplification ranges, so geometry on either side of a mandatory point is never
 * approximated by a segment that skips across that point.
 */
object RouteSimplifier {
    fun simplify(points: List<RoutePoint>, toleranceMeters: Double): List<RoutePoint> {
        require(toleranceMeters.isFinite() && toleranceMeters >= 0.0) {
            "Tolerance must be finite and non-negative"
        }
        if (points.size <= 2 || toleranceMeters == 0.0) {
            validate(points)
            return points.toList()
        }
        validate(points)

        val mandatory = points.indices.filter { index ->
            index == 0 || index == points.lastIndex || points[index].isSemanticWaypoint()
        }
        val keep = BooleanArray(points.size)
        mandatory.forEach { keep[it] = true }

        // Simplify each range between mandatory points independently.
        for (rangeIndex in 0 until mandatory.lastIndex) {
            simplifyRange(points, mandatory[rangeIndex], mandatory[rangeIndex + 1], toleranceMeters, keep)
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    private fun RoutePoint.isSemanticWaypoint(): Boolean =
        waitSec != 0.0 || elevationMeters != null || timestampEpochMillis != null

    private fun validate(points: List<RoutePoint>) {
        points.forEach { point ->
            require(point.lat.isFinite() && point.lat in -90.0..90.0) {
                "Latitude is out of range"
            }
            require(point.lng.isFinite() && point.lng in -180.0..180.0) {
                "Longitude is out of range"
            }
            require(point.waitSec.isFinite() && point.waitSec in 0.0..86_400.0) {
                "Waypoint dwell time is out of range"
            }
            val elevation = point.elevationMeters
            require(elevation == null || elevation.isFinite()) { "Waypoint elevation is invalid" }
            val timestamp = point.timestampEpochMillis
            require(timestamp == null || timestamp >= 0L) {
                "Waypoint timestamp is invalid"
            }
        }
    }

    private fun simplifyRange(
        points: List<RoutePoint>,
        start: Int,
        end: Int,
        toleranceMeters: Double,
        keep: BooleanArray
    ) {
        if (end - start <= 1) return
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(start to end)

        while (stack.isNotEmpty()) {
            val (rangeStart, rangeEnd) = stack.removeLast()
            if (rangeEnd - rangeStart <= 1) continue
            var maxDistance = -1.0
            var maxIndex = -1
            for (index in rangeStart + 1 until rangeEnd) {
                val distance = pointToSegmentDistance(
                    points[index], points[rangeStart], points[rangeEnd]
                )
                if (distance > maxDistance) {
                    maxDistance = distance
                    maxIndex = index
                }
            }
            if (maxDistance > toleranceMeters && maxIndex > rangeStart) {
                keep[maxIndex] = true
                stack.addLast(rangeStart to maxIndex)
                stack.addLast(maxIndex to rangeEnd)
            }
        }
    }

    private fun pointToSegmentDistance(point: RoutePoint, start: RoutePoint, end: RoutePoint): Double {
        val meanLatitude = Math.toRadians((start.lat + end.lat + point.lat) / 3.0)
        val xScale = 111_320.0 * cos(meanLatitude)
        val yScale = 110_574.0
        fun deltaLongitude(from: Double, to: Double): Double {
            var delta = to - from
            while (delta > 180.0) delta -= 360.0
            while (delta < -180.0) delta += 360.0
            return delta
        }
        val dx = deltaLongitude(start.lng, end.lng) * xScale
        val dy = (end.lat - start.lat) * yScale
        val px = deltaLongitude(start.lng, point.lng) * xScale
        val py = (point.lat - start.lat) * yScale
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-12) return sqrt(px * px + py * py)
        val t = ((px * dx + py * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val ex = px - t * dx
        val ey = py - t * dy
        return sqrt(ex * ex + ey * ey)
    }
}
