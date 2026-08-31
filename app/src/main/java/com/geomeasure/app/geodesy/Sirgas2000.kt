package com.geomeasure.app.geodesy

import kotlin.math.*

/**
 * UTM projection on the GRS80 ellipsoid used by SIRGAS 2000.
 *
 * Important: phone GNSS fixes are normally delivered in a WGS84-compatible terrestrial frame.
 * At smartphone-level accuracy this projection is suitable for operational field display, but
 * it is NOT a rigorous frame/epoch transformation from WGS84 to SIRGAS 2000 for centimeter work.
 * Professional RTK mode must use receiver/reference metadata and a proper geodetic transformation.
 */
object Sirgas2000 {
    private const val A = 6378137.0
    private const val INV_F = 298.257222101
    private const val F = 1.0 / INV_F
    private const val K0 = 0.9996
    private const val FALSE_EASTING = 500000.0
    private const val FALSE_NORTHING_SOUTH = 10000000.0

    data class Utm(
        val eastingM: Double,
        val northingM: Double,
        val zone: Int,
        val hemisphere: Char,
        val centralMeridianDeg: Double,
        val approximateFrameTransform: Boolean,
    ) {
        val zoneLabel: String get() = "$zone$hemisphere"
    }

    fun projectWgs84CompatibleFix(latitudeDeg: Double, longitudeDeg: Double): Utm {
        require(latitudeDeg in -80.0..84.0) { "UTM supports latitudes from 80°S to 84°N" }
        require(longitudeDeg in -180.0..180.0)

        val zone = (floor((longitudeDeg + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
        val centralMeridianDeg = zone * 6.0 - 183.0
        val lat = Math.toRadians(latitudeDeg)
        val lon = Math.toRadians(longitudeDeg)
        val lon0 = Math.toRadians(centralMeridianDeg)

        val e2 = F * (2.0 - F)
        val ep2 = e2 / (1.0 - e2)
        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val tanLat = tan(lat)
        val n = A / sqrt(1.0 - e2 * sinLat * sinLat)
        val t = tanLat * tanLat
        val c = ep2 * cosLat * cosLat
        val aa = cosLat * (lon - lon0)

        val e4 = e2 * e2
        val e6 = e4 * e2
        val m = A * (
            (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * lat -
                (3.0 * e2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * sin(2.0 * lat) +
                (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * sin(4.0 * lat) -
                (35.0 * e6 / 3072.0) * sin(6.0 * lat)
            )

        val easting = FALSE_EASTING + K0 * n * (
            aa + (1.0 - t + c) * aa.pow(3) / 6.0 +
                (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ep2) * aa.pow(5) / 120.0
            )

        var northing = K0 * (
            m + n * tanLat * (
                aa * aa / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c * c) * aa.pow(4) / 24.0 +
                    (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * ep2) * aa.pow(6) / 720.0
                )
            )
        val hemisphere = if (latitudeDeg < 0.0) 'S' else 'N'
        if (latitudeDeg < 0.0) northing += FALSE_NORTHING_SOUTH

        return Utm(
            eastingM = easting,
            northingM = northing,
            zone = zone,
            hemisphere = hemisphere,
            centralMeridianDeg = centralMeridianDeg,
            approximateFrameTransform = true,
        )
    }
}
