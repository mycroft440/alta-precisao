package com.geomeasure.app.gnss

import com.geomeasure.app.model.PointQuality
import kotlin.math.abs

object GnssQualityEvaluator {
    private const val MAX_FIX_AGE_MS = 5_000L

    /**
     * Evaluates the current autonomous-phone GNSS fix. When a monotonic reference is supplied,
     * elapsed realtime is preferred over wall-clock time because the wall clock can jump.
     */
    fun evaluate(
        snapshot: GnssSnapshot,
        nowMillis: Long = System.currentTimeMillis(),
        nowElapsedRealtimeNanos: Long? = null,
    ): PointQuality {
        if (!snapshot.hasFix || snapshot.isMock || !snapshot.providerEnabled) return PointQuality.REJECTED
        val hAcc = snapshot.horizontalAccuracyM ?: return PointQuality.REJECTED
        if (!hAcc.isFinite() || hAcc < 0.0) return PointQuality.REJECTED

        val ageMs = when {
            nowElapsedRealtimeNanos != null && snapshot.elapsedRealtimeNanos > 0L ->
                (nowElapsedRealtimeNanos - snapshot.elapsedRealtimeNanos) / 1_000_000L
            snapshot.timestampMillis > 0L -> nowMillis - snapshot.timestampMillis
            else -> Long.MAX_VALUE
        }
        if (ageMs < -1_000L || abs(ageMs) > MAX_FIX_AGE_MS) return PointQuality.REJECTED
        if (hAcc > 12.0 || snapshot.satellitesUsed < 4) return PointQuality.REJECTED

        val cn0 = snapshot.averageCn0DbHz ?: 0.0
        return when {
            hAcc <= 1.2 && snapshot.satellitesUsed >= 12 && cn0 >= 28.0 -> PointQuality.EXCELLENT
            hAcc <= 2.5 && snapshot.satellitesUsed >= 8 -> PointQuality.GOOD
            hAcc <= 5.0 && snapshot.satellitesUsed >= 6 -> PointQuality.MODERATE
            else -> PointQuality.POOR
        }
    }

    /**
     * Autonomous phone surveying is intentionally strict: only GOOD or EXCELLENT fixes may enter
     * a point occupation. MODERATE/POOR remain visible as diagnostics but are never stored as
     * measurement samples. Professional RTK will use its own FIXED/correction-age gate.
     */
    fun isCaptureQualityAllowed(quality: PointQuality): Boolean =
        quality == PointQuality.EXCELLENT || quality == PointQuality.GOOD
}
