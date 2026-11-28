package com.vincenthzr.locationspoofer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class GpxCodecTest {
    @Test
    fun parsesTrackSegmentsElevationAndTimestamps() {
        val xml = """
            <?xml version="1.0"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>Morning loop</name></metadata>
              <trk><name>Park route</name>
                <trkseg>
                  <trkpt lat="31.2304" lon="121.4737"><ele>12.5</ele><time>2026-01-02T03:04:05Z</time></trkpt>
                  <trkpt lat="31.2310" lon="121.4740"/>
                </trkseg>
                <trkseg><trkpt lat="31.2320" lon="121.4750"/></trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val document = GpxCodec.parse(xml)
        assertEquals(1, document.tracks.size)
        assertEquals("Park route", document.tracks.single().name)
        assertEquals(2, document.tracks.single().segments.size)
        assertEquals(3, document.pointCount)
        assertEquals(12.5, document.tracks[0].segments[0][0].elevationMeters!!, 0.0)
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), document.tracks[0].segments[0][0].time)
    }

    @Test
    fun parsesRoutesAndWaypoints() {
        val xml = """
            <gpx version="1.1" creator="test">
              <wpt lat="1" lon="2"><name>Start</name></wpt>
              <rte><name>Planned</name><rtept lat="3" lon="4"/><rtept lat="5" lon="6"/></rte>
            </gpx>
        """.trimIndent()
        val document = GpxCodec.parse(xml)
        assertEquals(1, document.waypoints.size)
        assertEquals(1, document.routes.size)
        assertEquals(2, document.routes.single().points.size)
    }

    @Test
    fun writesAndReadsTrackWithEscapedNameAndMetadata() {
        val point = GpxCodec.Point(
            35.681236,
            139.767125,
            40.2,
            Instant.parse("2026-02-03T04:05:06Z")
        )
        val xml = GpxCodec.writeTrack("A&B <loop>", listOf(listOf(point)))
        val parsed = GpxCodec.parse(xml)
        assertEquals("A&B <loop>", parsed.tracks.single().name)
        assertEquals(point, parsed.tracks.single().segments.single().single())
    }

    @Test
    fun rejectsOutOfRangeAndNonFiniteCoordinates() {
        val badLatitude = """<gpx version="1.1"><trk><trkseg><trkpt lat="91" lon="0"/></trkseg></trk></gpx>"""
        val badLongitude = """<gpx version="1.1"><trk><trkseg><trkpt lat="0" lon="Infinity"/></trkseg></trk></gpx>"""
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.parse(badLatitude) }
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.parse(badLongitude) }
    }

    @Test
    fun rejectsUnsupportedRootAndVersion() {
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.parse("<not-gpx/>") }
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.parse("""<gpx version="2.0"/>""") }
    }

    @Test
    fun rejectsDoctypeAndExternalEntities() {
        val xml = """<!DOCTYPE gpx [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><gpx version="1.1">&xxe;</gpx>"""
        assertThrows(Exception::class.java) { GpxCodec.parse(xml) }
    }

    @Test
    fun rejectsOversizedInput() {
        val oversized = "<gpx version=\"1.1\">" + " ".repeat(GpxCodec.MAX_DOCUMENT_BYTES) + "</gpx>"
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.parse(oversized) }
    }

    @Test
    fun emptySegmentIsNotSerializedAsAnEmptyTrack() {
        val xml = GpxCodec.writeTrack("route", listOf(emptyList(), listOf(GpxCodec.Point(0.0, 0.0))))
        assertEquals(1, GpxCodec.parse(xml).tracks.single().segments.size)
    }
}
