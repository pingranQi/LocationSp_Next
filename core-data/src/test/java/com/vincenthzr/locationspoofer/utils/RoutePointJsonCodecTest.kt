package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutePointJsonCodecTest {
    @Test
    fun roundTripsAllRoutePointMetadata() {
        val points = listOf(
            RoutePoint(31.2304, 121.4737, 12.5, 4.2, 1_700_000_000_000L),
            RoutePoint(31.2310, 121.4740, 0.0, 5.8, 1_700_000_010_000L)
        )

        assertEquals(points, RoutePointJsonCodec.decode(RoutePointJsonCodec.encode(points)))
    }

    @Test
    fun readsLegacyCoordinateOnlyRouteJson() {
        val points = RoutePointJsonCodec.decode("""[{"lat":31.2,"lng":121.4}]""")

        assertEquals(1, points.size)
        assertEquals(0.0, points.single().waitSec, 0.0)
        assertEquals(null, points.single().elevationMeters)
        assertEquals(null, points.single().timestampEpochMillis)
    }

    @Test
    fun rejectsInvalidRoutePointAndOversizedPayload() {
        assertThrows(IllegalArgumentException::class.java) {
            RoutePointJsonCodec.encode(listOf(RoutePoint(91.0, 0.0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutePointJsonCodec.decode(" ".repeat(RoutePointJsonCodec.MAX_BYTES + 1))
        }
    }
}
