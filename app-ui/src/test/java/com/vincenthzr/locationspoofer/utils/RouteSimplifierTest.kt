package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSimplifierTest {
    private val start = LatLng(31.0, 121.0)

    @Test
    fun removesCollinearIntermediatePointsWithinTolerance() {
        val points = (0..20).map { index ->
            val coordinate = GeoMath.destination(start, 90.0, index * 10.0)
            RoutePoint(coordinate.lat, coordinate.lng)
        }
        val simplified = RouteSimplifier.simplify(points, 1.0)
        assertEquals(2, simplified.size)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
    }

    @Test
    fun preservesSignificantBendsAndWaypointMetadata() {
        val east = GeoMath.destination(start, 90.0, 100.0)
        val north = GeoMath.destination(east, 0.0, 30.0)
        val end = GeoMath.destination(start, 90.0, 200.0)
        val points = listOf(
            RoutePoint(start.lat, start.lng),
            RoutePoint(east.lat, east.lng),
            RoutePoint(north.lat, north.lng, waitSec = 15.0, elevationMeters = 12.0, timestampEpochMillis = 1234L),
            RoutePoint(end.lat, end.lng)
        )
        val simplified = RouteSimplifier.simplify(points, 5.0)
        assertTrue(simplified.size >= 3)
        assertTrue(simplified.any { it.waitSec == 15.0 && it.elevationMeters == 12.0 && it.timestampEpochMillis == 1234L })

        val collinearMetadata = RoutePoint(east.lat, east.lng, waitSec = 5.0)
        val straight = RouteSimplifier.simplify(listOf(points.first(), collinearMetadata, points.last()), 5.0)
        assertTrue(straight.contains(collinearMetadata))
    }

    @Test
    fun zeroToleranceKeepsAllPoints() {
        val points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001), RoutePoint(0.0, 0.002))
        assertEquals(points, RouteSimplifier.simplify(points, 0.0))
    }
}
