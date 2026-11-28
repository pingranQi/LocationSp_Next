package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.model.RouteRunMode
import com.vincenthzr.locationspoofer.data.model.SimMode
import com.vincenthzr.locationspoofer.utils.CoordinateUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Portable route + simulation preset. The format is versioned independently of the Room schema. */
object RouteScenarioCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_POINTS = 100_000
    private const val FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class PointPayload(
        val lat: Double,
        val lng: Double,
        val waitSec: Double = 0.0,
        val elevationMeters: Double? = null,
        val timestampEpochMillis: Long? = null
    )

    @Serializable
    private data class ScenarioPayload(
        val format: String,
        val version: Int,
        val name: String,
        val points: List<PointPayload>,
        val runMode: String = RouteRunMode.LOOP.name,
        val speedMode: String = SimMode.WALKING.name,
        val customSpeedMs: Double = 3.0,
        val useRealRoute: Boolean = false,
        val stopAtDestination: Boolean = false,
        val enableStepSimulation: Boolean = true,
        val stepCadenceSpm: Int = 165,
        val isAutoCadence: Boolean = true,
        val paceVariationSec: Int = 0,
        val fatigueLevel: Int = 0,
        val firstKmBiasSec: Int = 0,
        val fatigueStartKm: String = "2.0",
        val mockWifi: Boolean = true,
        val mockCell: Boolean = true,
        val mockBluetooth: Boolean = true,
        val enableJitter: Boolean = true,
        val altitudeInput: String = "0.0",
        val altitudeVariationM: Int = 10,
        val satelliteCountInput: String = "20",
        val coordinateSystem: String = "WGS84"
    )

    data class Scenario(
        val name: String,
        val points: List<RoutePoint>,
        val runMode: RouteRunMode = RouteRunMode.LOOP,
        val speedMode: SimMode = SimMode.WALKING,
        val customSpeedMs: Double = 3.0,
        val useRealRoute: Boolean = false,
        val stopAtDestination: Boolean = false,
        val enableStepSimulation: Boolean = true,
        val stepCadenceSpm: Int = 165,
        val isAutoCadence: Boolean = true,
        val paceVariationSec: Int = 0,
        val fatigueLevel: Int = 0,
        val firstKmBiasSec: Int = 0,
        val fatigueStartKm: String = "2.0",
        val mockWifi: Boolean = true,
        val mockCell: Boolean = true,
        val mockBluetooth: Boolean = true,
        val enableJitter: Boolean = true,
        val altitudeInput: String = "0.0",
        val altitudeVariationM: Int = 10,
        val satelliteCountInput: String = "20",
        val coordinateSystem: String = "WGS84"
    )

    fun encode(scenario: Scenario): String {
        validate(scenario)
        val payload = ScenarioPayload(
            format = "location-spoofing-scenario",
            version = FORMAT_VERSION,
            name = scenario.name,
            points = scenario.points.map {
                PointPayload(it.lat, it.lng, it.waitSec, it.elevationMeters, it.timestampEpochMillis)
            },
            runMode = scenario.runMode.name,
            speedMode = scenario.speedMode.name,
            customSpeedMs = scenario.customSpeedMs,
            useRealRoute = scenario.useRealRoute,
            stopAtDestination = scenario.stopAtDestination,
            enableStepSimulation = scenario.enableStepSimulation,
            stepCadenceSpm = scenario.stepCadenceSpm,
            isAutoCadence = scenario.isAutoCadence,
            paceVariationSec = scenario.paceVariationSec,
            fatigueLevel = scenario.fatigueLevel,
            firstKmBiasSec = scenario.firstKmBiasSec,
            fatigueStartKm = scenario.fatigueStartKm,
            mockWifi = scenario.mockWifi,
            mockCell = scenario.mockCell,
            mockBluetooth = scenario.mockBluetooth,
            enableJitter = scenario.enableJitter,
            altitudeInput = scenario.altitudeInput,
            altitudeVariationM = scenario.altitudeVariationM,
            satelliteCountInput = scenario.satelliteCountInput,
            coordinateSystem = scenario.coordinateSystem
        )
        val encoded = json.encodeToString(payload)
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Scenario file exceeds size limit" }
        return encoded
    }

    fun decode(source: String): Scenario {
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Scenario file exceeds size limit" }
        val payload = json.decodeFromString<ScenarioPayload>(source)
        require(payload.format == "location-spoofing-scenario") { "Unsupported scenario format" }
        require(payload.version == FORMAT_VERSION) { "Unsupported scenario version" }
        val scenario = Scenario(
            name = payload.name.ifBlank { "Imported scenario" },
            points = normalizeCoordinates(payload.points.map {
                RoutePoint(it.lat, it.lng, it.waitSec, it.elevationMeters, it.timestampEpochMillis)
            }, payload.coordinateSystem),
            runMode = enumValueOrDefault(payload.runMode, RouteRunMode.LOOP),
            speedMode = enumValueOrDefault(payload.speedMode, SimMode.WALKING),
            customSpeedMs = payload.customSpeedMs,
            useRealRoute = payload.useRealRoute,
            stopAtDestination = payload.stopAtDestination,
            enableStepSimulation = payload.enableStepSimulation,
            stepCadenceSpm = payload.stepCadenceSpm,
            isAutoCadence = payload.isAutoCadence,
            paceVariationSec = payload.paceVariationSec,
            fatigueLevel = payload.fatigueLevel,
            firstKmBiasSec = payload.firstKmBiasSec,
            fatigueStartKm = payload.fatigueStartKm,
            mockWifi = payload.mockWifi,
            mockCell = payload.mockCell,
            mockBluetooth = payload.mockBluetooth,
            enableJitter = payload.enableJitter,
            altitudeInput = payload.altitudeInput,
            altitudeVariationM = payload.altitudeVariationM,
            satelliteCountInput = payload.satelliteCountInput,
            coordinateSystem = "WGS84"
        )
        validate(scenario)
        return scenario
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
    
    private fun normalizeCoordinates(points: List<RoutePoint>, sourceSystem: String): List<RoutePoint> =
        when (sourceSystem) {
            "WGS84" -> points
            "GCJ02" -> points.map { point ->
                val wgs = CoordinateUtils.gcj02ToWgs84(point.lat, point.lng)
                point.copy(lat = wgs.lat, lng = wgs.lng)
            }
            "BD09" -> points.map { point ->
                val gcj = CoordinateUtils.bd09ToGcj02(point.lat, point.lng)
                val wgs = CoordinateUtils.gcj02ToWgs84(gcj.lat, gcj.lng)
                point.copy(lat = wgs.lat, lng = wgs.lng)
            }
            else -> throw IllegalArgumentException("Coordinate system is unsupported")
        }


    private fun validate(scenario: Scenario) {
        require(scenario.name.isNotBlank() && scenario.name.length <= 256) { "Scenario name is invalid" }
        require(scenario.coordinateSystem in setOf("WGS84", "GCJ02", "BD09")) { "Coordinate system is unsupported" }
        require(scenario.points.size in 2..MAX_POINTS) { "Scenario must contain between 2 and $MAX_POINTS route points" }
        scenario.points.forEach { point ->
            require(point.lat.isFinite() && point.lat in -90.0..90.0) { "Latitude is out of range" }
            require(point.lng.isFinite() && point.lng in -180.0..180.0) { "Longitude is out of range" }
            require(point.waitSec.isFinite() && point.waitSec in 0.0..86_400.0) { "Waypoint dwell time is out of range" }
            val elevation = point.elevationMeters
            require(elevation == null || elevation.isFinite()) { "Waypoint elevation is invalid" }
        }
        require(scenario.customSpeedMs.isFinite() && scenario.customSpeedMs in 0.1..100.0) { "Custom speed is out of range" }
        require(scenario.stepCadenceSpm in 30..300) { "Step cadence is out of range" }
        require(scenario.paceVariationSec in 0..30) { "Pace variation is out of range" }
        require(scenario.fatigueLevel in 0..2) { "Fatigue level is out of range" }
        require(scenario.firstKmBiasSec in -30..0) { "First kilometre bias is out of range" }
        val fatigueStart = scenario.fatigueStartKm.toDoubleOrNull()
        require(fatigueStart != null && fatigueStart.isFinite() && fatigueStart in 0.0..42.0) {
            "Fatigue start distance is invalid"
        }
        val altitude = scenario.altitudeInput.toDoubleOrNull()
        require(altitude != null && altitude.isFinite() && altitude in -500.0..10_000.0) { "Altitude is invalid" }
        require(scenario.altitudeVariationM in 0..100) { "Altitude variation is out of range" }
        val satellites = scenario.satelliteCountInput.toIntOrNull()
        require(satellites != null && satellites in 0..80) { "Satellite count is invalid" }
    }
}
