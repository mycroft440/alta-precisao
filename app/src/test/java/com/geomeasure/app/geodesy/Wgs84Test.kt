package com.geomeasure.app.geodesy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Wgs84Test {
    @Test
    fun oneDegreeAtEquatorMatchesWgs84Ellipsoid() {
        val distance = Wgs84.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_319.4908, distance, 0.01)
    }

    @Test
    fun nearAntipodalDistanceRemainsFinite() {
        val distance = Wgs84.distanceMeters(0.0, 0.0, 0.0001, 179.9999)
        assertTrue(distance.isFinite())
        assertTrue(distance > 19_000_000.0)
    }

    @Test
    fun parcelAreaIsPositiveAndPerimeterClosesPolygon() {
        val points = listOf(
            Wgs84.Geo(-21.8360, -45.4000, 0.0),
            Wgs84.Geo(-21.8360, -45.3990, 0.0),
            Wgs84.Geo(-21.8350, -45.3990, 0.0),
            Wgs84.Geo(-21.8350, -45.4000, 0.0),
        )
        val measurement = Wgs84.measurePolygon(points)
        assertTrue(measurement.isValid)
        assertTrue(measurement.areaSquareMeters > 10_000.0)
        assertTrue(measurement.perimeterMeters > 400.0)
    }

    @Test
    fun bowTieBoundaryIsRejectedInsteadOfReturningMisleadingArea() {
        val points = listOf(
            Wgs84.Geo(-21.8360, -45.4000, 0.0),
            Wgs84.Geo(-21.8350, -45.3990, 0.0),
            Wgs84.Geo(-21.8360, -45.3990, 0.0),
            Wgs84.Geo(-21.8350, -45.4000, 0.0),
        )
        val measurement = Wgs84.measurePolygon(points)
        assertFalse(measurement.isValid)
        assertTrue(measurement.areaSquareMeters.isNaN())
    }

    @Test
    fun twoPointsAreOneSegmentNotDoubleClosedPerimeter() {
        val a = Wgs84.Geo(0.0, 0.0, 0.0)
        val b = Wgs84.Geo(0.0, 0.001, 0.0)
        val measurement = Wgs84.measurePolygon(listOf(a, b))
        assertTrue(measurement.isValid)
        assertEquals(0.0, measurement.areaSquareMeters, 0.0)
        assertEquals(Wgs84.distanceMeters(0.0, 0.0, 0.0, 0.001), measurement.perimeterMeters, 1e-6)
    }
}
