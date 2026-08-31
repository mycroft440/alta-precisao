package com.geomeasure.app.survey

import com.geomeasure.app.gnss.GnssSnapshot
import com.geomeasure.app.model.PointQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PointCaptureEngineTest {
    @Test
    fun sameLocationFixIsNotCountedAgainWhenOnlyGnssMetadataChanges() {
        val now = System.currentTimeMillis()
        val engine = PointCaptureEngine(minimumSamples = 3, maximumSamples = 10, minimumObservationMillis = 0)
        val fix = snapshot(now, elapsedNanos = 1_000_000_000L, index = 0)

        assertEquals(1, engine.add(fix).acceptedSamples)
        assertEquals(1, engine.add(fix.copy(rawMeasurements = 45, satellitesVisible = 20)).acceptedSamples)
    }

    @Test
    fun moderateAndPoorSamplesNeverEnterOccupation() {
        val now = System.currentTimeMillis()
        val base = now - 1_200L
        val engine = PointCaptureEngine(minimumSamples = 3, maximumSamples = 10, minimumObservationMillis = 1_000L)

        assertEquals(1, engine.add(snapshot(base, 1_000_000_000L, 0, hAcc = 1.0, sats = 14, cn0 = 35.0)).acceptedSamples)
        assertEquals(1, engine.add(snapshot(base + 300, 1_300_000_000L, 1, hAcc = 8.0, sats = 5, cn0 = 22.0)).acceptedSamples)
        assertEquals(2, engine.add(snapshot(base + 600, 1_600_000_000L, 2, hAcc = 2.0, sats = 10, cn0 = 32.0)).acceptedSamples)
        assertEquals(3, engine.add(snapshot(now, 2_200_000_000L, 3, hAcc = 2.0, sats = 10, cn0 = 31.0)).acceptedSamples)

        val point = engine.buildPoint(1)
        assertNotNull(point)
        assertEquals(PointQuality.GOOD, point!!.quality)
        assertEquals(2.0, point.horizontalAccuracyM, 0.0)
        assertEquals(10, point.satellitesUsed)
    }

    @Test
    fun spatiallyUnstableCloudIsNotSavedEvenWithEnoughSamples() {
        val now = System.currentTimeMillis()
        val engine = PointCaptureEngine(
            minimumSamples = 4,
            maximumSamples = 10,
            minimumObservationMillis = 0,
            maximumDispersionM = 0.25,
            maximumEllipseSemiMajorM = 0.50,
        )
        val base = snapshot(now - 1_000L, 1_000_000_000L, 0, hAcc = 1.5, sats = 10, cn0 = 32.0)
        val lat = base.latitudeDeg!!
        val lon = base.longitudeDeg!!

        engine.add(base.copy(latitudeDeg = lat + 0.000009, longitudeDeg = lon, timestampMillis = now - 900L, elapsedRealtimeNanos = 1_100_000_000L))
        engine.add(base.copy(latitudeDeg = lat - 0.000009, longitudeDeg = lon, timestampMillis = now - 600L, elapsedRealtimeNanos = 1_400_000_000L))
        engine.add(base.copy(latitudeDeg = lat, longitudeDeg = lon + 0.000009, timestampMillis = now - 300L, elapsedRealtimeNanos = 1_700_000_000L))
        engine.add(base.copy(latitudeDeg = lat, longitudeDeg = lon - 0.000009, timestampMillis = now, elapsedRealtimeNanos = 2_000_000_000L))

        assertNull(engine.buildPoint(1))
    }

    private fun snapshot(
        timeMillis: Long,
        elapsedNanos: Long,
        index: Int,
        hAcc: Double = 2.0,
        sats: Int = 10,
        cn0: Double = 30.0,
    ) = GnssSnapshot(
        latitudeDeg = -21.8333 + index * 0.0000001,
        longitudeDeg = -45.4000 + index * 0.0000001,
        ellipsoidalHeightM = 900.0,
        horizontalAccuracyM = hAcc,
        verticalAccuracyM = 3.0,
        satellitesVisible = 18,
        satellitesUsed = sats,
        averageCn0DbHz = cn0,
        rawMeasurements = 30,
        timestampMillis = timeMillis,
        elapsedRealtimeNanos = elapsedNanos,
    )
}
