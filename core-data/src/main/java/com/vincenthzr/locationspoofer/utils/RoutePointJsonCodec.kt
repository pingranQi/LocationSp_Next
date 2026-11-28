package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.data.model.RoutePoint
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Version-compatible JSON representation for saved route points.
 *
 * Older versions stored only lat/lng. Missing metadata fields decode to RoutePoint defaults,
 * while new versions preserve waypoint dwell, elevation and recorded timestamps.
 */
object RoutePointJsonCodec {
    const val MAX_POINTS = 100_000
    const val MAX_BYTES = 10 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(points: List<RoutePoint>): String {
        validate(points)
        return json.encodeToString(points).also { encoded ->
            require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
                "Route data exceeds size limit"
            }
        }
    }

    fun decode(source: String): List<RoutePoint> {
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Route data exceeds size limit"
        }
        return json.decodeFromString<List<RoutePoint>>(source).also(::validate)
    }

    private fun validate(points: List<RoutePoint>) {
        require(points.size <= MAX_POINTS) { "Route contains too many points" }
        points.forEach { point ->
            require(point.lat.isFinite() && point.lat in -90.0..90.0) {
                "Route latitude is out of range"
            }
            require(point.lng.isFinite() && point.lng in -180.0..180.0) {
                "Route longitude is out of range"
            }
            require(point.waitSec.isFinite() && point.waitSec in 0.0..86_400.0) {
                "Route waypoint dwell time is out of range"
            }
            val elevation = point.elevationMeters
            require(elevation == null || elevation.isFinite()) {
                "Route elevation is invalid"
            }
        }
    }
}
