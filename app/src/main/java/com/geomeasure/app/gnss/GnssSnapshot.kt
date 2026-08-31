package com.geomeasure.app.gnss

data class GnssSnapshot(
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val ellipsoidalHeightM: Double? = null,
    val horizontalAccuracyM: Double? = null,
    val verticalAccuracyM: Double? = null,
    val satellitesVisible: Int = 0,
    val satellitesUsed: Int = 0,
    val averageCn0DbHz: Double? = null,
    val rawMeasurements: Int = 0,
    /** Number of satellites for which measurements were seen in both a lower and upper GNSS band. */
    val dualFrequencySignals: Int = 0,
    /** UTC epoch time reported for the fix. Use only for display/persistence, not ordering. */
    val timestampMillis: Long = 0L,
    /** Monotonic Android elapsed-realtime timestamp of the fix. */
    val elapsedRealtimeNanos: Long = 0L,
    val providerEnabled: Boolean = true,
    val isMock: Boolean = false,
    /** Last GNSS startup/runtime error suitable for field UI; cleared on a fresh valid location. */
    val errorMessage: String? = null,
    val rawLogPath: String? = null,
) {
    val hasFix: Boolean
        get() = providerEnabled && !isMock &&
            latitudeDeg?.isFinite() == true && latitudeDeg in -90.0..90.0 &&
            longitudeDeg?.isFinite() == true && longitudeDeg in -180.0..180.0 &&
            horizontalAccuracyM?.isFinite() == true && horizontalAccuracyM >= 0.0
}
