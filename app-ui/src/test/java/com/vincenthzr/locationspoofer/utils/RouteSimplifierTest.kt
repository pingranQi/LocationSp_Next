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
    @Test
    fun semanticWaypointSplitsIndependentGeometryRanges() {
        val anchor = RoutePoint(31.0003, 121.0009, waitSec = 8.0)
        val before = listOf(
            RoutePoint(31.0000, 121.0000),
            RoutePoint(31.0002, 121.0003),
            RoutePoint(31.0000, 121.0006),
            anchor
        )
        val after = listOf(
            anchor,
            RoutePoint(31.0006, 121.0012),
            RoutePoint(31.0003, 121.0015)
        )
        val route = before + after.drop(1)
        val simplified = RouteSimplifier.simplify(route, 12.0)
        val independentlySimplified = RouteSimplifier.simplify(before, 12.0) +
            RouteSimplifier.simplify(after, 12.0).drop(1)

        assertEquals(independentlySimplified, simplified)
        assertTrue(simplified.contains(anchor))
    }

    @Test
    fun rejectsInvalidWaypointMetadataEvenWhenGeometryWouldBeUnchanged() {
        val invalidWaitFailure = runCatching {
            RouteSimplifier.simplify(
                listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001, waitSec = Double.NaN)),
                0.0
            )
        }.exceptionOrNull()
        assertTrue(invalidWaitFailure is IllegalArgumentException)

        val invalidTimestampFailure = runCatching {
            RouteSimplifier.simplify(
                listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001, timestampEpochMillis = -1L)),
                1.0
            )
        }.exceptionOrNull()
        assertTrue(invalidTimestampFailure is IllegalArgumentException)
    }

    @Test
    fun simplifiesAcrossAntimeridianUsingShortestLongitudeDelta() {
        val points = listOf(
            RoutePoint(10.0, 179.99),
            RoutePoint(10.0, 179.995),
            RoutePoint(10.0, -180.0),
            RoutePoint(10.0, -179.995)
        )
        val simplified = RouteSimplifier.simplify(points, 2.0)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertTrue(simplified.size <= points.size)
    }

}
