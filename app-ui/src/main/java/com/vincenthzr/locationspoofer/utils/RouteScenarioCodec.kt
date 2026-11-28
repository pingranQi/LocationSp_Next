package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import com.vincenthzr.locationspoofer.data.model.RouteRunMode
import com.vincenthzr.locationspoofer.data.model.SimMode
import org.json.JSONArray
import org.json.JSONObject

/** Portable route + simulation preset. The format is versioned independently of the Room schema. */
object RouteScenarioCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_POINTS = 100_000
    private const val FORMAT_VERSION = 1

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
        val fatigueStartKm: String = "2.0"
    )

    fun encode(scenario: Scenario): String {
        validate(scenario)
        val points = JSONArray()
        scenario.points.forEach { point ->
            points.put(
                JSONObject()
                    .put("lat", point.lat)
                    .put("lng", point.lng)
                    .put("waitSec", point.waitSec)
                    .put("elevationMeters", point.elevationMeters ?: JSONObject.NULL)
                    .put("timestampEpochMillis", point.timestampEpochMillis ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("format", "location-spoofing-scenario")
            .put("version", FORMAT_VERSION)
            .put("name", scenario.name)
            .put("points", points)
            .put("runMode", scenario.runMode.name)
            .put("speedMode", scenario.speedMode.name)
            .put("customSpeedMs", scenario.customSpeedMs)
            .put("useRealRoute", scenario.useRealRoute)
            .put("stopAtDestination", scenario.stopAtDestination)
            .put("enableStepSimulation", scenario.enableStepSimulation)
            .put("stepCadenceSpm", scenario.stepCadenceSpm)
            .put("isAutoCadence", scenario.isAutoCadence)
            .put("paceVariationSec", scenario.paceVariationSec)
            .put("fatigueLevel", scenario.fatigueLevel)
            .put("firstKmBiasSec", scenario.firstKmBiasSec)
            .put("fatigueStartKm", scenario.fatigueStartKm)
            .toString(2)
    }

    fun decode(json: String): Scenario {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Scenario file exceeds size limit" }
        val root = JSONObject(json)
        require(root.optString("format") == "location-spoofing-scenario") { "Unsupported scenario format" }
        require(root.optInt("version", -1) == FORMAT_VERSION) { "Unsupported scenario version" }
        val array = root.getJSONArray("points")
        require(array.length() in 2..MAX_POINTS) { "Scenario must contain between 2 and $MAX_POINTS route points" }
        val points = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            RoutePoint(
                lat = item.getDouble("lat"),
                lng = item.getDouble("lng"),
                waitSec = item.optDouble("waitSec", 0.0),
                elevationMeters = if (item.has("elevationMeters") && !item.isNull("elevationMeters")) item.getDouble("elevationMeters") else null,
                timestampEpochMillis = if (item.has("timestampEpochMillis") && !item.isNull("timestampEpochMillis")) item.getLong("timestampEpochMillis") else null
            )
        }
        val scenario = Scenario(
            name = root.optString("name", "Imported scenario").ifBlank { "Imported scenario" },
            points = points,
            runMode = enumValueOrDefault(root.optString("runMode"), RouteRunMode.LOOP),
            speedMode = enumValueOrDefault(root.optString("speedMode"), SimMode.WALKING),
            customSpeedMs = root.optDouble("customSpeedMs", 3.0),
            useRealRoute = root.optBoolean("useRealRoute", false),
            stopAtDestination = root.optBoolean("stopAtDestination", false),
            enableStepSimulation = root.optBoolean("enableStepSimulation", true),
            stepCadenceSpm = root.optInt("stepCadenceSpm", 165),
            isAutoCadence = root.optBoolean("isAutoCadence", true),
            paceVariationSec = root.optInt("paceVariationSec", 0),
            fatigueLevel = root.optInt("fatigueLevel", 0),
            firstKmBiasSec = root.optInt("firstKmBiasSec", 0),
            fatigueStartKm = root.optString("fatigueStartKm", "2.0")
        )
        validate(scenario)
        return scenario
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun validate(scenario: Scenario) {
        require(scenario.points.size in 2..MAX_POINTS) { "Scenario must contain between 2 and $MAX_POINTS route points" }
        scenario.points.forEach { point ->
            require(point.lat.isFinite() && point.lat in -90.0..90.0) { "Latitude is out of range" }
            require(point.lng.isFinite() && point.lng in -180.0..180.0) { "Longitude is out of range" }
            require(point.waitSec.isFinite() && point.waitSec in 0.0..86_400.0) { "Waypoint dwell time is out of range" }
            require(point.elevationMeters == null || point.elevationMeters.isFinite()) { "Waypoint elevation is invalid" }
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
    }
}
