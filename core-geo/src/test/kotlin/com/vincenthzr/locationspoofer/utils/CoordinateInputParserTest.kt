package com.vincenthzr.locationspoofer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateInputParserTest {
    @Test
    fun parsesPlainCoordinatePair() {
        val result = CoordinateInputParser.parse(" 39.9087, 116.3975 ")
        assertEquals(39.9087, result?.latitude ?: Double.NaN, 1e-9)
        assertEquals(116.3975, result?.longitude ?: Double.NaN, 1e-9)
        assertEquals(CoordinateInputParser.Source.PLAIN_TEXT, result?.source)
    }

    @Test
    fun parsesChineseComma() {
        val result = CoordinateInputParser.parse("31.2304，121.4737")
        assertEquals(31.2304, result?.latitude ?: Double.NaN, 1e-9)
        assertEquals(121.4737, result?.longitude ?: Double.NaN, 1e-9)
    }

    @Test
    fun parsesGeoUriAndPrefersSearchCoordinate() {
        val result = CoordinateInputParser.parse("geo:0,0?q=22.5431%2C114.0579")
        assertEquals(22.5431, result?.latitude ?: Double.NaN, 1e-9)
        assertEquals(114.0579, result?.longitude ?: Double.NaN, 1e-9)
        assertEquals(CoordinateInputParser.Source.GEO_URI, result?.source)
    }

    @Test
    fun parsesLatLngQueryParameters() {
        val result = CoordinateInputParser.parse("https://maps.example/?lat=30.5728&lng=104.0668")
        assertEquals(30.5728, result?.latitude ?: Double.NaN, 1e-9)
        assertEquals(104.0668, result?.longitude ?: Double.NaN, 1e-9)
        assertEquals(CoordinateInputParser.Source.URL_QUERY, result?.source)
    }

    @Test
    fun parsesGoogleMapsAtPath() {
        val result = CoordinateInputParser.parse("https://www.google.com/maps/@37.422,-122.084,15z")
        assertEquals(37.422, result?.latitude ?: Double.NaN, 1e-9)
        assertEquals(-122.084, result?.longitude ?: Double.NaN, 1e-9)
        assertEquals(CoordinateInputParser.Source.URL_PATH, result?.source)
    }

    @Test
    fun parsesGoogleMapsPlaceDataCoordinates() {
        val result = CoordinateInputParser.parse("https://www.google.com/maps/place/X/data=!3d45.8038!4d126.5349")
        assertEquals(45.8038, result?.latitude ?: Double.NaN, 1e-9)
        assertEquals(126.5349, result?.longitude ?: Double.NaN, 1e-9)
    }

    @Test
    fun parsesQueryPairAndEncodedComma() {
        val result = CoordinateInputParser.parse("https://maps.example/?q=30.1%2C120.2")
        assertEquals(30.1, result?.latitude ?: Double.NaN, 1e-9)
        assertEquals(120.2, result?.longitude ?: Double.NaN, 1e-9)
    }

    @Test
    fun rejectsMalformedAndOutOfRangeInputs() {
        listOf(
            "",
            "not a coordinate",
            "91,0",
            "0,181",
            "NaN,120",
            "Infinity,120",
            "1,2,3",
            "https://maps.example/?lat=abc&lng=120"
        ).forEach { assertNull("input=$it", CoordinateInputParser.parse(it)) }
    }

    @Test
    fun rejectsOversizedInput() {
        assertNull(CoordinateInputParser.parse("1,2" + "x".repeat(5000)))
    }
}
