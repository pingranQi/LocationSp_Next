package com.vincenthzr.locationspoofer.utils

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses coordinates copied from common map apps and location links.
 *
 * Supported forms:
 * - "latitude, longitude"
 * - geo:latitude,longitude and geo:0,0?q=latitude,longitude
 * - Google Maps @latitude,longitude URLs
 * - URLs with lat/lng (or latitude/longitude) query parameters
 * - q, query, ll, center and destination query values containing a coordinate pair
 *
 * Coordinates are returned exactly as supplied. This parser does not guess or
 * silently convert between WGS-84, GCJ-02 and BD-09.
 */
object CoordinateInputParser {
    data class ParsedCoordinate(
        val latitude: Double,
        val longitude: Double,
        val source: Source
    )

    enum class Source {
        PLAIN_TEXT,
        GEO_URI,
        URL_PATH,
        URL_QUERY
    }

    private const val MAX_INPUT_LENGTH = 4096
    private val pairPattern = Regex(
        """^\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*[,，]\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*$"""
    )
    private val atPattern = Regex(
        """@([+-]?(?:\d+(?:\.\d*)?|\.\d+)),\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))"""
    )
    private val dataPattern = Regex(
        """!3d([+-]?(?:\d+(?:\.\d*)?|\.\d+))!4d([+-]?(?:\d+(?:\.\d*)?|\.\d+))"""
    )

    fun parse(input: String): ParsedCoordinate? {
        val value = input.trim()
        if (value.isEmpty() || value.length > MAX_INPUT_LENGTH) return null

        parsePair(value, Source.PLAIN_TEXT)?.let { return it }

        if (value.startsWith("geo:", ignoreCase = true)) {
            val body = value.substringAfter(':')
            val query = body.substringAfter('?', "")
            queryCoordinate(query)?.let { (lat, lng) ->
                return coordinate(lat, lng, Source.GEO_URI)
            }
            parsePair(body.substringBefore('?'), Source.GEO_URI)?.let { return it }
            return null
        }

        val query = value.substringAfter('?', "")
        queryCoordinate(query)?.let { (lat, lng) ->
            return coordinate(lat, lng, Source.URL_QUERY)
        }

        dataPattern.find(value)?.let { match ->
            coordinate(match.groupValues[1], match.groupValues[2], Source.URL_PATH)?.let { return it }
        }
        atPattern.find(value)?.let { match ->
            coordinate(match.groupValues[1], match.groupValues[2], Source.URL_PATH)?.let { return it }
        }

        return null
    }

    private fun queryCoordinate(query: String): Pair<String, String>? {
        if (query.isBlank()) return null
        val parameters = query.split('&').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val key = decode(part.substring(0, separator)).lowercase()
            val value = decode(part.substring(separator + 1))
            key to value
        }.toMap()

        val latitude = parameters["lat"] ?: parameters["latitude"]
        val longitude = parameters["lng"] ?: parameters["lon"] ?: parameters["longitude"]
        if (latitude != null && longitude != null) return latitude to longitude

        listOf("q", "query", "ll", "center", "destination", "daddr").forEach { key ->
            val candidate = parameters[key] ?: return@forEach
            val pair = pairPattern.matchEntire(candidate.trim()) ?: return@forEach
            return pair.groupValues[1] to pair.groupValues[2]
        }
        return null
    }

    private fun parsePair(value: String, source: Source): ParsedCoordinate? {
        val match = pairPattern.matchEntire(value) ?: return null
        return coordinate(match.groupValues[1], match.groupValues[2], source)
    }

    private fun coordinate(
        latitudeText: String,
        longitudeText: String,
        source: Source
    ): ParsedCoordinate? {
        val latitude = latitudeText.toDoubleOrNull() ?: return null
        val longitude = longitudeText.toDoubleOrNull() ?: return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return ParsedCoordinate(latitude, longitude, source)
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: IllegalArgumentException) {
        value
    }
}
