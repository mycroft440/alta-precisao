package com.geomeasure.app.model

data class SurveyPoint(
    val id: Int,
    val projectId: Long = 0L,
    val databaseId: Long = 0L,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val ellipsoidalHeightM: Double?,
    val horizontalAccuracyM: Double,
    val verticalAccuracyM: Double?,
    val satellitesUsed: Int,
    val averageCn0DbHz: Double?,
    val rawMeasurements: Int,
    val capturedAtMillis: Long,
    val observationCount: Int,
    val dispersionM: Double,
    val ellipseSemiMajorM: Double? = null,
    val ellipseSemiMinorM: Double? = null,
    val ellipseAzimuthDeg: Double? = null,
    val quality: PointQuality,
)

enum class PointQuality {
    EXCELLENT,
    GOOD,
    MODERATE,
    POOR,
    REJECTED,
}
