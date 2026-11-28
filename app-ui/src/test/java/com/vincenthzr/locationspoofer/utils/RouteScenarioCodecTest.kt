package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.model.RouteRunMode
import com.vincenthzr.locationspoofer.data.model.SimMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteScenarioCodecTest {
    @Test
    fun roundTripsRouteAndSimulationSettings() {
        val source = RouteScenarioCodec.Scenario(
            name = "Evening loop",
            points = listOf(
                RoutePoint(31.2304, 121.4737, waitSec = 0.0, elevationMeters = 4.5, timestampEpochMillis = 1_700_000_000_000L),
                RoutePoint(31.2310, 121.4740, waitSec = 15.0, elevationMeters = 5.0, timestampEpochMillis = 1_700_000_010_000L)
            ),
            runMode = RouteRunMode.LOOP,
            speedMode = SimMode.CUSTOM,
            customSpeedMs = 4.2,
            useRealRoute = true,
            stopAtDestination = false,
            enableStepSimulation = true,
            stepCadenceSpm = 172,
            isAutoCadence = false,
            paceVariationSec = 12,
            fatigueLevel = 2,
            firstKmBiasSec = -5,
            fatigueStartKm = "3.5",
            mockWifi = false,
            mockCell = true,
            mockBluetooth = false,
            enableJitter = false,
            altitudeInput = "120.5",
            altitudeVariationM = 35,
            satelliteCountInput = "18"
        )

        val decoded = RouteScenarioCodec.decode(RouteScenarioCodec.encode(source))
        assertEquals(source, decoded)
    }

    @Test
    fun rejectsUnsupportedScenarioVersion() {
        val json = """{"format":"location-spoofing-scenario","version":99,"name":"x","points":[]}"""
        assertThrows(IllegalArgumentException::class.java) {
            RouteScenarioCodec.decode(json)
        }
    }

    @Test
    fun rejectsInvalidCoordinatesAndDwellDurations() {
        val invalidCoordinates = RouteScenarioCodec.Scenario(
            name = "bad",
            points = listOf(RoutePoint(91.0, 0.0), RoutePoint(0.0, 1.0))
        )
        assertThrows(IllegalArgumentException::class.java) {
            RouteScenarioCodec.encode(invalidCoordinates)
        }

        val invalidWait = RouteScenarioCodec.Scenario(
            name = "bad wait",
            points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 1.0, waitSec = 90_000.0))
        )
        assertThrows(IllegalArgumentException::class.java) {
            RouteScenarioCodec.encode(invalidWait)
        }
    }
}
