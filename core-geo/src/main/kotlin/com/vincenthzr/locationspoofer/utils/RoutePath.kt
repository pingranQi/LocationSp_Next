package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    const val EARTH_RADIUS_M = 6378137.0

    fun distance(a: LatLng, b: LatLng): Double {
        require(a.lat.isFinite() && a.lng.isFinite() && b.lat.isFinite() && b.lng.isFinite())
        require(a.lat in -90.0..90.0 && b.lat in -90.0..90.0 && a.lng in -180.0..180.0 && b.lng in -180.0..180.0)
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(shortestLongitudeDelta(a.lng, b.lng))
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        // Floating-point rounding near antipodal points can push h slightly outside [0, 1].
        val boundedH = h.coerceIn(0.0, 1.0)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(boundedH), sqrt(1 - boundedH))
    }

    /** 从 from 指向 to 的方位角（度，正北为 0，顺时针） */
    fun bearing(from: LatLng, to: LatLng): Float {
        require(from.lat.isFinite() && from.lng.isFinite() && to.lat.isFinite() && to.lng.isFinite())
        require(from.lat in -90.0..90.0 && to.lat in -90.0..90.0 && from.lng in -180.0..180.0 && to.lng in -180.0..180.0)
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(shortestLongitudeDelta(from.lng, to.lng))
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    private fun shortestLongitudeDelta(from: Double, to: Double): Double =
        ((to - from + 540.0) % 360.0) - 180.0

    /** Normalize longitude to [-180, 180), including antimeridian crossings. */
    private fun normalizeLongitude(longitude: Double): Double =
        ((longitude + 540.0) % 360.0) - 180.0

    /** 从 start 沿 bearing 方向前进 meters 米后的位置 */
    fun destination(start: LatLng, bearingDeg: Double, meters: Double): LatLng {
        require(start.lat.isFinite() && start.lng.isFinite() && start.lat in -90.0..90.0 && start.lng in -180.0..180.0)
        require(bearingDeg.isFinite() && meters.isFinite())
        val angular = meters / EARTH_RADIUS_M
        val bearingRad = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(start.lat)
        val lng1 = Math.toRadians(start.lng)
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearingRad))
        val lng2 = lng1 + atan2(sin(bearingRad) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
        return LatLng(Math.toDegrees(lat2), normalizeLongitude(Math.toDegrees(lng2)))
    }
}

/**
 * 一条路线上"已行进距离 → 位置"的映射，App 和 Xposed 两端共用，保证两边对同一距离算出同一位置。
 *
 * 规则：首尾相距 5 米以内视为闭环，走到终点后从起点继续；否则走到终点后原路折返，来回往复。
 * stopAtDestination 为真时走到终点就停下。
 */
class RoutePath(points: List<LatLng>, waitSeconds: List<Double> = emptyList()) {
    /** Immutable snapshot: callers may safely reuse or mutate their source collection. */
    val points: List<LatLng> = points.toList()
    val waitSeconds: List<Double> = if (waitSeconds.isEmpty()) List(points.size) { 0.0 } else waitSeconds.toList()

    init {
        require(this.points.all { point ->
            point.lat.isFinite() && point.lng.isFinite() &&
                point.lat in -90.0..90.0 && point.lng in -180.0..180.0
        }) { "route points must contain finite, in-range coordinates" }
        require(this.waitSeconds.size == this.points.size && this.waitSeconds.all { it.isFinite() && it in 0.0..86_400.0 }) {
            "route waypoint dwell times must be finite and between 0 and 86400 seconds"
        }
    }

    data class Position(val lat: Double, val lng: Double, val bearing: Float, val arrived: Boolean)

    private val cumulative: DoubleArray = DoubleArray(this.points.size).also { cum ->
        for (i in 1 until this.points.size) cum[i] = cum[i - 1] + GeoMath.distance(this.points[i - 1], this.points[i])
    }

    val totalDistance: Double = if (points.isEmpty()) 0.0 else cumulative.last()
    val isClosedLoop: Boolean = this.points.size >= 2 && GeoMath.distance(this.points.first(), this.points.last()) <= 5.0
    val isValid: Boolean get() = points.size >= 2 && totalDistance > 0.0

    private val forwardDwellEvents: List<Pair<Double, Double>> =
        (1 until points.size).mapNotNull { index ->
            waitSeconds[index].takeIf { it > 0.0 }?.let { cumulative[index] to it }
        }

    private val outAndBackDwellEvents: List<Pair<Double, Double>> =
        (forwardDwellEvents + (points.lastIndex - 1 downTo 0).mapNotNull { index ->
            waitSeconds[index].takeIf { it > 0.0 }?.let { (2.0 * totalDistance - cumulative[index]) to it }
        }).sortedBy { it.first }

    data class TimedProgress(val distanceM: Double, val isDwelling: Boolean)

    fun distanceAtPoint(index: Int): Double {
        require(index in cumulative.indices)
        return cumulative[index]
    }

