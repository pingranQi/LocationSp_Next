package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePathTest {

    private val a = LatLng(30.0, 120.0)
    private val b = GeoMath.destination(a, 90.0, 1000.0) // 正东 1000 米

    @Test
    fun `destination and distance are consistent`() {
        assertEquals(1000.0, GeoMath.distance(a, b), 0.01)
        assertEquals(90.0, GeoMath.bearing(a, b).toDouble(), 0.01)
    }

    @Test
    fun `open route goes back and forth`() {
        val path = RoutePath(listOf(a, b))
        assertFalse(path.isClosedLoop)
        val out = path.positionAt(400.0, stopAtDestination = false)
        assertEquals(400.0, GeoMath.distance(a, LatLng(out.lat, out.lng)), 0.5)
        assertEquals(90.0, out.bearing.toDouble(), 0.5)
        val back = path.positionAt(1600.0, stopAtDestination = false) // 折返途中，距起点 400 米
        assertEquals(400.0, GeoMath.distance(a, LatLng(back.lat, back.lng)), 0.5)
        assertEquals(270.0, back.bearing.toDouble(), 0.5)
    }

    @Test
    fun `closed loop keeps going forward`() {
        val c = GeoMath.destination(b, 0.0, 1000.0)
        val path = RoutePath(listOf(a, b, c, a))
        assertTrue(path.isClosedLoop)
        val lap = path.positionAt(path.totalDistance + 300.0, stopAtDestination = false)
        assertEquals(300.0, GeoMath.distance(a, LatLng(lap.lat, lap.lng)), 0.5)
    }

    @Test
    fun `defensively copies source point list`() {
        val source = mutableListOf(a, b)
        val path = RoutePath(source)
        source[1] = LatLng(Double.NaN, Double.NaN)
        assertEquals(1000.0, path.totalDistance, 0.01)
        assertEquals(500.0, GeoMath.distance(a, LatLng(path.positionAt(500.0, false).lat, path.positionAt(500.0, false).lng)), 0.5)
    }

    @Test
    fun `interpolates across the antimeridian using the short direction`() {
        val west = LatLng(0.0, 179.9)
        val east = LatLng(0.0, -179.9)
        val path = RoutePath(listOf(west, east))

        val midpoint = path.positionAt(path.totalDistance / 2.0, stopAtDestination = false)

        assertEquals(0.0, midpoint.lat, 1e-9)
        assertTrue(kotlin.math.abs(midpoint.lng) > 179.99)
        assertTrue(kotlin.math.abs(midpoint.lng) <= 180.0)
        assertEquals(GeoMath.distance(west, east) / 2.0,
            GeoMath.distance(west, LatLng(midpoint.lat, midpoint.lng)), 0.5)
    }

    @Test
    fun `interpolates along a great-circle arc at high latitudes`() {
        val start = LatLng(70.0, 10.0)
        val end = GeoMath.destination(start, 90.0, 100_000.0)
        val path = RoutePath(listOf(start, end))
        val midpoint = path.positionAt(path.totalDistance / 2.0, stopAtDestination = false)

        assertTrue(midpoint.lat > start.lat)
        assertEquals(path.totalDistance / 2.0,
            GeoMath.distance(start, LatLng(midpoint.lat, midpoint.lng)), 0.5)
        assertEquals(90.0, midpoint.bearing.toDouble(), 0.5)
    }

    @Test
    fun `destination normalizes longitude after crossing antimeridian`() {
        val start = LatLng(0.0, 179.9)
        val destination = GeoMath.destination(start, 90.0, 30_000.0)

        assertTrue(destination.lng in -180.0..180.0)
        assertTrue(destination.lng < 0.0)
    }

    @Test
    fun `rejects non-finite and out-of-range route coordinates`() {
        assertThrows(IllegalArgumentException::class.java) {
            RoutePath(listOf(a, LatLng(Double.NaN, 120.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutePath(listOf(a, LatLng(91.0, 120.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutePath(listOf(a, LatLng(30.0, 181.0)))
        }
    }

    @Test
    fun `skips repeated stationary fixes without producing invalid positions`() {
        val c = GeoMath.destination(b, 90.0, 1000.0)
        val path = RoutePath(listOf(a, a, b, b, c))
        val midpoint = path.positionAt(500.0, stopAtDestination = false)

        assertTrue(midpoint.lat.isFinite())
        assertTrue(midpoint.lng.isFinite())
        assertEquals(500.0, GeoMath.distance(a, LatLng(midpoint.lat, midpoint.lng)), 0.5)
    }

    @Test
    fun `holds at a waypoint for its configured dwell duration`() {
        val middle = GeoMath.destination(a, 90.0, 500.0)
        val end = GeoMath.destination(a, 90.0, 1000.0)
        val path = RoutePath(listOf(a, middle, end), listOf(0.0, 10.0, 0.0))
        val profile = PaceProfile.fromConfig(
            baseSpeedMps = 2.0,
            paceVariationSec = 0.0,
            fatigueLevel = 0,
            firstKmBiasSec = 0.0,
            seed = 42L
        )

        val duringWait = path.progressAfter(profile, 0.0, 255.0, stopAtDestination = false)
        assertEquals(500.0, duringWait.distanceM, 0.5)
        assertTrue(duringWait.isDwelling)

        val afterWait = path.progressAfter(profile, 0.0, 265.0, stopAtDestination = false)
        assertEquals(510.0, afterWait.distanceM, 0.5)
        assertFalse(afterWait.isDwelling)
    }

    @Test
    fun `applies waypoint dwell again on the return leg`() {
        val middle = GeoMath.destination(a, 90.0, 500.0)
        val end = GeoMath.destination(a, 90.0, 1000.0)
        val path = RoutePath(listOf(a, middle, end), listOf(0.0, 10.0, 0.0))
        val profile = PaceProfile.fromConfig(
            baseSpeedMps = 2.0,
            paceVariationSec = 0.0,
            fatigueLevel = 0,
            firstKmBiasSec = 0.0,
            seed = 42L
        )

        val returnWait = path.progressAfter(profile, 0.0, 765.0, stopAtDestination = false)
        assertEquals(1500.0, returnWait.distanceM, 0.5)
        assertTrue(returnWait.isDwelling)
    }

    @Test
    fun `stops at the destination when requested`() {
        val path = RoutePath(listOf(a, b))
        val end = path.positionAt(5000.0, stopAtDestination = true)
        assertTrue(end.arrived)
        assertEquals(b.lat, end.lat, 1e-9)
        assertEquals(b.lng, end.lng, 1e-9)
    }
}
