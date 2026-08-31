package com.geomeasure.app.survey

import com.geomeasure.app.gnss.GnssSnapshot
import com.geomeasure.app.model.PointQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun capturedPointUsesConservativeWorstQuality() {
        val now = System.currentTimeMillis()
        val base = now - 1_200L
        val engine = PointCaptureEngine(minimumSamples = 3, maximumSamples = 10, minimumObservationMillis = 1_000L)
        engine.add(snapshot(base, 1_000_000_000L, 0, hAcc = 1.0, sats = 14, cn0 = 35.0))
        engine.add(snapshot(base + 600, 1_600_000_000L, 1, hAcc = 2.0, sats = 10, cn0 = 32.0))
        engine.add(snapshot(now, 2_200_000_000L, 2, hAcc = 8.0, sats = 5, cn0 = 22.0))

        val point = engine.buildPoint(1)
        assertNotNull(point)
        assertEquals(PointQuality.POOR, point!!.quality)
        assertEquals(8.0, point.horizontalAccuracyM, 0.0)
        assertEquals(5, point.satellitesUsed)
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
