package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import kotlin.math.cos
import kotlin.math.sqrt

/** Iterative Douglas–Peucker simplification; retained points keep all waypoint metadata. */
object RouteSimplifier {
    fun simplify(points: List<RoutePoint>, toleranceMeters: Double): List<RoutePoint> {
        require(toleranceMeters.isFinite() && toleranceMeters >= 0.0) { "Tolerance must be finite and non-negative" }
        if (points.size <= 2 || toleranceMeters == 0.0) return points.toList()
        points.forEach {
            require(it.lat.isFinite() && it.lat in -90.0..90.0)
            require(it.lng.isFinite() && it.lng in -180.0..180.0)
        }

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.lastIndex)

        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            if (end - start <= 1) continue
            var maxDistance = -1.0
            var maxIndex = -1
            for (index in start + 1 until end) {
                val distance = pointToSegmentDistance(points[index], points[start], points[end])
                if (distance > maxDistance) {
                    maxDistance = distance
                    maxIndex = index
                }
            }
            if (maxDistance > toleranceMeters && maxIndex > start) {
                keep[maxIndex] = true
                stack.addLast(start to maxIndex)
                stack.addLast(maxIndex to end)
            }
        }

        return points.filterIndexed { index, _ -> keep[index] }
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