    /** Shared deterministic waypoint dwell scheduling for the app and location hook. */
    fun progressAfter(
        profile: PaceProfile,
        offsetMeters: Double,
        elapsedSeconds: Double,
        stopAtDestination: Boolean
    ): TimedProgress {
        require(offsetMeters.isFinite() && offsetMeters >= 0.0)
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0)
        if (!isValid || elapsedSeconds <= 0.0) return TimedProgress(offsetMeters, false)

        val cycleDistance = if (stopAtDestination || isClosedLoop) totalDistance else totalDistance * 2.0
        if (cycleDistance <= 0.0) return TimedProgress(offsetMeters, false)
        val rawMaximum = profile.distanceAfter(offsetMeters, elapsedSeconds)
        val maximum = if (stopAtDestination) rawMaximum.coerceAtMost(totalDistance) else rawMaximum
        val events = if (!stopAtDestination && !isClosedLoop) outAndBackDwellEvents else forwardDwellEvents
        if (events.isEmpty()) return TimedProgress(maximum, false)

        var consumedWait = 0.0
        val firstCycle = kotlin.math.floor(offsetMeters / cycleDistance).toLong().coerceAtLeast(0L)
        val lastCycle = kotlin.math.floor(maximum / cycleDistance).toLong().coerceAtLeast(firstCycle)
        val offsetWithinCycle = offsetMeters - firstCycle * cycleDistance
        var low = 0
        var high = events.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (events[middle].first <= offsetWithinCycle + 1e-6) low = middle + 1 else high = middle
        }
        var cycle = firstCycle
        var eventCount = 0
        while (cycle <= lastCycle && eventCount < 100_000) {
            val firstIndex = if (cycle == firstCycle) low else 0
            for (index in firstIndex until events.size) {
                val (eventOffset, wait) = events[index]
                val eventDistance = cycle * cycleDistance + eventOffset
                if (eventDistance > maximum + 1e-6) break
                eventCount++
                val travelTime = (profile.elapsedAt(eventDistance) - profile.elapsedAt(offsetMeters)).coerceAtLeast(0.0)
                val arrivalTime = travelTime + consumedWait
                if (elapsedSeconds < arrivalTime) {
                    return TimedProgress(profile.distanceAfter(offsetMeters, (elapsedSeconds - consumedWait).coerceAtLeast(0.0)), false)
                }
                if (elapsedSeconds < arrivalTime + wait) {
                    return TimedProgress(eventDistance, true)
                }
                consumedWait += wait
            }
            cycle++
        }

        val distance = profile.distanceAfter(offsetMeters, (elapsedSeconds - consumedWait).coerceAtLeast(0.0))
        return TimedProgress(if (stopAtDestination) distance.coerceAtMost(totalDistance) else distance, false)
    }

    fun distanceAfter(
        profile: PaceProfile,
        offsetMeters: Double,
        elapsedSeconds: Double,
        stopAtDestination: Boolean
    ): Double = progressAfter(profile, offsetMeters, elapsedSeconds, stopAtDestination).distanceM

    /**
     * Find the segment containing [along] in O(log n), then skip any zero-length
     * segments at the same cumulative distance. This matters for recorded tracks
     * that can contain repeated fixes while the device is stationary.
     */
    private fun segmentIndex(along: Double): Int {
        var low = 0
        var high = cumulative.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (cumulative[middle] < along) low = middle + 1 else high = middle
        }

        var segment = (low - 1).coerceAtLeast(0)
        while (segment < points.size - 2 &&
            cumulative[segment + 1] <= cumulative[segment]
        ) {
            segment++
        }
        return segment.coerceAtMost(points.size - 2)
    }

    fun positionAt(distanceTraveled: Double, stopAtDestination: Boolean): Position {
        require(distanceTraveled.isFinite()) { "distance traveled must be finite" }
        require(isValid) { "route needs at least two distinct points" }
        val traveled = distanceTraveled.coerceAtLeast(0.0)

        if (stopAtDestination && traveled >= totalDistance) {
            val last = points.last()
            return Position(last.lat, last.lng, GeoMath.bearing(points[points.size - 2], last), arrived = true)
        }

        val forward: Boolean
        val along: Double
        if (isClosedLoop) {
            forward = true
            along = traveled % totalDistance
        } else {
            val cycle = totalDistance * 2
            val inCycle = traveled % cycle
            forward = inCycle <= totalDistance
            along = if (forward) inCycle else cycle - inCycle
        }

        val seg = segmentIndex(along)
        val from = points[seg]
        val to = points[seg + 1]
        val segLen = (cumulative[seg + 1] - cumulative[seg]).coerceAtLeast(0.0001)
        val ratio = ((along - cumulative[seg]) / segLen).coerceIn(0.0, 1.0)
        val segmentBearing = GeoMath.bearing(from, to)
        val movementBearing = if (forward) segmentBearing else GeoMath.bearing(to, from)
        // Interpolate along the same great-circle arc used by distance/bearing math.
        // Linear latitude/longitude interpolation visibly drifts at high latitudes.
        val position = GeoMath.destination(from, segmentBearing.toDouble(), segLen * ratio)
        return Position(
            position.lat,
            position.lng,
            movementBearing,
            arrived = false
        )
    }
}
