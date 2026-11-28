package com.vincenthzr.locationspoofer.utils

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Bounded, XXE-safe GPX 1.0/1.1 reader and GPX 1.1 writer.
 *
 * Track segments are preserved as separate lists. Route points and waypoints are
 * exposed separately so callers can decide how to merge them into an app route.
 */
object GpxCodec {
    const val MAX_DOCUMENT_BYTES = 10 * 1024 * 1024
    const val MAX_POINTS = 100_000

    data class Point(
        val latitude: Double,
        val longitude: Double,
        val elevationMeters: Double? = null,
        val time: Instant? = null
    )

    data class Track(
        val name: String,
        val segments: List<List<Point>>
    )

    data class Route(
        val name: String,
        val points: List<Point>
    )

    data class Document(
        val name: String = "",
        val tracks: List<Track> = emptyList(),
        val routes: List<Route> = emptyList(),
        val waypoints: List<Point> = emptyList()
    ) {
        val pointCount: Int
            get() = tracks.sumOf { track -> track.segments.sumOf { segment -> segment.size } } +
                routes.sumOf { it.points.size } + waypoints.size
    }

    fun parse(xml: String): Document {
        require(xml.toByteArray(StandardCharsets.UTF_8).size <= MAX_DOCUMENT_BYTES) {
            "GPX document exceeds the 10 MiB limit"
        }
        return parse(xml.toByteArray(StandardCharsets.UTF_8))
    }

    fun parse(bytes: ByteArray): Document {
        require(bytes.isNotEmpty()) { "GPX document is empty" }
        require(bytes.size <= MAX_DOCUMENT_BYTES) { "GPX document exceeds the 10 MiB limit" }

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setXIncludeAware(false)
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ ->
            throw org.xml.sax.SAXException("External entities are not allowed")
        }
        val root = builder.parse(ByteArrayInputStream(bytes)).documentElement
        require(root.localTag() == "gpx") { "Root element must be gpx" }
        val version = root.getAttribute("version")
        require(version == "1.0" || version == "1.1") { "Only GPX 1.0 and 1.1 are supported" }

        var total = 0
        fun point(element: Element): Point {
            val lat = element.getAttribute("lat").toDoubleOrNull()
                ?: throw IllegalArgumentException("Track point has an invalid latitude")
            val lon = element.getAttribute("lon").toDoubleOrNull()
                ?: throw IllegalArgumentException("Track point has an invalid longitude")
            require(lat.isFinite() && lat in -90.0..90.0) { "Latitude is out of range" }
            require(lon.isFinite() && lon in -180.0..180.0) { "Longitude is out of range" }
            total++
            require(total <= MAX_POINTS) { "GPX document contains more than $MAX_POINTS points" }

            val elevationText = element.directChild("ele")?.textContent?.trim()?.takeIf(String::isNotEmpty)
            val elevation = elevationText?.toDoubleOrNull()
            require(elevationText == null || elevation != null && elevation.isFinite()) { "Elevation is invalid" }
            val timeText = element.directChild("time")?.textContent?.trim()?.takeIf(String::isNotEmpty)
            val time = timeText?.let {
                try {
                    Instant.parse(it)
                } catch (_: Exception) {
                    throw IllegalArgumentException("Invalid GPX timestamp: $it")
                }
            }
            return Point(lat, lon, elevation, time)
        }

        val tracks = root.directChildren("trk").map { track ->
            val segments = track.directChildren("trkseg").map { segment ->
                segment.directChildren("trkpt").map(::point)
            }
            Track(track.directChild("name")?.textContent?.trim().orEmpty(), segments)
        }
        val routes = root.directChildren("rte").map { route ->
            Route(
                route.directChild("name")?.textContent?.trim().orEmpty(),
                route.directChildren("rtept").map(::point)
            )
        }
        val waypoints = root.directChildren("wpt").map(::point)
        return Document(
            name = root.directChild("metadata")?.directChild("name")?.textContent?.trim().orEmpty(),
            tracks = tracks,
            routes = routes,
            waypoints = waypoints
        )
    }

    /** Converts one or more route points into a portable GPX 1.1 track. */
    fun writeTrack(name: String, segments: List<List<Point>>): String {
        require(segments.isNotEmpty() && segments.any { it.isNotEmpty() }) {
            "At least one track point is required"
        }
        val count = segments.sumOf { it.size }
        require(count <= MAX_POINTS) { "Track contains more than $MAX_POINTS points" }
        segments.flatten().forEach {
            require(it.latitude.isFinite() && it.latitude in -90.0..90.0) { "Latitude is out of range" }
            require(it.longitude.isFinite() && it.longitude in -180.0..180.0) { "Longitude is out of range" }
            require(it.elevationMeters == null || it.elevationMeters.isFinite()) { "Elevation is invalid" }
        }

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<gpx version=\"1.1\" creator=\"LocationSp_Next\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            append("  <trk>\n")
            if (name.isNotBlank()) append("    <name>").append(escape(name)).append("</name>\n")
            segments.filter { it.isNotEmpty() }.forEach { segment ->
                append("    <trkseg>\n")
                segment.forEach { point ->
                    append("      <trkpt lat=\"").append(point.latitude).append("\" lon=\"")
                        .append(point.longitude).append("\">")
                    if (point.elevationMeters != null) {
                        append("<ele>").append(point.elevationMeters).append("</ele>")
                    }
                    if (point.time != null) append("<time>").append(point.time).append("</time>")
                    append("</trkpt>\n")
                }
                append("    </trkseg>\n")
            }
            append("  </trk>\n</gpx>\n")
        }
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun Element.localTag(): String = (localName ?: tagName.substringAfter(':')).lowercase()

    private fun Element.directChildren(tag: String): List<Element> {
        val result = mutableListOf<Element>()
        var child: Node? = firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && (child as Element).localTag() == tag) {
                result += child
            }
            child = child.nextSibling
        }
        return result
    }

    private fun Element.directChild(tag: String): Element? = directChildren(tag).firstOrNull()
}
