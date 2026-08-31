package com.geomeasure.app.gnss

import com.geomeasure.app.model.PointQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class GnssQualityEvaluatorTest {
    @Test
    fun mockAndStaleFixesAreRejected() {
        val now = System.currentTimeMillis()
        val good = GnssSnapshot(
            latitudeDeg = -21.8,
            longitudeDeg = -45.4,
            horizontalAccuracyM = 1.0,
            satellitesUsed = 14,
            averageCn0DbHz = 35.0,
            timestampMillis = now,
            elapsedRealtimeNanos = 10_000_000_000L,
        )
        assertEquals(PointQuality.REJECTED, GnssQualityEvaluator.evaluate(good.copy(isMock = true), now))
        assertEquals(PointQuality.REJECTED, GnssQualityEvaluator.evaluate(good.copy(timestampMillis = now - 6_000L), now))
        assertEquals(
            PointQuality.EXCELLENT,
            GnssQualityEvaluator.evaluate(good, now, nowElapsedRealtimeNanos = 10_500_000_000L),
        )
    }
}
