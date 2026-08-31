package com.geomeasure.app.rtk

enum class RtkFixType {
    NONE,
    AUTONOMOUS,
    DGPS,
    RTK_FIXED,
    RTK_FLOAT,
    ESTIMATED,
    UNKNOWN,
}

data class RtkPosition(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    /** NMEA GGA altitude above mean sea level. */
    val orthometricHeightM: Double?,
    /** NMEA GGA geoid separation. Ellipsoidal height = orthometric + separation. */
    val geoidSeparationM: Double?,
    val ellipsoidalHeightM: Double?,
    val fixType: RtkFixType,
    val satellites: Int,
    val hdop: Double?,
    val correctionAgeSeconds: Double?,
    val stationId: String?,
    val timestampUtc: String?,
)
